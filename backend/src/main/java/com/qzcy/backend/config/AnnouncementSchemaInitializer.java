package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnnouncementSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS announcement (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    title VARCHAR(160) NOT NULL,
                    content TEXT NOT NULL,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    pinned TINYINT(1) NOT NULL DEFAULT 0,
                    sort_order INT NOT NULL DEFAULT 10,
                    published_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_announcement_public (enabled, pinned, sort_order, published_at),
                    INDEX idx_announcement_created (created_at)
                )
                """);
        ensureIndex("idx_announcement_public",
                "ALTER TABLE announcement ADD INDEX idx_announcement_public (enabled, pinned, sort_order, published_at)");
        ensureIndex("idx_announcement_created",
                "ALTER TABLE announcement ADD INDEX idx_announcement_created (created_at)");
    }

    private void ensureIndex(String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'announcement'
                  AND INDEX_NAME = ?
                """, Integer.class, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
        }
    }
}
