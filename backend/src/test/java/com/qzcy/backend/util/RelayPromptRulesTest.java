package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RelayPromptRulesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void chatPreservesOriginalSystemMultimodalContentToolsAndCacheFields() throws Exception {
        var body = body("""
                {"messages":[{"role":"system","content":"project instructions"},
                  {"role":"user","content":[{"type":"text","text":"分析 jailbreak 研究"},
                    {"type":"image_url","image_url":{"url":"data:image/png;base64,AA"}}]},
                  {"role":"tool","tool_call_id":"call1","content":"result"}],
                  "tools":[{"type":"function","function":{"name":"search"}}],
                  "prompt_cache_key":"cache1","stream":true}
                """);
        var original = body.deepCopy();
        RelayPromptRules.apply(body, "/v1/chat/completions", "platform rule");
        assertEquals("system", body.path("messages").path(0).path("role").asText());
        assertEquals("platform rule", body.path("messages").path(0).path("content").asText());
        ((com.fasterxml.jackson.databind.node.ArrayNode) body.path("messages")).remove(0);
        assertEquals(original, body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/messages", "/v1/messages/count_tokens"})
    void anthropicPreservesSystemCacheBlocksAndUsesSameRuleForCounting(String path) throws Exception {
        var body = body("""
                {"system":[{"type":"text","text":"original","cache_control":{"type":"ephemeral"}}],
                 "messages":[{"role":"user","content":"你好"}]}
                """);
        var original = body.deepCopy();
        RelayPromptRules.apply(body, path, "rule");
        assertEquals("rule", body.path("system").path(0).path("text").asText());
        assertEquals(original.path("system").path(0), body.path("system").path(1));
        assertEquals(original.path("messages"), body.path("messages"));
    }

    @Test
    void anthropicAcceptsStringAndAbsentSystem() throws Exception {
        var body = body("{\"system\":\"project\"}");
        RelayPromptRules.apply(body, "/v1/messages", "rule");
        assertEquals("project", body.path("system").path(1).path("text").asText());
        var absent = mapper.createObjectNode();
        RelayPromptRules.apply(absent, "/v1/messages", "rule");
        assertEquals(1, absent.path("system").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/responses", "/v1/responses/compact"})
    void responsesPreservesToolOutputsHistoryAndExistingInstructions(String path) throws Exception {
        var body = body("""
                {"instructions":"project","previous_response_id":"resp_1",
                 "input":[{"type":"function_call_output","call_id":"call1","output":"result"}]}
                """);
        var original = body.deepCopy();
        RelayPromptRules.apply(body, path, "rule");
        assertEquals("rule\n\nproject", body.path("instructions").asText());
        body.set("instructions", original.get("instructions"));
        assertEquals(original, body);
        var empty = mapper.createObjectNode();
        RelayPromptRules.apply(empty, path, "rule");
        assertEquals("rule", empty.path("instructions").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/images/generations", "/v1/images/edits", "/v1/completions"})
    void prefixesPromptWithoutTouchingImageAndGenerationOptions(String path) throws Exception {
        var body = body("{\"prompt\":\"画猫\",\"image\":[\"reference\"],\"size\":\"1024x1024\",\"n\":2}");
        var original = body.deepCopy();
        RelayPromptRules.apply(body, path, "rule");
        assertEquals("rule\n\n画猫", body.path("prompt").asText());
        body.set("prompt", original.get("prompt"));
        assertEquals(original, body);
    }

    @Test
    void completionBatchesAllReceiveRuleAndTokenIdsCannotBypassRule() throws Exception {
        var body = body("{\"prompt\":[\"one\",\"two\"]}");
        RelayPromptRules.apply(body, "/v1/completions", "rule");
        assertEquals("rule\n\none", body.path("prompt").path(0).asText());
        assertEquals("rule\n\ntwo", body.path("prompt").path(1).asText());
        var tokens = body("{\"prompt\":[1,2,3]}");
        assertThrows(BusinessException.class, () -> RelayPromptRules.apply(tokens, "/v1/completions", "rule"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/embeddings", "/v1/moderations", "/v1/audio/speech",
            "/v1/audio/transcriptions", "/v1/audio/translations", "/v1/models"})
    void doesNotCorruptNonInstructionInputs(String path) throws Exception {
        var body = body("{\"input\":\"hello\",\"prompt\":\"vocabulary\",\"voice\":\"alloy\"}");
        var original = body.deepCopy();
        RelayPromptRules.apply(body, path, "rule");
        assertEquals(original, body);
    }

    private ObjectNode body(String json) throws Exception {
        return (ObjectNode) mapper.readTree(json);
    }
}
