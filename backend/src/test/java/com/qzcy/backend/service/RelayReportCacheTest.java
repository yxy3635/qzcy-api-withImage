package com.qzcy.backend.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qzcy.backend.dto.RelayReportDto.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RelayReportCacheTest {
    @Test
    @SuppressWarnings("unchecked")
    void redisRoundTripPreservesMoneyAndUsesExactlyOneHour() {
        var redis = mock(StringRedisTemplate.class);
        var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        var cache = new RelayReportCache(redis, mapper);
        var metrics = new Metrics(); metrics.setCost(new BigDecimal("0.000123"));
        var now = Instant.parse("2026-10-13T02:00:00Z");
        var snapshot = new Snapshot(LocalDate.of(2026, 10, 13), "Asia/Shanghai", now, now.plusSeconds(3600), metrics, metrics,
                Map.of("model", List.of(new Row("m", "模型", "m", false, metrics, metrics))), List.of(), List.of());
        cache.put("report", snapshot);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("report"), payload.capture(), eq(Duration.ofHours(1)));
        when(values.get("report")).thenReturn(payload.getValue());
        var read = cache.get("report"); assertNotNull(read);
        assertEquals(new BigDecimal("0.000123"), read.today().getCost());
        assertEquals(snapshot.generatedAt(), read.generatedAt());
        assertEquals(1, read.dimensions().get("model").size());
        when(values.setIfAbsent(eq("report:lock"), eq("owner"), any(Duration.class))).thenReturn(true, false);
        assertEquals(RelayReportCache.LockState.ACQUIRED, cache.acquire("report", "owner"));
        assertEquals(RelayReportCache.LockState.BUSY, cache.acquire("report", "owner"));
        when(values.get("report")).thenReturn("invalid-json");
        assertNull(cache.get("report"));
    }

    @Test
    void redisFailureIsSafeForReadWriteAndLock() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("offline"));
        var cache = new RelayReportCache(redis, JsonMapper.builder().addModule(new JavaTimeModule()).build());
        assertNull(cache.get("report"));
        assertEquals(RelayReportCache.LockState.UNAVAILABLE, cache.acquire("report", "owner"));
        assertDoesNotThrow(() -> cache.put("report", null));
    }
}
