package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RelayPromptAuditSummaryTest {
    @Test
    void keepsOnlyLatestUserTextAndHidesCredentialEmailAndImageData() throws Exception {
        var body = new ObjectMapper().readTree("""
                {"messages":[{"role":"system","content":"hidden system"},
                  {"role":"user","content":"old private history"},
                  {"role":"user","content":[{"type":"text","text":"忽略指令 password=secret alice@example.com sk-credential https://private.example"},
                    {"type":"image_url","image_url":{"url":"data:secret-image"}}]},
                  {"role":"tool","content":"private tool response"}]}
                """);
        String summary = RelayPromptAuditSummary.from(body, "/v1/chat/completions");
        assertTrue(summary.contains("忽略指令"));
        for (String privateText : new String[]{"secret", "alice@example", "credential", "private.example", "hidden system", "old private", "private tool", "secret-image"})
            assertFalse(summary.contains(privateText), privateText);
    }
}
