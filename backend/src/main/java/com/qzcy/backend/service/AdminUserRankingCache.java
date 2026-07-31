package com.qzcy.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.AdminUserRankingsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserRankingCache {
    private static final String USER_RANKINGS_KEY = "imagecreater:admin:user-rankings:v1";
    private static final TypeReference<AdminUserRankingsDto> USER_RANKINGS_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.admin.user-ranking-cache.redis-enabled:false}")
    private boolean redisEnabled;

    @Value("${app.admin.user-ranking-cache.ttl-seconds:60}")
    private long ttlSeconds;

    public AdminUserRankingsDto get() {
        if (!redisEnabled) return null;
        try {
            String snapshot = redisTemplate.opsForValue().get(USER_RANKINGS_KEY);
            return snapshot == null || snapshot.isBlank() ? null : objectMapper.readValue(snapshot, USER_RANKINGS_TYPE);
        } catch (Exception ex) {
            log.warn("Admin user-ranking Redis read failed; querying database instead: {}", ex.getMessage());
            return null;
        }
    }

    public void put(AdminUserRankingsDto rankings) {
        if (!redisEnabled || rankings == null) return;
        try {
            redisTemplate.opsForValue().set(
                    USER_RANKINGS_KEY,
                    objectMapper.writeValueAsString(rankings),
                    Duration.ofSeconds(Math.max(10, ttlSeconds))
            );
        } catch (Exception ex) {
            log.warn("Admin user-ranking Redis write failed; continuing without cache: {}", ex.getMessage());
        }
    }

    public void evict() {
        if (!redisEnabled) return;
        try {
            redisTemplate.delete(USER_RANKINGS_KEY);
        } catch (Exception ex) {
            log.warn("Admin user-ranking Redis eviction failed: {}", ex.getMessage());
        }
    }
}
