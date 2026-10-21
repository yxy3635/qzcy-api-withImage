package com.qzcy.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayReportDto.Snapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RelayReportCache {
    public static final Duration TTL = Duration.ofHours(1);
    public enum LockState { ACQUIRED, BUSY, UNAVAILABLE }
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final long FAILURE_COOLDOWN_NANOS = Duration.ofSeconds(30).toNanos();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private volatile long unavailableUntilNanos;

    public Snapshot get(String key) {
        if (redisUnavailable()) return null;
        try {
            String value = redis.opsForValue().get(key);
            markAvailable();
            if (value == null) return null;
            try {
                return objectMapper.readValue(value, Snapshot.class);
            } catch (Exception ex) {
                log.warn("Relay report cache contains invalid snapshot: {}", ex.getMessage());
                return null;
            }
        } catch (Exception ex) {
            markUnavailable(ex);
            log.warn("Relay report cache unavailable; using local fallback: {}", ex.getMessage());
            return null;
        }
    }

    public void put(String key, Snapshot snapshot) {
        if (redisUnavailable()) return;
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(snapshot), TTL);
            markAvailable();
        } catch (Exception ex) {
            markUnavailable(ex);
            log.warn("Relay report cache write failed; keeping local snapshot: {}", ex.getMessage());
        }
    }

    public LockState acquire(String key, String owner) {
        if (redisUnavailable()) return LockState.UNAVAILABLE;
        try {
            LockState state = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key + ":lock", owner, Duration.ofMinutes(5)))
                    ? LockState.ACQUIRED : LockState.BUSY;
            markAvailable();
            return state;
        } catch (Exception ex) {
            markUnavailable(ex);
            log.warn("Relay report Redis lock unavailable: {}", ex.getMessage());
            return LockState.UNAVAILABLE;
        }
    }

    public void release(String key, String owner) {
        if (redisUnavailable()) return;
        try {
            redis.execute(UNLOCK, List.of(key + ":lock"), owner);
            markAvailable();
        } catch (Exception ex) {
            markUnavailable(ex);
            log.warn("Relay report lock release failed; lock will expire: {}", ex.getMessage());
        }
    }

    private boolean redisUnavailable() {
        return System.nanoTime() < unavailableUntilNanos;
    }

    private void markUnavailable(Exception ex) {
        unavailableUntilNanos = System.nanoTime() + FAILURE_COOLDOWN_NANOS;
    }

    private void markAvailable() {
        unavailableUntilNanos = 0;
    }
}
