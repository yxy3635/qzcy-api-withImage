package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelayPromptRefusalDetectorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void marksOnlyTheConfiguredCompleteRefusalAndNotMentioningItInResearch() throws Exception {
        assertTrue(RelayPromptRefusalDetector.matches(mapper.readTree("{\"choices\":[{\"message\":{\"content\":\"抱歉，该请求违反平台使用规范。\"}}]}"), "/v1/chat/completions"));
        assertFalse(RelayPromptRefusalDetector.matches(mapper.readTree("{\"choices\":[{\"message\":{\"content\":\"本文分析 jailbreak 防御，不执行攻击。\"}}]}"), "/v1/chat/completions"));
        assertTrue(RelayPromptRefusalDetector.matches(mapper.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"抱歉，该请求违反平台使用规范\"}]}"), "/v1/messages"));
        assertFalse(RelayPromptRefusalDetector.matches(mapper.readTree("{\"error\":{\"message\":\"抱歉，该请求违反平台使用规范\"}}"), "/v1/chat/completions"));
    }

    @Test
    void detectsChunkedRefusalWithoutRetainingUnboundedOutput() throws Exception {
        var stream = new RelayPromptRefusalDetector.Stream();
        stream.accept(mapper.readTree("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"抱歉，该请求违反平台\"}}]}"), "/v1/chat/completions");
        stream.accept(mapper.readTree("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"使用规范\"}}]}"), "/v1/chat/completions");
        assertTrue(stream.matched());
        var safe = new RelayPromptRefusalDetector.Stream();
        safe.accept(mapper.readTree("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"抱歉，该请求违反平台使用规范，同时补充长篇说明\"}}]}"), "/v1/chat/completions");
        assertFalse(safe.matched());
    }

    @Test
    void ignoresThinkingAndToolArgumentsAndRecognizesResponsesFinalOutput() throws Exception {
        var stream = new RelayPromptRefusalDetector.Stream();
        stream.accept(mapper.readTree("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"抱歉，该请求违反平台使用规范\"}}"), "/v1/messages");
        stream.accept(mapper.readTree("{\"type\":\"response.function_call_arguments.delta\",\"delta\":\"抱歉，该请求违反平台使用规范\"}"), "/v1/responses");
        assertFalse(stream.matched());
        stream.accept(mapper.readTree("{\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"抱歉，该请求违反平台使用规范\"}]}]}}"), "/v1/responses");
        assertTrue(stream.matched());
    }
}
