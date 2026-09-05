package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.RelayChannelTestAttemptDto;
import com.qzcy.backend.dto.RelayChannelTestRequestDto;
import com.qzcy.backend.dto.RelayChannelTestResultDto;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.service.RelayChannelTestService;
import com.qzcy.backend.service.RelayProviderScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端渠道真实调用测试：选定渠道上绑定的模型，按该渠道的调度策略对供应商排序，
 * 逐个发起一次非流式对话请求（openai/anthropic 双协议），失败自动切换下一个，
 * 返回首个成功结果（含实际生效的供应商与模型名）或全部尝试的失败明细。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelayChannelTestServiceImpl implements RelayChannelTestService {
    static final String DEFAULT_PROMPT = "你好，请用一句话介绍你自己。";
    private static final int MAX_PROMPT_LENGTH = 4000;
    private static final int ANTHROPIC_MAX_TOKENS = 512;
    private static final int MAX_ATTEMPT_MESSAGE_LENGTH = 500;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final RelayChannelMapper channelMapper;
    private final RelayChannelProviderMapper channelProviderMapper;
    private final RelayChannelModelMapper channelModelMapper;
    private final RelayModelMapper modelMapper;
    private final RelayProviderScheduler providerScheduler;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Override
    public RelayChannelTestResultDto test(Long channelId, RelayChannelTestRequestDto request) {
        RelayChannel channel = channelMapper.selectById(channelId);
        if (channel == null) throw new BusinessException(404, "Relay channel not found");
        Long modelId = request == null ? null : request.getModelId();
        if (modelId == null) throw new BusinessException(400, "请选择要测试的模型");
        String prompt = request.getPrompt() == null || request.getPrompt().isBlank()
                ? DEFAULT_PROMPT
                : request.getPrompt().trim();
        if (prompt.length() > MAX_PROMPT_LENGTH) prompt = prompt.substring(0, MAX_PROMPT_LENGTH);

        RelayChannelModel binding = channelModelMapper.selectByChannelAndModel(channelId, modelId);
        if (binding == null) throw new BusinessException(400, "该渠道未绑定此模型，请先在渠道编辑中启用");
        RelayModel model = modelMapper.selectById(modelId);
        String publicModel = model == null || isBlank(model.getModel()) ? "" : model.getModel().trim();
        String upstreamModel = !isBlank(binding.getUpstreamModel())
                ? binding.getUpstreamModel().trim()
                : publicModel;
        if (isBlank(upstreamModel)) throw new BusinessException(400, "模型名称为空，无法发起测试");

        List<RelayChannelProvider> providers = usableProviders(channel);
        List<RelayChannelTestAttemptDto> attempts = new ArrayList<>();
        for (RelayChannelProvider provider : providerScheduler.order(channel, providers)) {
            String rule = resolveRule(channel, provider);
            long startedAt = System.currentTimeMillis();
            try {
                String content = callUpstream(channel, provider, rule, upstreamModel, prompt);
                long latencyMs = System.currentTimeMillis() - startedAt;
                log.info("Relay channel test ok channelId={} providerId={} model={} upstreamModel={} latencyMs={}",
                        channelId, provider.getId(), publicModel, upstreamModel, latencyMs);
                return new RelayChannelTestResultDto(true, providerName(provider), provider.getId(), rule,
                        publicModel, upstreamModel, latencyMs, content, null, attempts);
            } catch (Exception ex) {
                long latencyMs = System.currentTimeMillis() - startedAt;
                String message = ex.getMessage() == null || ex.getMessage().isBlank()
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage();
                attempts.add(new RelayChannelTestAttemptDto(providerName(provider), truncate(message), latencyMs));
                log.info("Relay channel test attempt failed channelId={} providerId={} latencyMs={} message={}",
                        channelId, provider.getId(), latencyMs, truncate(message));
            }
        }
        return new RelayChannelTestResultDto(false, null, null, resolveRule(channel, null), publicModel,
                upstreamModel, 0, null, "全部供应商调用失败", attempts);
    }

    /** 启用且健康的供应商；老渠道无供应商记录时回退渠道自身凭证（与派发侧口径一致）。 */
    private List<RelayChannelProvider> usableProviders(RelayChannel channel) {
        List<RelayChannelProvider> all = channelProviderMapper.selectByChannelId(channel.getId());
        List<RelayChannelProvider> providers = all.stream()
                .filter(item -> item.getEnabled() == null || item.getEnabled())
                .filter(item -> item.getStatus() == null || !"failed".equalsIgnoreCase(item.getStatus()))
                .filter(item -> !isBlank(item.getApiBaseUrl()) && !isBlank(item.getApiKey()))
                .toList();
        if (!providers.isEmpty()) return providers;
        if (!all.isEmpty() || isBlank(channel.getApiBaseUrl()) || isBlank(channel.getApiKey())) {
            throw new BusinessException(400, "该渠道当前没有可用的供应商，请先配置或启用供应商");
        }
        return List.of(legacyProvider(channel));
    }

    private RelayChannelProvider legacyProvider(RelayChannel channel) {
        RelayChannelProvider provider = new RelayChannelProvider();
        provider.setChannelId(channel.getId());
        provider.setName(channel.getProvider());
        provider.setApiBaseUrl(channel.getApiBaseUrl());
        provider.setApiKey(channel.getApiKey());
        provider.setChannelRule(channel.getChannelRule());
        provider.setPriority(channel.getPriority());
        provider.setWeight(channel.getWeight());
        provider.setStatus("available");
        provider.setEnabled(true);
        return provider;
    }

    private String resolveRule(RelayChannel channel, RelayChannelProvider provider) {
        if (provider != null && !isBlank(provider.getChannelRule())) return provider.getChannelRule().trim().toLowerCase();
        if (!isBlank(channel.getChannelRule())) return channel.getChannelRule().trim().toLowerCase();
        return "openai";
    }

    private String providerName(RelayChannelProvider provider) {
        return isBlank(provider.getName()) ? "供应商#" + provider.getId() : provider.getName().trim();
    }

    String callUpstream(RelayChannel channel, RelayChannelProvider provider, String rule,
                        String upstreamModel, String prompt) throws Exception {
        boolean anthropic = "anthropic".equalsIgnoreCase(rule);
        String url = relayUrl(provider.getApiBaseUrl(), anthropic ? "/v1/messages" : "/v1/chat/completions");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", upstreamModel);
        var messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);
        if (anthropic) {
            body.put("max_tokens", ANTHROPIC_MAX_TOKENS);
        } else {
            body.put("stream", false);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (anthropic) {
            builder.header("x-api-key", provider.getApiKey())
                    .header("anthropic-version", "2023-06-01");
        } else {
            builder.header("Authorization", "Bearer " + provider.getApiKey());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode responseBody = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + truncate(errorDetail(responseBody, response.body())));
        }
        String content = extractContent(responseBody, anthropic);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException(truncate(errorDetail(responseBody, response.body())));
        }
        return content;
    }

    /** openai：choices[0].message.content（兼容块数组）；anthropic：content[] 中 text 块拼接。 */
    static String extractContent(JsonNode body, boolean anthropic) {
        if (body == null || body.path("error").isObject()) return "";
        if (anthropic) {
            StringBuilder text = new StringBuilder();
            for (JsonNode block : body.path("content")) {
                if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText(""));
            }
            return text.toString();
        }
        JsonNode message = body.path("choices").path(0).path("message");
        JsonNode content = message.path("content");
        if (content.isTextual()) return content.asText("");
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText(""));
            }
            return text.toString();
        }
        return "";
    }

    private String errorDetail(JsonNode body, String rawBody) {
        String message = body.path("error").path("message").asText("");
        if (message.isBlank()) message = body.path("error").asText("");
        if (message.isBlank()) message = rawBody == null ? "" : rawBody;
        return message.isBlank() ? "上游返回了空回复" : message;
    }

    private String relayUrl(String apiBaseUrl, String path) {
        String baseUrl = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        if (baseUrl.endsWith("/v1") && path.startsWith("/v1/")) path = path.substring(3);
        return baseUrl + path;
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= MAX_ATTEMPT_MESSAGE_LENGTH ? value : value.substring(0, MAX_ATTEMPT_MESSAGE_LENGTH) + "…";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
