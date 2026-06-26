package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.dto.relay.RelayCostBreakdown;
import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.dto.relay.RelayDispatchResult;
import com.qzcy.backend.dto.relay.RelayMultipartFile;
import com.qzcy.backend.dto.relay.RelayStreamDispatchResult;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.entity.RelayUsageLog;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.service.RelayDispatchService;
import com.qzcy.backend.service.RelayPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelayDispatchServiceImpl implements RelayDispatchService {
    private static final Duration RELAY_TIMEOUT = Duration.ofMinutes(10);
    private static final RelayCostBreakdown ZERO_COST = new RelayCostBreakdown(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
    );
    private static final Map<Long, ChannelStreamGate> STREAM_CHANNEL_GATES = new ConcurrentHashMap<>();

    /** 每渠道流式并发闸门：持有信号量与配置的许可数，便于配置变更后自愈重建。 */
    private record ChannelStreamGate(Semaphore semaphore, int permits) {
        ChannelStreamGate(int permits) {
            this(new Semaphore(permits, true), permits);
        }
    }
    // 每渠道流式并发：默认 8（Anthropic 各档限额远高于 1，写死 1 会把天然并发的客户端直接拒掉）；未配置时回退此默认值。
    private static final int DEFAULT_STREAM_CONCURRENCY = 8;
    private static final int MAX_STREAM_CONCURRENCY = 128;
    // 闸门令牌等待：瞬时并发已满时短暂排队，而非立即对客户端返回 429。
    private static final Duration STREAM_GATE_ACQUIRE_TIMEOUT = Duration.ofSeconds(5);
    // 429/容量错误重试退避上限（尊重但截断上游 retry-after，避免长时间占用线程）。
    private static final Duration STREAM_RETRY_BACKOFF_CAP = Duration.ofSeconds(5);
    // anthropic-beta 透传：按格式校验放行（含 prompt-caching / redact-thinking / fine-grained-tool-streaming 等），
    // 仅丢弃畸形项，避免把客户端的缓存/隐私特性 beta 标记整段滤掉导致上游不生效。
    private static final java.util.regex.Pattern ANTHROPIC_BETA_ITEM =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");
    private static final int ANTHROPIC_BETA_MAX_ITEMS = 16;
    private static final int ANTHROPIC_BETA_MAX_LENGTH = 512;

    private final RelayPolicyService relayPolicyService;
    private final RelayUsageLogMapper usageLogMapper;
    private final RelayTokenMapper tokenMapper;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public RelayDispatchResult dispatch(RelayDispatchRequest request) throws Exception {
        String model = request.body().path("model").asText("");
        List<RelayContext> contexts = relayPolicyService.buildContexts(
                request.authorization(),
                request.apiKeyHeader(),
                request.queryKey(),
                request.clientIp(),
                request.endpointType(),
                model
        );
        BusinessException rateLimitFailure = null;
        for (int index = 0; index < contexts.size(); index++) {
            RelayContext context = contexts.get(index);
            try {
                relayPolicyService.enforceRateLimits(context.token(), context.channel());
            } catch (BusinessException ex) {
                if (ex.getCode() != 429 || index == contexts.size() - 1) {
                    throw ex;
                }
                rateLimitFailure = ex;
                continue;
            }
            long startedAt = System.currentTimeMillis();
            log.debug("Relay upstream attempt path={} model={} channelId={} channelName={} rule={} group={} endpointType={} attempt={}/{}",
                    request.upstreamPath(),
                    model,
                    context.channel().getId(),
                    context.channel().getName(),
                    context.channel().getChannelRule(),
                    context.group() == null ? "" : context.group().getCode(),
                    request.endpointType(),
                    index + 1,
                    contexts.size());
            HttpResponse<String> response;
            try {
                response = hasFiles(request.files())
                        ? relayMultipart(request.body(), request.files(), context, request.upstreamPath(), request.anthropicVersion(), request.anthropicBeta())
                        : relayString(request.body(), context, request.upstreamPath(), request.anthropicVersion(), request.anthropicBeta());
            } catch (Exception ex) {
                log.warn("Relay upstream request failed path={} channelId={} channelName={} rule={} message={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        context.channel().getName(),
                        context.channel().getChannelRule(),
                        ex.getMessage(),
                        ex);
                throw ex;
            }
            JsonNode responseBody = parseResponseBody(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && !isCountTokensRequest(request.upstreamPath())
                    && !hasBillableUsage(responseBody)) {
                responseBody = withEstimatedUsage(request.body(), response.body());
            }
            RelayCostBreakdown cost = isCountTokensRequest(request.upstreamPath())
                    ? ZERO_COST
                    : relayPolicyService.estimateCost(context.model(), context.channel(), context.group(), responseBody);
            boolean retryable = isRetryableCapacityError(response.statusCode(), responseBody, response.body());
            log.debug("Relay upstream response path={} channelId={} status={} retryable={} billable={} durationMs={} usage={}",
                    request.upstreamPath(),
                    context.channel().getId(),
                    response.statusCode(),
                    retryable,
                    cost.billable(),
                    System.currentTimeMillis() - startedAt,
                    responseBody.path("usage").isMissingNode() ? "" : truncateMessage(responseBody.path("usage").toString()));
            if (!retryable || index == contexts.size() - 1) {
                chargeIfSuccessful(response.statusCode(), context, cost);
                saveUsage(context, request.upstreamPath(), request.userAgent(), response.statusCode(), responseBody, cost, System.currentTimeMillis() - startedAt);
                return new RelayDispatchResult(response.statusCode(), contentType(response), response.body());
            }
            saveUsage(context, request.upstreamPath(), request.userAgent(), response.statusCode(), responseBody, cost, System.currentTimeMillis() - startedAt);
        }
        if (rateLimitFailure != null) throw rateLimitFailure;
        throw new BusinessException(400, "No available relay channel for current group and model");
    }

    @Override
    public RelayStreamDispatchResult dispatchStream(RelayDispatchRequest request) throws Exception {
        String model = request.body().path("model").asText("");
        List<RelayContext> contexts = relayPolicyService.buildContexts(
                request.authorization(),
                request.apiKeyHeader(),
                request.queryKey(),
                request.clientIp(),
                request.endpointType(),
                model
        );
        BusinessException rateLimitFailure = null;
        String lastErrorText = "";
        int lastStatus = 500;
        for (int index = 0; index < contexts.size(); index++) {
            RelayContext context = contexts.get(index);
            try {
                relayPolicyService.enforceRateLimits(context.token(), context.channel());
            } catch (BusinessException ex) {
                if (ex.getCode() != 429 || index == contexts.size() - 1) {
                    throw ex;
                }
                rateLimitFailure = ex;
                continue;
            }
            long startedAt = System.currentTimeMillis();
            ChannelStreamGate gate = streamGate(context.channel());
            Semaphore streamGate = gate.semaphore();
            log.debug("Relay stream gate acquire channelId={} permits={} available={} queued={}",
                    context.channel().getId(),
                    gate.permits(),
                    streamGate.availablePermits(),
                    streamGate.getQueueLength());
            boolean acquired;
            try {
                acquired = streamGate.tryAcquire(STREAM_GATE_ACQUIRE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException(500, "Relay dispatch interrupted while acquiring concurrency slot");
            }
            if (!acquired) {
                log.warn("Relay upstream stream skipped by local concurrency gate path={} channelId={} channelName={} permits={} available={} queued={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        context.channel().getName(),
                        gate.permits(),
                        streamGate.availablePermits(),
                        streamGate.getQueueLength());
                if (index < contexts.size() - 1) {
                    continue;
                }
                String errorText = "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"Relay channel is busy, please retry later\"}}";
                saveUsage(context, request.upstreamPath(), request.userAgent(), 429, parseResponseBody(errorText), ZERO_COST, 0);
                return new RelayStreamDispatchResult(
                        429,
                        MediaType.APPLICATION_JSON_VALUE,
                        outputStream -> outputStream.write(errorText.getBytes(StandardCharsets.UTF_8))
                );
            }
            log.debug("Relay upstream stream attempt path={} model={} channelId={} channelName={} rule={} group={} endpointType={} attempt={}/{}",
                    request.upstreamPath(),
                    model,
                    context.channel().getId(),
                    context.channel().getName(),
                    context.channel().getChannelRule(),
                    context.group() == null ? "" : context.group().getCode(),
                    request.endpointType(),
                    index + 1,
                    contexts.size());
            HttpResponse<InputStream> response;
            try {
                response = relayStream(request.body(), context, request.upstreamPath(), request.anthropicVersion(), request.anthropicBeta());
            } catch (Exception ex) {
                streamGate.release();
                log.warn("Relay upstream stream request failed path={} channelId={} channelName={} rule={} message={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        context.channel().getName(),
                        context.channel().getChannelRule(),
                        ex.getMessage(),
                        ex);
                throw ex;
            }
            log.debug("Relay upstream stream connected path={} channelId={} status={} contentType={} durationMs={}",
                    request.upstreamPath(),
                    context.channel().getId(),
                    response.statusCode(),
                    contentType(response),
                    System.currentTimeMillis() - startedAt);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorText;
                try (InputStream inputStream = response.body()) {
                    errorText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                } finally {
                    streamGate.release();
                }
                JsonNode responseBody = parseResponseBody(errorText);
                RelayCostBreakdown cost = ZERO_COST;
                saveUsage(context, request.upstreamPath(), request.userAgent(), response.statusCode(),
                        responseBody, cost, System.currentTimeMillis() - startedAt);
                boolean retryable = isRetryableCapacityError(response.statusCode(), responseBody, errorText);
                lastErrorText = errorText;
                lastStatus = response.statusCode();
                log.warn("Relay upstream stream error path={} channelId={} status={} retryable={} contentType={} body={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        response.statusCode(),
                        retryable,
                        contentType(response),
                        truncateMessage(errorText));
                if (retryable && index < contexts.size() - 1) {
                    sleepBounded(parseRetryAfterMillis(response.headers()));
                    continue;
                }
                return new RelayStreamDispatchResult(
                        response.statusCode(),
                        MediaType.APPLICATION_JSON_VALUE,
                        outputStream -> outputStream.write(normalizeErrorBody(errorText, request.upstreamPath(), response.statusCode()).getBytes(StandardCharsets.UTF_8))
                );
            }
            RelayContext finalContext = context;
            long finalStartedAt = startedAt;
            HttpResponse<InputStream> finalResponse = response;
            Semaphore finalStreamGate = streamGate;
            StreamingResponseBody stream = outputStream -> {
                // 边收边转发：用阻塞 read() 等待上游每一段数据，收到立即 flush 给客户端。
                // 不用 InputStream.available() 轮询判断首字节——对分块/SSE 响应体它不可靠，
                // 会因上游整体缓冲返回而误判“无数据”，向客户端投放假错误并触发重试。
                // 上游“迟迟不发数据”的真实情形由 RELAY_TIMEOUT（10 分钟）兜底，无需在这里伪造错误。
                StreamUsageAccumulator acc = new StreamUsageAccumulator();
                long totalBytes = 0;
                byte[] buffer = new byte[8192];
                try (InputStream inputStream = finalResponse.body()) {
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        if (read <= 0) {
                            continue;
                        }
                        acc.onBytes(buffer, 0, read);
                        outputStream.write(buffer, 0, read);
                        outputStream.flush();
                        totalBytes += read;
                    }
                } catch (Exception ex) {
                    log.warn("Relay downstream stream write/read failed path={} channelId={} status={} capturedBytes={} preview={} message={}",
                            request.upstreamPath(),
                            finalContext.channel().getId(),
                            finalResponse.statusCode(),
                            totalBytes,
                            sanitizeStreamPreview(acc.previewString()),
                            ex.getMessage(),
                            ex);
                    throw ex;
                } finally {
                    finalStreamGate.release();
                    if (log.isDebugEnabled() && acc.hasPreview()) {
                        log.debug("Relay upstream stream preview path={} channelId={} status={} preview={}",
                                request.upstreamPath(),
                                finalContext.channel().getId(),
                                finalResponse.statusCode(),
                                sanitizeStreamPreview(acc.previewString()));
                    }
                    log.debug("Relay upstream stream captured path={} channelId={} status={} bytes={}",
                            request.upstreamPath(),
                            finalContext.channel().getId(),
                            finalResponse.statusCode(),
                            totalBytes);
                    JsonNode responseBody = acc.buildResponseBody();
                    if (finalResponse.statusCode() >= 200 && finalResponse.statusCode() < 300
                            && !isCountTokensRequest(request.upstreamPath())
                            && !hasBillableUsage(responseBody)) {
                        responseBody = withEstimatedUsage(request.body(), acc.previewString());
                    }
                    RelayCostBreakdown cost = isCountTokensRequest(request.upstreamPath())
                            ? ZERO_COST
                            : relayPolicyService.estimateCost(finalContext.model(), finalContext.channel(), finalContext.group(), responseBody);
                    chargeIfSuccessful(finalResponse.statusCode(), finalContext, cost);
                    saveUsage(finalContext, request.upstreamPath(), request.userAgent(), finalResponse.statusCode(),
                            responseBody, cost, System.currentTimeMillis() - finalStartedAt);
                    log.debug("Relay upstream stream response path={} channelId={} status={} billable={} durationMs={} usage={}",
                            request.upstreamPath(),
                            finalContext.channel().getId(),
                            finalResponse.statusCode(),
                            cost.billable(),
                            System.currentTimeMillis() - finalStartedAt,
                            responseBody.path("usage").isMissingNode() ? "" : truncateMessage(responseBody.path("usage").toString()));
                }
            };
            return new RelayStreamDispatchResult(response.statusCode(), contentType(response), stream);
        }
        if (rateLimitFailure != null) throw rateLimitFailure;
        final int finalLastStatus = lastStatus;
        final String finalLastErrorText = lastErrorText;
        return new RelayStreamDispatchResult(
                finalLastStatus,
                MediaType.APPLICATION_JSON_VALUE,
                outputStream -> outputStream.write(normalizeErrorBody(finalLastErrorText, request.upstreamPath(), finalLastStatus).getBytes(StandardCharsets.UTF_8))
        );
    }

    private void chargeIfSuccessful(int statusCode, RelayContext context, RelayCostBreakdown cost) {
        if (statusCode < 200 || statusCode >= 300 || !cost.billable()) {
            return;
        }
        relayPolicyService.enforceQuota(context.token(), cost.total());
        paymentService.deductBalanceOnly(context.token().getUserId(), cost.total());
    }

    private HttpResponse<String> relayString(ObjectNode body, RelayContext context, String path, String anthropicVersion, String anthropicBeta) throws Exception {
        return httpClient.send(upstreamRequest(body, context, path, anthropicVersion, anthropicBeta), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> relayMultipart(ObjectNode body, List<RelayMultipartFile> files, RelayContext context, String path, String anthropicVersion, String anthropicBeta) throws Exception {
        return httpClient.send(upstreamMultipartRequest(body, files, context, path, anthropicVersion, anthropicBeta), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<InputStream> relayStream(ObjectNode body, RelayContext context, String path, String anthropicVersion, String anthropicBeta) throws Exception {
        return httpClient.send(upstreamRequest(body, context, path, anthropicVersion, anthropicBeta), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest upstreamRequest(ObjectNode body, RelayContext context, String path, String anthropicVersion, String anthropicBeta) throws Exception {
        RelayChannel channel = context.channel();
        ObjectNode outboundBody = prepareOutboundBody(body, context, path);
        String url = relayUrl(channel.getApiBaseUrl(), path);
        log.debug("Relay upstream request url={} channelId={} channelName={} rule={} model={} outboundModel={} stream={} authMode={} anthropicVersion={} anthropicBeta={}",
                url,
                channel.getId(),
                channel.getName(),
                channel.getChannelRule(),
                body == null ? "" : body.path("model").asText(""),
                outboundBody.path("model").asText(""),
                body != null && body.path("stream").asBoolean(false),
                isAnthropicChannel(channel) ? "x-api-key" : "bearer",
                headerOrDefault(anthropicVersion, "2023-06-01"),
                anthropicBeta == null || anthropicBeta.isBlank() ? "" : "present");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(relayUrl(channel.getApiBaseUrl(), path)))
                .timeout(RELAY_TIMEOUT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, acceptHeader(body))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(outboundBody)));
        applyAuthHeaders(builder, channel, anthropicVersion, anthropicBeta);
        return builder.build();
    }

    private HttpRequest upstreamMultipartRequest(ObjectNode body, List<RelayMultipartFile> files, RelayContext context, String path, String anthropicVersion, String anthropicBeta) throws Exception {
        RelayChannel channel = context.channel();
        ObjectNode outboundBody = prepareOutboundBody(body, context, path);
        String url = relayUrl(channel.getApiBaseUrl(), path);
        log.debug("Relay upstream multipart request url={} channelId={} channelName={} rule={} model={} outboundModel={} fileCount={} authMode={}",
                url,
                channel.getId(),
                channel.getName(),
                channel.getChannelRule(),
                body == null ? "" : body.path("model").asText(""),
                outboundBody.path("model").asText(""),
                files == null ? 0 : files.size(),
                isAnthropicChannel(channel) ? "x-api-key" : "bearer");
        String boundary = "----imageCreaterBoundary" + UUID.randomUUID();
        byte[] multipartBody = multipartBody(outboundBody, files, boundary);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(relayUrl(channel.getApiBaseUrl(), path)))
                .timeout(RELAY_TIMEOUT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody));
        applyAuthHeaders(builder, channel, anthropicVersion, anthropicBeta);
        return builder.build();
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

    private void applyAuthHeaders(HttpRequest.Builder builder, RelayChannel channel, String anthropicVersion, String anthropicBeta) {
        if (isAnthropicChannel(channel)) {
            builder.header("x-api-key", channel.getApiKey())
                    .header("anthropic-version", headerOrDefault(anthropicVersion, "2023-06-01"));
            String beta = safeAnthropicBeta(anthropicBeta);
            if (!beta.isBlank()) {
                builder.header("anthropic-beta", beta);
            }
            return;
        }
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + channel.getApiKey());
    }

    private String headerOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String safeAnthropicBeta(String value) {
        if (value == null || value.isBlank()) return "";
        String joined = List.of(value.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .filter(item -> ANTHROPIC_BETA_ITEM.matcher(item).matches())
                .limit(ANTHROPIC_BETA_MAX_ITEMS)
                .collect(Collectors.joining(","));
        if (joined.length() > ANTHROPIC_BETA_MAX_LENGTH) {
            joined = joined.substring(0, ANTHROPIC_BETA_MAX_LENGTH);
            int lastComma = joined.lastIndexOf(',');
            if (lastComma > 0) {
                joined = joined.substring(0, lastComma);
            }
        }
        return joined;
    }

    private boolean isAnthropicChannel(RelayChannel channel) {
        String rule = channel == null || channel.getChannelRule() == null ? "" : channel.getChannelRule().toLowerCase();
        if ("anthropic".equals(rule)) return true;
        if ("openai".equals(rule)) return false;
        String provider = channel == null || channel.getProvider() == null ? "" : channel.getProvider().toLowerCase();
        String baseUrl = channel == null || channel.getApiBaseUrl() == null ? "" : channel.getApiBaseUrl().toLowerCase();
        return provider.contains("anthropic")
                || provider.contains("claude")
                || baseUrl.contains("api.anthropic.com");
    }

    private boolean hasFiles(List<RelayMultipartFile> files) {
        return files != null && !files.isEmpty();
    }

    private boolean isCountTokensRequest(String path) {
        return path != null && path.endsWith("/messages/count_tokens");
    }

    private ChannelStreamGate streamGate(RelayChannel channel) {
        Long channelId = channel == null ? -1L : channel.getId();
        int permits = resolveConcurrency(channel);
        return STREAM_CHANNEL_GATES.compute(channelId, (key, existing) -> {
            if (existing != null && existing.permits() == permits) {
                return existing;
            }
            // 首次使用或配置变更：重建闸门。旧闸门上在途请求仍会 release 到旧信号量（随后被 GC），不丢失、不阻塞。
            log.debug("Relay stream concurrency gate (re)built channelId={} permits={} previous={}",
                    channelId, permits, existing == null ? 0 : existing.permits());
            return new ChannelStreamGate(permits);
        });
    }

    private int resolveConcurrency(RelayChannel channel) {
        Integer configured = channel == null ? null : channel.getMaxConcurrency();
        if (configured == null || configured <= 0) {
            return DEFAULT_STREAM_CONCURRENCY;
        }
        return Math.min(configured, MAX_STREAM_CONCURRENCY);
    }

    private long parseRetryAfterMillis(java.net.http.HttpHeaders headers) {
        if (headers == null) {
            return 0;
        }
        return headers.firstValue("retry-after")
                .map(value -> {
                    try {
                        long seconds = Long.parseLong(value.trim());
                        if (seconds <= 0) {
                            return 0L;
                        }
                        long capped = Math.min(seconds, STREAM_RETRY_BACKOFF_CAP.toSeconds());
                        return capped * 1000L;
                    } catch (NumberFormatException ex) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    private void sleepBounded(long millis) throws InterruptedException {
        if (millis <= 0) {
            return;
        }
        Thread.sleep(Math.min(millis, STREAM_RETRY_BACKOFF_CAP.toMillis()));
    }

    /**
     * 边转发边解析 SSE 流，只保留用量与一小段预览，不再把整条上游响应缓冲进内存。
     * 用量来自 Anthropic 的 message_start / message_delta（以及 OpenAI 末帧 usage）等事件。
     */
    private final class StreamUsageAccumulator {
        private static final int PREVIEW_CAP = 4096;
        private static final int LINE_CAP = 1 << 20;
        private final ByteArrayOutputStream preview = new ByteArrayOutputStream();
        private final ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        private final ObjectNode usage = objectMapper.createObjectNode();

        void onBytes(byte[] b, int off, int len) {
            if (len <= 0) {
                return;
            }
            if (preview.size() < PREVIEW_CAP) {
                int n = Math.min(len, PREVIEW_CAP - preview.size());
                preview.write(b, off, n);
            }
            for (int i = 0; i < len; i++) {
                byte c = b[off + i];
                if (c == '\n') {
                    processLine(lineBuf.toString(StandardCharsets.UTF_8));
                    lineBuf.reset();
                } else {
                    lineBuf.write(c);
                    if (lineBuf.size() > LINE_CAP) {
                        lineBuf.reset();
                    }
                }
            }
        }

        private void processLine(String line) {
            if (line == null) {
                return;
            }
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                return;
            }
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) {
                return;
            }
            try {
                JsonNode event = objectMapper.readTree(payload);
                mergeUsage(usage, event.path("usage"));
                mergeUsage(usage, event.path("message").path("usage"));
                mergeUsage(usage, event.path("response").path("usage"));
            } catch (Exception ignored) {
                // 跳过非 JSON 的 SSE 帧。
            }
        }

        boolean hasPreview() {
            return preview.size() > 0;
        }

        String previewString() {
            return preview.toString(StandardCharsets.UTF_8);
        }

        JsonNode buildResponseBody() {
            ObjectNode wrapper = objectMapper.createObjectNode();
            if (!usage.isEmpty()) {
                wrapper.set("usage", usage);
            }
            wrapper.put("message", "");
            return wrapper;
        }
    }

    private String sanitizeStreamPreview(String value) {
        if (value == null || value.isBlank()) return "";
        String sanitized = value
                .replaceAll("(?i)(x-api-key|authorization|api[_-]?key|sk-[A-Za-z0-9_-]{8,})[:=][^\\s,}]+", "$1=<redacted>")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return truncateMessage(sanitized);
    }

    private byte[] multipartBody(ObjectNode fields, List<RelayMultipartFile> files, String boundary) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Iterator<String> names = fields.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = fields.get(name);
            if (value == null || value.isNull() || value.isContainerNode()) {
                continue;
            }
            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"" + escapeMultipartName(name) + "\"\r\n\r\n");
            output.write(value.asText("").getBytes(StandardCharsets.UTF_8));
            writeAscii(output, "\r\n");
        }
        if (files != null) {
            for (RelayMultipartFile file : files) {
                writeAscii(output, "--" + boundary + "\r\n");
                writeAscii(output, "Content-Disposition: form-data; name=\"" + escapeMultipartName(file.fieldName()) + "\"; filename=\"" + escapeMultipartName(file.filename()) + "\"\r\n");
                writeAscii(output, "Content-Type: " + safeContentType(file.contentType()) + "\r\n\r\n");
                output.write(file.content());
                writeAscii(output, "\r\n");
            }
        }
        writeAscii(output, "--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream output, String text) throws Exception {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private String escapeMultipartName(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String safeContentType(String value) {
        return value == null || value.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : value;
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
        ObjectNode mergedUsage = objectMapper.createObjectNode();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isBlank() || "[DONE]".equals(payload)) continue;
            try {
                JsonNode event = objectMapper.readTree(payload);
                JsonNode usage = event.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    mergeUsage(mergedUsage, usage);
                }
                JsonNode messageUsage = event.path("message").path("usage");
                if (!messageUsage.isMissingNode() && !messageUsage.isNull()) {
                    mergeUsage(mergedUsage, messageUsage);
                }
                JsonNode responseUsage = event.path("response").path("usage");
                if (!responseUsage.isMissingNode() && !responseUsage.isNull()) {
                    mergeUsage(mergedUsage, responseUsage);
                }
            } catch (Exception ignored) {
                // Ignore non-JSON SSE frames.
            }
        }
        return mergedUsage.isEmpty() ? null : mergedUsage;
    }

    private void mergeUsage(ObjectNode target, JsonNode source) {
        source.fields().forEachRemaining(entry -> {
            JsonNode existing = target.path(entry.getKey());
            if (entry.getValue().isNumber() && (!existing.isNumber() || entry.getValue().asLong() > existing.asLong())) {
                target.put(entry.getKey(), entry.getValue().asLong());
            } else if (existing.isMissingNode()) {
                target.set(entry.getKey(), entry.getValue());
            }
        });
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

    private String normalizeErrorBody(String body, String path, int statusCode) {
        if (path != null && path.startsWith("/v1/messages")) {
            try {
                JsonNode root = objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
                if ("error".equals(root.path("type").asText()) && root.path("error").isObject()) {
                    return objectMapper.writeValueAsString(root);
                }
                JsonNode upstreamError = root.path("error");
                String message = upstreamError.path("message").asText(root.path("message").asText(body == null ? "" : body));
                ObjectNode wrapper = objectMapper.createObjectNode();
                ObjectNode error = objectMapper.createObjectNode();
                wrapper.put("type", "error");
                error.put("type", anthropicErrorType(statusCode));
                error.put("message", message == null || message.isBlank() ? "Upstream request failed" : message);
                wrapper.set("error", error);
                return objectMapper.writeValueAsString(wrapper);
            } catch (Exception ignored) {
                ObjectNode wrapper = objectMapper.createObjectNode();
                ObjectNode error = objectMapper.createObjectNode();
                wrapper.put("type", "error");
                error.put("type", anthropicErrorType(statusCode));
                error.put("message", body == null || body.isBlank() ? "Upstream request failed" : truncateMessage(body));
                wrapper.set("error", error);
                try {
                    return objectMapper.writeValueAsString(wrapper);
                } catch (Exception ignoredAgain) {
                    return "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"Upstream request failed\"}}";
                }
            }
        }
        return body == null ? "" : body;
    }

    private String anthropicErrorType(int code) {
        return switch (code) {
            case 400 -> "invalid_request_error";
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found_error";
            case 429 -> "rate_limit_error";
            default -> "api_error";
        };
    }

    private String logMessage(int statusCode, JsonNode responseBody) {
        if (statusCode < 200 || statusCode >= 300) {
            return truncateMessage(responseBody.toString());
        }
        return responseBody.path("usage").path("estimated").asBoolean(false) ? "usage estimated from request/response text" : "";
    }

    private boolean isRetryableCapacityError(int statusCode, JsonNode responseBody, String rawBody) {
        if (statusCode < 429 && statusCode != 408) {
            return false;
        }
        String text = (rawBody == null || rawBody.isBlank()) ? responseBody.toString() : rawBody;
        text = text == null ? "" : text.toLowerCase();
        return text.contains("at capacity")
                || text.contains("try a different model")
                || text.contains("overloaded")
                || text.contains("temporarily unavailable")
                || text.contains("server is busy")
                || text.contains("rate limit");
    }

    private String truncateMessage(String value) {
        if (value == null) return "";
        return value.length() <= 1000 ? value : value.substring(0, 997) + "...";
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
