package com.qzcy.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdOutputStream;
import com.qzcy.backend.controller.RelayController;
import com.qzcy.backend.controller.RelayCompressionConfigController;
import com.qzcy.backend.dto.RelayCompressionConfigDto;
import com.qzcy.backend.dto.relay.*;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RelayCompressionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JdbcTemplate jdbc;
    private RelayCompressionService service;
    private RelayDispatchService dispatch;
    private RelayCompressionFilter filter;
    private MockMvc mvc;
    private static final List<String> PATHS = List.of("/api/v1/chat/completions",
            "/api/v1/responses", "/api/responses", "/api/v1/v1/responses",
            "/api/v1/responses/compact", "/api/responses/compact", "/api/v1/v1/responses/compact",
            "/api/v1/messages", "/api/messages", "/api/v1/v1/messages",
            "/api/v1/messages/count_tokens", "/api/messages/count_tokens", "/api/v1/v1/messages/count_tokens");

    @BeforeEach
    void setup() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        service = new RelayCompressionService(jdbc);
        dispatch = mock(RelayDispatchService.class);
        RelayController controller = new RelayController(mock(RelayService.class), dispatch,
                mock(RelayPolicyService.class), mock(RelayChannelStatusService.class), mock(RelayModelMapper.class),
                mock(RelayGroupMapper.class), mock(RelayGroupModelMapper.class), mock(RelayChannelModelMapper.class),
                mock(RelayUsageLogMapper.class), mock(UserMapper.class), mapper);
        filter = new RelayCompressionFilter(service, mapper);
        mvc = MockMvcBuilders.standaloneSetup(controller, new RelayCompressionConfigController(service))
                .setControllerAdvice(new com.qzcy.backend.exception.GlobalExceptionHandler(), new RelayCompressionBodyAdvice()).addFilters(filter).build();
        when(dispatch.dispatch(any())).thenAnswer(call -> {
            assertEquals(0, service.status().reservedMb(), "release BEFORE upstream dispatch");
            RelayDispatchRequest request = call.getArgument(0);
            return new RelayDispatchResult(200, "application/json", request.body().toString());
        });
    }

    private byte[] compress(byte[] raw, String encoding) throws IOException {
        if (encoding.equals("zstd")) return Zstd.compress(raw);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var stream = new java.util.zip.GZIPOutputStream(bytes)) { stream.write(raw); }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "zstd"})
    void realRelayRoutesDecodeBothFormatsAndReleaseBeforeDispatch(String encoding) throws Exception {
        byte[] raw = "{\"model\":\"test\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}".getBytes(StandardCharsets.UTF_8);
        for (String path : PATHS) {
            mvc.perform(post(path).contentType("application/json").header("Content-Encoding", encoding).content(compress(raw, encoding)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].content").value("你好"));
        }
        assertEquals(0, service.status().activeRequests());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "zstd"})
    void streamingDoesNotHoldBudget(String encoding) throws Exception {
        when(dispatch.dispatchStream(any())).thenAnswer(call -> {
            assertEquals(0, service.status().reservedMb());
            return new RelayStreamDispatchResult(200, "text/event-stream", output -> {
                assertEquals(0, service.status().activeRequests());
                output.write("data: done\n\n".getBytes(StandardCharsets.UTF_8));
            });
        });
        for (String path : List.of("/api/v1/chat/completions", "/api/v1/responses", "/api/v1/messages")) {
            mvc.perform(post(path).contentType("application/json").header("Content-Encoding", encoding)
                    .content(compress("{\"model\":\"test\",\"stream\":true}".getBytes(), encoding)))
                    .andExpect(status().isOk()).andExpect(content().string("data: done\n\n"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"gzip", "zstd"})
    void corruptTruncatedAndOversizedBodiesNeverReachUpstream(String encoding) throws Exception {
        byte[] compressed = compress("{\"model\":\"test\"}".getBytes(), encoding);
        for (byte[] bytes : List.of(new byte[]{1, 2, 3}, Arrays.copyOf(compressed, compressed.length - 3))) {
            mvc.perform(post("/api/v1/messages").contentType("application/json").header("Content-Encoding", encoding).content(bytes))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("error"));
            assertEquals(0, service.status().reservedMb());
        }
        byte[] large = new byte[RelayCompressionService.MAX_BODY_BYTES + 1];
        Arrays.fill(large, (byte) 'a');
        mvc.perform(post("/api/v1/responses").contentType("application/json").header("Content-Encoding", encoding).content(compress(large, encoding)))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(0, service.status().activeRequests());
        verifyNoInteractions(dispatch);
    }

    @Test
    void rejectsLargeZstdWindow() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var zstd = new ZstdOutputStream(bytes).setWindowLog(24)) {
            zstd.write(new byte[10 * 1024 * 1024]);
        }
        mvc.perform(post("/api/v1/responses").contentType("application/json").header("Content-Encoding", "zstd").content(bytes.toByteArray()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("invalid_compressed_body"));
        assertEquals(0, service.status().reservedMb());
    }

    @Test
    void disabledEncodingUnknownEncodingAndInvalidJsonHaveUsefulStatus() throws Exception {
        service.update(new RelayCompressionConfigDto(false, false, 128));
        for (String encoding : List.of("gzip", "zstd", "br", "gzip, zstd")) {
            mvc.perform(post("/api/v1/chat/completions").contentType("application/json").header("Content-Encoding", encoding).content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
        }
        mvc.perform(post("/api/v1/chat/completions").contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        service.update(new RelayCompressionConfigDto(true, true, 128));
        mvc.perform(post("/api/v1/chat/completions").contentType("application/json").header("Content-Encoding", "gzip")
                .content(compress("{bad".getBytes(), "gzip"))).andExpect(status().isBadRequest());
        assertEquals(0, service.status().reservedMb());
    }

    @Test
    void globalBudgetDynamicReductionAndIdempotentRelease() throws Exception {
        var a = service.acquire("gzip");
        var b = service.acquire("zstd");
        var c = service.acquire("gzip");
        var d = service.acquire("zstd");
        assertEquals(128, service.status().reservedMb());
        mvc.perform(post("/api/v1/chat/completions").contentType("application/json").header("Content-Encoding", "gzip").content("any"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"));
        service.update(new RelayCompressionConfigDto(true, true, 32));
        a.close(); b.close(); c.close();
        assertThrows(RelayCompressionService.CompressionException.class, () -> service.acquire("gzip"));
        d.close(); d.close();
        try (var lease = service.acquire("gzip")) { assertEquals(32, service.status().reservedMb()); }
        assertEquals(0, service.status().activeRequests());
        verifyNoInteractions(dispatch);
    }

    @Test
    void removesEncodingAndStaleLengthAndReleasesOnEarlyAbort() throws Exception {
        byte[] raw = "{\"model\":\"test\"}".getBytes();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/responses");
        request.setContentType("application/json");
        request.setContent(compress(raw, "gzip"));
        request.addHeader("Content-Encoding", "gzip");
        request.addHeader("Content-Length", request.getContentAsByteArray().length);
        assertThrows(IOException.class, () -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var wrapped = (jakarta.servlet.http.HttpServletRequest) req;
            assertNull(wrapped.getHeader("Content-Encoding"));
            assertNull(wrapped.getHeader("Content-Length"));
            assertFalse(java.util.Collections.list(wrapped.getHeaderNames()).contains("Content-Encoding"));
            assertEquals(raw.length, wrapped.getContentLength());
            assertArrayEquals(raw, wrapped.getInputStream().readAllBytes());
            throw new IOException("aborted");
        }));
        assertEquals(0, service.status().reservedMb());
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsPersistReloadAndInvalidUpdatesCannotPublish() throws Exception {
        mvc.perform(put("/api/admin/relay/compression-config").contentType("application/json")
                .content("{\"gzipEnabled\":true,\"zstdEnabled\":true,\"memoryBudgetMb\":128.5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/relay/compression-config"))
                .andExpect(jsonPath("$.data.memoryBudgetMb").value(128));
        mvc.perform(put("/api/admin/relay/compression-config").contentType("application/json")
                .content("{\"gzipEnabled\":false,\"zstdEnabled\":true,\"memoryBudgetMb\":64}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.gzipEnabled").value(false));
        verify(jdbc).update(startsWith("UPDATE relay_compression_config"), eq(false), eq(true), eq(64));
        assertThrows(BusinessException.class, () -> service.update(new RelayCompressionConfigDto(true, true, 31)));
        doThrow(new IllegalStateException("db failed")).when(jdbc).update(startsWith("UPDATE relay_compression_config"), eq(true), eq(true), eq(256));
        assertThrows(IllegalStateException.class, () -> service.update(new RelayCompressionConfigDto(true, true, 256)));
        assertEquals(64, service.current().memoryBudgetMb());
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(new RelayCompressionConfigDto(false, true, 64));
        var restarted = new RelayCompressionService(jdbc);
        restarted.run();
        assertEquals(service.current(), restarted.current());
    }

    @Test
    void simultaneousRequestsCannotOverbookBudget() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        var admitted = new java.util.concurrent.CountDownLatch(8);
        var release = new java.util.concurrent.CountDownLatch(1);
        var accepted = new java.util.concurrent.atomic.AtomicInteger();
        var rejected = new java.util.concurrent.atomic.AtomicInteger();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    RelayCompressionService.Lease lease = null;
                    try {
                        lease = service.acquire("zstd");
                        accepted.incrementAndGet();
                    } catch (RelayCompressionService.CompressionException ex) {
                        assertEquals(503, ex.status());
                        rejected.incrementAndGet();
                    } finally {
                        admitted.countDown();
                    }
                    try { release.await(); } finally { if (lease != null) lease.close(); }
                    return null;
                }));
            }
            start.countDown();
            assertTrue(admitted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(4, accepted.get());
            assertEquals(4, rejected.get());
            assertEquals(128, service.status().reservedMb());
            release.countDown();
            for (var future : futures) future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0, service.status().reservedMb());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
