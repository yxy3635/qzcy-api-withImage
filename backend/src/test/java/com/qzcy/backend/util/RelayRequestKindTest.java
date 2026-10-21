package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RelayRequestKindTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLAUDE_SUMMARY = "Your task is to create a detailed summary of the conversation so far, "
            + "paying close attention to the user's explicit requests and your previous actions.";

    @ParameterizedTest
    @ValueSource(strings = {"/v1/chat/completions", "/v1/messages", "/v1/responses"})
    void detectsCurrentSummaryAcrossConversationalProtocols(String path) {
        for (String prompt : new String[]{CLAUDE_SUMMARY,
                "You are performing a CONTEXT CHECKPOINT COMPACTION. Create a handoff summary for another LLM.",
                "Please summarize the conversation so far for continuing the task.",
                "Please write a detailed summary of this conversation.",
                "Provide a detailed prompt for continuing our conversation above.",
                "Summarize what we did so far.",
                "请将以上对话压缩成摘要，保留待办事项。", "总结之前的上下文，以便继续工作。"}) {
            ObjectNode body = MAPPER.createObjectNode();
            body.putArray(path.endsWith("/responses") ? "input" : "messages")
                    .addObject().put("role", "user").putArray("content")
                    .addObject().put("type", path.endsWith("/responses") ? "input_text" : "text").put("text", prompt);
            assertTrue(RelayRequestKind.isCompaction(path, body), prompt);
        }
    }

    @Test
    void supportsDedicatedSummarySystemAndResponsesInstructions() {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("system", "You are a helpful AI assistant tasked with summarizing conversations.");
        body.putArray("messages").addObject().put("role", "user").put("content", "Earlier conversation text");
        assertTrue(RelayRequestKind.isCompaction("/v1/messages", body));
        body.remove("system");
        body.put("instructions", CLAUDE_SUMMARY);
        assertTrue(RelayRequestKind.isCompaction("/v1/responses", body));
        body.remove("instructions");
        body.remove("messages");
        body.put("input", CLAUDE_SUMMARY);
        assertTrue(RelayRequestKind.isCompaction("/v1/responses", body));
    }

    @Test
    void oldSummaryTurnsAndCompactedHistoryDoNotMarkOrdinaryContinuation() throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        var messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", CLAUDE_SUMMARY);
        messages.addObject().put("role", "assistant").put("content", "This session is being continued from a previous conversation.");
        messages.addObject().put("role", "user").put("content", "Continue implementing the feature.");
        assertFalse(RelayRequestKind.isCompaction("/v1/messages", body));
        body.set("input", MAPPER.readTree("[{\"type\":\"compaction\",\"encrypted_content\":\"opaque\"},"
                + "{\"role\":\"user\",\"content\":\"Continue implementing the feature.\"}]"));
        body.remove("messages");
        assertFalse(RelayRequestKind.isCompaction("/v1/responses", body));
    }

    @Test
    void ordinarySummaryAndQuotedInstructionsAreNotConversationCompaction() {
        for (String prompt : new String[]{"Summarize this document.", "Explain how context compaction works.",
                "Translate this prompt: " + CLAUDE_SUMMARY, "请总结这份报告。", "如何压缩上下文？", "你好"}) {
            ObjectNode body = MAPPER.createObjectNode();
            body.putArray("messages").addObject().put("role", "user").put("content", prompt);
            assertFalse(RelayRequestKind.isCompaction("/v1/chat/completions", body), prompt);
        }
    }

    @Test
    void oldCompactEndpointsRemainSupportedWithoutBody() {
        assertTrue(RelayRequestKind.isCompaction("/api/v1/v1/responses/compact/?trace=1", null));
        assertFalse(RelayRequestKind.isCompaction("/v1/messages", null));
        ObjectNode body = MAPPER.createObjectNode().put("input", CLAUDE_SUMMARY);
        assertFalse(RelayRequestKind.isCompaction("/v1/images/generations", body));
    }

    @Test
    void recognizesGeneratedCompactionWithoutClassifyingEnabledSettingsOrText() throws Exception {
        assertTrue(RelayRequestKind.hasGeneratedCompaction(MAPPER.readTree(
                "{\"content\":[{\"type\":\"compaction\",\"content\":\"summary\"}]}")));
        assertTrue(RelayRequestKind.hasGeneratedCompaction(MAPPER.readTree(
                "{\"response\":{\"output\":[{\"type\":\"compaction\"}]}}")));
        assertFalse(RelayRequestKind.hasGeneratedCompaction(MAPPER.readTree(
                "{\"content\":[{\"type\":\"text\",\"text\":\"compaction\"}]}")));
        assertFalse(RelayRequestKind.isCompaction("/v1/messages", MAPPER.readTree(
                "{\"context_management\":{\"edits\":[{\"type\":\"compact_20260112\"}]},"
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}")));
    }
}
