package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.dto.relay.RelayUsage;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.service.RelayProviderScheduler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelayBillingCompatibilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CHAT = "/v1/chat/completions";
    private static final String RESPONSES = "/v1/responses";
    private static final String MESSAGES = "/v1/messages";

    static Stream<Arguments> formats() {
        return Stream.of(
                Arguments.of(CHAT, """
                        {"prompt_tokens":287555,"completion_tokens":168,"total_tokens":287723,
                         "prompt_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(RESPONSES, """
                        {"input_tokens":287555,"output_tokens":168,"total_tokens":287723,
                         "input_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(MESSAGES, """
                        {"input_tokens":195,"output_tokens":168,"cache_read_input_tokens":287360,
                         "cache_creation_input_tokens":0}
                        """));
    }

    @ParameterizedTest
    @MethodSource("formats")
    void allProtocolsChargeOnlyUncachedInput(String path, String json) throws Exception {
        var policy = policy();
        var response = response(json);
        var usage = RelayUsage.fromResponse(response, path);
        assertEquals(287555, usage.inputTokens());
        assertEquals(195, usage.billableInputTokens());
        assertEquals(287723, usage.totalTokens());
        var cost = policy.estimateCost(model(), channel(), group(), response, path);
        assertMoney("0.000390", cost.input());
        assertMoney("0.0114944", cost.cacheRead());
        assertMoney("0.000672", cost.output());
        assertMoney("0.000125564", cost.total());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void longContextUsesTotalInputIncludingCache(String path, String json) throws Exception {
        var model = model();
        model.setLongContextThreshold(272000L);
        model.setLongContextBillingMode("multiplier");
        model.setLongContextMultiplier(new BigDecimal("2"));
        assertMoney("0.000251128", policy().estimateCost(model, channel(), group(), response(json), path).total());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void cacheWritesArePricedSeparately(String path, String json) throws Exception {
        var response = response(json);
        var usage = (com.fasterxml.jackson.databind.node.ObjectNode) response.path("usage");
        usage.put("cache_creation_input_tokens", 1000);
        if (!MESSAGES.equals(path)) usage.put(CHAT.equals(path) ? "prompt_tokens" : "input_tokens", 288555);
        var cost = policy().estimateCost(model(), channel(), group(), response, path);
        assertMoney("0.000390", cost.input());
        assertMoney("0.0015", cost.cacheCreation());
        assertMoney("0.000140564", cost.total());
    }

    static Stream<Arguments> mixedOpenaiFields() {
        return Stream.of(
                Arguments.of(CHAT, """
                        {"prompt_tokens":287555,"completion_tokens":168,"cache_read_input_tokens":287360}
                        """),
                Arguments.of(RESPONSES, """
                        {"input_tokens":287555,"output_tokens":168,"cache_read_input_tokens":287360}
                        """),
                Arguments.of(RESPONSES, """
                        {"input_tokens":287555,"output_tokens":168,"cache_creation_input_tokens":0,
                         "input_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(RESPONSES, """
                        {"input_tokens":287555,"output_tokens":168,"cache_read_input_tokens":null,
                         "input_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(MESSAGES, """
                        {"prompt_tokens":287555,"completion_tokens":168,"cache_creation_input_tokens":0,
                         "prompt_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(MESSAGES, """
                        {"input_tokens":195,"input_tokens_total":287555,"output_tokens":168,
                         "cache_read_input_tokens":287360}
                        """));
    }

    @ParameterizedTest
    @MethodSource("mixedOpenaiFields")
    void aliasesAndEmptyAnthropicFieldsCannotCauseDoubleBilling(String path, String json) throws Exception {
        // The channel's legacy rule must not override the actual request protocol.
        var channel = channel();
        channel.setChannelRule("anthropic");
        assertMoney("0.000125564", policy().estimateCost(model(), channel, group(), response(json), path).total());
    }

    static Stream<Arguments> fullyCachedFormats() {
        return Stream.of(
                Arguments.of(CHAT, """
                        {"prompt_tokens":287360,"completion_tokens":0,
                         "prompt_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(RESPONSES, """
                        {"input_tokens":287360,"output_tokens":0,
                         "input_tokens_details":{"cached_tokens":287360}}
                        """),
                Arguments.of(MESSAGES, """
                        {"input_tokens":0,"output_tokens":0,"cache_read_input_tokens":287360}
                        """));
    }

    @ParameterizedTest
    @MethodSource("fullyCachedFormats")
    void fullyCachedUsageIsNotReplacedWithEstimatedUsage(String path, String json) throws Exception {
        var response = response(json);
        var service = new RelayDispatchServiceImpl(null, new RelayProviderScheduler(), null, null, null, null, MAPPER);
        assertEquals(Boolean.TRUE, org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "hasBillableUsage", response, path));
        assertMoney("0.000114944", policy().estimateCost(model(), channel(), group(), response, path).total());
    }

    static Stream<Arguments> estimatedFormats() {
        return Stream.of(Arguments.of(CHAT), Arguments.of(RESPONSES), Arguments.of(MESSAGES));
    }

    @ParameterizedTest
    @MethodSource("estimatedFormats")
    void localEstimatesRemainInclusiveForEveryEndpoint(String path) throws Exception {
        var usage = RelayUsage.fromResponse(response("""
                {"prompt_tokens":195,"completion_tokens":168,"estimated":true}
                """), path);
        assertEquals(195, usage.billableInputTokens());
        assertEquals(363, usage.totalTokens());
    }

    @ParameterizedTest
    @MethodSource("estimatedFormats")
    void missingCacheDetailsDoNotReduceOrdinaryInput(String path) throws Exception {
        var cost = policy().estimateCost(model(), channel(), group(), response("""
                {"input_tokens":195,"output_tokens":168}
                """), path);
        assertMoney("0.00001062", cost.total());
        assertMoney("0", cost.cacheRead());
        assertMoney("0", cost.cacheCreation());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void reasoningTokensAlreadyInOutputAreNotChargedAgain(String path, String json) throws Exception {
        var body = response(json);
        var usage = (com.fasterxml.jackson.databind.node.ObjectNode) body.path("usage");
        usage.putObject(CHAT.equals(path) ? "completion_tokens_details" : "output_tokens_details")
                .put("reasoning_tokens", 100);
        var cost = policy().estimateCost(model(), channel(), group(), body, path);
        assertMoney("0.000672", cost.output());
        assertMoney("0.000125564", cost.total());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void longContextThresholdBoundaryIsConsistentAcrossFormats(String path, String json) throws Exception {
        var model = model();
        model.setLongContextBillingMode("multiplier");
        model.setLongContextMultiplier(new BigDecimal("2"));
        model.setLongContextThreshold(287556L);
        assertMoney("0.000125564", policy().estimateCost(model, channel(), group(), response(json), path).total());
        model.setLongContextThreshold(287555L);
        assertMoney("0.000251128", policy().estimateCost(model, channel(), group(), response(json), path).total());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void detailedAnthropicCacheWritesUseDifferentRatesWithoutDuplicateCharge(String path, String json) throws Exception {
        var body = response(json);
        var usage = (com.fasterxml.jackson.databind.node.ObjectNode) body.path("usage");
        usage.put("cache_creation_input_tokens", 1000);
        usage.putObject("cache_creation").put("ephemeral_5m_input_tokens", 600)
                .put("ephemeral_1h_input_tokens", 400);
        if (!MESSAGES.equals(path)) usage.put(CHAT.equals(path) ? "prompt_tokens" : "input_tokens", 288555);
        var cost = policy().estimateCost(model(), channel(), group(), body, path);
        assertMoney("0.0009", cost.cacheCreation5m());
        assertMoney("0.0016", cost.cacheCreation1h());
        assertMoney("0.0025", cost.cacheCreation());
        assertMoney("0.000150564", cost.total());
    }

    @Test
    void explicitZeroCacheIsAuthoritativeOverLegacyAlias() throws Exception {
        var body = response("""
                {"input_tokens":195,"output_tokens":168,"cache_read_input_tokens":100,
                 "input_tokens_details":{"cached_tokens":0}}
                """);
        assertMoney("0.00001062", policy().estimateCost(model(), channel(), group(), body, RESPONSES).total());
    }

    @ParameterizedTest
    @MethodSource("formats")
    void fixedRequestBillingAndChannelCostFloorStillApply(String path, String json) throws Exception {
        var model = model();
        var channel = channel();
        channel.setPriceMultiplier(new BigDecimal("0.02"));
        assertMoney("0.000251128", policy().estimateCost(model, channel, group(), response(json), path).total());
        model.setFixedRequestBilling(true);
        model.setRequestPrice(new BigDecimal("0.3"));
        assertMoney("0.3", policy().estimateCost(model, channel, group(), response(json), path).total());
    }

    static Stream<Arguments> dispatchFormats() {
        return formats().flatMap(args -> Stream.of(
                Arguments.of(args.get()[0], args.get()[1], "json"),
                Arguments.of(args.get()[0], args.get()[1], "cache_updates"),
                Arguments.of(args.get()[0], args.get()[1], "protocol_events"),
                Arguments.of(args.get()[0], args.get()[1], "fragmented_events"),
                Arguments.of(args.get()[0], args.get()[1], "ttl_json"),
                Arguments.of(args.get()[0], args.get()[1], "ttl_cache_updates"),
                Arguments.of(args.get()[0], args.get()[1], "ttl_protocol_events"),
                Arguments.of(args.get()[0], args.get()[1], "ttl_fragmented_events")));
    }

    @ParameterizedTest
    @MethodSource("dispatchFormats")
    void realDispatchChargesOnceAndRecordsNormalizedUsage(String path, String json, String scenario) throws Exception {
        boolean ttl = scenario.startsWith("ttl_");
        if (ttl) {
            scenario = scenario.substring(4);
            var ttlUsage = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(json);
            ttlUsage.put("cache_creation_input_tokens", 1000);
            ttlUsage.putObject("cache_creation").put("ephemeral_5m_input_tokens", 600)
                    .put("ephemeral_1h_input_tokens", 400);
            if (!MESSAGES.equals(path)) ttlUsage.put(CHAT.equals(path) ? "prompt_tokens" : "input_tokens", 288555);
            json = ttlUsage.toString();
        }
        final String eventScenario = scenario;
        final var forwardedBody = new java.util.concurrent.atomic.AtomicReference<String>();
        boolean stream = !"json".equals(scenario);
        String payload = switch (scenario) {
            case "json" -> response(json).toString();
            case "cache_updates" -> sse(path, json);
            case "fragmented_events" -> protocolEvents(path, json).replace("\n", "\r\n");
            default -> protocolEvents(path, json);
        };
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            forwardedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", stream ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                if ("fragmented_events".equals(eventScenario)) {
                    for (int offset = 0; offset < bytes.length; offset += 17) {
                        out.write(bytes, offset, Math.min(17, bytes.length - offset));
                        out.flush();
                    }
                } else {
                    out.write(bytes);
                }
            }
        });
        upstream.start();
        try {
            var logs = mock(RelayUsageLogMapper.class);
            var tokens = mock(RelayTokenMapper.class);
            var payments = mock(PaymentService.class);
            var policy = spy(policy());
            var channel = channel();
            var provider = new RelayChannelProvider();
            String url = "http://127.0.0.1:" + upstream.getAddress().getPort();
            provider.setApiBaseUrl(url);
            provider.setOpenaiBaseUrl(url);
            provider.setAnthropicBaseUrl(url);
            provider.setApiKey("test-only");
            var token = new RelayToken();
            token.setId(7L);
            token.setUserId(3L);
            var context = new RelayContext(token, model(), group(), channel, provider, null, "chat");
            doReturn(List.of(context)).when(policy).buildContexts(any(), any(), any(), any(), any(), any());
            var dispatch = new RelayDispatchServiceImpl(policy, new RelayProviderScheduler(), logs, tokens,
                    mock(UserMapper.class), payments, MAPPER);
            var requestBody = MAPPER.createObjectNode().put("model", "test-model").put("stream", stream);
            if (ttl && MESSAGES.equals(path)) {
                requestBody.putObject("cache_control").put("type", "ephemeral").put("ttl", "1h");
                requestBody.putArray("messages").addObject().put("role", "user")
                        .putArray("content").addObject().put("type", "text").put("text", "hello")
                        .putObject("cache_control").put("type", "ephemeral").put("ttl", "5m");
            }
            var request = new RelayDispatchRequest("Bearer test-only", "", "", "test-client", "127.0.0.1",
                    "chat", path, requestBody);
            if (stream) {
                var result = dispatch.dispatchStream(request);
                assertEquals(200, result.statusCode());
                var output = new ByteArrayOutputStream();
                result.body().writeTo(output);
                assertEquals(payload, output.toString(StandardCharsets.UTF_8));
            } else {
                var result = dispatch.dispatch(request);
                assertEquals(200, result.statusCode());
                assertEquals(payload, result.body());
            }
            var amount = ArgumentCaptor.forClass(BigDecimal.class);
            verify(payments).deductBalanceOnly(eq(3L), amount.capture());
            String expectedCost = ttl ? "0.000150564" : "0.000125564";
            assertMoney(expectedCost, amount.getValue());
            var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(logs).insert(saved.capture());
            assertEquals(ttl ? 288555 : 287555, saved.getValue().getPromptTokens());
            assertEquals(287360, saved.getValue().getCachedTokens());
            assertEquals(168, saved.getValue().getCompletionTokens());
            assertEquals(ttl ? 288723 : 287723, saved.getValue().getTotalTokens());
            assertMoney(expectedCost, saved.getValue().getCost());
            assertEquals(ttl ? 600 : 0, saved.getValue().getCacheCreation5mTokens());
            assertEquals(ttl ? 400 : 0, saved.getValue().getCacheCreation1hTokens());
            assertMoney(ttl ? "0.0009" : "0", saved.getValue().getCacheCreation5mCost());
            assertMoney(ttl ? "0.0016" : "0", saved.getValue().getCacheCreation1hCost());
            verify(tokens).incrementUsage(eq(7L), eq(1L), eq(ttl ? 288723L : 287723L), any(), any(), any());
            if (ttl && MESSAGES.equals(path)) {
                var outbound = MAPPER.readTree(forwardedBody.get());
                assertEquals(requestBody.path("cache_control"), outbound.path("cache_control"));
                assertEquals(requestBody.path("messages"), outbound.path("messages"));
            }
        } finally {
            upstream.stop(0);
        }
    }

    private static String protocolEvents(String path, String json) throws Exception {
        var finalUsage = MAPPER.readTree(json);
        if (MESSAGES.equals(path)) {
            var initialUsage = (com.fasterxml.jackson.databind.node.ObjectNode) finalUsage.deepCopy();
            initialUsage.put("output_tokens", 1);
            return "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"usage\":" + initialUsage + "}}\n\n"
                    + "event: message_delta\ndata: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":168}}\n\n"
                    + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n";
        }
        if (CHAT.equals(path)) {
            return "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}],\"usage\":null}\n\n"
                    + "data: {\"choices\":[],\"usage\":" + finalUsage + "}\n\n"
                    + "data: [DONE]\n\n";
        }
        return "event: response.created\ndata: {\"type\":\"response.created\",\"response\":{\"usage\":null}}\n\n"
                + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}\n\n"
                + "event: response.completed\ndata: {\"type\":\"response.completed\",\"response\":{\"usage\":" + finalUsage + "}}\n\n";
    }

    private static String sse(String path, String json) throws Exception {
        var finalUsage = MAPPER.readTree(json);
        var initialUsage = finalUsage.deepCopy();
        var initial = (com.fasterxml.jackson.databind.node.ObjectNode) initialUsage;
        initial.put(CHAT.equals(path) ? "completion_tokens" : "output_tokens", 0);
        if (MESSAGES.equals(path)) {
            initial.put("cache_read_input_tokens", 0);
            return "data: {\"type\":\"message_start\",\"message\":{\"usage\":" + initial + "}}\n\n"
                    + "data: {\"type\":\"message_delta\",\"usage\":" + finalUsage + "}\n\n"
                    + "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":168}}\n\n";
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) initial.path(
                CHAT.equals(path) ? "prompt_tokens_details" : "input_tokens_details")).put("cached_tokens", 0);
        String prefix = CHAT.equals(path) ? "{\"usage\":" : "{\"type\":\"response.completed\",\"response\":{\"usage\":";
        String suffix = CHAT.equals(path) ? "}" : "}}";
        return "data: " + prefix + initial + suffix + "\n\n"
                + "data: " + prefix + finalUsage + suffix + "\n\n"
                + "data: " + prefix + finalUsage + suffix + "\n\n"
                + "data: " + prefix + initial + suffix + "\n\n"
                + "data: [DONE]\n\n";
    }

    private static JsonNode response(String usage) throws Exception {
        return MAPPER.createObjectNode().set("usage", MAPPER.readTree(usage));
    }

    private static RelayPolicyServiceImpl policy() {
        return new RelayPolicyServiceImpl(null, null, null, null, null, null, null, new RelayProviderScheduler(), null, null);
    }

    private static RelayModel model() {
        var model = new RelayModel();
        model.setModel("test-model");
        model.setInputPrice(new BigDecimal("2"));
        model.setOutputPrice(new BigDecimal("4"));
        model.setCachedInputPrice(new BigDecimal("0.04"));
        model.setCacheCreationPrice(new BigDecimal("1.5"));
        return model;
    }

    private static RelayChannel channel() {
        var channel = new RelayChannel();
        channel.setId(5L);
        channel.setPriceMultiplier(new BigDecimal("0.01"));
        return channel;
    }

    private static RelayGroup group() {
        var group = new RelayGroup();
        group.setRatio(new BigDecimal("0.01"));
        return group;
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + ", got " + actual);
    }
}
