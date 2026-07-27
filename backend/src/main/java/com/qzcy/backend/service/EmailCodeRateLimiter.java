package com.qzcy.backend.service;

import com.qzcy.backend.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EmailCodeRateLimiter {
    private static final long COOLDOWN_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 60 * 60_000L;
    private static final long DAY_MILLIS = 24 * HOUR_MILLIS;
    private static final int MAX_EMAIL_REQUESTS_PER_HOUR = 5;
    private static final int MAX_EMAIL_REQUESTS_PER_DAY = 12;
    private static final int MAX_IP_REQUESTS_PER_HOUR = 15;
    private static final int MAX_GLOBAL_REQUESTS_PER_HOUR = 120;
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final Map<String, Long> nextAllowedAtByEmail = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Deque<Long>> emailRequests = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Deque<Long>> ipRequests = new LinkedHashMap<>(16, 0.75f, true);
    private final Deque<Long> globalRequests = new ArrayDeque<>();
    private long lastCleanupAt;

    /**
     * Checks and records a request atomically so concurrent requests cannot bypass the limits.
     */
    public synchronized void checkAndRecord(String email, String clientIp) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);

        Long nextAllowedAt = nextAllowedAtByEmail.get(email);
        if (nextAllowedAt != null && nextAllowedAt > now) {
            long seconds = Math.max(1, (nextAllowedAt - now + 999) / 1_000);
            throw new BusinessException(429, "请在 " + seconds + " 秒后再获取验证码");
        }

        Deque<Long> emailTimes = requestsFor(emailRequests, email);
        trimBefore(emailTimes, now - DAY_MILLIS);
        if (countSince(emailTimes, now - HOUR_MILLIS) >= MAX_EMAIL_REQUESTS_PER_HOUR) {
            throw new BusinessException(429, "该邮箱请求过于频繁，请 1 小时后再试");
        }
        if (emailTimes.size() >= MAX_EMAIL_REQUESTS_PER_DAY) {
            throw new BusinessException(429, "该邮箱今日验证码请求次数已达上限，请明天再试");
        }

        Deque<Long> ipTimes = requestsFor(ipRequests, normalizeIp(clientIp));
        trimBefore(ipTimes, now - HOUR_MILLIS);
        if (ipTimes.size() >= MAX_IP_REQUESTS_PER_HOUR) {
            throw new BusinessException(429, "当前网络请求过于频繁，请稍后再试");
        }

        trimBefore(globalRequests, now - HOUR_MILLIS);
        if (globalRequests.size() >= MAX_GLOBAL_REQUESTS_PER_HOUR) {
            throw new BusinessException(429, "验证码服务请求繁忙，请稍后再试");
        }

        nextAllowedAtByEmail.put(email, now + COOLDOWN_MILLIS);
        emailTimes.addLast(now);
        ipTimes.addLast(now);
        globalRequests.addLast(now);
        cap(nextAllowedAtByEmail);
        cap(emailRequests);
        cap(ipRequests);
    }

    private Deque<Long> requestsFor(Map<String, Deque<Long>> requests, String key) {
        return requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    }

    private int countSince(Deque<Long> timestamps, long threshold) {
        int count = 0;
        for (Long timestamp : timestamps) {
            if (timestamp >= threshold) {
                count++;
            }
        }
        return count;
    }

    private void trimBefore(Deque<Long> timestamps, long threshold) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < threshold) {
            timestamps.removeFirst();
        }
    }

    private void cleanupIfNeeded(long now) {
        if (now - lastCleanupAt < COOLDOWN_MILLIS) {
            return;
        }
        lastCleanupAt = now;
        nextAllowedAtByEmail.entrySet().removeIf(entry -> entry.getValue() <= now);
        cleanupRequests(emailRequests, now - DAY_MILLIS);
        cleanupRequests(ipRequests, now - HOUR_MILLIS);
        trimBefore(globalRequests, now - HOUR_MILLIS);
    }

    private void cleanupRequests(Map<String, Deque<Long>> requests, long threshold) {
        Iterator<Map.Entry<String, Deque<Long>>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            trimBefore(entry.getValue(), threshold);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private <T> void cap(Map<String, T> values) {
        while (values.size() > MAX_TRACKED_KEYS) {
            Iterator<String> iterator = values.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }
}
