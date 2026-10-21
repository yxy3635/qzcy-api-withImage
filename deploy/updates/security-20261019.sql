-- Account security update, MySQL 8.x. Select the existing application database first.
-- Back up the database before applying. Run once with no concurrent DDL.
-- Safe to repeat after successful completion; the backend also applies this on startup.
-- Existing accounts, passwords, balances and API keys are not deleted or rewritten.

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'auth_version') = 0, 'ALTER TABLE `user` ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS auth_attempt_bucket (
    bucket_key VARCHAR(100) PRIMARY KEY,
    attempts INT NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX idx_auth_attempt_expiry (expires_at)
);
