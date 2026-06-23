package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.qzcy.backend.dto.RelayModelUpdateDto;
import com.qzcy.backend.dto.RelayModelUsageDto;
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
import com.qzcy.backend.mapper.ImageRecordMapper;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;

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
    private final ImageRecordMapper imageRecordMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
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
        if (isBlank(channel.getGroupNames())) channel.setGroupNames("default");
        if (channel.getStatus() == null) channel.setStatus("unknown");
        if (channel.getPriority() == null) channel.setPriority(10);
        if (channel.getWeight() == null) channel.setWeight(10);
        if (channel.getRpmLimit() == null) channel.setRpmLimit(0);
        if (channel.getTpmLimit() == null) channel.setTpmLimit(0);
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
        boolean duplicateModelName = dto.getModel() != null && modelMapper.selectCount(new QueryWrapper<RelayModel>()
                .eq("model", dto.getModel().trim())) > 0;
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
        if (isBlank(model.getStatus())) model.setStatus("available");
        if (model.getEnabled() == null) model.setEnabled(true);
        if (model.getSortOrder() == null) model.setSortOrder(10);
        modelMapper.insert(model);
        if (!duplicateModelName) {
            attachModelToAllGroups(model.getId());
            attachModelToAllChannels(model.getId(), model.getModel());
        }
        return toModelDto(modelMapper.selectById(model.getId()));
    }

    @Override
    public RelayModelDto updateModel(Long id, RelayModelUpdateDto dto) {
        RelayModel model = modelMapper.selectById(id);
        if (model == null) throw new BusinessException(404, "Relay model not found");
        String oldModelName = model.getModel();
        apply(model, dto);
        if (isBlank(model.getModel())) throw new BusinessException(400, "Model is required");
        if (isBlank(model.getDisplayName())) model.setDisplayName(model.getModel());
        if (isBlank(model.getModelType())) model.setModelType("chat");
        if (model.getCachedInputPrice() == null) model.setCachedInputPrice(BigDecimal.ZERO);
        if (model.getCacheCreationPrice() == null) model.setCacheCreationPrice(BigDecimal.ZERO);
        if (model.getFixedRequestBilling() == null) model.setFixedRequestBilling(false);
        if (isBlank(model.getStatus())) model.setStatus("available");
        modelMapper.updateById(model);
        syncDefaultChannelUpstreamModelName(model.getId(), oldModelName, model.getModel());
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
            HttpRequest request = HttpRequest.newBuilder(URI.create(relayUrl(channel.getApiBaseUrl(), "/v1/models")))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + channel.getApiKey())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
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
                    .map(RelayChannelModelDto::getUpstreamModel)
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
        item.setQuota(dto.getQuota() == null ? BigDecimal.ZERO : dto.getQuota());
        item.setUsedQuota(BigDecimal.ZERO);
        item.setRequestCount(0L);
        item.setTokenCount(0L);
        item.setRpmLimit(dto.getRpmLimit() == null ? 0 : Math.max(0, dto.getRpmLimit()));
        item.setTpmLimit(dto.getTpmLimit() == null ? 0 : Math.max(0, dto.getTpmLimit()));
        item.setIpWhitelist(dto.getIpWhitelist() == null ? "" : dto.getIpWhitelist().trim());
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
        if (dto.getName() != null) item.setName(dto.getName().trim());
        if (dto.getGroups() != null) item.setGroupNames(dto.getGroups().trim());
        if (dto.getAllowedModels() != null) item.setAllowedModels(dto.getAllowedModels().trim());
        if (dto.getQuota() != null) item.setQuota(nonNegative(dto.getQuota()));
        if (dto.getRpmLimit() != null) item.setRpmLimit(Math.max(0, dto.getRpmLimit()));
        if (dto.getTpmLimit() != null) item.setTpmLimit(Math.max(0, dto.getTpmLimit()));
        if (dto.getIpWhitelist() != null) item.setIpWhitelist(dto.getIpWhitelist().trim());
        if (dto.getEnabled() != null) item.setEnabled(dto.getEnabled());
        tokenMapper.updateById(item);
        return toTokenDto(tokenMapper.selectById(tokenId));
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

        List<String> accessibleModels = accessibleModelsForTokenGroups(userId);
        List<RelayModelDto> models = modelMapper.selectList(new QueryWrapper<RelayModel>()
                        .eq("enabled", true)
                        .orderByAsc("sort_order")
                        .orderByAsc("id"))
                .stream()
                .filter(model -> accessibleModels.contains(model.getModel()))
                .filter(distinctByModelName())
                .map(this::toModelDto).toList();
        List<RelayTokenDto> tokens = tokenMapper.selectList(new QueryWrapper<RelayToken>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at"))
                .stream().map(item -> toTokenDto(item, true)).toList();
        List<RelayChannelDto> channels = channelMapper.selectList(new QueryWrapper<RelayChannel>()
                        .eq("enabled", true)
                        .orderByAsc("priority")
                        .orderByDesc("weight"))
                .stream().map(this::toChannelDto).toList();
        List<RelayUsageLogDto> logs = usageLogMapper.selectPage(
                        Page.of(1, 20),
                        new QueryWrapper<RelayUsageLog>()
                                .eq("user_id", userId)
                                .orderByDesc("created_at"))
                .getRecords().stream().map(this::toUsageDto).toList();
        List<ErrorRequestLogDto> relayErrorLogs = usageLogMapper.selectPage(
                        Page.of(1, 50),
                        new QueryWrapper<RelayUsageLog>()
                                .eq("user_id", userId)
                                .and(wrapper -> wrapper
                                        .eq("status", "failed")
                                        .or()
                                        .notBetween("status_code", 200, 299))
                                .orderByDesc("created_at"))
                .getRecords().stream().map(this::toErrorRequestLogDto).toList();
        List<ErrorRequestLogDto> imageErrorLogs = imageRecordMapper.imageErrorLogs(userId, 50);
        List<ErrorRequestLogDto> errorLogs = java.util.stream.Stream.concat(relayErrorLogs.stream(), imageErrorLogs.stream())
                .sorted(Comparator.comparing(ErrorRequestLogDto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(50)
                .toList();
        List<RelayModelUsageDto> modelUsage = usageLogMapper.modelUsage(userId);
        List<RelayGroupDto> groups = groupMapper.selectList(new QueryWrapper<RelayGroup>()
                        .eq("enabled", true)
                        .orderByAsc("id"))
                .stream().map(this::toGroupDto).toList();

        long totalRequests = tokens.stream().mapToLong(item -> item.getRequestCount() == null ? 0L : item.getRequestCount()).sum();
        long totalTokens = tokens.stream().mapToLong(item -> item.getTokenCount() == null ? 0L : item.getTokenCount()).sum();
        BigDecimal totalCost = tokens.stream()
                .map(item -> item.getUsedQuota() == null ? BigDecimal.ZERO : item.getUsedQuota())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime minuteStart = LocalDateTime.now().minusMinutes(1);

        return new RelayUserOverviewDto(
                user.getBalance(), models, tokens, channels, logs, errorLogs, modelUsage, usageLogMapper.userTrend(userId), groups,
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

    private void apply(RelayChannel channel, RelayChannelUpdateDto dto) {
        if (dto.getName() != null) channel.setName(dto.getName().trim());
        if (dto.getProvider() != null) channel.setProvider(dto.getProvider().trim());
        if (dto.getApiBaseUrl() != null) channel.setApiBaseUrl(normalizeBaseUrl(dto.getApiBaseUrl()));
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) channel.setApiKey(dto.getApiKey().trim());
        if (dto.getGroupNames() != null) channel.setGroupNames(normalizeCsv(dto.getGroupNames(), "default"));
        if (dto.getRemark() != null) channel.setRemark(dto.getRemark().trim());
        if (dto.getPriority() != null) channel.setPriority(Math.max(0, dto.getPriority()));
        if (dto.getWeight() != null) channel.setWeight(Math.max(0, dto.getWeight()));
        if (dto.getRpmLimit() != null) channel.setRpmLimit(Math.max(0, dto.getRpmLimit()));
        if (dto.getTpmLimit() != null) channel.setTpmLimit(Math.max(0, dto.getTpmLimit()));
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
        if (dto.getStatus() != null) model.setStatus(dto.getStatus().trim());
        if (dto.getEnabled() != null) model.setEnabled(dto.getEnabled());
        if (dto.getSortOrder() != null) model.setSortOrder(dto.getSortOrder());
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

    private void attachModelToDefaultGroup(Long modelId) {
        RelayGroup defaultGroup = groupMapper.selectOne(new QueryWrapper<RelayGroup>().eq("code", "default"));
        if (defaultGroup == null) return;
        attachModelToGroup(defaultGroup.getId(), modelId);
    }

    private void attachModelToAllGroups(Long modelId) {
        groupMapper.selectList(new QueryWrapper<RelayGroup>())
                .forEach(group -> attachModelToGroup(group.getId(), modelId));
    }

    private void attachModelToAllChannels(Long modelId, String upstreamModel) {
        channelMapper.selectList(new QueryWrapper<RelayChannel>())
                .forEach(channel -> attachModelToChannel(channel.getId(), modelId, upstreamModel, true));
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
                    String upstreamModel = isBlank(item.getUpstreamModel()) ? model.getModel() : item.getUpstreamModel().trim();
                    attachModelToChannel(channelId, model.getId(), upstreamModel, item.getEnabled() == null || item.getEnabled());
                });
    }

    private void attachModelToChannel(Long channelId, Long modelId, String upstreamModel, boolean enabled) {
        if (channelId == null || modelId == null) return;
        RelayChannelModel existing = channelModelMapper.selectByChannelAndModel(channelId, modelId);
        if (existing != null) {
            existing.setUpstreamModel(isBlank(upstreamModel) ? existing.getUpstreamModel() : upstreamModel.trim());
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

    private void syncDefaultChannelUpstreamModelName(Long modelId, String oldModelName, String newModelName) {
        if (modelId == null || isBlank(newModelName) || oldModelName == null || oldModelName.equals(newModelName)) {
            return;
        }
        RelayChannelModel update = new RelayChannelModel();
        update.setUpstreamModel(newModelName.trim());
        channelModelMapper.update(update, new UpdateWrapper<RelayChannelModel>()
                .eq("model_id", modelId)
                .and(wrapper -> wrapper
                        .eq("upstream_model", oldModelName)
                        .or()
                        .isNull("upstream_model")
                        .or()
                        .eq("upstream_model", "")));
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
        return model -> seen.add(model.getModel());
    }

    private RelayChannelDto toChannelDto(RelayChannel channel) {
        return new RelayChannelDto(channel.getId(), channel.getName(), channel.getProvider(), channel.getApiBaseUrl(),
                mask(channel.getApiKey()), channel.getGroupNames(), channel.getRemark(), channel.getStatus(), channel.getPriority(), channel.getWeight(), channel.getRpmLimit(),
                channel.getTpmLimit(), channel.getPriceMultiplier(), channel.getEnabled(), channelModelMapper.modelsForChannel(channel.getId()));
    }

    private RelayModelDto toModelDto(RelayModel model) {
        return new RelayModelDto(model.getId(), model.getModel(), model.getDisplayName(), model.getModelType(),
                model.getInputPrice(), model.getOutputPrice(), model.getCachedInputPrice(), model.getCacheCreationPrice(),
                model.getRequestPrice(), model.getFixedRequestBilling(), model.getStatus(), model.getEnabled(), model.getSortOrder());
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

    private RelayUsageLogDto toUsageDto(RelayUsageLog item) {
        return new RelayUsageLogDto(item.getId(), item.getTokenName(), item.getChannelName(), item.getGroupNames(),
                item.getEndpoint(), item.getModel(), item.getModelType(), item.getPromptTokens(),
                item.getCompletionTokens(), item.getCachedTokens(), item.getCacheCreationTokens(), item.getTotalTokens(),
                item.getInputCost(), item.getOutputCost(), item.getCacheReadCost(), item.getCacheCreationCost(),
                item.getRequestCost(), item.getGroupRatio(), item.getChannelRatio(), item.getCost(), item.getStatusCode(),
                item.getDurationMs(), item.getUserAgent(), item.getStatus(), item.getMessage(), item.getCreatedAt());
    }

    private ErrorRequestLogDto toErrorRequestLogDto(RelayUsageLog item) {
        return new ErrorRequestLogDto(
                item.getId(),
                "relay",
                item.getTokenName(),
                item.getChannelName(),
                item.getGroupNames(),
                item.getEndpoint(),
                "",
                item.getModel(),
                item.getModelType(),
                item.getStatusCode(),
                item.getDurationMs(),
                item.getUserAgent(),
                item.getStatus(),
                errorTypeFromMessage(item.getMessage()),
                item.getMessage(),
                "",
                item.getCreatedAt()
        );
    }

    private String errorTypeFromMessage(String message) {
        if (message == null || message.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String type = error.path("type").asText("");
                if (!type.isBlank()) return type;
                String code = error.path("code").asText("");
                if (!code.isBlank()) return code;
            }
        } catch (Exception ignored) {
            if (message.toLowerCase().contains("<html")) return "html_error";
        }
        return "";
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

    private String relayUrl(String apiBaseUrl, String path) {
        String baseUrl = normalizeBaseUrl(apiBaseUrl);
        if (baseUrl.endsWith("/v1") && path.startsWith("/v1/")) {
            path = path.substring(3);
        }
        return baseUrl + path;
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
