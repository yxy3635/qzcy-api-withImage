package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.dto.relay.RelayCostBreakdown;
import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.dto.relay.RelayDispatchResult;
import com.qzcy.backend.dto.relay.RelayStreamDispatchResult;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.entity.RelayUsageLog;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.service.RelayDispatchService;
import com.qzcy.backend.service.RelayPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RelayDispatchServiceImpl implements RelayDispatchService {
    private static final Duration RELAY_TIMEOUT = Duration.ofMinutes(10);
    private static final RelayCostBreakdown ZERO_COST = new RelayCostBreakdown(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
    );

    private final RelayPolicyService relayPolicyService;
    private final RelayUsageLogMapper usageLogMapper;
    private final RelayTokenMapper tokenMapper;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Override
    public RelayDispatchResult dispatch(RelayDispatchRequest request) throws Exception {
        String model = request.body().path("model").asText("");
        RelayContext context = relayPolicyService.buildContext(
                request.authorization(),
                request.apiKeyHeader(),
                request.queryKey(),
                request.clientIp(),
                request.endpointType(),
                model
        );
        long startedAt = System.currentTimeMillis();
        HttpResponse<String> response = relayString(request.body(), context, request.upstreamPath());
        JsonNode responseBody = parseResponseBody(response.body());
        if (response.statusCode() >= 200 && response.statusCode() < 300 && !hasBillableUsage(responseBody)) {
            responseBody = withEstimatedUsage(request.body(), response.body());
        }
        RelayCostBreakdown cost = relayPolicyService.estimateCost(context.model(), context.channel(), context.group(), responseBody);
        chargeIfSuccessful(response.statusCode(), context, cost);
        saveUsage(context, request.upstreamPath(), request.userAgent(), response.statusCode(), responseBody, cost, System.currentTimeMillis() - startedAt);
        return new RelayDispatchResult(response.statusCode(), contentType(response), response.body());
    }

    @Override
    public RelayStreamDispatchResult dispatchStream(RelayDispatchRequest request) throws Exception {
        String model = request.body().path("model").asText("");
        RelayContext context = relayPolicyService.buildContext(
                request.authorization(),
                request.apiKeyHeader(),
                request.queryKey(),
                request.clientIp(),
                request.endpointType(),
                model
        );
        long startedAt = System.currentTimeMillis();
        HttpResponse<InputStream> response = relayStream(request.body(), context, request.upstreamPath());
        JsonNode responseBody = emptyResponseBody();
        RelayCostBreakdown cost = relayPolicyService.estimateCost(context.model(), context.channel(), context.group(), responseBody);
        chargeIfSuccessful(response.statusCode(), context, cost);
        StreamingResponseBody stream = outputStream -> {
            try (InputStream inputStream = response.body()) {
                inputStream.transferTo(outputStream);
            } finally {
                saveUsage(context, request.upstreamPath(), request.userAgent(), response.statusCode(),
                        responseBody, cost, System.currentTimeMillis() - startedAt);
            }
        };
        return new RelayStreamDispatchResult(response.statusCode(), contentType(response), stream);
    }

    private void chargeIfSuccessful(int statusCode, RelayContext context, RelayCostBreakdown cost) {
        if (statusCode < 200 || statusCode >= 300 || !cost.billable()) {
            return;
        }
        relayPolicyService.enforceQuota(context.token(), cost.total());
        paymentService.deductBalance(context.token().getUserId(), cost.total());
    }

    private HttpResponse<String> relayString(ObjectNode body, RelayContext context, String path) throws Exception {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                .send(upstreamRequest(body, context, path), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<InputStream> relayStream(ObjectNode body, RelayContext context, String path) throws Exception {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
                .send(upstreamRequest(body, context, path), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest upstreamRequest(ObjectNode body, RelayContext context, String path) throws Exception {
        RelayChannel channel = context.channel();
        ObjectNode outboundBody = prepareOutboundBody(body, context, path);
        return HttpRequest.newBuilder(URI.create(relayUrl(channel.getApiBaseUrl(), path)))
                .timeout(RELAY_TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + channel.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, acceptHeader(body))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(outboundBody)))
                .build();
    }

    private ObjectNode prepareOutboundBody(ObjectNode body, RelayContext context, String path) {
        ObjectNode outbound = body.deepCopy();
        String upstreamModel = upstreamModel(context);
        if (!upstreamModel.isBlank()) {
            outbound.put("model", upstreamModel);
        }
        if (outbound.path("stream").asBoolean(false) && path.endsWith("/chat/completions")) {
            ObjectNode streamOptions = outbound.withObject("/stream_options");
            streamOptions.put("include_usage", true);
        }
        return outbound;
    }

    private String upstreamModel(RelayContext context) {
        RelayChannelModel binding = context.channelModel();
        if (binding != null && binding.getUpstreamModel() != null && !binding.getUpstreamModel().isBlank()) {
            return binding.getUpstreamModel().trim();
        }
        return context.model() == null || context.model().getModel() == null ? "" : context.model().getModel();
    }

    private String acceptHeader(ObjectNode body) {
        return body.path("stream").asBoolean(false) ? MediaType.TEXT_EVENT_STREAM_VALUE : MediaType.APPLICATION_JSON_VALUE;
    }

    private void saveUsage(RelayContext context, String endpoint, String userAgent,
                           int statusCode, JsonNode responseBody, RelayCostBreakdown cost, long durationMs) {
        RelayToken access = context.token();
        RelayChannel channel = context.channel();
        RelayGroup group = context.group();
        JsonNode usage = responseBody.path("usage");
        int promptTokens = inputTokens(usage);
        int completionTokens = outputTokens(usage);
        int cachedTokens = cacheReadTokens(usage);
        int cacheCreationTokens = cacheCreationTokens(usage);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        RelayUsageLog log = new RelayUsageLog();
        log.setUserId(access.getUserId());
        log.setTokenId(access.getId());
        log.setChannelId(channel.getId());
        log.setTokenName(access.getName());
        log.setChannelName(channel.getName());
        log.setGroupNames(access.getGroupNames());
        log.setEndpoint(endpoint);
        log.setModel(context.model() == null ? "" : context.model().getModel());
        log.setModelType(context.effectiveModelType());
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setCachedTokens(cachedTokens);
        log.setCacheCreationTokens(cacheCreationTokens);
        log.setTotalTokens(totalTokens);
        log.setInputCost(cost.input());
        log.setOutputCost(cost.output());
        log.setCacheReadCost(cost.cacheRead());
        log.setCacheCreationCost(cost.cacheCreation());
        log.setRequestCost(cost.request());
        log.setGroupRatio(group == null || group.getRatio() == null ? BigDecimal.ONE : group.getRatio());
        log.setChannelRatio(channel == null || channel.getPriceMultiplier() == null ? BigDecimal.ONE : channel.getPriceMultiplier());
        log.setCost(cost.total());
        log.setStatusCode(statusCode);
        log.setDurationMs(durationMs);
        log.setUserAgent(userAgent == null ? "" : userAgent);
        log.setStatus(statusCode >= 200 && statusCode < 300 ? "success" : "failed");
        log.setMessage(logMessage(statusCode, responseBody));
        log.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log);
        access.setRequestCount((access.getRequestCount() == null ? 0L : access.getRequestCount()) + 1);
        access.setTokenCount((access.getTokenCount() == null ? 0L : access.getTokenCount()) + totalTokens);
        access.setUsedQuota((access.getUsedQuota() == null ? BigDecimal.ZERO : access.getUsedQuota()).add(cost.total()));
        access.setLastUsedAt(LocalDateTime.now());
        tokenMapper.updateById(access);
    }

    private JsonNode parseResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return emptyResponseBody();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                return root;
            }
            JsonNode sseUsage = parseSseUsage(body);
            if (sseUsage != null) {
                ObjectNode wrapper = objectMapper.createObjectNode();
                wrapper.set("usage", sseUsage);
                return wrapper;
            }
            return root;
        } catch (Exception ignored) {
            JsonNode sseUsage = parseSseUsage(body);
            if (sseUsage != null) {
                ObjectNode wrapper = objectMapper.createObjectNode();
                wrapper.set("usage", sseUsage);
                return wrapper;
            }
            return emptyResponseBody();
        }
    }

    private JsonNode parseSseUsage(String body) {
        JsonNode latestUsage = null;
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) continue;
            try {
                JsonNode event = objectMapper.readTree(payload);
                JsonNode usage = event.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    latestUsage = usage;
                }
                JsonNode responseUsage = event.path("response").path("usage");
                if (!responseUsage.isMissingNode() && !responseUsage.isNull()) {
                    latestUsage = responseUsage;
                }
            } catch (Exception ignored) {
                // Ignore non-JSON SSE frames.
            }
        }
        return latestUsage;
    }

    private int intValue(JsonNode node, String primary, String fallback) {
        return node.path(primary).asInt(node.path(fallback).asInt(0));
    }

    private int inputTokens(JsonNode usage) {
        return firstInt(usage, "prompt_tokens", "input_tokens", "input_tokens_total");
    }

    private int outputTokens(JsonNode usage) {
        return firstInt(usage, "completion_tokens", "output_tokens");
    }

    private int cacheReadTokens(JsonNode usage) {
        int nested = firstInt(usage.path("prompt_tokens_details"), "cached_tokens", "cache_read_tokens");
        if (nested > 0) return nested;
        nested = firstInt(usage.path("input_tokens_details"), "cached_tokens", "cache_read_tokens");
        if (nested > 0) return nested;
        return firstInt(usage, "cache_read_input_tokens", "cache_read_tokens", "cached_tokens", "prompt_cache_hit_tokens");
    }

    private int cacheCreationTokens(JsonNode usage) {
        int nested = firstInt(usage.path("prompt_tokens_details"), "cache_creation_tokens", "cached_creation_tokens");
        if (nested > 0) return nested;
        nested = firstInt(usage.path("input_tokens_details"), "cache_creation_tokens", "cached_creation_tokens");
        if (nested > 0) return nested;
        return firstInt(usage, "cache_creation_input_tokens", "cache_creation_tokens", "cached_creation_tokens");
    }

    private int firstInt(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) return value.asInt();
        }
        return 0;
    }

    private boolean hasBillableUsage(JsonNode responseBody) {
        JsonNode usage = responseBody.path("usage");
        int promptTokens = intValue(usage, "prompt_tokens", "input_tokens");
        int completionTokens = intValue(usage, "completion_tokens", "output_tokens");
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        return totalTokens > 0;
    }

    private JsonNode withEstimatedUsage(ObjectNode requestBody, String responseBody) {
        int promptTokens = estimateTokens(textFromRequest(requestBody));
        int completionTokens = estimateTokens(textFromResponse(responseBody));
        ObjectNode wrapper = objectMapper.createObjectNode();
        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        usage.put("estimated", true);
        wrapper.set("usage", usage);
        return wrapper;
    }

    private String textFromRequest(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        collectText(node, builder, Set.of("input", "instructions", "messages", "content", "text", "prompt"));
        return builder.toString();
    }

    private String textFromResponse(String body) {
        if (body == null || body.isBlank()) return "";
        StringBuilder builder = new StringBuilder();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) continue;
            try {
                collectText(objectMapper.readTree(payload), builder, Set.of("output_text", "text", "delta", "content"));
            } catch (Exception ignored) {
                // Ignore non-JSON SSE frames.
            }
        }
        if (!builder.isEmpty()) return builder.toString();
        try {
            collectText(objectMapper.readTree(body), builder, Set.of("output_text", "text", "content"));
        } catch (Exception ignored) {
            builder.append(body);
        }
        return builder.toString();
    }

    private void collectText(JsonNode node, StringBuilder builder, Set<String> allowedNames) {
        collectText(node, builder, allowedNames, "");
    }

    private void collectText(JsonNode node, StringBuilder builder, Set<String> allowedNames, String fieldName) {
        if (node == null || node.isNull()) return;
        if (node.isTextual() && allowedNames.contains(fieldName)) {
            builder.append(node.asText()).append('\n');
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectText(item, builder, allowedNames, fieldName));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), builder, allowedNames, entry.getKey()));
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int ascii = 0;
        int nonAscii = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) < 128) ascii++;
            else nonAscii++;
        }
        return Math.max(1, (int) Math.ceil(ascii / 4.0 + nonAscii / 1.6));
    }

    private JsonNode emptyResponseBody() {
        ObjectNode empty = objectMapper.createObjectNode();
        empty.put("message", "");
        return empty;
    }

    private String logMessage(int statusCode, JsonNode responseBody) {
        if (statusCode < 200 || statusCode >= 300) {
            return responseBody.toString();
        }
        return responseBody.path("usage").path("estimated").asBoolean(false) ? "usage estimated from request/response text" : "";
    }

    private String contentType(HttpResponse<?> response) {
        return response.headers()
                .firstValue(HttpHeaders.CONTENT_TYPE)
                .orElse(MediaType.APPLICATION_JSON_VALUE);
    }

    private String relayUrl(String apiBaseUrl, String path) {
        String baseUrl = apiBaseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/v1") && path.startsWith("/v1/")) {
            path = path.substring(3);
        }
        return baseUrl + path;
    }
}
