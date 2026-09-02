package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.ErrorRequestLogDto;
import com.qzcy.backend.dto.RelayAdminOverviewDto;
import com.qzcy.backend.dto.RelayChannelDto;
import com.qzcy.backend.dto.RelayChannelModelDto;
import com.qzcy.backend.dto.RelayChannelModelUpdateDto;
import com.qzcy.backend.dto.RelayChannelUpdateDto;
import com.qzcy.backend.dto.RelayGroupDto;
import com.qzcy.backend.dto.RelayGroupUpdateDto;
import com.qzcy.backend.dto.RelayModelDto;
import com.qzcy.backend.dto.RelayModelRecentCallDto;
import com.qzcy.backend.dto.RelayModelUpdateDto;
import com.qzcy.backend.dto.RelayModelUsageDto;
import com.qzcy.backend.dto.RelayPublicChannelDto;
import com.qzcy.backend.dto.RelayPublicChannelModelDto;
import com.qzcy.backend.dto.RelayStatsDto;
import com.qzcy.backend.dto.RelayTokenCreateDto;
import com.qzcy.backend.dto.RelayTokenDto;
import com.qzcy.backend.dto.RelayUpstreamModelDto;
import com.qzcy.backend.dto.RelayUsageLogDto;
import com.qzcy.backend.dto.RelayUserOverviewDto;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayGroupModel;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.entity.RelayUsageLog;
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
import com.qzcy.backend.service.RelayService;
import com.qzcy.backend.service.RelayModelStatusCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RelayServiceImpl implements RelayService {
    private final RelayChannelMapper channelMapper;
    private final RelayChannelModelMapper channelModelMapper;
    private final RelayGroupMapper groupMapper;
    private final RelayGroupModelMapper groupModelMapper;
    private final RelayModelMapper modelMapper;
    private final RelayTokenMapper tokenMapper;
    private final RelayUsageLogMapper usageLogMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final RelayModelStatusCache relayModelStatusCache;
    private final SecureRandom random = new SecureRandom();

    @Override
    public RelayAdminOverviewDto adminOverview() {
        RelayStatsDto stats = new RelayStatsDto(
                channelMapper.selectCount(null),
                channelMapper.selectCount(new QueryWrapper<RelayChannel>().eq("enabled", true)),
                tokenMapper.selectCount(null),
                tokenMapper.selectCount(new QueryWrapper<RelayToken>().eq("enabled", true)),
                usageLogMapper.selectCount(null),
                usageLogMapper.totalTokens(),
                usageLogMapper.totalCost()
        );
        List<RelayChannelDto> channels = channelMapper.selectList(new QueryWrapper<RelayChannel>()
                        .orderByAsc("priority")
                        .orderByDesc("weight")
                        .orderByDesc("id"))
                .stream().map(this::toChannelDto).toList();
        List<RelayTokenDto> tokens = tokenMapper.selectList(new QueryWrapper<RelayToken>()
                        .orderByDesc("created_at")
                        .last("LIMIT 50"))
                .stream().map(this::toTokenDto).toList();
        List<RelayModelDto> models = modelMapper.selectList(new QueryWrapper<RelayModel>()
                        .orderByAsc("sort_order")
                        .orderByAsc("id"))
                .stream().map(this::toModelDto).toList();
        List<RelayGroupDto> groups = groupMapper.selectList(new QueryWrapper<RelayGroup>()
                        .orderByAsc("id"))
                .stream().map(this::toGroupDto).toList();
        return new RelayAdminOverviewDto(stats, channels, tokens, models, groups);
    }

    @Override
    public RelayChannelDto createChannel(RelayChannelUpdateDto dto) {
        RelayChannel channel = new RelayChannel();
        apply(channel, dto);
        if (isBlank(channel.getName())) channel.setName("New relay channel");
        if (isBlank(channel.getProvider())) channel.setProvider("OpenAI Compatible");
        if (isBlank(channel.getChannelRule())) channel.setChannelRule("openai");
        if (isBlank(channel.getGroupNames())) channel.setGroupNames("default");
        if (channel.getStatus() == null) channel.setStatus("unknown");
        if (channel.getPriority() == null) channel.setPriority(10);
        if (channel.getWeight() == null) channel.setWeight(10);
        if (channel.getRpmLimit() == null) channel.setRpmLimit(0);
        if (channel.getTpmLimit() == null) channel.setTpmLimit(0);
        if (channel.getMaxConcurrency() == null) channel.setMaxConcurrency(0);
        if (channel.getPriceMultiplier() == null) channel.setPriceMultiplier(BigDecimal.ONE);
        if (channel.getEnabled() == null) channel.setEnabled(true);
        channelMapper.insert(channel);
        replaceChannelModels(channel.getId(), dto.getModels());
        return toChannelDto(channelMapper.selectById(channel.getId()));
    }

    @Override
    public RelayChannelDto updateChannel(Long id, RelayChannelUpdateDto dto) {
        RelayChannel channel = channelMapper.selectById(id);
        if (channel == null) throw new BusinessException(404, "Relay channel not found");
        apply(channel, dto);
        channelMapper.updateById(channel);
        if (dto.getModels() != null) {
            replaceChannelModels(channel.getId(), dto.getModels());
        }
        return toChannelDto(channelMapper.selectById(id));
    }

    @Override
    public void deleteChannel(Long id) {
        RelayChannel channel = channelMapper.selectById(id);
        if (channel == null) throw new BusinessException(404, "Relay channel not found");
        channelModelMapper.deleteByChannelId(id);
        channelMapper.deleteById(id);
    }

    @Override
    public RelayGroupDto createGroup(RelayGroupUpdateDto dto) {
        RelayGroup group = new RelayGroup();
        apply(group, dto);
        if (isBlank(group.getCode())) throw new BusinessException(400, "Group code is required");
        if (isBlank(group.getName())) group.setName(group.getCode());
        if (group.getRatio() == null) group.setRatio(BigDecimal.ONE);
        if (group.getEnabled() == null) group.setEnabled(true);
        groupMapper.insert(group);
        if (dto.getModelIds() != null) {
            replaceGroupModels(group.getId(), dto.getModelIds());
        } else {
            attachAllModelsToGroup(group.getId());
        }
        return toGroupDto(groupMapper.selectById(group.getId()));
    }

    @Override
    public RelayGroupDto updateGroup(Long id, RelayGroupUpdateDto dto) {
        RelayGroup group = groupMapper.selectById(id);
        if (group == null) throw new BusinessException(404, "Relay group not found");
        apply(group, dto);
        if (isBlank(group.getCode())) throw new BusinessException(400, "Group code is required");
        if (isBlank(group.getName())) group.setName(group.getCode());
        if (group.getRatio() == null) group.setRatio(BigDecimal.ONE);
        groupMapper.updateById(group);
        if (dto.getModelIds() != null) {
            replaceGroupModels(group.getId(), dto.getModelIds());
        }
        return toGroupDto(groupMapper.selectById(id));
    }

    @Override
    public void deleteGroup(Long id) {
        RelayGroup group = groupMapper.selectById(id);
        if (group == null) throw new BusinessException(404, "Relay group not found");
        if ("default".equalsIgnoreCase(group.getCode())) {
            throw new BusinessException(400, "Default group cannot be deleted");
        }
        groupMapper.deleteById(id);
    }

    @Override
    public RelayModelDto createModel(RelayModelUpdateDto dto) {
        RelayModel model = new RelayModel();
        apply(model, dto);
        if (isBlank(model.getModel())) throw new BusinessException(400, "Model is required");
        if (isBlank(model.getDisplayName())) model.setDisplayName(model.getModel());
        if (isBlank(model.getModelType())) model.setModelType("chat");
        if (model.getInputPrice() == null) model.setInputPrice(BigDecimal.ZERO);
        if (model.getOutputPrice() == null) model.setOutputPrice(BigDecimal.ZERO);
        if (model.getCachedInputPrice() == null) model.setCachedInputPrice(BigDecimal.ZERO);
        if (model.getCacheCreationPrice() == null) model.setCacheCreationPrice(BigDecimal.ZERO);
        if (model.getRequestPrice() == null) model.setRequestPrice(BigDecimal.ZERO);
        if (model.getFixedRequestBilling() == null) model.setFixedRequestBilling(false);
        if (model.getLongContextThreshold() == null) model.setLongContextThreshold(0L);
        if (isBlank(model.getLongContextBillingMode())) model.setLongContextBillingMode("price");
        validateLongContextBilling(model);
        if (isBlank(model.getStatus())) model.setStatus("available");
        if (model.getEnabled() == null) model.setEnabled(true);
        if (model.getSortOrder() == null) model.setSortOrder(10);
        modelMapper.insert(model);
        return toModelDto(modelMapper.selectById(model.getId()));
    }

    @Override
    public RelayModelDto updateModel(Long id, RelayModelUpdateDto dto) {
        RelayModel model = modelMapper.selectById(id);
        if (model == null) throw new BusinessException(404, "Relay model not found");
        apply(model, dto);
        if (isBlank(model.getModel())) throw new BusinessException(400, "Model is required");
        if (isBlank(model.getDisplayName())) model.setDisplayName(model.getModel());
        if (isBlank(model.getModelType())) model.setModelType("chat");
        if (model.getCachedInputPrice() == null) model.setCachedInputPrice(BigDecimal.ZERO);
        if (model.getCacheCreationPrice() == null) model.setCacheCreationPrice(BigDecimal.ZERO);
        if (model.getFixedRequestBilling() == null) model.setFixedRequestBilling(false);
        if (model.getLongContextThreshold() == null) model.setLongContextThreshold(0L);
        if (isBlank(model.getLongContextBillingMode())) model.setLongContextBillingMode("price");
        validateLongContextBilling(model);
        if (isBlank(model.getStatus())) model.setStatus("available");
        modelMapper.updateById(model);
        return toModelDto(modelMapper.selectById(id));
    }

    @Override
    public void deleteModel(Long id) {
        RelayModel model = modelMapper.selectById(id);
        if (model == null) throw new BusinessException(404, "Relay model not found");
        groupModelMapper.delete(new QueryWrapper<RelayGroupModel>().eq("model_id", id));
        channelModelMapper.delete(new QueryWrapper<RelayChannelModel>().eq("model_id", id));
        modelMapper.deleteById(id);
    }

    @Override
    public List<RelayUpstreamModelDto> fetchUpstreamModels(Long channelId) {
        RelayChannel channel = channelMapper.selectById(channelId);
        if (channel == null) throw new BusinessException(404, "Relay channel not found");
        if (isBlank(channel.getApiBaseUrl())) throw new BusinessException(400, "Channel base URL is not configured");
        if (isBlank(channel.getApiKey())) throw new BusinessException(400, "Channel API key is not configured");
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(relayUrl(channel.getApiBaseUrl(), "/v1/models")))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .GET();
            applyRelayAuthHeaders(builder, channel);
            HttpRequest request = builder.build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(response.statusCode(), "Upstream model query failed: " + response.body());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            Set<String> configured = new HashSet<>(channelModelMapper.modelsForChannel(channelId).stream()
                    .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                    .map(item -> isBlank(item.getUpstreamModel())
                            ? (isBlank(item.getDisplayName()) ? item.getModel() : item.getDisplayName())
                            : item.getUpstreamModel().trim())
                    .filter(value -> !isBlank(value))
                    .toList());
            if (!data.isArray()) return List.of();
            return java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .map(item -> new RelayUpstreamModelDto(
                            item.path("id").asText(""),
                            item.path("owned_by").asText(""),
                            configured.contains(item.path("id").asText(""))
                    ))
                    .filter(item -> item.getId() != null && !item.getId().isBlank())
                    .toList();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(500, "Upstream model query failed: " + ex.getMessage());
        }
    }

    @Override
    public RelayTokenDto createToken(Long userId, RelayTokenCreateDto dto) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(404, "User not found");
        String newKey = createApiKeyValue();
        RelayToken item = new RelayToken();
        item.setUserId(userId);
        item.setName(isBlank(dto.getName()) ? "Default API Key" : dto.getName().trim());
        item.setToken(newKey);
        item.setTokenPreview(preview(newKey));
        item.setGroupNames(isBlank(dto.getGroups()) ? "default" : dto.getGroups().trim());
        item.setAllowedModels(dto.getAllowedModels() == null ? "" : dto.getAllowedModels().trim());
        item.setQuota(dto.getQuota() == null ? BigDecimal.ZERO : nonNegative(dto.getQuota()));
        item.setUsedQuota(BigDecimal.ZERO);
        item.setRequestCount(0L);
        item.setTokenCount(0L);
        item.setRpmLimit(dto.getRpmLimit() == null ? 0 : Math.max(0, dto.getRpmLimit()));
        item.setTpmLimit(dto.getTpmLimit() == null ? 0 : Math.max(0, dto.getTpmLimit()));
        item.setIpWhitelist(normalizeIpWhitelist(dto.getIpWhitelist()));
        item.setExpiresAt(validateExpiresAt(dto.getExpiresAt()));
        item.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        tokenMapper.insert(item);
        RelayTokenDto result = toTokenDto(tokenMapper.selectById(item.getId()));
        result.setPlainToken(newKey);
        return result;
    }

    @Override
    public RelayTokenDto updateToken(Long userId, Long tokenId, RelayTokenCreateDto dto) {
        RelayToken item = tokenMapper.selectById(tokenId);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(404, "API key not found");
        }
        // Build a partial update entity so a stale snapshot cannot overwrite
        // usage counters accumulated by concurrent relay requests.
        RelayToken update = new RelayToken();
        update.setId(tokenId);
        if (dto.getName() != null) update.setName(dto.getName().trim());
        if (dto.getGroups() != null) update.setGroupNames(dto.getGroups().trim());
        if (dto.getAllowedModels() != null) update.setAllowedModels(dto.getAllowedModels().trim());
        if (dto.getQuota() != null) update.setQuota(nonNegative(dto.getQuota()));
        if (dto.getRpmLimit() != null) update.setRpmLimit(Math.max(0, dto.getRpmLimit()));
        if (dto.getTpmLimit() != null) update.setTpmLimit(Math.max(0, dto.getTpmLimit()));
        if (dto.getIpWhitelist() != null) update.setIpWhitelist(normalizeIpWhitelist(dto.getIpWhitelist()));
        if (dto.getExpiresAt() != null) update.setExpiresAt(validateExpiresAt(dto.getExpiresAt()));
        if (dto.getEnabled() != null) update.setEnabled(dto.getEnabled());
        tokenMapper.updateById(update);
        return toTokenDto(tokenMapper.selectById(tokenId));
    }

    @Override
    public String revealToken(Long userId, Long tokenId) {
        RelayToken item = tokenMapper.selectById(tokenId);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(404, "API key not found");
        }
        return item.getToken();
    }

    @Override
    public void deleteToken(Long userId, Long tokenId) {
        RelayToken item = tokenMapper.selectById(tokenId);
        if (item == null || !userId.equals(item.getUserId())) {
            throw new BusinessException(404, "API key not found");
        }
        tokenMapper.deleteById(tokenId);
    }

    @Override
    public RelayUserOverviewDto userOverview(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(404, "User not found");

        List<RelayModel> systemModels = modelMapper.selectList(new QueryWrapper<RelayModel>()
                        .orderByAsc("sort_order")
                        .orderByAsc("id"));
        Map<String, String> publicModelNames = new HashMap<>();
        systemModels.forEach(model -> publicModelNames.putIfAbsent(model.getModel(), publicModelName(model)));
        List<RelayModelDto> models = systemModels.stream()
                .filter(distinctByModelName())
                .map(this::toPublicModelDto).toList();
        List<RelayTokenDto> tokens = tokenMapper.selectList(new QueryWrapper<RelayToken>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at"))
                .stream().map(item -> toTokenDto(item, true)).toList();
        List<RelayChannel> enabledChannels = channelMapper.selectList(new QueryWrapper<RelayChannel>()
                        .eq("enabled", true)
                        .orderByAsc("priority")
                        .orderByDesc("weight"));
        List<RelayPublicChannelDto> channels = IntStream.range(0, enabledChannels.size())
                .mapToObj(index -> toPublicChannelDto(enabledChannels.get(index), index + 1))
                .toList();
        List<RelayUsageLogDto> logs = usageLogMapper.selectPage(
                        Page.of(1, 20),
                        new QueryWrapper<RelayUsageLog>()
                                .eq("user_id", userId)
                                .orderByDesc("created_at"))
                .getRecords().stream()
                .map(item -> toUsageDto(item, publicModelNames.getOrDefault(item.getModel(), item.getModel())))
                .toList();
        List<ErrorRequestLogDto> errorLogs = usageLogMapper.selectPage(
                        Page.of(1, 50),
                        new QueryWrapper<RelayUsageLog>()
                                .eq("user_id", userId)
                                .and(wrapper -> wrapper
                                        .eq("status", "failed")
                                        .or()
                                        .notBetween("status_code", 200, 299))
                                .orderByDesc("created_at"))
                .getRecords().stream()
                .map(item -> toErrorRequestLogDto(item, publicModelNames.getOrDefault(item.getModel(), item.getModel())))
                .toList();
        List<RelayModelUsageDto> modelUsage = usageLogMapper.modelUsage(userId).stream()
                .map(item -> new RelayModelUsageDto(
                        publicModelNames.getOrDefault(item.getModel(), item.getModel()),
                        item.getRequests(),
                        item.getTotalTokens(),
                        item.getCost()))
                .toList();
        List<RelayModelRecentCallDto> modelRecentCalls = relayModelStatusCache.getRecentCalls();
        if (modelRecentCalls == null) {
            modelRecentCalls = loadModelRecentCalls(systemModels, publicModelNames);
            relayModelStatusCache.putRecentCalls(modelRecentCalls);
        }
        List<RelayGroupDto> groups = groupMapper.selectList(new QueryWrapper<RelayGroup>()
                        .eq("enabled", true)
                        .orderByAsc("id"))
                .stream().map(this::toGroupDto).toList();

        long tokenRequests = tokens.stream().mapToLong(item -> item.getRequestCount() == null ? 0L : item.getRequestCount()).sum();
        long tokenCount = tokens.stream().mapToLong(item -> item.getTokenCount() == null ? 0L : item.getTokenCount()).sum();
        BigDecimal tokenCost = tokens.stream()
                .map(item -> item.getUsedQuota() == null ? BigDecimal.ZERO : item.getUsedQuota())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalRequests = Math.max(tokenRequests, nullToZero(usageLogMapper.userTotalRequests(userId)));
        long totalTokens = Math.max(tokenCount, nullToZero(usageLogMapper.userTotalTokens(userId)));
        BigDecimal totalCost = max(tokenCost, usageLogMapper.userTotalCost(userId));
        LocalDateTime minuteStart = LocalDateTime.now().minusMinutes(1);

        return new RelayUserOverviewDto(
                user.getBalance(), models, tokens, channels, logs, errorLogs, modelUsage, modelRecentCalls, usageLogMapper.userTrend(userId), groups,
                (long) logs.size(), 1L, 1L, 20L,
                totalRequests, totalTokens, totalCost, usageLogMapper.averageDurationMs(userId),
                usageLogMapper.userPromptTokens(userId),
                usageLogMapper.userCompletionTokens(userId),
                usageLogMapper.userCachedTokens(userId),
                usageLogMapper.userCacheCreationTokens(userId),
                usageLogMapper.userTodayRequests(userId),
                usageLogMapper.userTodayPromptTokens(userId),
                usageLogMapper.userTodayCompletionTokens(userId),
                usageLogMapper.userTodayTokens(userId),
                usageLogMapper.userTodayCost(userId),
                usageLogMapper.userRequestsSince(userId, minuteStart),
                usageLogMapper.userTokensSince(userId, minuteStart)
        );
    }

    @Override
    public RelayUserOverviewDto userOverviewSection(Long userId, String section, long page, long size,
                                                     String keyword, String status, String sort) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(404, "User not found");

        RelayUserOverviewDto result = new RelayUserOverviewDto();
        result.setBalance(user.getBalance());

        switch (normalizeOverviewSection(section)) {
            case "dashboard" -> fillDashboardSection(result, userId);
            case "keys" -> fillKeysSection(result, userId);
            case "logs" -> fillLogsSection(result, userId, page, size, keyword, status, sort);
            case "channels" -> fillChannelsSection(result);
            case "models" -> fillModelsSection(result, userId);
            default -> {
                // Billing, profile, and the pending pages only need the shared balance.
            }
        }
        return result;
    }

    private void fillDashboardSection(RelayUserOverviewDto result, Long userId) {
        List<RelayModel> systemModels = loadSystemModels();
        Map<String, String> publicModelNames = publicModelNames(systemModels);
        List<RelayTokenDto> tokens = loadUserTokens(userId);
        result.setModels(systemModels.stream().filter(distinctByModelName()).map(this::toPublicModelDto).toList());
        result.setTokens(tokens);
        result.setChannels(loadPublicChannels());
        Page<RelayUsageLog> page = selectUserLogs(userId, 1, 20, null, "all", "latest");
        setUsageLogs(result, page, publicModelNames);
        result.setModelUsage(loadModelUsage(userId, publicModelNames));
        result.setModelRecentCalls(loadRecentModelCalls(systemModels, publicModelNames));
        result.setTrend(usageLogMapper.userTrend(userId));
        fillUsageStats(result, userId, tokens);
    }

    private void fillKeysSection(RelayUserOverviewDto result, Long userId) {
        List<RelayTokenDto> tokens = loadUserTokens(userId);
        result.setTokens(tokens);
        result.setGroups(loadGroups());
        fillUsageStats(result, userId, tokens);
    }

    private void fillLogsSection(RelayUserOverviewDto result, Long userId, long page, long size,
                                 String keyword, String status, String sort) {
        List<RelayModel> systemModels = loadSystemModels();
        Map<String, String> publicModelNames = publicModelNames(systemModels);
        setUsageLogs(result, selectUserLogs(userId, page, size, keyword, status, sort), publicModelNames);

        Page<RelayUsageLog> errorPage = usageLogMapper.selectPage(
                Page.of(1, 50),
                new QueryWrapper<RelayUsageLog>()
                        .eq("user_id", userId)
                        .and(wrapper -> wrapper.eq("status", "failed").or().ge("status_code", 400))
                        .orderByDesc("created_at"));
        result.setErrorLogs(errorPage.getRecords().stream()
                .map(item -> toErrorRequestLogDto(item, publicModelNames.getOrDefault(item.getModel(), item.getModel())))
                .toList());
    }

    private void fillChannelsSection(RelayUserOverviewDto result) {
        result.setChannels(loadPublicChannels());
        result.setGroups(loadGroups());
    }

    private void fillModelsSection(RelayUserOverviewDto result, Long userId) {
        List<RelayModel> systemModels = loadSystemModels();
        Map<String, String> publicModelNames = publicModelNames(systemModels);
        result.setModels(systemModels.stream().filter(distinctByModelName()).map(this::toPublicModelDto).toList());
        result.setModelUsage(loadModelUsage(userId, publicModelNames));
        result.setModelRecentCalls(loadRecentModelCalls(systemModels, publicModelNames));
    }

    private List<RelayModel> loadSystemModels() {
        return modelMapper.selectList(new QueryWrapper<RelayModel>()
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    private List<RelayTokenDto> loadUserTokens(Long userId) {
        return tokenMapper.selectList(new QueryWrapper<RelayToken>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at"))
                .stream().map(item -> toTokenDto(item, true)).toList();
    }

    private List<RelayPublicChannelDto> loadPublicChannels() {
        List<RelayChannel> enabledChannels = channelMapper.selectList(new QueryWrapper<RelayChannel>()
                .eq("enabled", true)
                .orderByAsc("priority")
                .orderByDesc("weight"));
        return IntStream.range(0, enabledChannels.size())
                .mapToObj(index -> toPublicChannelDto(enabledChannels.get(index), index + 1))
                .toList();
    }

    private List<RelayGroupDto> loadGroups() {
        return groupMapper.selectList(new QueryWrapper<RelayGroup>()
                        .eq("enabled", true)
                        .orderByAsc("id"))
                .stream().map(this::toGroupDto).toList();
    }

    private Map<String, String> publicModelNames(List<RelayModel> systemModels) {
        Map<String, String> names = new HashMap<>();
        systemModels.forEach(model -> names.putIfAbsent(model.getModel(), publicModelName(model)));
        return names;
    }

    private List<RelayModelUsageDto> loadModelUsage(Long userId, Map<String, String> publicModelNames) {
        return usageLogMapper.modelUsage(userId).stream()
                .map(item -> new RelayModelUsageDto(
                        publicModelNames.getOrDefault(item.getModel(), item.getModel()),
                        item.getRequests(), item.getTotalTokens(), item.getCost()))
                .toList();
    }

    private List<RelayModelRecentCallDto> loadRecentModelCalls(List<RelayModel> systemModels,
                                                                Map<String, String> publicModelNames) {
        List<RelayModelRecentCallDto> cached = relayModelStatusCache.getRecentCalls();
        if (cached != null) return cached;
        List<RelayModelRecentCallDto> loaded = loadModelRecentCalls(systemModels, publicModelNames);
        relayModelStatusCache.putRecentCalls(loaded);
        return loaded;
    }

    private void fillUsageStats(RelayUserOverviewDto result, Long userId, List<RelayTokenDto> tokens) {
        long tokenRequests = tokens.stream().mapToLong(item -> item.getRequestCount() == null ? 0L : item.getRequestCount()).sum();
        long tokenCount = tokens.stream().mapToLong(item -> item.getTokenCount() == null ? 0L : item.getTokenCount()).sum();
        BigDecimal tokenCost = tokens.stream()
                .map(item -> item.getUsedQuota() == null ? BigDecimal.ZERO : item.getUsedQuota())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalRequests = Math.max(tokenRequests, nullToZero(usageLogMapper.userTotalRequests(userId)));
        long totalTokens = Math.max(tokenCount, nullToZero(usageLogMapper.userTotalTokens(userId)));
        BigDecimal totalCost = max(tokenCost, usageLogMapper.userTotalCost(userId));
        LocalDateTime minuteStart = LocalDateTime.now().minusMinutes(1);
        result.setTotalRequests(totalRequests);
        result.setTotalTokens(totalTokens);
        result.setTotalCost(totalCost);
        result.setAverageDurationMs(usageLogMapper.averageDurationMs(userId));
        result.setTotalPromptTokens(usageLogMapper.userPromptTokens(userId));
        result.setTotalCompletionTokens(usageLogMapper.userCompletionTokens(userId));
        result.setTotalCachedTokens(usageLogMapper.userCachedTokens(userId));
        result.setTotalCacheCreationTokens(usageLogMapper.userCacheCreationTokens(userId));
        result.setTodayRequests(usageLogMapper.userTodayRequests(userId));
        result.setTodayPromptTokens(usageLogMapper.userTodayPromptTokens(userId));
        result.setTodayCompletionTokens(usageLogMapper.userTodayCompletionTokens(userId));
        result.setTodayTotalTokens(usageLogMapper.userTodayTokens(userId));
        result.setTodayCost(usageLogMapper.userTodayCost(userId));
        result.setCurrentRpm(usageLogMapper.userRequestsSince(userId, minuteStart));
        result.setCurrentTpm(usageLogMapper.userTokensSince(userId, minuteStart));
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal max(BigDecimal first, BigDecimal second) {
        BigDecimal left = zero(first);
        BigDecimal right = zero(second);
        return left.compareTo(right) >= 0 ? left : right;
    }

    private void setUsageLogs(RelayUserOverviewDto result, Page<RelayUsageLog> page,
                              Map<String, String> publicModelNames) {
        result.setLogs(page.getRecords().stream()
                .map(item -> toUsageDto(item, publicModelNames.getOrDefault(item.getModel(), item.getModel())))
                .toList());
        result.setLogsTotal(page.getTotal());
        result.setLogsCurrent(page.getCurrent());
        result.setLogsPages(page.getPages());
        result.setLogsSize(page.getSize());
    }

    private Page<RelayUsageLog> selectUserLogs(Long userId, long page, long size,
                                               String keyword, String status, String sort) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        QueryWrapper<RelayUsageLog> query = new QueryWrapper<RelayUsageLog>().eq("user_id", userId);
        String term = keyword == null ? "" : keyword.trim();
        if (!term.isBlank()) {
            query.and(wrapper -> wrapper
                    .like("token_name", term)
                    .or().like("channel_name", term)
                    .or().like("group_names", term)
                    .or().like("model", term)
                    .or().like("endpoint", term)
                    .or().like("user_agent", term)
                    .or().like("message", term));
        }
        if ("failed".equalsIgnoreCase(status)) {
            query.and(wrapper -> wrapper.eq("status", "failed").or().ge("status_code", 400));
        } else if ("success".equalsIgnoreCase(status)) {
            query.and(wrapper -> wrapper.ne("status", "failed")
                    .and(inner -> inner.lt("status_code", 400).or().isNull("status_code")));
        }
        if ("slowest".equalsIgnoreCase(sort)) {
            query.orderByDesc("duration_ms").orderByDesc("created_at").orderByDesc("id");
        } else if ("cost".equalsIgnoreCase(sort)) {
            query.orderByDesc("cost").orderByDesc("created_at").orderByDesc("id");
        } else {
            query.orderByDesc("created_at").orderByDesc("id");
        }
        return usageLogMapper.selectPage(Page.of(safePage, safeSize), query);
    }

    private String normalizeOverviewSection(String section) {
        String value = section == null ? "dashboard" : section.trim().toLowerCase();
        return switch (value) {
            case "dashboard", "keys", "logs", "channels", "models", "billing", "profile", "subscription", "orders" -> value;
            default -> "dashboard";
        };
    }

    private List<RelayModelRecentCallDto> loadModelRecentCalls(List<RelayModel> systemModels, Map<String, String> publicModelNames) {
        Set<String> statusModelNames = new LinkedHashSet<>();
        systemModels.forEach(model -> {
            if (!isBlank(model.getModel())) statusModelNames.add(model.getModel());
            String publicName = publicModelName(model);
            if (!isBlank(publicName)) statusModelNames.add(publicName);
        });
        Map<Long, RelayModelRecentCallDto> uniqueRecentCalls = new LinkedHashMap<>();
        statusModelNames.forEach(modelName -> usageLogMapper.recentCallsForModel(modelName)
                .forEach(call -> uniqueRecentCalls.putIfAbsent(call.getId(), call)));

        Map<String, List<RelayModelRecentCallDto>> recentCallsByPublicModel = new LinkedHashMap<>();
        uniqueRecentCalls.values().forEach(call -> {
            String publicName = publicModelNames.getOrDefault(call.getModel(), call.getModel());
            recentCallsByPublicModel.computeIfAbsent(publicName, ignored -> new ArrayList<>())
                    .add(new RelayModelRecentCallDto(
                            call.getId(), publicName, call.getStatus(), call.getStatusCode(),
                            call.getDurationMs(), call.getCreatedAt()));
        });
        Comparator<RelayModelRecentCallDto> newestFirst = Comparator
                .comparing(RelayModelRecentCallDto::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RelayModelRecentCallDto::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        List<RelayModelRecentCallDto> result = new ArrayList<>();
        recentCallsByPublicModel.values().forEach(calls -> {
            calls.sort(newestFirst);
            List<RelayModelRecentCallDto> latestCalls = new ArrayList<>(calls.subList(0, Math.min(20, calls.size())));
            latestCalls.sort(newestFirst.reversed());
            result.addAll(latestCalls);
        });
        return result;
    }

    private void apply(RelayChannel channel, RelayChannelUpdateDto dto) {
        if (dto.getName() != null) channel.setName(dto.getName().trim());
        if (dto.getProvider() != null) channel.setProvider(dto.getProvider().trim());
        if (dto.getChannelRule() != null) channel.setChannelRule(normalizeChannelRule(dto.getChannelRule()));
        if (dto.getApiBaseUrl() != null) channel.setApiBaseUrl(normalizeBaseUrl(dto.getApiBaseUrl()));
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) channel.setApiKey(dto.getApiKey().trim());
        if (dto.getGroupNames() != null) channel.setGroupNames(normalizeCsv(dto.getGroupNames(), "default"));
        if (dto.getRemark() != null) channel.setRemark(dto.getRemark().trim());
        if (dto.getPriority() != null) channel.setPriority(Math.max(0, dto.getPriority()));
        if (dto.getWeight() != null) channel.setWeight(Math.max(0, dto.getWeight()));
        if (dto.getRpmLimit() != null) channel.setRpmLimit(Math.max(0, dto.getRpmLimit()));
        if (dto.getTpmLimit() != null) channel.setTpmLimit(Math.max(0, dto.getTpmLimit()));
        if (dto.getMaxConcurrency() != null) channel.setMaxConcurrency(Math.max(0, dto.getMaxConcurrency()));
        if (dto.getPriceMultiplier() != null) {
            if (dto.getPriceMultiplier().compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "Price multiplier cannot be negative");
            channel.setPriceMultiplier(dto.getPriceMultiplier());
        }
        if (dto.getEnabled() != null) channel.setEnabled(dto.getEnabled());
    }

    private void apply(RelayModel model, RelayModelUpdateDto dto) {
        if (dto.getModel() != null) model.setModel(dto.getModel().trim());
        if (dto.getDisplayName() != null) model.setDisplayName(dto.getDisplayName().trim());
        if (dto.getModelType() != null) model.setModelType(dto.getModelType().trim());
        if (dto.getInputPrice() != null) model.setInputPrice(nonNegative(dto.getInputPrice()));
        if (dto.getOutputPrice() != null) model.setOutputPrice(nonNegative(dto.getOutputPrice()));
        if (dto.getCachedInputPrice() != null) model.setCachedInputPrice(nonNegative(dto.getCachedInputPrice()));
        if (dto.getCacheCreationPrice() != null) model.setCacheCreationPrice(nonNegative(dto.getCacheCreationPrice()));
        if (dto.getRequestPrice() != null) model.setRequestPrice(nonNegative(dto.getRequestPrice()));
        if (dto.getFixedRequestBilling() != null) model.setFixedRequestBilling(dto.getFixedRequestBilling());
        if (dto.getLongContextThreshold() != null) {
            if (dto.getLongContextThreshold() < 0) throw new BusinessException(400, "Long context threshold cannot be negative");
            model.setLongContextThreshold(dto.getLongContextThreshold());
        }
        if (dto.getLongContextBillingMode() != null) {
            String mode = dto.getLongContextBillingMode().trim().toLowerCase();
            if (!"price".equals(mode) && !"multiplier".equals(mode)) {
                throw new BusinessException(400, "Long context billing mode must be price or multiplier");
            }
            model.setLongContextBillingMode(mode);
        }
        if (dto.getLongContextMultiplier() != null) {
            model.setLongContextMultiplier(nonNegative(dto.getLongContextMultiplier()));
        }
        if (dto.getLongContextInputPrice() != null) model.setLongContextInputPrice(nonNegative(dto.getLongContextInputPrice()));
        if (dto.getLongContextOutputPrice() != null) model.setLongContextOutputPrice(nonNegative(dto.getLongContextOutputPrice()));
        if (dto.getLongContextCachedInputPrice() != null) model.setLongContextCachedInputPrice(nonNegative(dto.getLongContextCachedInputPrice()));
        if (dto.getLongContextCacheCreationPrice() != null) model.setLongContextCacheCreationPrice(nonNegative(dto.getLongContextCacheCreationPrice()));
        if (dto.getStatus() != null) model.setStatus(dto.getStatus().trim());
        if (dto.getEnabled() != null) model.setEnabled(dto.getEnabled());
        if (dto.getSortOrder() != null) model.setSortOrder(dto.getSortOrder());
    }

    private void validateLongContextBilling(RelayModel model) {
        String mode = isBlank(model.getLongContextBillingMode()) ? "price" : model.getLongContextBillingMode().trim().toLowerCase();
        if (!"price".equals(mode) && !"multiplier".equals(mode)) {
            throw new BusinessException(400, "Long context billing mode must be price or multiplier");
        }
        model.setLongContextBillingMode(mode);
        if ("multiplier".equals(mode)
                && model.getLongContextThreshold() != null
                && model.getLongContextThreshold() > 0
                && (model.getLongContextMultiplier() == null || model.getLongContextMultiplier().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException(400, "Long context multiplier must be greater than 0");
        }
    }

    private void apply(RelayGroup group, RelayGroupUpdateDto dto) {
        if (dto.getCode() != null) group.setCode(dto.getCode().trim());
        if (dto.getName() != null) group.setName(dto.getName().trim());
        if (dto.getRatio() != null) {
            if (dto.getRatio().compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "Group ratio cannot be negative");
            group.setRatio(dto.getRatio());
        }
        if (dto.getEnabled() != null) group.setEnabled(dto.getEnabled());
    }

    private String normalizeIpWhitelist(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('\r', ',').replace('\n', ',').trim();
        if (normalized.length() > 500) {
            throw new BusinessException(400, "IP whitelist is too long");
        }
        return normalized;
    }

    private LocalDateTime validateExpiresAt(LocalDateTime value) {
        if (value == null) return null;
        if (!value.isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "API key expiration time must be in the future");
        }
        return value;
    }

    private void attachAllModelsToGroup(Long groupId) {
        modelMapper.selectList(new QueryWrapper<RelayModel>().eq("enabled", true))
                .forEach(model -> attachModelToGroup(groupId, model.getId()));
    }

    private void replaceGroupModels(Long groupId, List<Long> modelIds) {
        groupModelMapper.deleteByGroupId(groupId);
        if (modelIds == null) return;
        Set<String> selectedNames = new HashSet<>();
        modelIds.stream()
                .distinct()
                .forEach(modelId -> {
                    RelayModel model = modelId == null ? null : modelMapper.selectById(modelId);
                    if (model == null || !selectedNames.add(model.getModel())) return;
                    attachModelToGroup(groupId, model.getId());
                });
    }

    private void attachModelToGroup(Long groupId, Long modelId) {
        if (groupId == null || modelId == null) return;
        RelayModel model = modelMapper.selectById(modelId);
        if (model == null) return;
        Long sameName = groupModelMapper.countGroupModelName(groupId, model.getModel());
        if (sameName != null && sameName > 0) return;
        Long count = groupModelMapper.countGroupModel(groupId, modelId);
        if (count != null && count > 0) return;
        RelayGroupModel item = new RelayGroupModel();
        item.setGroupId(groupId);
        item.setModelId(modelId);
        groupModelMapper.insert(item);
    }

    private void replaceChannelModels(Long channelId, List<RelayChannelModelUpdateDto> models) {
        channelModelMapper.deleteByChannelId(channelId);
        if (models == null) {
            modelMapper.selectList(new QueryWrapper<RelayModel>().eq("enabled", true))
                    .forEach(model -> attachModelToChannel(channelId, model.getId(), model.getModel(), true));
            return;
        }
        models.stream()
                .filter(item -> item != null && item.getModelId() != null)
                .forEach(item -> {
                    RelayModel model = modelMapper.selectById(item.getModelId());
                    if (model == null) return;
                    // Blank means "forward the client's model id unchanged".
                    String upstreamModel = isBlank(item.getUpstreamModel()) ? "" : item.getUpstreamModel().trim();
                    attachModelToChannel(channelId, model.getId(), upstreamModel, item.getEnabled() == null || item.getEnabled());
                });
    }

    private void attachModelToChannel(Long channelId, Long modelId, String upstreamModel, boolean enabled) {
        if (channelId == null || modelId == null) return;
        RelayChannelModel existing = channelModelMapper.selectByChannelAndModel(channelId, modelId);
        if (existing != null) {
            existing.setUpstreamModel(isBlank(upstreamModel) ? "" : upstreamModel.trim());
            existing.setEnabled(enabled);
            channelModelMapper.updateById(existing);
            return;
        }
        RelayChannelModel item = new RelayChannelModel();
        item.setChannelId(channelId);
        item.setModelId(modelId);
        item.setUpstreamModel(isBlank(upstreamModel) ? "" : upstreamModel.trim());
        item.setEnabled(enabled);
        channelModelMapper.insert(item);
    }

    private List<String> accessibleModelsForTokenGroups(Long userId) {
        List<RelayToken> tokens = tokenMapper.selectList(new QueryWrapper<RelayToken>().eq("user_id", userId));
        Set<String> groupCodes = new HashSet<>();
        tokens.forEach(token -> csvValues(token.getGroupNames()).forEach(groupCodes::add));
        if (groupCodes.isEmpty()) groupCodes.add("default");
        Set<String> models = new HashSet<>();
        groupCodes.forEach(code -> {
            RelayGroup group = groupMapper.selectOne(new QueryWrapper<RelayGroup>().eq("code", code).eq("enabled", true));
            if (group != null) {
                Long configuredModels = groupModelMapper.countEnabledModelsForGroup(group.getId());
                if (configuredModels == null || configuredModels == 0 || "default".equalsIgnoreCase(group.getCode())) {
                    models.addAll(channelModelMapper.enabledModelNamesForGroup(group.getCode()));
                } else {
                    Set<String> channelModels = new HashSet<>(channelModelMapper.enabledModelNamesForGroup(group.getCode()));
                    groupModelMapper.modelsForGroup(group.getId()).stream()
                            .filter(channelModels::contains)
                            .forEach(models::add);
                }
            }
        });
        return List.copyOf(models);
    }

    private java.util.function.Predicate<RelayModel> distinctByModelName() {
        Set<String> seen = new HashSet<>();
        return model -> seen.add(publicModelName(model));
    }

    private String publicModelName(RelayModel model) {
        if (model == null) return "";
        return isBlank(model.getDisplayName()) ? model.getModel() : model.getDisplayName();
    }

    private RelayChannelDto toChannelDto(RelayChannel channel) {
        return new RelayChannelDto(channel.getId(), channel.getName(), channel.getProvider(), channel.getChannelRule(), channel.getApiBaseUrl(),
                mask(channel.getApiKey()), channel.getGroupNames(), channel.getRemark(), channel.getStatus(), channel.getPriority(), channel.getWeight(), channel.getRpmLimit(),
                channel.getTpmLimit(), channel.getMaxConcurrency(), channel.getPriceMultiplier(), channel.getEnabled(), channelModelMapper.modelsForChannel(channel.getId()));
    }

    private RelayPublicChannelDto toPublicChannelDto(RelayChannel channel, int position) {
        List<RelayPublicChannelModelDto> publicModels = channelModelMapper.modelsForChannel(channel.getId()).stream()
                .map(model -> {
                    String publicName = isBlank(model.getDisplayName()) ? model.getModel() : model.getDisplayName();
                    return new RelayPublicChannelModelDto(
                            model.getModelId(),
                            publicName,
                            publicName,
                            model.getModelType(),
                            model.getInputPrice(),
                            model.getOutputPrice(),
                            model.getCachedInputPrice(),
                            model.getCacheCreationPrice(),
                            model.getRequestPrice(),
                            model.getFixedRequestBilling(),
                            model.getLongContextThreshold(),
                            model.getLongContextBillingMode(),
                            model.getLongContextMultiplier(),
                            model.getLongContextInputPrice(),
                            model.getLongContextOutputPrice(),
                            model.getLongContextCachedInputPrice(),
                            model.getLongContextCacheCreationPrice(),
                            model.getEnabled()
                    );
                })
                .toList();
        String rule = normalizeChannelRule(channel.getChannelRule());
        return new RelayPublicChannelDto(
                channel.getId(),
                isBlank(channel.getName()) ? "服务节点 " + String.format("%02d", position) : channel.getName(),
                rule,
                channel.getGroupNames(),
                channel.getRemark(),
                channel.getStatus(),
                channel.getRpmLimit(),
                channel.getMaxConcurrency(),
                channel.getEnabled(),
                publicModels
        );
    }

    private RelayModelDto toModelDto(RelayModel model) {
        return new RelayModelDto(model.getId(), model.getModel(), model.getDisplayName(), model.getModelType(),
                model.getInputPrice(), model.getOutputPrice(), model.getCachedInputPrice(), model.getCacheCreationPrice(),
                model.getRequestPrice(), model.getFixedRequestBilling(), model.getLongContextThreshold(),
                model.getLongContextBillingMode(), model.getLongContextMultiplier(),
                model.getLongContextInputPrice(), model.getLongContextOutputPrice(), model.getLongContextCachedInputPrice(),
                model.getLongContextCacheCreationPrice(), model.getStatus(), model.getEnabled(), model.getSortOrder());
    }

    private RelayModelDto toPublicModelDto(RelayModel model) {
        String publicName = publicModelName(model);
        return new RelayModelDto(model.getId(), publicName, publicName, model.getModelType(),
                model.getInputPrice(), model.getOutputPrice(), model.getCachedInputPrice(), model.getCacheCreationPrice(),
                model.getRequestPrice(), model.getFixedRequestBilling(), model.getLongContextThreshold(),
                model.getLongContextBillingMode(), model.getLongContextMultiplier(),
                model.getLongContextInputPrice(), model.getLongContextOutputPrice(), model.getLongContextCachedInputPrice(),
                model.getLongContextCacheCreationPrice(), model.getStatus(), model.getEnabled(), model.getSortOrder());
    }

    private RelayGroupDto toGroupDto(RelayGroup group) {
        return new RelayGroupDto(group.getId(), group.getCode(), group.getName(), group.getRatio(), group.getEnabled(),
                groupModelMapper.modelIdsForGroup(group.getId()));
    }

    private RelayTokenDto toTokenDto(RelayToken item) {
        return toTokenDto(item, false);
    }

    private RelayTokenDto toTokenDto(RelayToken item, boolean revealToken) {
        User user = item.getUserId() == null ? null : userMapper.selectById(item.getUserId());
        return new RelayTokenDto(item.getId(), item.getUserId(), user == null ? "" : user.getUsername(),
                item.getName(), item.getTokenPreview(), revealToken ? item.getToken() : "", item.getGroupNames(), item.getAllowedModels(),
                item.getQuota(), item.getUsedQuota(), usageLogMapper.tokenTodayCost(item.getId()), item.getRequestCount(), item.getTokenCount(),
                item.getRpmLimit(), item.getTpmLimit(), item.getIpWhitelist(), item.getEnabled(), item.getExpiresAt(),
                item.getLastUsedAt(), item.getCreatedAt());
    }

    private RelayUsageLogDto toUsageDto(RelayUsageLog item, String publicModel) {
        return new RelayUsageLogDto(item.getId(), item.getTokenName(), item.getChannelName(), item.getGroupNames(),
                item.getEndpoint(), publicModel, item.getModelType(), item.getThinkingEffort(), item.getPromptTokens(),
                item.getCompletionTokens(), item.getCachedTokens(), item.getCacheCreationTokens(), item.getTotalTokens(),
                item.getCost(), item.getStatusCode(), item.getDurationMs(), item.getUserAgent(), item.getStatus(),
                publicUsageMessage(item.getStatusCode()), item.getCreatedAt());
    }

    private ErrorRequestLogDto toErrorRequestLogDto(RelayUsageLog item, String publicModel) {
        return new ErrorRequestLogDto(
                item.getId(),
                "relay",
                item.getTokenName(),
                item.getChannelName(),
                item.getGroupNames(),
                item.getEndpoint(),
                "",
                publicModel,
                item.getModelType(),
                item.getStatusCode(),
                item.getDurationMs(),
                item.getUserAgent(),
                item.getStatus(),
                publicErrorType(item.getStatusCode()),
                publicUsageMessage(item.getStatusCode()),
                "",
                item.getCreatedAt()
        );
    }

    private String publicErrorType(Integer statusCode) {
        int status = statusCode == null ? 0 : statusCode;
        if (status == 400 || status == 422) return "invalid_request";
        if (status == 401 || status == 403) return "authentication_error";
        if (status == 404) return "not_found";
        if (status == 408 || status == 504) return "timeout";
        if (status == 429) return "rate_limit";
        if (status >= 500) return "service_unavailable";
        return status >= 400 ? "request_failed" : "";
    }

    private String publicUsageMessage(Integer statusCode) {
        int status = statusCode == null ? 0 : statusCode;
        if (status >= 200 && status < 300) return "";
        if (status == 400 || status == 422) return "请求参数不符合要求";
        if (status == 401 || status == 403) return "身份验证失败";
        if (status == 404) return "请求的资源不可用";
        if (status == 408 || status == 504) return "请求处理超时，请稍后重试";
        if (status == 429) return "请求过于频繁，请稍后重试";
        if (status >= 500) return "服务暂时不可用，请稍后重试";
        return "请求处理失败";
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "Price cannot be negative");
        return value;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) return "";
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BusinessException(400, "API base URL must start with http:// or https://");
        }
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private String normalizeChannelRule(String value) {
        if (value == null || value.isBlank()) return "openai";
        String normalized = value.trim().toLowerCase();
        if ("anthrotic".equals(normalized)) return "anthropic";
        if ("claude".equals(normalized)) return "anthropic";
        if (!"openai".equals(normalized) && !"anthropic".equals(normalized)) {
            throw new BusinessException(400, "Unsupported channel rule: " + value);
        }
        return normalized;
    }

    private String relayUrl(String apiBaseUrl, String path) {
        String baseUrl = normalizeBaseUrl(apiBaseUrl);
        if (baseUrl.endsWith("/v1") && path.startsWith("/v1/")) {
            path = path.substring(3);
        }
        return baseUrl + path;
    }

    private void applyRelayAuthHeaders(HttpRequest.Builder builder, RelayChannel channel) {
        if (isAnthropicChannel(channel)) {
            builder.header("x-api-key", channel.getApiKey())
                    .header("anthropic-version", "2023-06-01");
            return;
        }
        builder.header("Authorization", "Bearer " + channel.getApiKey());
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

    private String createApiKeyValue() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "sk-ic-" + HexFormat.of().formatHex(bytes);
    }

    private String preview(String value) {
        if (value == null || value.length() <= 14) return "configured";
        return value.substring(0, 8) + "..." + value.substring(value.length() - 6);
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 10) return "configured";
        return value.substring(0, 6) + "..." + value.substring(value.length() - 4);
    }

    private String normalizeCsv(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return normalized.isBlank() ? fallback : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsCsv(String csv, String value) {
        if (value == null || value.isBlank()) return true;
        if (csv == null || csv.isBlank()) return true;
        return csvValues(csv).stream().anyMatch(item -> item.equalsIgnoreCase(value.trim()));
    }

    private List<String> csvValues(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
