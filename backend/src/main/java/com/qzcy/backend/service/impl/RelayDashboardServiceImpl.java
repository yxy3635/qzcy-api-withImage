package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qzcy.backend.dto.RelayDashboardChannelDto;
import com.qzcy.backend.dto.RelayDashboardChannelStatsDto;
import com.qzcy.backend.dto.RelayDashboardDto;
import com.qzcy.backend.dto.RelayDashboardErrorDto;
import com.qzcy.backend.dto.RelayDashboardLastErrorDto;
import com.qzcy.backend.dto.RelayDashboardProviderDto;
import com.qzcy.backend.dto.RelayDashboardSummaryDto;
import com.qzcy.backend.dto.RelayDashboardTrendPointDto;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.service.RelayDashboardService;
import com.qzcy.backend.service.RelayDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端仪表盘聚合：渠道/供应商健康、今日总量、24h 趋势、最近错误与模型热度。
 * 渠道健康判定：
 * <ul>
 *   <li>ok —— 渠道启用且至少一个启用供应商探活可用</li>
 *   <li>degraded —— 启用供应商均未探活成功但存在 unknown（尚未探测）</li>
 *   <li>down —— 渠道启用但启用供应商全部失败或缺失</li>
 *   <li>disabled —— 渠道停用</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelayDashboardServiceImpl implements RelayDashboardService {
    static final String HEALTH_OK = "ok";
    static final String HEALTH_DEGRADED = "degraded";
    static final String HEALTH_DOWN = "down";
    static final String HEALTH_DISABLED = "disabled";

    private static final int RECENT_ERROR_LIMIT = 20;
    private static final int TOP_MODEL_LIMIT = 8;
    private static final int TREND_HOURS = 24;
    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    private final RelayChannelMapper channelMapper;
    private final RelayChannelProviderMapper channelProviderMapper;
    private final RelayUsageLogMapper usageLogMapper;
    private final RelayDispatchService relayDispatchService;

    @Override
    public RelayDashboardDto dashboard() {
        LocalDateTime since24h = LocalDateTime.now().minusHours(TREND_HOURS);
        List<RelayChannel> channels = channelMapper.selectList(new QueryWrapper<RelayChannel>()
                .orderByAsc("priority")
                .orderByDesc("weight")
                .orderByDesc("id"));
        Map<Long, RelayDashboardChannelStatsDto> statsByChannel = usageLogMapper.dashboardChannelStats(since24h)
                .stream()
                .collect(Collectors.toMap(RelayDashboardChannelStatsDto::getChannelId, Function.identity(), (first, ignored) -> first));
        Map<Long, RelayDashboardLastErrorDto> lastErrorByChannel = new HashMap<>();
        for (RelayDashboardLastErrorDto item : usageLogMapper.dashboardLastErrors(since24h)) {
            // 同一秒可能存在并列行，取查询返回的第一条（即最新）。
            lastErrorByChannel.putIfAbsent(item.getChannelId(), item);
        }
        Set<String> openCircuits = relayDispatchService.openCircuitScopes();

        int channelsAvailable = 0;
        int providersTotal = 0;
        int providersAvailable = 0;
        List<RelayDashboardChannelDto> channelDtos = new ArrayList<>(channels.size());
        for (RelayChannel channel : channels) {
            List<RelayChannelProvider> providers = channelProviderMapper.selectByChannelId(channel.getId());
            List<RelayDashboardProviderDto> providerDtos = new ArrayList<>(providers.size());
            boolean channelEnabled = Boolean.TRUE.equals(channel.getEnabled());
            boolean anyEnabledProvider = false;
            boolean anyAvailable = false;
            boolean anyUnknown = false;
            for (RelayChannelProvider provider : providers) {
                // 停用渠道下的供应商无法承载流量，不参与可用性统计（卡片上仍展示、灰显）。
                boolean enabled = channelEnabled && !Boolean.FALSE.equals(provider.getEnabled());
                if (enabled) {
                    anyEnabledProvider = true;
                    providersTotal++;
                    String status = provider.getStatus() == null ? "unknown" : provider.getStatus();
                    if ("available".equalsIgnoreCase(status)) {
                        anyAvailable = true;
                        providersAvailable++;
                    } else if ("unknown".equalsIgnoreCase(status)) {
                        anyUnknown = true;
                    }
                }
                boolean circuitOpen = enabled && openCircuits.contains(circuitScope(channel.getId(), provider.getId()));
                providerDtos.add(new RelayDashboardProviderDto(
                        provider.getId(), provider.getName(), provider.getChannelRule(),
                        provider.getStatus(), provider.getEnabled(), circuitOpen));
            }
            String health = healthOf(channel, anyEnabledProvider, anyAvailable, anyUnknown);
            if (HEALTH_OK.equals(health) || HEALTH_DEGRADED.equals(health)) {
                channelsAvailable++;
            }

            RelayDashboardChannelStatsDto stats = statsByChannel.get(channel.getId());
            RelayDashboardLastErrorDto lastError = lastErrorByChannel.get(channel.getId());
            channelDtos.add(new RelayDashboardChannelDto(
                    channel.getId(),
                    channel.getName(),
                    channel.getStatus(),
                    channel.getEnabled(),
                    channel.getPriority(),
                    channel.getWeight(),
                    channel.getScheduleStrategy(),
                    channel.getGroupNames(),
                    health,
                    providerDtos,
                    stats == null ? 0L : nullToZero(stats.getRequests()),
                    stats == null ? 0L : nullToZero(stats.getErrors()),
                    stats == null ? 0L : nullToZero(stats.getAvgDurationMs()),
                    stats == null ? 0L : nullToZero(stats.getAvgFirstTokenMs()),
                    stats == null ? 0L : nullToZero(stats.getTotalTokens()),
                    stats == null || stats.getCost() == null ? BigDecimal.ZERO : stats.getCost(),
                    lastError == null ? null : lastError.getLastErrorAt(),
                    lastError == null ? null : lastError.getLastErrorCode()
            ));
        }

        long todayRequests = nullToZero(usageLogMapper.todayRequests());
        long todayErrors = nullToZero(usageLogMapper.todayErrors());
        double errorRate = todayRequests == 0 ? 0.0 : Math.round(todayErrors * 1000.0 / todayRequests) / 10.0;
        RelayDashboardSummaryDto summary = new RelayDashboardSummaryDto(
                channels.size(),
                channelsAvailable,
                providersTotal,
                providersAvailable,
                todayRequests,
                todayErrors,
                errorRate,
                nullToZero(usageLogMapper.todayTokens()),
                usageLogMapper.todayCost() == null ? BigDecimal.ZERO : usageLogMapper.todayCost(),
                nullToZero(usageLogMapper.requestsSince(LocalDateTime.now().minusMinutes(1)))
        );

        return new RelayDashboardDto(
                summary,
                buildTrend(usageLogMapper.dashboardHourlyTrend(since24h)),
                channelDtos,
                usageLogMapper.dashboardRecentErrors(RECENT_ERROR_LIMIT),
                usageLogMapper.dashboardModelTop(TOP_MODEL_LIMIT)
        );
    }

    private String healthOf(RelayChannel channel, boolean anyEnabledProvider, boolean anyAvailable, boolean anyUnknown) {
        if (!Boolean.TRUE.equals(channel.getEnabled())) {
            return HEALTH_DISABLED;
        }
        if (!anyEnabledProvider) {
            return HEALTH_DOWN;
        }
        if (anyAvailable) {
            return HEALTH_OK;
        }
        return anyUnknown ? HEALTH_DEGRADED : HEALTH_DOWN;
    }

    private String circuitScope(Long channelId, Long providerId) {
        return providerId == null ? String.valueOf(channelId) : channelId + ":" + providerId;
    }

    /** 补齐 24 个连续小时桶（无流量的小时补零），按时间升序返回。 */
    private List<RelayDashboardTrendPointDto> buildTrend(List<RelayDashboardTrendPointDto> rows) {
        Map<String, RelayDashboardTrendPointDto> byHour = new HashMap<>();
        for (RelayDashboardTrendPointDto row : rows == null ? List.<RelayDashboardTrendPointDto>of() : rows) {
            if (row.getHour() != null && row.getHour().length() >= 13) {
                byHour.put(row.getHour().substring(0, 13), row);
            }
        }
        List<RelayDashboardTrendPointDto> trend = new ArrayList<>(TREND_HOURS);
        LocalDateTime cursor = LocalDateTime.now().minusHours(TREND_HOURS - 1).withMinute(0).withSecond(0).withNano(0);
        for (int index = 0; index < TREND_HOURS; index++) {
            RelayDashboardTrendPointDto row = byHour.get(HOUR_KEY.format(cursor));
            if (row == null) {
                row = new RelayDashboardTrendPointDto(HOUR_KEY.format(cursor) + ":00", 0L, 0L, 0L, BigDecimal.ZERO);
            } else {
                row.setHour(HOUR_KEY.format(cursor) + ":00");
                row.setRequests(nullToZero(row.getRequests()));
                row.setErrors(nullToZero(row.getErrors()));
                row.setTotalTokens(nullToZero(row.getTotalTokens()));
                if (row.getCost() == null) row.setCost(BigDecimal.ZERO);
            }
            trend.add(row);
            cursor = cursor.plusHours(1);
        }
        return trend;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
