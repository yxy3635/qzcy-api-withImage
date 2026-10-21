package com.qzcy.backend.service;

import com.qzcy.backend.dto.RelayReportDto.*;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RelayReportServiceTest {
    private RelayReportMapper mapper;
    private RelayReportCache cache;
    private RelayReportService service;
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final Instant now = Instant.parse("2026-10-13T02:00:00Z");

    @BeforeEach
    void setup() {
        mapper = mock(RelayReportMapper.class);
        cache = mock(RelayReportCache.class);
        var models = mock(RelayModelMapper.class);
        var groups = mock(RelayGroupMapper.class);
        var channels = mock(RelayChannelMapper.class);
        var providers = mock(RelayChannelProviderMapper.class);
        var model = new RelayModel(); model.setModel("active-model"); model.setDisplayName("当前模型");
        var zero = new RelayModel(); zero.setModel("zero-model");
        when(models.selectList(any())).thenReturn(List.of(model, zero));
        var group = new RelayGroup(); group.setCode("premium"); group.setName("高级分组");
        when(groups.selectList(any())).thenReturn(List.of(group));
        var channel = new RelayChannel(); channel.setId(1L); channel.setName("当前渠道名");
        when(channels.selectList(any())).thenReturn(List.of(channel));
        var provider = new RelayChannelProvider(); provider.setId(11L); provider.setChannelId(1L); provider.setName("同名供应商");
        var provider2 = new RelayChannelProvider(); provider2.setId(12L); provider2.setChannelId(1L); provider2.setName("同名供应商");
        when(providers.selectList(any())).thenReturn(List.of(provider, provider2));
        when(mapper.aggregates(any(), any())).thenReturn(List.of());
        when(mapper.hourly(any(), any())).thenReturn(List.of());
        when(mapper.daily(any(), any())).thenReturn(List.of());
        when(cache.acquire(anyString(), anyString())).thenReturn(RelayReportCache.LockState.ACQUIRED);
        service = new RelayReportService(mapper, models, groups, channels, providers, cache);
        setTime(now);
    }

    private void setTime(Instant time) { ReflectionTestUtils.setField(service, "clock", Clock.fixed(time, zone)); }
    private Aggregate aggregate(String model, String group, Long channel, Long provider, long requests, String cost) {
        var row = new Aggregate(); row.setModel(model); row.setGroupKey(group); row.setChannelId(channel); row.setProviderId(provider);
        row.setChannelName("历史渠道名"); row.setProviderName("历史供应商名");
        row.getToday().setRequests(requests); row.getToday().setTokens(requests * 100); row.getToday().setCost(new BigDecimal(cost));
        row.getTotal().add(row.getToday()); row.getTotal().setRequests(requests + 10);
        return row;
    }

    @Test
    void aggregatesAllDimensionsOnceWithoutDuplicatingGroupsOrLosingArchivedUsage() {
        when(mapper.aggregates(any(), any())).thenReturn(List.of(
                aggregate("active-model", "group:premium", 1L, 11L, 2, "0.000123"),
                aggregate("active-model", "legacy:default,premium", 1L, 12L, 1, "0.000456"),
                aggregate("deleted-model", "group:deleted", 99L, 91L, 1, "0.000789"),
                aggregate("active-model", "group:premium", 1L, null, 1, "0.000001")));
        var snapshot = service.report().snapshot();
        assertEquals(5, snapshot.today().getRequests());
        assertEquals(45, snapshot.total().getRequests());
        assertEquals(new BigDecimal("0.001369"), snapshot.today().getCost());
        snapshot.dimensions().values().forEach(rows -> {
            assertEquals(5, rows.stream().mapToLong(row -> row.today().getRequests()).sum());
            assertEquals(45, rows.stream().mapToLong(row -> row.total().getRequests()).sum());
            assertEquals(0, snapshot.today().getCost().compareTo(rows.stream().map(row -> row.today().getCost()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        });
        assertTrue(snapshot.dimensions().get("group").stream().anyMatch(Row::legacy));
        assertEquals(3, snapshot.dimensions().get("group").stream().filter(row -> row.key().equals("group:premium")).findFirst().orElseThrow().today().getRequests());
        assertEquals(3, snapshot.dimensions().get("model").size());
        assertEquals(4, snapshot.dimensions().get("provider").size());
        assertEquals(1, snapshot.dimensions().get("channel").stream().filter(row -> row.name().equals("当前渠道名")).count());
        verify(mapper).aggregates(LocalDateTime.of(2026, 10, 13, 0, 0), LocalDateTime.of(2026, 10, 13, 10, 0));
    }

    @Test
    void redisHitAvoidsAllDatabaseQueriesAndKeepsGenerationTimestamp() {
        var snapshot = new Snapshot(LocalDate.of(2026, 10, 13), zone.getId(), now.minusSeconds(600), now.plusSeconds(3000),
                new Metrics(), new Metrics(), Map.of(), List.of(), List.of());
        when(cache.get(anyString())).thenReturn(snapshot);
        var result = service.report();
        assertEquals("redis", result.cacheSource()); assertSame(snapshot, result.snapshot());
        assertEquals("local", service.report().cacheSource());
        verifyNoInteractions(mapper); verify(cache, never()).acquire(any(), any());
    }

    @Test
    void oneHourExpiryAndMidnightBothTriggerNewSnapshot() {
        var first = service.report().snapshot();
        assertEquals(Duration.ofHours(1), Duration.between(first.generatedAt(), first.expiresAt()));
        setTime(now.plusSeconds(3599)); service.report();
        verify(mapper, times(1)).aggregates(any(), any());
        setTime(now.plusSeconds(3600)); service.report();
        verify(mapper, times(2)).aggregates(any(), any());
        setTime(Instant.parse("2026-10-13T15:50:00Z")); service.report();
        setTime(Instant.parse("2026-10-13T16:00:00Z"));
        assertEquals(LocalDate.of(2026, 10, 14), service.report().snapshot().date());
        verify(cache).put(contains("2026-10-14"), any());
    }

    @Test
    void redisOutageStillCachesAndConcurrentMissesOnlyQueryOnce() throws Exception {
        when(cache.acquire(anyString(), anyString())).thenReturn(RelayReportCache.LockState.UNAVAILABLE);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Snapshot>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) tasks.add(() -> service.report().snapshot());
            for (Future<Snapshot> future : executor.invokeAll(tasks)) assertNotNull(future.get());
            verify(mapper, times(1)).aggregates(any(), any());
            verify(cache, never()).release(any(), any());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void peerGeneratingNeverStartsAnotherHistoricalScan() {
        when(cache.acquire(anyString(), anyString())).thenReturn(RelayReportCache.LockState.BUSY);
        var error = assertThrows(BusinessException.class, () -> service.report());
        assertEquals(503, error.getCode()); verifyNoInteractions(mapper);
    }

    @Test
    void failedQueriesReleaseLockAndAreNotCached() {
        when(mapper.aggregates(any(), any())).thenThrow(new RuntimeException("query failed"));
        assertThrows(RuntimeException.class, () -> service.report());
        verify(cache).release(any(), any()); verify(cache, never()).put(any(), any());
    }

    @Test
    void trendGapsAreZeroFilledAndUseSnapshotDay() {
        var hourly = new TrendAggregate(); hourly.setBucket("2026-10-13 09:00"); hourly.getMetrics().setRequests(5);
        when(mapper.hourly(any(), any())).thenReturn(List.of(hourly));
        var snapshot = service.report().snapshot();
        assertEquals(24, snapshot.hourly().size()); assertEquals(30, snapshot.daily().size());
        assertEquals(5, snapshot.hourly().get(9).metrics().getRequests());
        assertEquals(0, snapshot.hourly().get(8).metrics().getRequests());
        assertEquals("2026-09-14", snapshot.daily().get(0).bucket());
        assertEquals("2026-10-13", snapshot.daily().get(29).bucket());
    }
}
