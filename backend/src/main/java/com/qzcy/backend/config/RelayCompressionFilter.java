package com.qzcy.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import com.qzcy.backend.service.RelayCompressionService;
import com.qzcy.backend.service.RelayCompressionService.CompressionException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Decode before Spring's JSON converter; never retain an unbounded byte array. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RelayCompressionFilter extends OncePerRequestFilter {
    public static final String BODY_ATTRIBUTE = RelayCompressionFilter.class.getName() + ".body";
    private static final int CHUNK_BYTES = 64 * 1024;
    private static final int MAX_ENCODED_BYTES = 18 * 1024 * 1024;
    private static final Set<String> PATHS = Set.of(
            "/api/v1/chat/completions",
            "/api/v1/responses", "/api/responses", "/api/v1/v1/responses",
            "/api/v1/responses/compact", "/api/responses/compact", "/api/v1/v1/responses/compact",
            "/api/v1/messages", "/api/messages", "/api/v1/v1/messages",
            "/api/v1/messages/count_tokens", "/api/messages/count_tokens", "/api/v1/v1/messages/count_tokens");
    private final RelayCompressionService service;
    private final ObjectMapper mapper;

    public RelayCompressionFilter(RelayCompressionService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !PATHS.contains(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String encoding = String.join(",", Collections.list(request.getHeaders("Content-Encoding")))
                .trim().toLowerCase(Locale.ROOT);
        if (encoding.isEmpty() || "identity".equals(encoding)) {
            chain.doFilter(request, response);
            return;
        }
        DecodedBody body;
        try {
            if (!Set.of("gzip", "zstd").contains(encoding)) {
                throw new CompressionException(415, "unsupported_content_encoding", "仅支持单层 gzip 或 zstd 请求压缩");
            }
            String contentType = request.getContentType();
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw new CompressionException(415, "unsupported_media_type", "压缩请求仍须使用 Content-Type: application/json");
            }
            body = decode(request.getInputStream(), encoding);
        } catch (CompressionException ex) {
            writeError(request, response, ex);
            return;
        } catch (IllegalArgumentException ex) {
            writeError(request, response, new CompressionException(415, "unsupported_media_type", "Content-Type 无效"));
            return;
        }
        request.setAttribute(BODY_ATTRIBUTE, body);
        try {
            chain.doFilter(new DecodedRequest(request, body), response);
        } finally {
            // Fallback for JSON errors, authentication failures and aborted connections.
            body.close();
            request.removeAttribute(BODY_ATTRIBUTE);
        }
    }

    private DecodedBody decode(InputStream source, String encoding) {
        RelayCompressionService.Lease lease = service.acquire(encoding);
        DecodedBody body = new DecodedBody(lease);
        try (InputStream limited = new EncodedLimitStream(source);
             InputStream decoder = decoder(limited, encoding)) {
            int total = 0;
            while (true) {
                byte[] chunk = new byte[CHUNK_BYTES];
                int length = decoder.readNBytes(chunk, 0, chunk.length);
                if (length == 0) break;
                total += length;
                if (total > RelayCompressionService.MAX_BODY_BYTES) {
                    throw new CompressionException(413, "decompressed_body_too_large", "解压后请求体不能超过 16 MiB");
                }
                body.chunks.add(new Chunk(chunk, length));
            }
            body.length = total;
            return body;
        } catch (CompressionException ex) {
            body.close();
            throw ex;
        } catch (IOException ex) {
            body.close();
            throw new CompressionException(400, "invalid_compressed_body", "压缩请求损坏、不完整或 zstd 窗口超过 8 MiB");
        } catch (RuntimeException | Error ex) {
            body.close();
            throw ex;
        }
    }

    private InputStream decoder(InputStream source, String encoding) throws IOException {
        if ("gzip".equals(encoding)) return new GZIPInputStream(source, CHUNK_BYTES);
        ZstdInputStream stream = new ZstdInputStream(source);
        try {
            return stream.setLongMax(RelayCompressionService.ZSTD_WINDOW_LOG_MAX).setContinuous(false);
        } catch (IOException | RuntimeException ex) {
            stream.close();
            throw ex;
        }
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, CompressionException ex) throws IOException {
        response.setStatus(ex.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (ex.status() == 503) response.setHeader("Retry-After", "1");
        Map<String, Object> error = Map.of("type", ex.status() == 503 ? "overloaded_error" : "invalid_request_error",
                "code", ex.code(), "message", ex.getMessage());
        Map<String, Object> payload = request.getRequestURI().contains("/messages")
                ? Map.of("type", "error", "error", error) : Map.of("error", error);
        mapper.writeValue(response.getOutputStream(), payload);
    }

    private static final class EncodedLimitStream extends FilterInputStream {
        private long count;
        private EncodedLimitStream(InputStream source) { super(source); }
        private void count(int n) {
            if (n > 0 && (count += n) > MAX_ENCODED_BYTES) {
                throw new CompressionException(413, "compressed_body_too_large", "压缩请求体不能超过 18 MiB");
            }
        }
        @Override public int read() throws IOException {
            int value = in.read();
            count(value < 0 ? 0 : 1);
            return value;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            count(n);
            return n;
        }
    }

    private record Chunk(byte[] bytes, int length) {}

    public static final class DecodedBody extends ServletInputStream {
        private final Deque<Chunk> chunks = new ArrayDeque<>();
        private final RelayCompressionService.Lease lease;
        private Chunk current;
        private int offset;
        private int length;
        private boolean closed;
        private DecodedBody(RelayCompressionService.Lease lease) { this.lease = lease; }
        private boolean next() {
            if (current == null || offset == current.length()) {
                current = chunks.poll();
                offset = 0;
            }
            return current != null;
        }
        @Override public int read() {
            return next() ? current.bytes()[offset++] & 0xff : -1;
        }
        @Override public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) return 0;
            if (!next()) return -1;
            int n = Math.min(len, current.length() - offset);
            System.arraycopy(current.bytes(), offset, b, off, n);
            offset += n;
            return n;
        }
        @Override public boolean isFinished() {
            return (current == null || offset == current.length()) && chunks.isEmpty();
        }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("Relay JSON request parsing is synchronous");
        }
        @Override public void close() {
            if (!closed) {
                closed = true;
                chunks.clear();
                current = null;
                lease.close();
            }
        }
    }

    private static final class DecodedRequest extends HttpServletRequestWrapper {
        private final DecodedBody body;
        private DecodedRequest(HttpServletRequest request, DecodedBody body) { super(request); this.body = body; }
        private boolean hidden(String name) {
            return "Content-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name);
        }
        @Override public String getHeader(String name) { return hidden(name) ? null : super.getHeader(name); }
        @Override public Enumeration<String> getHeaders(String name) {
            return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }
        @Override public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream().filter(name -> !hidden(name)).toList());
        }
        @Override public int getIntHeader(String name) { return hidden(name) ? -1 : super.getIntHeader(name); }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() { return body; }
        @Override public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(body,
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }
}
