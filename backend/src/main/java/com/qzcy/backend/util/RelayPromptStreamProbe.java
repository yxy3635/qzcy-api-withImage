package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Inspect a possible refusal before committing HTTP headers; replay every byte for normal SSE. */
public final class RelayPromptStreamProbe implements AutoCloseable {
    private static final int MEMORY_LIMIT = 64 * 1024;
    private final InputStream upstream;
    private final ObjectMapper mapper;
    private final String path;
    private final boolean inspectToCompletion;
    private final Runnable activity;
    private final RelayPromptRefusalDetector.Stream detector = new RelayPromptRefusalDetector.Stream();
    private final ByteArrayOutputStream event = new ByteArrayOutputStream();
    private ByteArrayOutputStream memory = new ByteArrayOutputStream();
    private OutputStream captured = memory;
    private Path file;
    private boolean lineEmpty = true;
    private boolean terminal;
    private boolean error;
    private IOException readFailure;

    public RelayPromptStreamProbe(InputStream upstream, ObjectMapper mapper, String path, boolean multipleChoices) {
        this(upstream, mapper, path, multipleChoices, () -> { });
    }

    public RelayPromptStreamProbe(InputStream upstream, ObjectMapper mapper, String path, boolean multipleChoices, Runnable activity) {
        this.upstream = upstream;
        this.mapper = mapper;
        this.path = path;
        // Responses can add further assistant messages after a regular answer.
        // Any of those messages may match; headers must remain uncommitted until completion.
        this.inspectToCompletion = multipleChoices || "/v1/responses".equals(path);
        this.activity = activity;
    }

    public boolean inspect() throws IOException {
        byte[] buffer = new byte[8192];
        while (!terminal && (inspectToCompletion || !detector.hasNonRefusalText())) {
            int length;
            try { length = upstream.read(buffer); } catch (IOException ex) { readFailure = ex; break; }
            if (length < 0) break;
            if (length == 0) continue;
            activity.run();
            if (file == null && memory.size() + length > MEMORY_LIMIT) {
                file = Files.createTempFile("relay-policy-", ".sse");
                captured = Files.newOutputStream(file);
                memory.writeTo(captured);
                memory = null;
            }
            captured.write(buffer, 0, length);
            for (int i = 0; i < length && !terminal; i++) {
                int value = buffer[i] & 0xff;
                event.write(value);
                if (value == '\n') {
                    if (lineEmpty) inspectEvent();
                    lineEmpty = true;
                } else if (value != '\r') lineEmpty = false;
                if (event.size() > 32 * 1024 * 1024) {
                    // Replay through the existing failure/usage settlement path.
                    readFailure = new IOException("Upstream SSE event exceeds size limit");
                    event.reset();
                    return false;
                }
            }
        }
        return terminal && !error && detector.matched();
    }

    private void inspectEvent() {
        String raw = event.toString(StandardCharsets.UTF_8);
        event.reset();
        StringBuilder data = new StringBuilder();
        for (String line : raw.split("\\R")) {
            if (line.startsWith("data:")) data.append(line.substring(5).stripLeading()).append('\n');
            if (line.startsWith("event:") && "error".equals(line.substring(6).trim())) {
                error = true;
                terminal = true;
            }
        }
        String payload = data.toString().trim();
        if ("[DONE]".equals(payload)) {
            if (path.endsWith("/chat/completions") || path.endsWith("/completions")) terminal = true;
            return;
        }
        try {
            JsonNode json = mapper.readTree(payload);
            if (json == null) return;
            detector.accept(json, path);
            String type = json.path("type").asText();
            error |= json.hasNonNull("error") || "error".equals(type) || "response.failed".equals(type) || "response.incomplete".equals(type);
            terminal |= error || (path.endsWith("/messages") && "message_stop".equals(type))
                    || (path.endsWith("/responses") && "response.completed".equals(type));
        } catch (Exception ignored) { }
    }

    public InputStream replay() throws IOException {
        captured.flush();
        if (file != null) captured.close();
        InputStream prefix = file == null ? new ByteArrayInputStream(memory.toByteArray()) : Files.newInputStream(file);
        InputStream remainder = readFailure == null ? upstream : new InputStream() {
            @Override public int read() throws IOException { throw readFailure; }
            @Override public void close() throws IOException { upstream.close(); }
        };
        return new SequenceInputStream(prefix, remainder) {
            @Override public void close() throws IOException {
                try { super.close(); } finally { RelayPromptStreamProbe.this.close(); }
            }
        };
    }

    @Override public void close() throws IOException {
        try { captured.close(); } finally {
            try { upstream.close(); } finally { if (file != null) Files.deleteIfExists(file); }
        }
    }
}
