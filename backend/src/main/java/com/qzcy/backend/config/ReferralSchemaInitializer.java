package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReferralSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        addUserColumnIfMissing("banned", "TINYINT(1) NOT NULL DEFAULT 0");
        addUserColumnIfMissing("invitation_code", "VARCHAR(6) NULL");
        addUserColumnIfMissing("inviter_id", "BIGINT NULL");
        addUserColumnIfMissing("referral_balance", "DECIMAL(12, 6) NOT NULL DEFAULT 0.000000");
        addPaymentConfigColumnIfMissing("referral_rebate_rate", "DECIMAL(5, 2) NOT NULL DEFAULT 0.00");
        addIndexIfMissing("user", "uk_user_invitation_code", "CREATE UNIQUE INDEX uk_user_invitation_code ON `user` (invitation_code)");
        addIndexIfMissing("user", "idx_user_inviter_id", "CREATE INDEX idx_user_inviter_id ON `user` (inviter_id)");
        createReferralRebateTable();
        addReferralRecordColumnIfMissing("status", "VARCHAR(30) NOT NULL DEFAULT 'pending_review'");
        addReferralRecordColumnIfMissing("reviewed_by", "BIGINT NULL");
        addReferralRecordColumnIfMissing("reject_reason", "VARCHAR(500) NULL");
        addReferralRecordColumnIfMissing("withdraw_qr_code_url", "VARCHAR(500) NULL");
        addReferralRecordColumnIfMissing("withdraw_fail_reason", "VARCHAR(500) NULL");
        addReferralRecordColumnIfMissing("reviewed_at", "DATETIME NULL");
        addReferralRecordColumnIfMissing("withdrawn_at", "DATETIME NULL");
        createReferralWithdrawQrCodeTable();
        createReferralWithdrawRequestTable();
    }

    private void addUserColumnIfMissing(String columnName, String definition) {
        addColumnIfMissing("user", columnName, "ALTER TABLE `user` ADD COLUMN " + columnName + " " + definition);
    }

    private void addPaymentConfigColumnIfMissing(String columnName, String definition) {
        addColumnIfMissing("payment_config", columnName, "ALTER TABLE payment_config ADD COLUMN " + columnName + " " + definition);
    }

    private void addReferralRecordColumnIfMissing(String columnName, String definition) {
        addColumnIfMissing("referral_rebate_record", columnName, "ALTER TABLE referral_rebate_record ADD COLUMN " + columnName + " " + definition);
    }

    private void addColumnIfMissing(String tableName, String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void createReferralRebateTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS referral_rebate_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    inviter_id BIGINT NOT NULL,
                    invitee_id BIGINT NOT NULL,
                    payment_record_id BIGINT NOT NULL,
                    recharge_amount DECIMAL(12, 6) NOT NULL,
                    rebate_rate DECIMAL(5, 2) NOT NULL,
                    rebate_amount DECIMAL(12, 6) NOT NULL,
                    status VARCHAR(30) NOT NULL DEFAULT 'pending_review',
                    reviewed_by BIGINT NULL,
                    reject_reason VARCHAR(500) NULL,
                    withdraw_qr_code_url VARCHAR(500) NULL,
                    withdraw_fail_reason VARCHAR(500) NULL,
                    reviewed_at DATETIME NULL,
                    withdrawn_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_referral_payment_record (payment_record_id),
                    INDEX idx_referral_inviter (inviter_id, created_at),
                    INDEX idx_referral_invitee (invitee_id)
                )
                """);
    }

    private void createReferralWithdrawQrCodeTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS referral_withdraw_qr_code (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    channel VARCHAR(20) NOT NULL,
                    qr_code_url VARCHAR(500) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_referral_withdraw_qr_user_channel (user_id, channel),
                    INDEX idx_referral_withdraw_qr_user (user_id)
                )
                """);
    }

    private void createReferralWithdrawRequestTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS referral_withdraw_request (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    amount DECIMAL(12, 6) NOT NULL,
                    channel VARCHAR(20) NOT NULL,
                    qr_code_url VARCHAR(500) NOT NULL,
                    status VARCHAR(30) NOT NULL DEFAULT 'pending',
                    reviewed_by BIGINT NULL,
                    fail_reason VARCHAR(500) NULL,
                    reviewed_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_referral_withdraw_user (user_id, created_at),
                    INDEX idx_referral_withdraw_status (status, created_at)
                )
                """);
    }
}
