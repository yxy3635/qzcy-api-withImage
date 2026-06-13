package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentConfigSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        addColumnIfMissing("alipay_enabled", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumnIfMissing("wxpay_enabled", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumnIfMissing("qqpay_enabled", "TINYINT(1) NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'payment_config' AND column_name = ?",
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE payment_config ADD COLUMN " + columnName + " " + definition);
        }
    }
}
