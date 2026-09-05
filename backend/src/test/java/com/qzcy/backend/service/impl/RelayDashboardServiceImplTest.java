package com.qzcy.backend.service.impl;

import com.qzcy.backend.dto.RelayDashboardChannelDto;
import com.qzcy.backend.dto.RelayDashboardChannelStatsDto;
import com.qzcy.backend.dto.RelayDashboardDto;
import com.qzcy.backend.dto.RelayDashboardLastErrorDto;
import com.qzcy.backend.dto.RelayDashboardSummaryDto;
import com.qzcy.backend.dto.RelayDashboardTrendPointDto;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.service.RelayDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelayDashboardServiceImplTest {
    private static final DateTimeFormatter HOUR_KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");

    private RelayChannelMapper channelMapper;
    private RelayChannelProviderMapper providerMapper;
    private RelayUsageLogMapper usageLogMapper;
    private RelayDispatchService dispatchService;
    private RelayDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        channelMapper = mock(RelayChannelMapper.class);
        providerMapper = mock(RelayChannelProviderMapper.class);
        usageLogMapper = mock(RelayUsageLogMapper.class);
        dispatchService = mock(RelayDispatchService.class);
        service = new RelayDashboardServiceImpl(channelMapper, providerMapper, usageLogMapper, dispatchService);
    }

    private RelayChannel channel(long id, String name, boolean enabled) {
        RelayChannel item = new RelayChannel();
        item.setId(id);
        item.setName(name);
        item.setEnabled(enabled);
        item.setPriority(10);
        item.setWeight(10);
        item.setScheduleStrategy("weighted_random");
        item.setGroupNames("default");
        item.setStatus("available");
        return item;
    }

    private RelayChannelProvider provider(long id, long channelId, boolean enabled, String status) {
        RelayChannelProvider item = new RelayChannelProvider();
        item.setId(id);
        item.setChannelId(channelId);
        item.setName("p" + id);
        item.setChannelRule("openai");
        item.setEnabled(enabled);
        item.setStatus(status);
        return item;
    }

    private void stubEmptyAggregates() {
        when(usageLogMapper.dashboardChannelStats(any(LocalDateTime.class))).thenReturn(List.of());
        when(usageLogMapper.dashboardLastErrors(any(LocalDateTime.class))).thenReturn(List.of());
        when(usageLogMapper.dashboardHourlyTrend(any(LocalDateTime.class))).thenReturn(List.of());
        when(usageLogMapper.dashboardRecentErrors(any(long.class))).thenReturn(List.of());
        when(usageLogMapper.dashboardModelTop(any(long.class))).thenReturn(List.of());
        when(usageLogMapper.todayRequests()).thenReturn(0L);
        when(usageLogMapper.todayErrors()).thenReturn(0L);
        when(usageLogMapper.todayTokens()).thenReturn(0L);
        when(usageLogMapper.todayCost()).thenReturn(BigDecimal.ZERO);
        when(usageLogMapper.requestsSince(any(LocalDateTime.class))).thenReturn(0L);
        when(dispatchService.openCircuitScopes()).thenReturn(Set.of());
    }

    @Test
    void healthCoversOkDegradedDownAndDisabled() {
        stubEmptyAggregates();
        RelayChannel okChannel = channel(1, "ok", true);
        RelayChannel degradedChannel = channel(2, "degraded", true);
        RelayChannel downChannel = channel(3, "down", true);
        RelayChannel disabledChannel = channel(4, "disabled", false);
        RelayChannel noProviderChannel = channel(5, "no-provider", true);
        when(channelMapper.selectList(any())).thenReturn(List.of(okChannel, degradedChannel, downChannel, disabledChannel, noProviderChannel));
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(
                provider(11, 1, true, "available"),
                provider(12, 1, true, "failed")));
        when(providerMapper.selectByChannelId(2L)).thenReturn(List.of(provider(21, 2, true, "unknown")));
        when(providerMapper.selectByChannelId(3L)).thenReturn(List.of(provider(31, 3, true, "failed")));
        when(providerMapper.selectByChannelId(4L)).thenReturn(List.of(provider(41, 4, true, "available")));
        when(providerMapper.selectByChannelId(5L)).thenReturn(List.of());

        RelayDashboardDto result = service.dashboard();

        List<String> healths = result.getChannels().stream().map(RelayDashboardChannelDto::getHealth).toList();
        assertEquals(List.of("ok", "degraded", "down", "disabled", "down"), healths);
        // ok 渠道的失败供应商不计入可用数；停用渠道(ch4)的供应商整体不参与统计。
        assertEquals(1, result.getSummary().getProvidersAvailable());
        assertEquals(4, result.getSummary().getProvidersTotal());
        assertEquals(2, result.getSummary().getChannelsAvailable());
    }

    @Test
    void circuitOpenMarksProviderAndDisabledProvidersAreExcluded() {
        stubEmptyAggregates();
        RelayChannel okChannel = channel(1, "main", true);
        when(channelMapper.selectList(any())).thenReturn(List.of(okChannel));
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(
                provider(11, 1, true, "available"),
                provider(12, 1, false, "available")));
        when(dispatchService.openCircuitScopes()).thenReturn(Set.of("1:11"));

        RelayDashboardDto result = service.dashboard();

        assertTrue(result.getChannels().get(0).getProviders().get(0).getCircuitOpen());
        assertTrue(!result.getChannels().get(0).getProviders().get(1).getCircuitOpen());
        assertEquals(1, result.getSummary().getProvidersTotal());
        assertEquals(1, result.getSummary().getProvidersAvailable());
    }

    @Test
    void errorRateAndTodayTotalsUseFailedDefinition() {
        stubEmptyAggregates();
        when(channelMapper.selectList(any())).thenReturn(List.of());
        when(usageLogMapper.todayRequests()).thenReturn(10L);
        when(usageLogMapper.todayErrors()).thenReturn(2L);
        when(usageLogMapper.todayTokens()).thenReturn(1234L);
        when(usageLogMapper.todayCost()).thenReturn(new BigDecimal("1.234"));
        when(usageLogMapper.requestsSince(any(LocalDateTime.class))).thenReturn(7L);

        RelayDashboardSummaryDto summary = service.dashboard().getSummary();

        assertEquals(10L, summary.getTodayRequests());
        assertEquals(2L, summary.getTodayErrors());
        assertEquals(20.0, summary.getErrorRate());
        assertEquals(7L, summary.getCurrentRpm());
        assertEquals(0, summary.getTodayCost().compareTo(new BigDecimal("1.234")));
    }

    @Test
    void trendFillsEmptyHoursAndMergesQueriedRows() {
        stubEmptyAggregates();
        when(channelMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2).withMinute(0).withSecond(0).withNano(0);
        RelayDashboardTrendPointDto hit = new RelayDashboardTrendPointDto(
                HOUR_KEY.format(twoHoursAgo) + ":00:00", 5L, 1L, 500L, new BigDecimal("0.5"));
        when(usageLogMapper.dashboardHourlyTrend(any(LocalDateTime.class))).thenReturn(List.of(hit));

        List<RelayDashboardTrendPointDto> trend = service.dashboard().getTrend();

        assertEquals(24, trend.size());
        RelayDashboardTrendPointDto merged = trend.stream()
                .filter(point -> point.getHour().startsWith(HOUR_KEY.format(twoHoursAgo)))
                .findFirst()
                .orElseThrow();
        assertEquals(5L, merged.getRequests());
        assertEquals(1L, merged.getErrors());
        // 其余小时应为补零桶
        assertEquals(23, trend.stream().filter(point -> point.getRequests() == 0L).count());
        assertEquals(HOUR_KEY.format(twoHoursAgo) + ":00", merged.getHour());
    }

    @Test
    void channelStatsAndLastErrorAttachToChannels() {
        stubEmptyAggregates();
        RelayChannel main = channel(1, "main", true);
        when(channelMapper.selectList(any())).thenReturn(List.of(main));
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(provider(11, 1, true, "available")));
        RelayDashboardChannelStatsDto stats = new RelayDashboardChannelStatsDto();
        stats.setChannelId(1L);
        stats.setRequests(100L);
        stats.setErrors(3L);
        stats.setAvgDurationMs(1200L);
        stats.setAvgFirstTokenMs(800L);
        stats.setTotalTokens(20000L);
        stats.setCost(new BigDecimal("2.5"));
        when(usageLogMapper.dashboardChannelStats(any(LocalDateTime.class))).thenReturn(List.of(stats));
        RelayDashboardLastErrorDto lastError = new RelayDashboardLastErrorDto();
        lastError.setChannelId(1L);
        lastError.setLastErrorAt(LocalDateTime.of(2026, 9, 5, 12, 0));
        lastError.setLastErrorCode(502);
        when(usageLogMapper.dashboardLastErrors(any(LocalDateTime.class))).thenReturn(List.of(lastError));

        RelayDashboardChannelDto channelDto = service.dashboard().getChannels().get(0);

        assertEquals(100L, channelDto.getRequests24h());
        assertEquals(3L, channelDto.getErrors24h());
        assertEquals(1200L, channelDto.getAvgDurationMs());
        assertEquals(800L, channelDto.getAvgFirstTokenMs());
        assertEquals(20000L, channelDto.getTokens24h());
        assertEquals(502, channelDto.getLastErrorCode());
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 0), channelDto.getLastErrorAt());
    }
}
