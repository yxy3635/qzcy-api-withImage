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
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.entity.RelayUsageLog;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.service.RelayDispatchService;
import com.qzcy.backend.service.RelayPolicyService;
import com.qzcy.backend.service.RelayProviderScheduler;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelayDispatchServiceImpl implements RelayDispatchService {
    private static final Duration RELAY_TIMEOUT = Duration.ofMinutes(10);
    private static final RelayCostBreakdown ZERO_COST = new RelayCostBreakdown(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
    );
    private static final Map<Long, ChannelStreamGate> STREAM_CHANNEL_GATES = new ConcurrentHashMap<>();
    private static final Map<String, ChannelCircuitState> CHANNEL_CIRCUITS = new ConcurrentHashMap<>();

    /** 每渠道流式并发闸门：持有信号量与配置的许可数，便于配置变更后自愈重建。 */
    private record ChannelStreamGate(Semaphore semaphore, int permits) {
        ChannelStreamGate(int permits) {
            this(new Semaphore(permits, true), permits);
        }
    }

    private static final class ChannelCircuitState {
        private int consecutiveFailures;
        private long blockedUntilMillis;

        synchronized boolean isOpen(long nowMillis) {
            if (blockedUntilMillis <= nowMillis) {
                blockedUntilMillis = 0;
                return false;
            }
            return true;
        }

        synchronized long recordFailure(long nowMillis) {
            consecutiveFailures++;
            if (consecutiveFailures < CHANNEL_FAILURE_THRESHOLD) {
                return 0;
            }
            if (blockedUntilMillis > nowMillis) {
                return -1;
            }
            blockedUntilMillis = nowMillis + CHANNEL_CIRCUIT_COOLDOWN.toMillis();
            return blockedUntilMillis;
        }

        synchronized void recordSuccess() {
            consecutiveFailures = 0;
            blockedUntilMillis = 0;
        }
    }

    // max_concurrency=0 表示不启用本地并发闸门；只有管理员显式配置正数时才限制。
    private static final Duration STREAM_GATE_ACQUIRE_TIMEOUT = Duration.ofSeconds(30);
    // BodyHandlers.ofInputStream 收到响应头后即返回，HttpRequest.timeout 无法约束后续阻塞 read()。
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration STREAM_IDLE_CHECK_INTERVAL = Duration.ofSeconds(15);
    private static final ScheduledExecutorService STREAM_WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "relay-stream-watchdog");
        thread.setDaemon(true);
        return thread;
    });
    private static final int CHANNEL_FAILURE_THRESHOLD = 3;
    private static final Duration CHANNEL_CIRCUIT_COOLDOWN = Duration.ofMinutes(1);
    // 429/容量错误重试退避上限（尊重但截断上游 retry-after，避免长时间占用线程）。
    private static final Duration STREAM_RETRY_BACKOFF_CAP = Duration.ofSeconds(5);
    // anthropic-beta 透传：按格式校验放行（含 prompt-caching / redact-thinking / fine-grained-tool-streaming 等），
    // 仅丢弃畸形项，避免把客户端的缓存/隐私特性 beta 标记整段滤掉导致上游不生效。
    private static final java.util.regex.Pattern ANTHROPIC_BETA_ITEM =
            java.util.regex.Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");
    private static final int ANTHROPIC_BETA_MAX_ITEMS = 16;
    private static final int ANTHROPIC_BETA_MAX_LENGTH = 512;
    private static final Set<String> FORWARDED_PROTOCOL_HEADERS = Set.of(
            "openai-beta",
            "x-client-request-id",
            "x-codex-installation-id",
            "x-codex-turn-state"
    );
    private static final Set<String> FORWARDED_RESPONSE_HEADERS = Set.of(
            "openai-beta",
            "retry-after",
            "x-client-request-id",
            "x-codex-turn-state",
            "x-request-id"
    );
    private static final Pattern UPSTREAM_URL_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?:https?|wss?|ftp):(?:\\\\/\\\\/|//)[^\\s\"'<>]+"
    );

    private final RelayPolicyService relayPolicyService;
    private final RelayProviderScheduler providerScheduler;
    private final RelayUsageLogMapper usageLogMapper;
    private final RelayTokenMapper tokenMapper;
    private final UserMapper userMapper;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public RelayDispatchResult dispatch(RelayDispatchRequest request) throws Exception {
        String model = request.body().path("model").asText("");
        String thinkingEffort = extractThinkingEffort(request.body());
        List<RelayContext> contexts = relayPolicyService.buildContexts(
                request.authorization(),
                request.apiKeyHeader(),
                request.queryKey(),
                request.clientIp(),
                request.endpointType(),
                model
        );
        BusinessException rateLimitFailure = null;
        boolean circuitSkipped = false;
        for (int index = 0; index < contexts.size(); index++) {
            RelayContext context = contexts.get(index);
            if (isChannelCircuitOpen(context)) {
                circuitSkipped = true;
                log.warn("Relay channel skipped by circuit breaker path={} channelId={} channelName={} providerId={} providerName={}",
                        request.upstreamPath(), context.channel().getId(), context.channel().getName(),
                        providerId(context), providerName(context));
                continue;
            }
            try {
                relayPolicyService.enforceRateLimits(context.token(), context.channel());
            } catch (BusinessException ex) {
                if (ex.getCode() != 429 || index == contexts.size() - 1) {
                    throw ex;
                }
                rateLimitFailure = ex;
                continue;
            }
            ensureMinimumBalance(context);
            long startedAt = System.currentTimeMillis();
            log.debug("Relay upstream attempt path={} model={} channelId={} providerId={} providerName={} rule={} group={} endpointType={} attempt={}/{}",
                    request.upstreamPath(),
                    model,
                    context.channel().getId(),
                    providerId(context),
                    providerName(context),
                    endpointRule(context),
                    context.group() == null ? "" : context.group().getCode(),
                    request.endpointType(),
                    index + 1,
                    contexts.size());
            HttpResponse<String> response;
            AtomicInteger inFlight = beginProviderRequest(context);
            try {
                response = hasFiles(request.files())
                        ? relayMultipart(request.body(), request.files(), context, request.upstreamPath(), request.anthropicVersion(), request.anthropicBeta(), request.protocolHeaders())
                        : relayString(request.body(), context, request.upstreamPath(), request.anthropicVersion(), request.anthropicBeta(), request.protocolHeaders());
            } catch (Exception ex) {
                log.warn("Relay upstream request failed path={} channelId={} providerId={} providerName={} rule={} message={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        providerId(context),
                        providerName(context),
                        endpointRule(context),
                        ex.getMessage(),
                        ex);
                if (isRetryableTransportFailure(ex)) {
                    recordChannelFailure(context, "transport failure: " + safeExceptionMessage(ex));
                    JsonNode failureBody = upstreamFailureBody("Upstream connection failed: " + safeExceptionMessage(ex));
                    saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, 502, failureBody, ZERO_COST,
                            System.currentTimeMillis() - startedAt);
                    if (index < contexts.size() - 1) {
                        continue;
                    }
                }
                throw ex;
            } finally {
                endProviderRequest(inFlight);
            }
            String responseText = response.statusCode() >= 200 && response.statusCode() < 300
                    ? response.body()
                    : sanitizeUpstreamErrorBody(response.body());
            JsonNode responseBody = parseResponseBody(responseText);
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && !isCountTokensRequest(request.upstreamPath())
                    && !hasBillableUsage(responseBody)) {
                responseBody = withEstimatedUsage(request.body(), responseText);
            }
            RelayCostBreakdown cost = isCountTokensRequest(request.upstreamPath())
                    ? ZERO_COST
                    : relayPolicyService.estimateCost(context.model(), context.channel(), context.group(), responseBody);
            // 按请求次数计费只对真正成功的上游请求收费；按量计费的错误响应保留上游 usage，
            // 以兼容部分上游已产生可计费 Token 后才返回错误的现有口径。HTTP 200 不受影响。
            cost = billingCostForResponse(context, response.statusCode(), cost);
            boolean retryable = isRetryableUpstreamError(response.statusCode(), responseBody, responseText);
            if (retryable) {
                recordChannelFailure(context, "HTTP " + response.statusCode() + ": " + truncateMessage(responseText));
            } else {
                recordChannelSuccess(context);
            }
            log.debug("Relay upstream response path={} channelId={} status={} retryable={} billable={} durationMs={} usage={}",
                    request.upstreamPath(),
                    context.channel().getId(),
                    response.statusCode(),
                    retryable,
                    cost.billable(),
                    System.currentTimeMillis() - startedAt,
                    responseBody.path("usage").isMissingNode() ? "" : truncateMessage(responseBody.path("usage").toString()));
            if (!retryable || index == contexts.size() - 1) {
                try {
                    enforceQuotaIfSuccessful(response.statusCode(), context, cost);
                } catch (BusinessException ex) {
                    saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, response.statusCode(), responseBody, cost, System.currentTimeMillis() - startedAt);
                    disableTokenAfterBillingFailure(context.token(), ex);
                    throw ex;
                }
                saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, response.statusCode(), responseBody, cost, System.currentTimeMillis() - startedAt);
                chargeIfSuccessful(response.statusCode(), context, cost);
                return new RelayDispatchResult(response.statusCode(), contentType(response), responseText, forwardedResponseHeaders(response));
            }
            saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, response.statusCode(), responseBody, cost, System.currentTimeMillis() - startedAt);
        }
        if (rateLimitFailure != null) throw rateLimitFailure;
        if (circuitSkipped) throw new BusinessException(503, "All matching relay channels are temporarily unavailable");
        throw new BusinessException(400, "No available relay channel for current group and model");
    }

    @Override
    public RelayStreamDispatchResult dispatchStream(RelayDispatchRequest request) throws Exception {
        String model = request.body().path("model").asText("");
        String thinkingEffort = extractThinkingEffort(request.body());
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
        boolean circuitSkipped = false;
        for (int index = 0; index < contexts.size(); index++) {
            RelayContext context = contexts.get(index);
            if (isChannelCircuitOpen(context)) {
                circuitSkipped = true;
                log.warn("Relay stream channel skipped by circuit breaker path={} channelId={} channelName={} providerId={} providerName={}",
                        request.upstreamPath(), context.channel().getId(), context.channel().getName(),
                        providerId(context), providerName(context));
                continue;
            }
            try {
                relayPolicyService.enforceRateLimits(context.token(), context.channel());
            } catch (BusinessException ex) {
                if (ex.getCode() != 429 || index == contexts.size() - 1) {
                    throw ex;
                }
                rateLimitFailure = ex;
                continue;
            }
            ensureMinimumBalance(context);
            long startedAt = System.currentTimeMillis();
            ChannelStreamGate gate = streamGate(context.channel());
            Semaphore streamGate = gate == null ? null : gate.semaphore();
            if (gate != null) {
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
                    log.warn("Relay upstream stream skipped by configured concurrency gate path={} channelId={} channelName={} permits={} available={} queued={}",
                            request.upstreamPath(),
                            context.channel().getId(),
                            context.channel().getName(),
                            gate.permits(),
                            streamGate.availablePermits(),
                            streamGate.getQueueLength());
                    if (index < contexts.size() - 1) {
                        continue;
                    }
                    String errorText = localOverloadError(request.upstreamPath());
                    saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, 503, parseResponseBody(errorText), ZERO_COST,
                            STREAM_GATE_ACQUIRE_TIMEOUT.toMillis());
                    return new RelayStreamDispatchResult(
                            503,
                            MediaType.APPLICATION_JSON_VALUE,
                            outputStream -> outputStream.write(errorText.getBytes(StandardCharsets.UTF_8))
                    );
                }
            } else {
                log.debug("Relay stream concurrency unlimited path={} channelId={} channelName={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        context.channel().getName());
            }
            log.debug("Relay upstream stream attempt path={} model={} channelId={} providerId={} providerName={} rule={} group={} endpointType={} attempt={}/{}",
                    request.upstreamPath(),
                    model,
                    context.channel().getId(),
                    providerId(context),
                    providerName(context),
                    endpointRule(context),
                    context.group() == null ? "" : context.group().getCode(),
                    request.endpointType(),
                    index + 1,
                    contexts.size());
            HttpResponse<InputStream> response;
            AtomicInteger inFlight = beginProviderRequest(context);
            try {
                response = relayStream(request.body(), context, request.upstreamPath(), request.anthropicVersion(), request.anthropicBeta(), request.protocolHeaders());
            } catch (Exception ex) {
                endProviderRequest(inFlight);
                releaseStreamGate(streamGate);
                log.warn("Relay upstream stream request failed path={} channelId={} providerId={} providerName={} rule={} message={}",
                        request.upstreamPath(),
                        context.channel().getId(),
                        providerId(context),
                        providerName(context),
                        endpointRule(context),
                        ex.getMessage(),
                        ex);
                if (isRetryableTransportFailure(ex)) {
                    recordChannelFailure(context, "transport failure: " + safeExceptionMessage(ex));
                    lastStatus = 502;
                    lastErrorText = localUpstreamFailureError(request.upstreamPath(), safeExceptionMessage(ex));
                    saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, lastStatus,
                            parseResponseBody(lastErrorText), ZERO_COST, System.currentTimeMillis() - startedAt);
                    if (index < contexts.size() - 1) {
                        continue;
                    }
                    break;
                }
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
                    errorText = sanitizeUpstreamErrorBody(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                } finally {
                    endProviderRequest(inFlight);
                    releaseStreamGate(streamGate);
                }
                JsonNode responseBody = parseResponseBody(errorText);
                RelayCostBreakdown cost = ZERO_COST;
                saveUsage(context, request.upstreamPath(), request.userAgent(), thinkingEffort, response.statusCode(),
                        responseBody, cost, System.currentTimeMillis() - startedAt);
                boolean retryable = isRetryableUpstreamError(response.statusCode(), responseBody, errorText);
                if (retryable) {
                    recordChannelFailure(context, "HTTP " + response.statusCode() + ": " + truncateMessage(errorText));
                } else {
                    recordChannelSuccess(context);
                }
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
                        contentType(response),
                        outputStream -> outputStream.write(errorText.getBytes(StandardCharsets.UTF_8)),
                        forwardedResponseHeaders(response)
                );
            }
            recordChannelSuccess(context);
            RelayContext finalContext = context;
            long finalStartedAt = startedAt;
            HttpResponse<InputStream> finalResponse = response;
            Semaphore finalStreamGate = streamGate;
            AtomicInteger finalInFlight = inFlight;
            StreamingResponseBody stream = outputStream -> {
                // 边收边转发：用阻塞 read() 等待上游每一段数据，收到立即 flush 给客户端。
                // 不用 InputStream.available() 轮询判断首字节——对分块/SSE 响应体它不可靠，
                // 会因上游整体缓冲返回而误判“无数据”，向客户端投放假错误并触发重试。
                StreamUsageAccumulator acc = new StreamUsageAccumulator();
                // 首字（TTFT）：从本次派发开始计时，到收到上游响应体第一段数据为止；0 表示未收到数据。
                long firstTokenMs = 0L;
                long totalBytes = 0;
                byte[] buffer = new byte[8192];
                InputStream inputStream = finalResponse.body();
                AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());
                AtomicBoolean idleTimedOut = new AtomicBoolean(false);
                ScheduledFuture<?> idleWatchdog = scheduleStreamIdleWatchdog(
                        inputStream,
                        lastActivityNanos,
                        idleTimedOut,
                        request.upstreamPath(),
                        finalContext.channel().getId(),
                        finalContext.channel().getName()
                );
                try (inputStream) {
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        if (read <= 0) {
                            continue;
                        }
                        if (firstTokenMs == 0L) {
                            firstTokenMs = Math.max(1, System.currentTimeMillis() - finalStartedAt);
                        }
                        lastActivityNanos.set(System.nanoTime());
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
                    idleWatchdog.cancel(false);
                    endProviderRequest(finalInFlight);
                    releaseStreamGate(finalStreamGate);
                    int completionStatus = idleTimedOut.get() ? 504 : finalResponse.statusCode();
                    if (idleTimedOut.get()) {
                        recordChannelFailure(finalContext, "stream idle timeout");
                    }
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
                    JsonNode responseBody = idleTimedOut.get()
                            ? upstreamFailureBody("Upstream stream produced no data for " + STREAM_IDLE_TIMEOUT.toMinutes() + " minutes")
                            : acc.buildResponseBody();
                    if (completionStatus >= 200 && completionStatus < 300
                            && !isCountTokensRequest(request.upstreamPath())
                            && !hasBillableUsage(responseBody)) {
                        responseBody = withEstimatedUsage(request.body(), acc.previewString());
                    }
                    RelayCostBreakdown cost = idleTimedOut.get() || isCountTokensRequest(request.upstreamPath())
                            ? ZERO_COST
                            : relayPolicyService.estimateCost(finalContext.model(), finalContext.channel(), finalContext.group(), responseBody);
                    try {
                        enforceQuotaIfSuccessful(completionStatus, finalContext, cost);
                    } catch (BusinessException ex) {
                        saveUsage(finalContext, request.upstreamPath(), request.userAgent(), thinkingEffort, completionStatus,
                                responseBody, cost, System.currentTimeMillis() - finalStartedAt, firstTokenMs);
                        disableTokenAfterBillingFailure(finalContext.token(), ex);
                        throw ex;
                    }
                    saveUsage(finalContext, request.upstreamPath(), request.userAgent(), thinkingEffort, completionStatus,
                            responseBody, cost, System.currentTimeMillis() - finalStartedAt, firstTokenMs);
                    chargeIfSuccessful(completionStatus, finalContext, cost);
                    log.debug("Relay upstream stream response path={} channelId={} status={} billable={} durationMs={} usage={}",
                            request.upstreamPath(),
                            finalContext.channel().getId(),
                            completionStatus,
                            cost.billable(),
                            System.currentTimeMillis() - finalStartedAt,
                            responseBody.path("usage").isMissingNode() ? "" : truncateMessage(responseBody.path("usage").toString()));
                }
            };
            return new RelayStreamDispatchResult(response.statusCode(), contentType(response), stream, forwardedResponseHeaders(response));
        }
        if (rateLimitFailure != null) throw rateLimitFailure;
        if (lastErrorText.isBlank() && circuitSkipped) {
            lastStatus = 503;
            lastErrorText = localUpstreamFailureError(request.upstreamPath(), "All matching relay channels are temporarily unavailable");
        }
        final int finalLastStatus = lastStatus;
        final String finalLastErrorText = lastErrorText;
        return new RelayStreamDispatchResult(
                finalLastStatus,
                MediaType.APPLICATION_JSON_VALUE,
                outputStream -> outputStream.write(sanitizeUpstreamErrorBody(finalLastErrorText).getBytes(StandardCharsets.UTF_8))
        );
    }

    private RelayCostBreakdown billingCostForResponse(RelayContext context, int statusCode, RelayCostBreakdown cost) {
        if (statusCode >= 200 && statusCode < 300) {
            return cost;
        }
        RelayModel model = context == null ? null : context.model();
        if (model != null && Boolean.TRUE.equals(model.getFixedRequestBilling())) {
            return ZERO_COST;
        }
        return cost;
    }

    private void enforceQuotaIfSuccessful(int statusCode, RelayContext context, RelayCostBreakdown cost) {
        if (statusCode < 200 || statusCode >= 300 || !cost.billable()) {
            return;
        }
        relayPolicyService.enforceQuota(context.token(), cost.total());
    }

    private void ensureMinimumBalance(RelayContext context) {
        BigDecimal minimum = minimumPreflightCost(context.model());
        if (minimum.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        User user = userMapper.selectById(context.token().getUserId());
        BigDecimal balance = user == null ? BigDecimal.ZERO : user.getBalance();
        if (balance == null || balance.compareTo(minimum) < 0) {
            throw new BusinessException(402, "Insufficient balance for selected model");
        }
    }

    private BigDecimal minimumPreflightCost(RelayModel model) {
        if (model == null) {
            return BigDecimal.ZERO;
        }
        return model.getRequestPrice() == null ? BigDecimal.ZERO : model.getRequestPrice();
    }

    private void chargeIfSuccessful(int statusCode, RelayContext context, RelayCostBreakdown cost) {
        if (statusCode < 200 || statusCode >= 300 || !cost.billable()) {
            return;
        }
        try {
            paymentService.deductBalanceOnly(context.token().getUserId(), cost.total());
        } catch (BusinessException ex) {
            disableTokenAfterBillingFailure(context.token(), ex);
            throw ex;
        }
    }

    private void disableTokenAfterBillingFailure(RelayToken token, BusinessException ex) {
        if (token == null || token.getId() == null) {
            return;
        }
        // Update only the flag. The token may be an old request snapshot and must
        // not overwrite usage counters accumulated by other requests.
        RelayToken update = new RelayToken();
        update.setId(token.getId());
        update.setEnabled(false);
        tokenMapper.updateById(update);
        log.warn("Relay API key disabled after billing failure tokenId={} userId={} code={} message={}",
                token.getId(),
                token.getUserId(),
                ex.getCode(),
                ex.getMessage());
    }

    private HttpResponse<String> relayString(ObjectNode body, RelayContext context, String path, String anthropicVersion, String anthropicBeta, Map<String, String> protocolHeaders) throws Exception {
        return httpClient.send(upstreamRequest(body, context, path, anthropicVersion, anthropicBeta, protocolHeaders), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> relayMultipart(ObjectNode body, List<RelayMultipartFile> files, RelayContext context, String path, String anthropicVersion, String anthropicBeta, Map<String, String> protocolHeaders) throws Exception {
        return httpClient.send(upstreamMultipartRequest(body, files, context, path, anthropicVersion, anthropicBeta, protocolHeaders), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<InputStream> relayStream(ObjectNode body, RelayContext context, String path, String anthropicVersion, String anthropicBeta, Map<String, String> protocolHeaders) throws Exception {
        return httpClient.send(upstreamRequest(body, context, path, anthropicVersion, anthropicBeta, protocolHeaders), HttpResponse.BodyHandlers.ofInputStream());
    }

    private HttpRequest upstreamRequest(ObjectNode body, RelayContext context, String path, String anthropicVersion, String anthropicBeta, Map<String, String> protocolHeaders) throws Exception {
        RelayChannel channel = context.channel();
        ObjectNode outboundBody = prepareOutboundBody(body, context, path);
        String url = relayUrl(endpointBaseUrl(context), path);
        log.debug("Relay upstream request url={} channelId={} channelName={} providerId={} providerName={} rule={} model={} outboundModel={} stream={} authMode={} anthropicVersion={} anthropicBeta={}",
                url,
                channel.getId(),
                channel.getName(),
                providerId(context),
                providerName(context),
                endpointRule(context),
                body == null ? "" : body.path("model").asText(""),
                outboundBody.path("model").asText(""),
                body != null && body.path("stream").asBoolean(false),
                isAnthropicEndpoint(context) ? "x-api-key" : "bearer",
                headerOrDefault(anthropicVersion, "2023-06-01"),
                anthropicBeta == null || anthropicBeta.isBlank() ? "" : "present");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(relayUrl(endpointBaseUrl(context), path)))
                .timeout(RELAY_TIMEOUT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, acceptHeader(body))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(outboundBody)));
        applyAuthHeaders(builder, context, anthropicVersion, anthropicBeta);
        applyProtocolHeaders(builder, protocolHeaders);
        return builder.build();
    }

    private HttpRequest upstreamMultipartRequest(ObjectNode body, List<RelayMultipartFile> files, RelayContext context, String path, String anthropicVersion, String anthropicBeta, Map<String, String> protocolHeaders) throws Exception {
        RelayChannel channel = context.channel();
        ObjectNode outboundBody = prepareOutboundBody(body, context, path);
        String url = relayUrl(endpointBaseUrl(context), path);
        log.debug("Relay upstream multipart request url={} channelId={} channelName={} providerId={} providerName={} rule={} model={} outboundModel={} fileCount={} authMode={}",
                url,
                channel.getId(),
                channel.getName(),
                providerId(context),
                providerName(context),
                endpointRule(context),
                body == null ? "" : body.path("model").asText(""),
                outboundBody.path("model").asText(""),
                files == null ? 0 : files.size(),
                isAnthropicEndpoint(context) ? "x-api-key" : "bearer");
        String boundary = "----imageCreaterBoundary" + UUID.randomUUID();
        byte[] multipartBody = multipartBody(outboundBody, files, boundary);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(relayUrl(endpointBaseUrl(context), path)))
                .timeout(RELAY_TIMEOUT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody));
        applyAuthHeaders(builder, context, anthropicVersion, anthropicBeta);
        applyProtocolHeaders(builder, protocolHeaders);
        return builder.build();
    }

    private ObjectNode prepareOutboundBody(ObjectNode body, RelayContext context, String path) {
        ObjectNode outbound = body.deepCopy();
        // The public model id is the source of truth unless the channel has an
        // explicit mapping. Do not silently replace it with the internal model
        // record: public aliases are allowed to differ from that record.
        String upstreamModel = upstreamModel(body, context);
        if (!upstreamModel.isBlank()) {
            outbound.put("model", upstreamModel);
        }
        if (outbound.path("stream").asBoolean(false) && path.endsWith("/chat/completions")) {
            ObjectNode streamOptions = outbound.withObject("/stream_options");
            streamOptions.put("include_usage", true);
        }
        return outbound;
    }

    private String extractThinkingEffort(ObjectNode body) {
        if (body == null) {
            return "";
        }
        String effort = firstThinkingText(body.path("reasoning"), "effort", "level");
        if (effort.isBlank()) {
            effort = body.path("reasoning_effort").asText("");
        }
        if (effort.isBlank()) {
            effort = firstThinkingText(body.path("thinking"), "effort", "level");
        }
        if (effort.isBlank()) {
            effort = body.path("reasoning").isTextual() ? body.path("reasoning").asText("") : "";
        }
        if (effort.isBlank()) {
            effort = body.path("thinking").isTextual() ? body.path("thinking").asText("") : "";
        }
        if (effort.isBlank()) {
            JsonNode thinking = body.path("thinking");
            JsonNode budget = thinking.path("budget_tokens");
            if (budget.isNumber() && budget.asInt() > 0) {
                effort = "budget:" + budget.asInt();
            }
        }
        return effort == null ? "" : effort.trim();
    }

    private String firstThinkingText(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private String upstreamModel(ObjectNode body, RelayContext context) {
        RelayChannelModel binding = context.channelModel();
        if (binding != null && binding.getUpstreamModel() != null && !binding.getUpstreamModel().isBlank()) {
            return binding.getUpstreamModel().trim();
        }
        return body == null ? "" : body.path("model").asText("").trim();
    }

    private String acceptHeader(ObjectNode body) {
        return body.path("stream").asBoolean(false) ? MediaType.TEXT_EVENT_STREAM_VALUE : MediaType.APPLICATION_JSON_VALUE;
    }

    /**
     * 候选端点解析：渠道内供应商优先，未配置供应商的老渠道回退到渠道自身字段。
     */
    private String endpointBaseUrl(RelayContext context) {
        RelayChannelProvider provider = context == null ? null : context.provider();
        if (provider != null && provider.getApiBaseUrl() != null && !provider.getApiBaseUrl().isBlank()) {
            return provider.getApiBaseUrl();
        }
        return context.channel().getApiBaseUrl();
    }

    private String endpointApiKey(RelayContext context) {
        RelayChannelProvider provider = context == null ? null : context.provider();
        if (provider != null && provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
            return provider.getApiKey();
        }
        return context.channel().getApiKey();
    }

    private String endpointRule(RelayContext context) {
        RelayChannelProvider provider = context == null ? null : context.provider();
        if (provider != null && provider.getChannelRule() != null && !provider.getChannelRule().isBlank()) {
            return provider.getChannelRule();
        }
        return context.channel().getChannelRule();
    }

    private boolean isAnthropicEndpoint(RelayContext context) {
        String rule = endpointRule(context) == null ? "" : endpointRule(context).toLowerCase();
        if ("anthropic".equals(rule)) return true;
        if ("openai".equals(rule)) return false;
        RelayChannelProvider provider = context == null ? null : context.provider();
        String providerName = provider == null || provider.getName() == null ? "" : provider.getName().toLowerCase();
        String channelName = context.channel() == null || context.channel().getProvider() == null
                ? "" : context.channel().getProvider().toLowerCase();
        String baseUrl = endpointBaseUrl(context) == null ? "" : endpointBaseUrl(context).toLowerCase();
        return providerName.contains("anthropic")
                || providerName.contains("claude")
                || channelName.contains("anthropic")
                || channelName.contains("claude")
                || baseUrl.contains("api.anthropic.com");
    }

    private void applyAuthHeaders(HttpRequest.Builder builder, RelayContext context, String anthropicVersion, String anthropicBeta) {
        if (isAnthropicEndpoint(context)) {
            builder.header("x-api-key", endpointApiKey(context))
                    .header("anthropic-version", headerOrDefault(anthropicVersion, "2023-06-01"));
            String beta = safeAnthropicBeta(anthropicBeta);
            if (!beta.isBlank()) {
                builder.header("anthropic-beta", beta);
            }
            return;
        }
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + endpointApiKey(context));
    }

    private void applyProtocolHeaders(HttpRequest.Builder builder, Map<String, String> protocolHeaders) {
        if (protocolHeaders == null || protocolHeaders.isEmpty()) {
            return;
        }
        protocolHeaders.forEach((name, value) -> {
            if (name != null && value != null && FORWARDED_PROTOCOL_HEADERS.contains(name.toLowerCase())) {
                builder.header(name, value);
            }
        });
    }

    private Map<String, List<String>> forwardedResponseHeaders(HttpResponse<?> response) {
        if (response == null) {
            return Map.of();
        }
        return response.headers().map().entrySet().stream()
                .filter(entry -> FORWARDED_RESPONSE_HEADERS.contains(entry.getKey().toLowerCase()))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
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

    private boolean hasFiles(List<RelayMultipartFile> files) {
        return files != null && !files.isEmpty();
    }

    private boolean isCountTokensRequest(String path) {
        return path != null && path.endsWith("/messages/count_tokens");
    }

    private ChannelStreamGate streamGate(RelayChannel channel) {
        Long channelId = channel == null ? -1L : channel.getId();
        int permits = configuredStreamConcurrency(channel);
        if (permits == 0) {
            STREAM_CHANNEL_GATES.remove(channelId);
            return null;
        }
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

    static int configuredStreamConcurrency(RelayChannel channel) {
        Integer configured = channel == null ? null : channel.getMaxConcurrency();
        return configured == null || configured <= 0 ? 0 : configured;
    }

    private void releaseStreamGate(Semaphore streamGate) {
        if (streamGate != null) {
            streamGate.release();
        }
    }

    private ScheduledFuture<?> scheduleStreamIdleWatchdog(
            InputStream inputStream,
            AtomicLong lastActivityNanos,
            AtomicBoolean idleTimedOut,
            String path,
            Long channelId,
            String channelName
    ) {
        return STREAM_WATCHDOG.scheduleAtFixedRate(() -> {
            long idleNanos = System.nanoTime() - lastActivityNanos.get();
            if (idleNanos < STREAM_IDLE_TIMEOUT.toNanos() || !idleTimedOut.compareAndSet(false, true)) {
                return;
            }
            log.warn("Relay upstream stream closed by idle watchdog path={} channelId={} channelName={} idleSeconds={}",
                    path, channelId, channelName, TimeUnit.NANOSECONDS.toSeconds(idleNanos));
            try {
                inputStream.close();
            } catch (Exception ex) {
                log.debug("Relay upstream stream close after idle timeout failed path={} channelId={} message={}",
                        path, channelId, ex.getMessage());
            }
        }, STREAM_IDLE_CHECK_INTERVAL.toMillis(), STREAM_IDLE_CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 熔断作用域：渠道内按供应商隔离（channelId:providerId），无供应商的兜底渠道退化为 channelId。 */
    private String circuitScopeKey(RelayContext context) {
        RelayChannel channel = context == null ? null : context.channel();
        RelayChannelProvider provider = context == null ? null : context.provider();
        if (channel == null || channel.getId() == null) {
            return "unknown";
        }
        if (provider != null && provider.getId() != null) {
            return channel.getId() + ":" + provider.getId();
        }
        return String.valueOf(channel.getId());
    }

    private Long providerId(RelayContext context) {
        RelayChannelProvider provider = context == null ? null : context.provider();
        return provider == null ? null : provider.getId();
    }

    private String providerName(RelayContext context) {
        RelayChannelProvider provider = context == null ? null : context.provider();
        return provider == null ? "" : provider.getName();
    }

    private boolean isChannelCircuitOpen(RelayContext context) {
        if (context == null || context.channel() == null || context.channel().getId() == null) {
            return false;
        }
        ChannelCircuitState state = CHANNEL_CIRCUITS.get(circuitScopeKey(context));
        return state != null && state.isOpen(System.currentTimeMillis());
    }

    private void recordChannelFailure(RelayContext context, String reason) {
        if (context == null || context.channel() == null || context.channel().getId() == null) {
            return;
        }
        long blockedUntil = CHANNEL_CIRCUITS
                .computeIfAbsent(circuitScopeKey(context), ignored -> new ChannelCircuitState())
                .recordFailure(System.currentTimeMillis());
        if (blockedUntil > 0) {
            log.warn("Relay channel circuit opened scope={} channelId={} channelName={} providerId={} providerName={} blockedForSeconds={} reason={}",
                    circuitScopeKey(context), context.channel().getId(), context.channel().getName(),
                    providerId(context), providerName(context), CHANNEL_CIRCUIT_COOLDOWN.toSeconds(), truncateMessage(reason));
        }
    }

    private void recordChannelSuccess(RelayContext context) {
        if (context == null || context.channel() == null || context.channel().getId() == null) {
            return;
        }
        ChannelCircuitState state = CHANNEL_CIRCUITS.remove(circuitScopeKey(context));
        if (state != null) {
            state.recordSuccess();
        }
    }

    /** least_conn 在途计数：上游尝试（含整个流式生命周期）期间保持 +1。 */
    private AtomicInteger beginProviderRequest(RelayContext context) {
        RelayChannelProvider provider = context == null ? null : context.provider();
        if (provider == null || provider.getId() == null) {
            return null;
        }
        return providerScheduler.beginRequest(provider.getId());
    }

    private void endProviderRequest(AtomicInteger counter) {
        providerScheduler.endRequest(counter);
    }

    private boolean isRetryableTransportFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return false;
            }
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketException
                    || current instanceof java.io.IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String safeExceptionMessage(Throwable error) {
        if (error == null) {
            return "unknown upstream error";
        }
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : sanitizeUpstreamErrorBody(truncateMessage(message));
    }

    private JsonNode upstreamFailureBody(String message) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode error = objectMapper.createObjectNode();
        error.put("type", "upstream_error");
        error.put("message", sanitizeUpstreamErrorBody(message));
        body.set("error", error);
        return body;
    }

    private String localOverloadError(String path) {
        return localErrorBody(path, "overloaded_error", "server_error",
                "All relay channels are busy, please retry shortly", 503);
    }

    private String localUpstreamFailureError(String path, String message) {
        return localErrorBody(path, "api_error", "upstream_error", "Upstream request failed: " + message, 502);
    }

    private String localErrorBody(String path, String anthropicType, String openAiType, String message, int code) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode error = objectMapper.createObjectNode();
        if (path != null && path.startsWith("/v1/messages")) {
            body.put("type", "error");
            error.put("type", anthropicType);
            error.put("message", sanitizeUpstreamErrorBody(message));
        } else {
            error.put("type", openAiType);
            error.put("message", sanitizeUpstreamErrorBody(message));
            error.put("code", code);
        }
        body.set("error", error);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ignored) {
            return "{\"error\":{\"type\":\"relay_error\",\"message\":\"Upstream request failed\"}}";
        }
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

    private void saveUsage(RelayContext context, String endpoint, String userAgent, String thinkingEffort,
                           int statusCode, JsonNode responseBody, RelayCostBreakdown cost, long durationMs) {
        saveUsage(context, endpoint, userAgent, thinkingEffort, statusCode, responseBody, cost, durationMs, null);
    }

    private void saveUsage(RelayContext context, String endpoint, String userAgent, String thinkingEffort,
                           int statusCode, JsonNode responseBody, RelayCostBreakdown cost, long durationMs, Long firstTokenMs) {
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
        log.setTokenName(limitForColumn(access.getName(), 80));
        log.setChannelName(limitForColumn(channel.getName(), 80));
        log.setGroupNames(limitForColumn(access.getGroupNames(), 160));
        log.setEndpoint(limitForColumn(endpoint, 80));
        log.setModel(limitForColumn(context.model() == null ? "" : context.model().getModel(), 120));
        log.setModelType(limitForColumn(context.effectiveModelType(), 40));
        log.setThinkingEffort(limitForColumn(thinkingEffort, 40));
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
        log.setFirstTokenMs(firstTokenMs == null || firstTokenMs <= 0 ? null : firstTokenMs);
        log.setUserAgent(limitForColumn(userAgent, 500));
        log.setStatus(statusCode >= 200 && statusCode < 300 ? "success" : "failed");
        log.setMessage(limitForColumn(logMessage(statusCode, responseBody), 1000));
        log.setCreatedAt(LocalDateTime.now());
        insertUsageLog(log);
        LocalDateTime now = LocalDateTime.now();
        tokenMapper.incrementUsage(access.getId(), 1L, totalTokens, cost.total(), now, now);
    }

    private void insertUsageLog(RelayUsageLog usageLog) {
        try {
            usageLogMapper.insert(usageLog);
        } catch (Exception ex) {
            log.warn("Relay usage log insert failed, writing fallback log userId={} tokenId={} channelId={} endpoint={} model={} statusCode={} message={}",
                    usageLog.getUserId(),
                    usageLog.getTokenId(),
                    usageLog.getChannelId(),
                    usageLog.getEndpoint(),
                    usageLog.getModel(),
                    usageLog.getStatusCode(),
                    ex.getMessage(),
                    ex);
            RelayUsageLog fallback = new RelayUsageLog();
            fallback.setUserId(usageLog.getUserId());
            fallback.setTokenId(usageLog.getTokenId());
            fallback.setChannelId(usageLog.getChannelId());
            fallback.setTokenName(limitForColumn(usageLog.getTokenName(), 80));
            fallback.setChannelName(limitForColumn(usageLog.getChannelName(), 80));
            fallback.setGroupNames(limitForColumn(usageLog.getGroupNames(), 160));
            fallback.setEndpoint(limitForColumn(usageLog.getEndpoint(), 80));
            fallback.setModel(limitForColumn(usageLog.getModel(), 120));
            fallback.setModelType(limitForColumn(usageLog.getModelType(), 40));
            // Keep the original usage and billing values.  The API key usage
            // counter is incremented independently, so replacing these values
            // with zero would make log-based totals (especially admin usage)
            // permanently lower whenever the first insert fails.
            fallback.setThinkingEffort(limitForColumn(usageLog.getThinkingEffort(), 40));
            fallback.setPromptTokens(usageLog.getPromptTokens() == null ? 0 : usageLog.getPromptTokens());
            fallback.setCompletionTokens(usageLog.getCompletionTokens() == null ? 0 : usageLog.getCompletionTokens());
            fallback.setCachedTokens(usageLog.getCachedTokens() == null ? 0 : usageLog.getCachedTokens());
            fallback.setCacheCreationTokens(usageLog.getCacheCreationTokens() == null ? 0 : usageLog.getCacheCreationTokens());
            fallback.setTotalTokens(usageLog.getTotalTokens() == null ? 0 : usageLog.getTotalTokens());
            fallback.setInputCost(usageLog.getInputCost() == null ? BigDecimal.ZERO : usageLog.getInputCost());
            fallback.setOutputCost(usageLog.getOutputCost() == null ? BigDecimal.ZERO : usageLog.getOutputCost());
            fallback.setCacheReadCost(usageLog.getCacheReadCost() == null ? BigDecimal.ZERO : usageLog.getCacheReadCost());
            fallback.setCacheCreationCost(usageLog.getCacheCreationCost() == null ? BigDecimal.ZERO : usageLog.getCacheCreationCost());
            fallback.setRequestCost(usageLog.getRequestCost() == null ? BigDecimal.ZERO : usageLog.getRequestCost());
            fallback.setGroupRatio(usageLog.getGroupRatio() == null ? BigDecimal.ONE : usageLog.getGroupRatio());
            fallback.setChannelRatio(usageLog.getChannelRatio() == null ? BigDecimal.ONE : usageLog.getChannelRatio());
            fallback.setCost(usageLog.getCost() == null ? BigDecimal.ZERO : usageLog.getCost());
            fallback.setStatusCode(usageLog.getStatusCode() == null ? 0 : usageLog.getStatusCode());
            fallback.setDurationMs(usageLog.getDurationMs() == null ? 0L : usageLog.getDurationMs());
            fallback.setUserAgent(limitForColumn(usageLog.getUserAgent(), 500));
            fallback.setStatus(usageLog.getStatus() == null ? "failed" : usageLog.getStatus());
            fallback.setMessage(limitForColumn("usage log insert failed: " + ex.getMessage(), 1000));
            // Keep the request timestamp so a delayed fallback cannot move a
            // previous day's charge into today's totals.
            fallback.setCreatedAt(usageLog.getCreatedAt() == null ? LocalDateTime.now() : usageLog.getCreatedAt());
            try {
                usageLogMapper.insert(fallback);
            } catch (Exception fallbackEx) {
                // Usage telemetry must not prevent the independent API-key
                // accounting update below from being applied.
                log.error("Relay usage fallback log insert failed userId={} tokenId={} cost={} message={}",
                        usageLog.getUserId(), usageLog.getTokenId(), usageLog.getCost(), fallbackEx.getMessage(), fallbackEx);
            }
        }
    }

    private String limitForColumn(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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

    private String sanitizeUpstreamErrorBody(String body) {
        if (body == null || body.isBlank()) {
            return body == null ? "" : body;
        }
        return UPSTREAM_URL_PATTERN.matcher(body).replaceAll("<redacted-url>");
    }

    private String logMessage(int statusCode, JsonNode responseBody) {
        if (statusCode < 200 || statusCode >= 300) {
            return truncateMessage(responseBody.toString());
        }
        return responseBody.path("usage").path("estimated").asBoolean(false) ? "usage estimated from request/response text" : "";
    }

    static boolean isRetryableUpstreamError(int statusCode, JsonNode responseBody, String rawBody) {
        String text = (rawBody == null || rawBody.isBlank()) ? responseBody.toString() : rawBody;
        text = text == null ? "" : text.toLowerCase();
        if (statusCode < 400) {
            return false;
        }
        // 同一次请求内继续尝试备用渠道。400 也必须纳入渠道级故障转移：
        // 不同上游对模型、协议、网关参数的校验可能返回 400，不能因为第一个渠道
        // 返回了 400 就直接把错误暴露给用户。所有候选渠道都失败后，调用方仍会
        // 收到最终的上游错误响应。
        if (statusCode == 400 || statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500) {
            return true;
        }
        if (statusCode == 401 || statusCode == 403 || statusCode == 404 || statusCode == 422) {
            return false;
        }
        return text.contains("at capacity")
                || text.contains("try a different model")
                || text.contains("overloaded")
                || text.contains("temporarily unavailable")
                || text.contains("server is busy")
                || text.contains("rate limit");
    }

    private static boolean containsTransportFailureMarker(String text) {
        return text.contains("i/o timeout")
                || text.contains("read tcp")
                || text.contains("write tcp")
                || text.contains("dial tcp")
                || text.contains("connection timed out")
                || text.contains("connection reset")
                || text.contains("connection refused")
                || text.contains("context deadline exceeded")
                || text.contains("unexpected eof")
                || text.contains("no route to host")
                || text.contains("broken pipe")
                || text.contains("upstream timeout")
                || text.contains("upstream request timeout");
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
