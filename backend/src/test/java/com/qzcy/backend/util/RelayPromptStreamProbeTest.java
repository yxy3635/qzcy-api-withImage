package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RelayPromptStreamProbeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHAT = "/v1/chat/completions";
    private static final String RESPONSES = "/v1/responses";
    private static final String DONE = "data: [DONE]\n\n";
    private static final String COMPLETED = "data: {\"type\":\"response.completed\"}\n\n";

    @Test
    void normalSingleChoiceReleasesBeforeCompletionAndReplaysEveryByte() throws Exception {
        String first = delta("正常回答", 0);
        String rest = delta("继续", 0) + DONE;
        var upstream = chunked(first + rest, first.getBytes(StandardCharsets.UTF_8).length);
        try (var probe = new RelayPromptStreamProbe(upstream, MAPPER, CHAT, false)) {
            assertFalse(probe.inspect());
            assertTrue(upstream.available() > 0, "Normal chat must not wait for the rest of the answer");
            try (var replay = probe.replay()) {
                assertEquals(first + rest, new String(replay.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void splitUtf8RefusalWaitsForDoneAndRetainsUsageFrame() throws Exception {
        String events = delta("抱歉，该请求违反", 0) + delta("平台使用规范。", 0)
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":10}}\r\n\r\n" + DONE;
        try (var probe = new RelayPromptStreamProbe(chunked(events, 1), MAPPER, CHAT, false)) {
            assertTrue(probe.inspect());
            try (var replay = probe.replay()) {
                assertEquals(events, new String(replay.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void refusalPrefixFollowedByExplanationIsNotAHit() throws Exception {
        String events = delta(RelayPromptRefusalDetector.REFUSAL, 0) + delta("只是示例中的引用", 0) + DONE;
        try (var probe = new RelayPromptStreamProbe(chunked(events, 5), MAPPER, CHAT, false)) {
            assertFalse(probe.inspect());
            try (var replay = probe.replay()) {
                assertEquals(events, new String(replay.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void laterChoiceRefusalIsDetectedAfterEarlierNormalChoice() throws Exception {
        String events = delta("正常回答", 0) + delta(RelayPromptRefusalDetector.REFUSAL, 1) + DONE;
        try (var probe = new RelayPromptStreamProbe(chunked(events, 1), MAPPER, CHAT, true)) {
            assertTrue(probe.inspect());
        }
    }

    @Test
    void responsesCanRefuseInALaterAssistantMessage() throws Exception {
        String events = "data: {\"type\":\"response.output_text.delta\",\"output_index\":0,\"delta\":\"正常内容\"}\n\n"
                + "data: {\"type\":\"response.output_text.delta\",\"output_index\":1,\"delta\":\"" + RelayPromptRefusalDetector.REFUSAL + "\"}\n\n"
                + COMPLETED;
        try (var probe = new RelayPromptStreamProbe(chunked(events, 1), MAPPER, RESPONSES, false)) {
            assertTrue(probe.inspect());
        }
    }

    @Test
    void errorsAndTruncatedRefusalsNeverBecomePolicyHits() throws Exception {
        String refusal = delta(RelayPromptRefusalDetector.REFUSAL, 0);
        for (String events : new String[]{refusal, refusal + "event: error\ndata: provider failure\n\n",
                refusal + "data: {\"type\":\"error\"}\n\n"}) {
            try (var probe = new RelayPromptStreamProbe(chunked(events, 1), MAPPER, CHAT, false)) {
                assertFalse(probe.inspect());
            }
        }
    }

    @Test
    void largeMetadataUsesTemporaryFileAndDeletesItAfterReplay() throws Exception {
        String events = (": " + "x".repeat(2000) + "\n\n").repeat(40) + COMPLETED;
        var probe = new RelayPromptStreamProbe(chunked(events, 8192), MAPPER, RESPONSES, false);
        assertFalse(probe.inspect());
        Path file = (Path) ReflectionTestUtils.getField(probe, "file");
        assertNotNull(file);
        assertTrue(Files.exists(file));
        try (var replay = probe.replay()) {
            assertEquals(events, new String(replay.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(file));
    }

    @Test
    void readFailureIsReplayedAfterCapturedBytesForBillingAndErrorHandling() throws Exception {
        String events = "data: {\"type\":\"response.in_progress\",\"response\":{\"usage\":{\"input_tokens\":20}}}\n\n";
        var upstream = new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)) {
            @Override public synchronized int read(byte[] target, int offset, int length) {
                if (available() == 0) throw new java.io.UncheckedIOException(new IOException("failed"));
                return super.read(target, offset, length);
            }
        };
        InputStream failing = new FilterInputStream(upstream) {
            @Override public int read(byte[] target, int offset, int length) throws IOException {
                try { return super.read(target, offset, length); }
                catch (UncheckedIOException ex) { throw ex.getCause(); }
            }
        };
        try (var probe = new RelayPromptStreamProbe(failing, MAPPER, RESPONSES, false)) {
            assertFalse(probe.inspect());
            try (var replay = probe.replay()) {
                assertArrayEquals(events.getBytes(StandardCharsets.UTF_8), replay.readNBytes(events.getBytes(StandardCharsets.UTF_8).length));
                assertThrows(IOException.class, replay::read);
            }
        }
    }

    private static String delta(String text, int index) {
        var json = MAPPER.createObjectNode();
        json.putArray("choices").addObject().put("index", index).putObject("delta").put("content", text);
        return "data: " + json + "\n\n";
    }

    private static ByteArrayInputStream chunked(String events, int chunkSize) {
        return new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)) {
            @Override public synchronized int read(byte[] target, int offset, int length) {
                return super.read(target, offset, Math.min(length, chunkSize));
            }
        };
    }
}
