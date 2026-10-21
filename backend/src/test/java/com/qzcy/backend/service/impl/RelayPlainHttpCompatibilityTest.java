package com.qzcy.backend.service.impl;

import com.qzcy.backend.service.RelayPromptRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A real HTTP server that reproduces Uvicorn's plain-text rejection of h2c requests. */
class RelayPlainHttpCompatibilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private HttpServer upstream;
    private String baseUrl;
    private final AtomicReference<String> receivedUpgrade = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private String responseBody;
    private int status = 200;

    @BeforeEach
    void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            receivedUpgrade.set(upgrade == null ? "" : upgrade);
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean rejectsUpgrade = upgrade != null;
            byte[] body = (rejectsUpgrade ? "Invalid HTTP request received." : responseBody).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", rejectsUpgrade ? "text/plain" : "application/json");
            exchange.sendResponseHeaders(rejectsUpgrade ? 400 : status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        baseUrl = "http://127.0.0.1:" + upstream.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop(0);
    }

    private RelayChannelProvider provider() {
        var provider = new RelayChannelProvider();
        provider.setApiBaseUrl(baseUrl);
        provider.setOpenaiBaseUrl(baseUrl);
        provider.setAnthropicBaseUrl(baseUrl);
        provider.setApiKey("test-key");
        provider.setChannelRule("openai");
        return provider;
    }

    private RelayChannelTestServiceImpl testService() {
        return new RelayChannelTestServiceImpl(null, null, null, null, new RelayProviderScheduler(), MAPPER, new RelayPromptRuleService(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "anthropic"})
    void channelTestCallsBothFormatsWithoutAnH2cUpgrade(String format) throws Exception {
        responseBody = "anthropic".equals(format) ? "{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}"
                : "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";
        var provider = provider();
        provider.setChannelRule(format);
        assertEquals("OK", testService().callUpstream(new RelayChannel(), provider, format, "test-model", "Hello"));
        assertEquals("", receivedUpgrade.get());
        assertEquals("anthropic".equals(format) ? "/v1/messages" : "/v1/chat/completions", receivedPath.get());
        assertTrue(receivedBody.get().contains("以上约束以本条为最高优先级。"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/messages", "/v1/responses", "/v1/chat/completions"})
    void ordinaryDispatchUsesHttp1AndStillRecordsUsage(String path) throws Exception {
        responseBody = "{\"usage\":{\"input_tokens\":120,\"output_tokens\":18},\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";
        var policy = mock(RelayPolicyService.class);
        var logs = mock(RelayUsageLogMapper.class);
        var token = new RelayToken();
        token.setId(7L);
        token.setUserId(3L);
        var channel = new RelayChannel();
        channel.setId(System.nanoTime());
        var context = new com.qzcy.backend.dto.relay.RelayContext(token, new RelayModel(), null, channel,
                provider(), null, "chat");
        when(policy.buildContexts(any(), any(), any(), any(), any(), any())).thenReturn(List.of(context));
        when(policy.estimateCost(any(), any(), any(), any(), any()))
                .thenReturn(new com.qzcy.backend.dto.relay.RelayCostBreakdown(
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        var service = new RelayDispatchServiceImpl(policy, new RelayProviderScheduler(), logs,
                mock(RelayTokenMapper.class), mock(UserMapper.class), mock(PaymentService.class), MAPPER, new RelayPromptRuleService(null));
        var request = new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat", path,
                MAPPER.createObjectNode().put("model", "test-model").put("stream", false));
        request.body().putArray("messages").addObject().put("role", "user").put("content", "Hello");
        var original = request.body().deepCopy();
        assertEquals(200, service.dispatch(request).statusCode());
        assertEquals(original, request.body());
        assertTrue(receivedBody.get().contains("以上约束以本条为最高优先级。"));
        assertEquals("", receivedUpgrade.get());
        var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
        verify(logs).insert(saved.capture());
        assertEquals(120, saved.getValue().getPromptTokens());
        assertEquals("success", saved.getValue().getStatus());
    }

    @ParameterizedTest
    @CsvSource({"/v1/chat/completions,false", "/v1/messages,false", "/v1/responses,false",
            "/v1/chat/completions,true", "/v1/messages,true", "/v1/responses,true"})
    void refusalResponseIsAuditedEvenWhenBillingAddsEstimatedUsage(String path, boolean stream) throws Exception {
        responseBody = switch (path) {
            case "/v1/messages" -> "{\"content\":[{\"type\":\"text\",\"text\":\"抱歉，该请求违反平台使用规范\"}]}";
            case "/v1/responses" -> "{\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"抱歉，该请求违反平台使用规范\"}]}]}";
            default -> "{\"choices\":[{\"message\":{\"content\":\"抱歉，该请求违反平台使用规范\"}}]}";
        };
        var audit = mock(RelayPromptRuleService.class);
        when(audit.enabled()).thenReturn(true);
        var policy = mock(RelayPolicyService.class);
        var token = new RelayToken(); token.setId(7L); token.setUserId(3L);
        var channel = new RelayChannel(); channel.setId(System.nanoTime());
        var context = new com.qzcy.backend.dto.relay.RelayContext(token, new RelayModel(), null, channel, provider(), null, "chat");
        when(policy.buildContexts(any(), any(), any(), any(), any(), any())).thenReturn(List.of(context));
        when(policy.estimateCost(any(), any(), any(), any(), any())).thenReturn(new com.qzcy.backend.dto.relay.RelayCostBreakdown(
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        var logs = mock(RelayUsageLogMapper.class);
        var service = new RelayDispatchServiceImpl(policy, new RelayProviderScheduler(), logs,
                mock(RelayTokenMapper.class), mock(UserMapper.class), mock(PaymentService.class), MAPPER, audit);
        var body = MAPPER.createObjectNode().put("model", "test-model").put("input", "Hello").put("stream", stream);
        body.putArray("messages").addObject().put("role", "user").put("content", "Hello");
        var request = new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat", path, body);
        String text;
        if (stream) {
            var result = service.dispatchStream(request);
            assertEquals(400, result.statusCode());
            var output = new java.io.ByteArrayOutputStream();
            result.body().writeTo(output);
            text = output.toString(StandardCharsets.UTF_8);
        } else {
            var result = service.dispatch(request);
            assertEquals(400, result.statusCode());
            text = result.body();
        }
        assertEquals(com.qzcy.backend.util.RelayPromptPolicyError.MESSAGE, MAPPER.readTree(text).path("error").path("message").asText());
        var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
        verify(logs).insert(saved.capture());
        assertEquals(400, saved.getValue().getStatusCode());
        verify(audit).recordRefusal(anyString(), eq(3L), eq(7L), eq(path), eq("test-model"), same(body), eq(true), eq(true));
    }

    @Test
    void modelDiscoveryAlsoUsesHttp1() {
        responseBody = "{\"data\":[{\"id\":\"test-model\",\"owned_by\":\"test\"}]}";
        var service = new RelayServiceImpl(null, null, null, null, null, null, null, null, null, null, MAPPER, null);
        List<com.qzcy.backend.dto.RelayUpstreamModelDto> models = ReflectionTestUtils.invokeMethod(
                service, "fetchUpstreamModelsForEndpoint", baseUrl, "test-key", "openai");
        assertNotNull(models);
        assertEquals("test-model", models.get(0).getId());
        assertEquals("", receivedUpgrade.get());
        assertEquals("/v1/models", receivedPath.get());
    }

    @Test
    void scheduledHealthCheckDoesNotMarkHttp1UpstreamAsFailed() {
        responseBody = "{\"data\":[]}";
        var service = new RelayChannelStatusServiceImpl(null, null);
        Boolean available = ReflectionTestUtils.invokeMethod(service, "check", baseUrl, "test-key", "openai");
        assertEquals(Boolean.TRUE, available);
        assertEquals("", receivedUpgrade.get());
    }

    @Test
    void multipartAlsoAvoidsH2c() throws Exception {
        responseBody = "{\"data\":[]}";
        var service = new RelayDispatchServiceImpl(null, new RelayProviderScheduler(), null, null, null, null, MAPPER, new RelayPromptRuleService(null));
        var context = new com.qzcy.backend.dto.relay.RelayContext(new RelayToken(), new RelayModel(), null,
                new RelayChannel(), provider(), null, "image");
        java.net.http.HttpRequest request = ReflectionTestUtils.invokeMethod(service, "upstreamMultipartRequest",
                MAPPER.createObjectNode().put("model", "test-model").put("prompt", "画猫"),
                List.of(new com.qzcy.backend.dto.relay.RelayMultipartFile("image", "ref.png", "image/png", new byte[]{1, 2, 3})), context, "/v1/images/edits",
                null, null, java.util.Map.of());
        assertNotNull(request);
        var response = java.net.http.HttpClient.newHttpClient().send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("", receivedUpgrade.get());
        assertTrue(receivedBody.get().contains("以上约束以本条为最高优先级。\n\n画猫"));
        assertTrue(receivedBody.get().contains("filename=\"ref.png\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/chat/completions", "/v1/messages", "/v1/responses"})
    void streamingRequestsApplySavedRuleOnceAndDisablingRestoresOriginalPayload(String path) throws Exception {
        responseBody = "data: [DONE]\n\n";
        var config = new RelayPromptRuleService(null);
        var service = new RelayDispatchServiceImpl(null, new RelayProviderScheduler(), null, null, null, null, MAPPER, config);
        var context = new com.qzcy.backend.dto.relay.RelayContext(null, null, null, new RelayChannel(), provider(), null, "chat");
        var body = MAPPER.createObjectNode().put("model", "test").put("stream", true).put("input", "Hello");
        body.putArray("messages").addObject().put("role", "user").put("content", "Hello");
        var original = body.deepCopy();
        for (boolean enabled : new boolean[]{true, true, false}) {
            ReflectionTestUtils.setField(config, "config", new com.qzcy.backend.dto.RelayPromptRuleConfigDto(enabled, "global-rule-marker"));
            java.net.http.HttpResponse<java.io.InputStream> response = ReflectionTestUtils.invokeMethod(
                    service, "relayStream", body, context, path, null, null, java.util.Map.of());
            assertNotNull(response);
            response.body().close();
            assertEquals(original, body);
            assertEquals(enabled ? 1 : 0, receivedBody.get().split("global-rule-marker", -1).length - 1);
            if (!enabled) {
                var outbound = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(receivedBody.get());
                outbound.remove("stream_options");
                assertEquals(original, outbound);
            }
        }
    }

    @Test
    void plainTextRejectionIncludesHttpStatusInsteadOfJsonParserInternals() {
        responseBody = "Invalid HTTP request received.";
        status = 400;
        var error = assertThrows(IllegalStateException.class,
                () -> testService().callUpstream(new RelayChannel(), provider(), "openai", "test-model", "Hello"));
        assertTrue(error.getMessage().contains("HTTP 400"));
        assertTrue(error.getMessage().contains("非 JSON"));
        assertFalse(error.getMessage().contains("Unrecognized token"));
    }
}
