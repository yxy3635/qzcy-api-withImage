package com.qzcy.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayModelRecentCallDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Optional Redis cache for the global model-status activity bars only.
 * Dashboard balances and usage totals intentionally bypass this cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelayModelStatusCache {
    private static final String RECENT_CALLS_KEY = "imagecreater:relay:model-status:recent-calls:v1";
    private static final TypeReference<List<RelayModelRecentCallDto>> RECENT_CALLS_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.relay.model-status-cache.redis-enabled:false}")
    private boolean redisEnabled;

    @Value("${app.relay.model-status-cache.ttl-seconds:30}")
    private long ttlSeconds;

    public List<RelayModelRecentCallDto> getRecentCalls() {
        if (!redisEnabled) return null;
        try {
            String snapshot = redisTemplate.opsForValue().get(RECENT_CALLS_KEY);
            return snapshot == null || snapshot.isBlank() ? null : objectMapper.readValue(snapshot, RECENT_CALLS_TYPE);
        } catch (Exception ex) {
            log.warn("Relay model-status Redis read failed; querying database instead: {}", ex.getMessage());
            return null;
        }
    }

    public void putRecentCalls(List<RelayModelRecentCallDto> recentCalls) {
        if (!redisEnabled || recentCalls == null) return;
        try {
            redisTemplate.opsForValue().set(
                    RECENT_CALLS_KEY,
                    objectMapper.writeValueAsString(recentCalls),
                    Duration.ofSeconds(Math.max(5, ttlSeconds))
            );
        } catch (Exception ex) {
            log.warn("Relay model-status Redis write failed; continuing without cache: {}", ex.getMessage());
        }
    }

    public void evictRecentCalls() {
        if (!redisEnabled) return;
        try {
            redisTemplate.delete(RECENT_CALLS_KEY);
        } catch (Exception ex) {
            log.warn("Relay model-status Redis eviction failed: {}", ex.getMessage());
        }
    }
}
