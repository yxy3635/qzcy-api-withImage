package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AccountSecuritySchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        if (!hasAuthVersion()) {
            try {
                jdbc.execute("ALTER TABLE `user` ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0");
            } catch (DataAccessException ex) {
                // Another backend may have completed the same migration concurrently.
                if (!hasAuthVersion()) throw ex;
            }
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS auth_attempt_bucket (
                    bucket_key VARCHAR(100) PRIMARY KEY,
                    attempts INT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    INDEX idx_auth_attempt_expiry (expires_at)
                )
                """);
    }

    private boolean hasAuthVersion() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'auth_version'
                """, Integer.class);
        return count != null && count > 0;
    }
}
