package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.dto.relay.RelayCostBreakdown;
import com.qzcy.backend.entity.RelayUsageLog;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.service.RelayPolicyService;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void retriesBadRequestToAllowChannelFailover() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("""
                {"error":{"type":"invalid_request_error","message":"messages is required"}}
                """);

        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(400, body, body.toString()));
    }

    @Test
    void retriesRateLimitsAndServerErrors() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("{}");

        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(429, body, ""));
        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(502, body, ""));
        assertTrue(RelayDispatchServiceImpl.isRetryableUpstreamError(503, body, ""));
    }

    @Test
    void requestBillingDoesNotChargeNonSuccessfulUpstreamResponse() {
        RelayModel model = new RelayModel();
        model.setFixedRequestBilling(true);
        RelayContext context = new RelayContext(null, model, null, null, null, "chat");
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        RelayCostBreakdown cost = new RelayCostBreakdown(
                new BigDecimal("0.010000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.050000"), new BigDecimal("0.050000"));

        RelayCostBreakdown failed = ReflectionTestUtils.invokeMethod(
                service, "billingCostForResponse", context, 502, cost);
        RelayCostBreakdown successful = ReflectionTestUtils.invokeMethod(
                service, "billingCostForResponse", context, 200, cost);

        assertEquals(BigDecimal.ZERO, failed.total());
        assertEquals(BigDecimal.ZERO, failed.request());
        assertEquals(cost.total(), successful.total());
    }

    @Test
    void usageBillingKeepsExistingErrorCostAndSuccessCost() {
        RelayModel model = new RelayModel();
        model.setFixedRequestBilling(false);
        RelayContext context = new RelayContext(null, model, null, null, null, "chat");
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        RelayCostBreakdown cost = new RelayCostBreakdown(
                new BigDecimal("0.010000"), new BigDecimal("0.020000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("0.030000"));

        RelayCostBreakdown failed = ReflectionTestUtils.invokeMethod(
                service, "billingCostForResponse", context, 502, cost);
        RelayCostBreakdown successful = ReflectionTestUtils.invokeMethod(
                service, "billingCostForResponse", context, 200, cost);

        assertEquals(cost.total(), failed.total());
        assertEquals(cost.total(), successful.total());
    }

    @Test
    void neverRetriesSuccessfulResponseText() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree("{\"message\":\"rate limit documentation\"}");

        assertFalse(RelayDispatchServiceImpl.isRetryableUpstreamError(200, body, body.toString()));
    }

    @Test
    void preservesUpstreamErrorBodyWhileRedactingUrls() {
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        String upstreamError = "{\"type\":\"error\",\"error\":{\"type\":\"provider_error\",\"message\":\"See https://api.example.com/v1/errors?id=42\"},\"request_id\":\"req_123\"}";

        String sanitized = ReflectionTestUtils.invokeMethod(service, "sanitizeUpstreamErrorBody", upstreamError);

        assertEquals("{\"type\":\"error\",\"error\":{\"type\":\"provider_error\",\"message\":\"See <redacted-url>\"},\"request_id\":\"req_123\"}", sanitized);
        assertFalse(sanitized.contains("https://"));
    }

    @Test
    void redactsJsonEscapedUrlsWithoutReformattingErrorBody() {
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        String upstreamError = "{\"message\":\"See https:\\/\\/api.example.com\\/v1\\/errors\"}";

        String sanitized = ReflectionTestUtils.invokeMethod(service, "sanitizeUpstreamErrorBody", upstreamError);

        assertEquals("{\"message\":\"See <redacted-url>\"}", sanitized);
    }

    @Test
    void extractsThinkingEffortFromCommonRequestFormats() throws Exception {
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);

        ObjectNode responsesBody = (ObjectNode) OBJECT_MAPPER.readTree("""
                {"model":"gpt-5.6-luna","reasoning":{"effort":"high"}}
                """);
        ObjectNode chatBody = (ObjectNode) OBJECT_MAPPER.readTree("""
                {"model":"gpt-5.6-luna","reasoning_effort":"medium"}
                """);
        ObjectNode thinkingBody = (ObjectNode) OBJECT_MAPPER.readTree("""
                {"model":"claude","thinking":{"type":"enabled","budget_tokens":4096}}
                """);

        assertEquals("high", ReflectionTestUtils.invokeMethod(service, "extractThinkingEffort", responsesBody));
        assertEquals("medium", ReflectionTestUtils.invokeMethod(service, "extractThinkingEffort", chatBody));
        assertEquals("budget:4096", ReflectionTestUtils.invokeMethod(service, "extractThinkingEffort", thinkingBody));
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
        model.setModel("internal-model");
        RelayContext context = new RelayContext(null, model, null, null, null, "chat");

        ObjectNode outbound = ReflectionTestUtils.invokeMethod(
                service, "prepareOutboundBody", inbound, context, "/v1/responses/compact");

        assertNotSame(inbound, outbound);
        assertEquals(original, inbound);
        assertEquals(original.path("instructions"), outbound.path("instructions"));
        assertEquals(original.path("input"), outbound.path("input"));
        assertEquals(original.path("parallel_tool_calls"), outbound.path("parallel_tool_calls"));
        assertEquals(original.path("previous_response_id"), outbound.path("previous_response_id"));
        assertEquals("public-model", outbound.path("model").asText());
    }

    @Test
    void explicitChannelMappingOverridesRequestModel() throws Exception {
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        ObjectNode inbound = (ObjectNode) OBJECT_MAPPER.readTree("""
                {"model":"public-alias","messages":[{"role":"user","content":"ping"}]}
                """);
        RelayChannelModel binding = new RelayChannelModel();
        binding.setUpstreamModel("provider/actual-model");
        RelayContext context = new RelayContext(null, null, null, null, binding, "chat");

        ObjectNode outbound = ReflectionTestUtils.invokeMethod(
                service, "prepareOutboundBody", inbound, context, "/v1/chat/completions");

        assertEquals("provider/actual-model", outbound.path("model").asText());
        assertEquals("public-alias", inbound.path("model").asText());
    }

    @Test
    void noMappingPreservesRequestModelForChatResponsesAndCompact() throws Exception {
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(null, null, null, null, null, OBJECT_MAPPER);
        RelayContext context = new RelayContext(null, null, null, null, null, "chat");
        for (String path : new String[]{"/v1/chat/completions", "/v1/responses", "/v1/responses/compact"}) {
            ObjectNode inbound = (ObjectNode) OBJECT_MAPPER.readTree("{\"model\":\"public-alias\",\"input\":\"ping\"}");
            ObjectNode outbound = ReflectionTestUtils.invokeMethod(
                    service, "prepareOutboundBody", inbound, context, path);
            assertEquals("public-alias", outbound.path("model").asText(), path);
            assertEquals("public-alias", inbound.path("model").asText(), path);
        }
    }

    @Test
    void fallbackUsageLogKeepsOriginalBillingValues() {
        RelayUsageLogMapper usageLogMapper = mock(RelayUsageLogMapper.class);
        RelayDispatchServiceImpl service = new RelayDispatchServiceImpl(
                mock(RelayPolicyService.class), usageLogMapper, mock(RelayTokenMapper.class),
                mock(UserMapper.class), mock(PaymentService.class), OBJECT_MAPPER);
        when(usageLogMapper.insert(any(RelayUsageLog.class)))
                .thenThrow(new RuntimeException("temporary insert failure"))
                .thenReturn(1);

        RelayUsageLog original = new RelayUsageLog();
        original.setUserId(3L);
        original.setTokenId(7L);
        original.setPromptTokens(120);
        original.setCompletionTokens(45);
        original.setCachedTokens(20);
        original.setTotalTokens(165);
        original.setInputCost(new BigDecimal("0.120000"));
        original.setOutputCost(new BigDecimal("0.045000"));
        original.setCost(new BigDecimal("0.165000"));
        original.setStatus("success");
        original.setStatusCode(200);

        ReflectionTestUtils.invokeMethod(service, "insertUsageLog", original);

        var captor = org.mockito.ArgumentCaptor.forClass(RelayUsageLog.class);
        verify(usageLogMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        RelayUsageLog fallback = captor.getAllValues().get(1);
        assertEquals(120, fallback.getPromptTokens());
        assertEquals(45, fallback.getCompletionTokens());
        assertEquals(165, fallback.getTotalTokens());
        assertEquals(new BigDecimal("0.165000"), fallback.getCost());
        assertEquals("success", fallback.getStatus());
    }
}
