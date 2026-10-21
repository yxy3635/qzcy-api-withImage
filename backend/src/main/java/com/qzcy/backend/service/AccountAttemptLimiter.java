package com.qzcy.backend.service;

import com.qzcy.backend.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Database-backed fixed windows: shared across backend replicas and preserved on restart. */
@Service
@RequiredArgsConstructor
public class AccountAttemptLimiter {
    private static final long WINDOW_MS = 15 * 60_000L;
    private final JdbcTemplate jdbc;

    @Transactional(noRollbackFor = BusinessException.class)
    public void login(String account, String ip) {
        consume("login-ip", ip, 100);
        consume("login-account", account == null ? "" : account.trim().toLowerCase(Locale.ROOT), 20);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void sensitive(String account, String ip) {
        consume("security-ip", ip, 60);
        consume("security-account", account == null ? "" : account.trim().toLowerCase(Locale.ROOT), 10);
    }

    private void consume(String scope, String value, int limit) {
        long now = System.currentTimeMillis();
        String key = digest(scope + ":" + value) + ":" + (now / WINDOW_MS);
        jdbc.update("""
                INSERT INTO auth_attempt_bucket (bucket_key, attempts, expires_at) VALUES (?, 1, ?)
                ON DUPLICATE KEY UPDATE attempts = LEAST(attempts + 1, ?)
                """, key, (now / WINDOW_MS + 1) * WINDOW_MS, limit + 1);
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM auth_attempt_bucket WHERE bucket_key = ?", Integer.class, key);
        if (attempts == null || attempts > limit) {
            throw new BusinessException(429, "尝试次数过多，请在15分钟后重试");
        }
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void cleanup() {
        jdbc.update("DELETE FROM auth_attempt_bucket WHERE expires_at < ?", System.currentTimeMillis() - WINDOW_MS);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
