package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RechargeCouponSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        createCouponTable();
        createUsageTable();
        if (tableExists("payment_record")) {
            addColumnIfMissing("payment_record", "recharge_amount", "DECIMAL(12, 6) NULL AFTER amount");
            addColumnIfMissing("payment_record", "discount_amount", "DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER recharge_amount");
            addColumnIfMissing("payment_record", "coupon_id", "BIGINT NULL AFTER discount_amount");
            addColumnIfMissing("payment_record", "coupon_code", "VARCHAR(255) NULL AFTER coupon_id");
            addColumnIfMissing("payment_record", "coupon_discount_percent", "DECIMAL(5, 2) NULL AFTER coupon_code");
        }
    }

    private void createCouponTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recharge_coupon (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    code VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                    discount_percent DECIMAL(5, 2) NOT NULL,
                    max_uses_per_user INT NOT NULL DEFAULT 0,
                    max_discount_amount DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_recharge_coupon_code (code),
                    INDEX idx_recharge_coupon_enabled (enabled, created_at)
                )
                """);
        jdbcTemplate.execute("ALTER TABLE recharge_coupon MODIFY COLUMN code VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL");
        addColumnIfMissing("recharge_coupon", "max_discount_amount", "DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER max_uses_per_user");
    }

    private void createUsageTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recharge_coupon_usage (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    coupon_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    payment_record_id BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'reserved',
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    completed_at DATETIME NULL,
                    UNIQUE KEY uk_recharge_coupon_payment (payment_record_id),
                    INDEX idx_recharge_coupon_user_status (coupon_id, user_id, status),
                    INDEX idx_recharge_coupon_payment_status (payment_record_id, status)
                )
                """);
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }
}
