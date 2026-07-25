package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayDispatchServiceImplTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void retriesNetworkTimeoutWrappedAsBadRequest() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("""
                {"error":{"type":"new_api_error","message":"Invalid request: read tcp 172.18.0.3:66224->172.18.0.1:46618: i/o timeout"}}
                """);

        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(400, body, body.toString()));
    }

    @Test
    void doesNotRetryNormalClientBadRequest() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("""
                {"error":{"type":"invalid_request_error","message":"messages is required"}}
                """);

        assertFalse(RelayDispatchServiceImpl.isRetryableUpstreamError(400, body, body.toString()));
    }

    @Test
    void retriesRateLimitsAndServerErrors() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("{}");

        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(429, body, ""));
        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(503, body, ""));
    }

    @Test
    void neverRetriesSuccessfulResponseText() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("{\"message\":\"rate limit documentation\"}");

        assertFalse(RelayDispatchServiceImpl.isRetryableUpstreamError(200, body, body.toString()));
    }

    @Test
    void zeroConcurrencyMeansUnlimitedAndPositiveValueIsNotClamped() {
        RelayChannel channel = new RelayChannel();
        channel.setMaxConcurrency(0);
        assertEquals(0, RelayDispatchServiceImpl.configuredStreamConcurrency(channel));

        channel.setMaxConcurrency(4096);
        assertEquals(4096, RelayDispatchServiceImpl.configuredStreamConcurrency(channel));
    }

    @Test
    void responsesCompactBodyIsForwardedWithoutRemovingContext() throws Exception {
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        ObjectNode inbound = (ObjectNode) OBJECT_MAPPER.readTree("""
                {
                  "model": "public-model",
                  "instructions": "Preserve project decisions and tool state",
                  "input": [
                    {"role":"user","content":[{"type":"input_text","text":"first turn"}]},
                    {"type":"function_call_output","call_id":"call_1","output":"important result"}
                  ],
                  "parallel_tool_calls": true,
                  "previous_response_id": "resp_123"
                }
                """);
        ObjectNode original = inbound.deepCopy();
        RelayModel model = new RelayModel();
        model.setModel("upstream-model");
        RelayContext context = new RelayContext(null, model, null, null, null, "chat");

        ObjectNode outbound = ReflectionTestUtils.invokeMethod(
                service, "prepareOutboundBody", inbound, context, "/v1/responses/compact");

        assertNotSame(inbound, outbound);
        assertEquals(original, inbound);
        assertEquals(original.path("instructions"), outbound.path("instructions"));
        assertEquals(original.path("input"), outbound.path("input"));
        assertEquals(original.path("parallel_tool_calls"), outbound.path("parallel_tool_calls"));
        assertEquals(original.path("previous_response_id"), outbound.path("previous_response_id"));
        assertEquals("upstream-model", outbound.path("model").asText());
    }
}
