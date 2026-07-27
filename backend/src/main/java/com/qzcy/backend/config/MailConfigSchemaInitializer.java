package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MailConfigSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        addColumnIfMissing("recharge_notice_enabled", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumnIfMissing("brand_name", "VARCHAR(100) NOT NULL DEFAULT 'imageCreater · API Relay'");
        addColumnIfMissing("brand_logo_url", "VARCHAR(500) NOT NULL DEFAULT ''");
        addColumnIfMissing("site_url", "VARCHAR(500) NOT NULL DEFAULT ''");
    }

    private void addColumnIfMissing(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'mail_config'
                  AND column_name = ?
                """, Integer.class, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE mail_config ADD COLUMN " + columnName + " " + definition);
        }
    }
}
