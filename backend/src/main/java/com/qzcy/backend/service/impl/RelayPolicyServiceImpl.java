package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.dto.relay.RelayCostBreakdown;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class RelayPolicyServiceImpl implements RelayPolicyService {
    private final RelayTokenMapper tokenMapper;
    private final RelayModelMapper modelMapper;
    private final RelayGroupMapper groupMapper;
    private final RelayGroupModelMapper groupModelMapper;
    private final RelayChannelMapper channelMapper;
    private final RelayChannelModelMapper channelModelMapper;
    private final RelayUsageLogMapper usageLogMapper;
    private final UserMapper userMapper;

    @Override
    public RelayContext buildContext(String authorization, String apiKeyHeader, String queryKey, String clientIp, String endpointType, String requestedModel) {
        List<RelayContext> contexts = buildContexts(authorization, apiKeyHeader, queryKey, clientIp, endpointType, requestedModel);
        RelayContext context = contexts.get(0);
        enforceRateLimits(context.token(), context.channel());
        return context;
    }

    @Override
    public List<RelayContext> buildContexts(String authorization, String apiKeyHeader, String queryKey, String clientIp, String endpointType, String requestedModel) {
        RelayToken access = requireRelayToken(authorization, apiKeyHeader, queryKey);
        enforceIpAccess(access, clientIp);
        enforceTokenModelAccess(access, requestedModel);
        RelayGroup group = resolveGroup(access.getGroupNames(), requestedModel, endpointType);
        RelayModel relayModel = requireModelForGroup(requestedModel, endpointType, group);
        List<RelayContext> contexts = chooseChannels(access, relayModel, group, endpointType);
        ensureBalance(access.getUserId());
        return contexts;
    }

    @Override
    public RelayToken requireRelayToken(String authorization) {
        return requireRelayToken(authorization, null, null);
    }

    @Override
    public RelayToken requireRelayToken(String authorization, String apiKeyHeader, String queryKey) {
        String value = relayKeyValue(authorization, apiKeyHeader, queryKey);
        if (value == null || value.isBlank()) {
            throw new BusinessException(401, "Missing relay API key");
        }
        RelayToken item = tokenMapper.selectOne(new QueryWrapper<RelayToken>().eq("token", value).eq("enabled", true));
        if (item == null) {
            throw new BusinessException(401, "Invalid relay API key");
        }
        if (item.getExpiresAt() != null && item.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(401, "Relay API key expired");
        }
        ensureUserUsable(item.getUserId());
        return item;
    }

    private String relayKeyValue(String authorization, String apiKeyHeader, String queryKey) {
        String value = firstNotBlank(authorization, apiKeyHeader, queryKey);
        if (value == null) return null;
        value = value.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            value = value.substring("Bearer ".length()).trim();
        }
        return value;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    @Override
    public void enforceIpAccess(RelayToken access, String clientIp) {
        if (access.getIpWhitelist() == null || access.getIpWhitelist().isBlank()) return;
        if (!containsCsv(access.getIpWhitelist(), clientIp)) {
            throw new BusinessException(403, "This API key cannot be used from current IP");
        }
    }

    @Override
    public RelayModel requireModel(String model, String endpointType) {
        if (model == null || model.isBlank()) {
            throw new BusinessException(400, "Model is required");
        }
        RelayModel item = selectEnabledModelByPublicName(model);
        if (item == null || !modelTypeMatches(item.getModelType(), endpointType)) {
            throw new BusinessException(400, "Selected relay model is unavailable");
        }
        return item;
    }

    @Override
    public RelayModel requireModelForGroup(String model, String endpointType, RelayGroup group) {
        if (model == null || model.isBlank()) {
            throw new BusinessException(400, "Model is required");
        }
        if (group == null || group.getId() == null) {
            return requireModel(model, endpointType);
        }
        RelayModel item = groupModelMapper.selectEnabledModelForGroup(group.getId(), model.trim());
        if (item == null) {
            Long configuredModels = groupModelMapper.countEnabledModelsForGroup(group.getId());
            if (configuredModels == null || configuredModels == 0 || "default".equalsIgnoreCase(group.getCode())) {
                return requireModel(model, endpointType);
            }
        }
        if (item == null || !modelTypeMatches(item.getModelType(), endpointType)) {
            throw new BusinessException(400, "Selected relay model is unavailable");
        }
        return item;
    }

    @Override
    public void enforceTokenModelAccess(RelayToken access, String model) {
        enforceTokenModelAccess(access, selectEnabledModelByPublicName(model), model);
    }

    @Override
    public RelayGroup resolveGroup(String groupNames, String model, String endpointType) {
        if (model == null || model.isBlank()) {
            throw new BusinessException(400, "Model is required");
        }
        RelayModel relayModel = selectEnabledModelByPublicName(model);
        List<String> groupCodes = csvValues(groupNames == null || groupNames.isBlank() ? "default" : groupNames);
        for (String code : groupCodes) {
            RelayGroup group = groupMapper.selectOne(new QueryWrapper<RelayGroup>().eq("code", code).eq("enabled", true));
            if (group == null) continue;
            RelayModel groupModel = groupModelMapper.selectEnabledModelForGroup(group.getId(), model.trim());
            if (groupModel != null && modelTypeMatches(groupModel.getModelType(), endpointType)) {
                return group;
            }
            Long configuredModels = groupModelMapper.countEnabledModelsForGroup(group.getId());
            if (configuredModels == null || configuredModels == 0 || "default".equalsIgnoreCase(group.getCode())) {
                RelayModel fallback = relayModel == null ? selectEnabledModelByPublicName(model) : relayModel;
                if (fallback != null && modelTypeMatches(fallback.getModelType(), endpointType)) {
                    return group;
                }
            }
        }
        throw new BusinessException(403, "Current group cannot access the selected model: " + model.trim());
    }

    @Override
    public RelayGroup resolveGroup(String groupNames, RelayModel model) {
        List<String> groupCodes = csvValues(groupNames == null || groupNames.isBlank() ? "default" : groupNames);
        for (String code : groupCodes) {
            RelayGroup group = groupMapper.selectOne(new QueryWrapper<RelayGroup>().eq("code", code).eq("enabled", true));
            if (group == null) continue;
            Long configuredModels = groupModelMapper.countEnabledModelsForGroup(group.getId());
            if (configuredModels == null || configuredModels == 0 || "default".equalsIgnoreCase(group.getCode())) {
                return group;
            }
            Long allowed = groupModelMapper.countGroupModel(group.getId(), model.getId());
            if (allowed != null && allowed > 0) {
                return group;
            }
        }
        throw new BusinessException(403, "Current group cannot access the selected model");
    }

    @Override
    public RelayContext chooseChannel(RelayToken access, RelayModel model, RelayGroup group, String endpointType) {
        return chooseChannels(access, model, group, endpointType).get(0);
    }

    private List<RelayContext> chooseChannels(RelayToken access, RelayModel model, RelayGroup group, String endpointType) {
        String groupCode = group == null || isBlank(group.getCode()) ? "default" : group.getCode();
        List<RelayChannel> candidates = channelMapper.selectDispatchCandidates(model.getId(), groupCode);
        if (candidates.isEmpty()) {
            throw new BusinessException(400, "No available relay channel for current group and model");
        }
        List<RelayChannel> ordered = new java.util.ArrayList<>();
        int index = 0;
        while (index < candidates.size()) {
            int priority = candidates.get(index).getPriority() == null ? 0 : candidates.get(index).getPriority();
            int start = index;
            while (index < candidates.size()
                    && (candidates.get(index).getPriority() == null ? 0 : candidates.get(index).getPriority()) == priority) {
                index++;
            }
            ordered.addAll(weightedOrder(candidates.subList(start, index)));
        }
        String effectiveModelType = isBlank(model.getModelType()) ? endpointType : model.getModelType();
        return ordered.stream()
                .map(channel -> new RelayContext(
                        access,
                        model,
                        group,
                        channel,
                        channelModelMapper.selectByChannelAndModel(channel.getId(), model.getId()),
                        effectiveModelType
                ))
                .toList();
    }

    @Override
    public void enforceRateLimits(RelayToken access, RelayChannel channel) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(1);
        if (access.getRpmLimit() != null && access.getRpmLimit() > 0) {
            Long requests = usageLogMapper.tokenRequestsSince(access.getId(), since);
            if (requests != null && requests >= access.getRpmLimit()) {
                throw new BusinessException(429, "API key RPM limit exceeded");
            }
        }
        if (access.getTpmLimit() != null && access.getTpmLimit() > 0) {
            Long tokens = usageLogMapper.tokenTokensSince(access.getId(), since);
            if (tokens != null && tokens >= access.getTpmLimit()) {
                throw new BusinessException(429, "API key TPM limit exceeded");
            }
        }
        if (channel.getRpmLimit() != null && channel.getRpmLimit() > 0) {
            Long requests = usageLogMapper.channelRequestsSince(channel.getId(), since);
            if (requests != null && requests >= channel.getRpmLimit()) {
                throw new BusinessException(429, "Relay channel RPM limit exceeded");
            }
        }
        if (channel.getTpmLimit() != null && channel.getTpmLimit() > 0) {
            Long tokens = usageLogMapper.channelTokensSince(channel.getId(), since);
            if (tokens != null && tokens >= channel.getTpmLimit()) {
                throw new BusinessException(429, "Relay channel TPM limit exceeded");
            }
        }
    }

    @Override
    public void ensureBalance(Long userId) {
        User user = ensureUserUsable(userId);
        if (user.getBalance() == null || user.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(402, "Insufficient balance");
        }
    }

    private User ensureUserUsable(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(401, "User not found");
        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new BusinessException(423, "账号已被封禁，无法使用网站功能");
        }
        return user;
    }

    @Override
    public void enforceQuota(RelayToken access, BigDecimal nextCost) {
        if (access.getQuota() == null || access.getQuota().compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal used = access.getUsedQuota() == null ? BigDecimal.ZERO : access.getUsedQuota();
        if (used.add(nextCost).compareTo(access.getQuota()) > 0) {
            throw new BusinessException(402, "API key quota exceeded");
        }
    }

    @Override
    public RelayCostBreakdown estimateCost(RelayModel model, RelayChannel channel, RelayGroup group, JsonNode responseBody) {
        JsonNode usage = responseBody.path("usage");
        BigDecimal promptTokens = BigDecimal.valueOf(inputTokens(usage));
        BigDecimal completionTokens = BigDecimal.valueOf(outputTokens(usage));
        BigDecimal cachedTokens = BigDecimal.valueOf(cacheReadTokens(usage));
        BigDecimal cacheCreationTokens = BigDecimal.valueOf(cacheCreationTokens(usage));
        BigDecimal billableInputTokens = claudeSeparateCacheUsage(usage)
                ? promptTokens
                : promptTokens.subtract(cachedTokens).subtract(cacheCreationTokens).max(BigDecimal.ZERO);
        BigDecimal input = perMillion(billableInputTokens).multiply(zeroIfNull(model.getInputPrice()));
        BigDecimal output = perMillion(completionTokens).multiply(zeroIfNull(model.getOutputPrice()));
        BigDecimal cacheRead = perMillion(cachedTokens).multiply(zeroIfNull(model.getCachedInputPrice()));
        BigDecimal cacheCreation = perMillion(cacheCreationTokens).multiply(zeroIfNull(model.getCacheCreationPrice()));
        BigDecimal request = zeroIfNull(model.getRequestPrice());
        if (Boolean.TRUE.equals(model.getFixedRequestBilling())) {
            return new RelayCostBreakdown(input, output, cacheRead, cacheCreation, request, request);
        }
        BigDecimal base = input.add(output).add(cacheRead).add(cacheCreation).add(request);
        BigDecimal upstreamCost = base.multiply(channelRatio(channel));
        BigDecimal userCost = base.multiply(groupRatio(group));
        BigDecimal total = userCost.max(upstreamCost);
        return new RelayCostBreakdown(input, output, cacheRead, cacheCreation, request, total);
    }

    private BigDecimal perMillion(BigDecimal value) {
        return value.divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal groupRatio(RelayGroup group) {
        return group == null || group.getRatio() == null ? BigDecimal.ONE : group.getRatio();
    }

    private BigDecimal channelRatio(RelayChannel channel) {
        return channel == null || channel.getPriceMultiplier() == null ? BigDecimal.ONE : channel.getPriceMultiplier();
    }

    private long inputTokens(JsonNode usage) {
        return firstLong(usage, "prompt_tokens", "input_tokens", "input_tokens_total");
    }

    private long outputTokens(JsonNode usage) {
        return firstLong(usage, "completion_tokens", "output_tokens");
    }

    private long cacheReadTokens(JsonNode usage) {
        long nested = firstLong(usage.path("prompt_tokens_details"), "cached_tokens", "cache_read_tokens");
        if (nested > 0) return nested;
        nested = firstLong(usage.path("input_tokens_details"), "cached_tokens", "cache_read_tokens");
        if (nested > 0) return nested;
        return firstLong(usage, "cache_read_input_tokens", "cache_read_tokens", "cached_tokens", "prompt_cache_hit_tokens");
    }

    private long cacheCreationTokens(JsonNode usage) {
        long nested = firstLong(usage.path("prompt_tokens_details"), "cache_creation_tokens", "cached_creation_tokens");
        if (nested > 0) return nested;
        nested = firstLong(usage.path("input_tokens_details"), "cache_creation_tokens", "cached_creation_tokens");
        if (nested > 0) return nested;
        return firstLong(usage, "cache_creation_input_tokens", "cache_creation_tokens", "cached_creation_tokens");
    }

    private boolean claudeSeparateCacheUsage(JsonNode usage) {
        return usage.has("cache_read_input_tokens") || usage.has("cache_creation_input_tokens");
    }

    private long firstLong(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) return value.asLong();
        }
        return 0;
    }

    private RelayChannel weightedPick(List<RelayChannel> channels) {
        return weightedOrder(channels).get(0);
    }

    private List<RelayChannel> weightedOrder(List<RelayChannel> channels) {
        List<RelayChannel> remaining = new java.util.ArrayList<>(channels);
        List<RelayChannel> ordered = new java.util.ArrayList<>();
        while (!remaining.isEmpty()) {
            RelayChannel picked = weightedPickOne(remaining);
            ordered.add(picked);
            remaining.remove(picked);
        }
        return ordered;
    }

    private RelayChannel weightedPickOne(List<RelayChannel> channels) {
        int totalWeight = channels.stream()
                .mapToInt(channel -> Math.max(0, channel.getWeight() == null ? 0 : channel.getWeight()))
                .sum();
        if (totalWeight <= 0) return channels.get(0);
        int value = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (RelayChannel channel : channels) {
            cursor += Math.max(0, channel.getWeight() == null ? 0 : channel.getWeight());
            if (value < cursor) return channel;
        }
        return channels.get(0);
    }

    private boolean modelTypeMatches(String configuredType, String requestedType) {
        if (configuredType == null || configuredType.isBlank()) return true;
        if (configuredType.equalsIgnoreCase(requestedType)) return true;
        return requestedType.equalsIgnoreCase("chat") && configuredType.equalsIgnoreCase("code");
    }

    private boolean containsCsv(String csv, String value) {
        if (value == null || value.isBlank()) return true;
        if (csv == null || csv.isBlank()) return true;
        return csvValues(csv).stream().anyMatch(item -> item.equalsIgnoreCase(value.trim()));
    }

    private void enforceTokenModelAccess(RelayToken access, RelayModel relayModel, String requestedModel) {
        if (access == null || isBlank(access.getAllowedModels())) return;
        if (relayModel != null) {
            if (containsCsv(access.getAllowedModels(), publicModelName(relayModel))
                    || containsCsv(access.getAllowedModels(), relayModel.getModel())) {
                return;
            }
        } else if (containsCsv(access.getAllowedModels(), requestedModel)) {
            return;
        }
        throw new BusinessException(403, "This API key cannot access the selected model");
    }

    private RelayModel selectEnabledModelByPublicName(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) return null;
        String value = requestedModel.trim();
        return modelMapper.selectOne(new QueryWrapper<RelayModel>()
                .eq("enabled", true)
                .and(wrapper -> wrapper.eq("display_name", value).or().eq("model", value))
                .orderByAsc("sort_order")
                .orderByAsc("id")
                .last("LIMIT 1"));
    }

    private String publicModelName(RelayModel model) {
        if (model == null) return "";
        return isBlank(model.getDisplayName()) ? model.getModel() : model.getDisplayName();
    }

    private List<String> csvValues(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
