USE image_creator;

CREATE TABLE IF NOT EXISTS relay_channel (
                                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                             name VARCHAR(80) NOT NULL,
    provider VARCHAR(60) NOT NULL DEFAULT 'OpenAI Compatible',
    api_base_url VARCHAR(255) NOT NULL DEFAULT '',
    api_key VARCHAR(255),
    group_names VARCHAR(160) NOT NULL DEFAULT 'default',
    remark VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'unknown',
    priority INT NOT NULL DEFAULT 10,
    weight INT NOT NULL DEFAULT 10,
    rpm_limit INT NOT NULL DEFAULT 0,
    tpm_limit INT NOT NULL DEFAULT 0,
    price_multiplier DECIMAL(10, 4) NOT NULL DEFAULT 1.0000,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_relay_channel_enabled (enabled),
    INDEX idx_relay_channel_priority (priority, weight)
    );

INSERT INTO relay_channel
(id, name, provider, api_base_url, api_key, group_names, status, priority, weight, rpm_limit, tpm_limit, price_multiplier, enabled, created_at, updated_at)
SELECT 1, 'OpenAI Compatible', 'OpenAI Compatible', 'https://api.openai.com', NULL,
       'default', 'unknown', 10, 10, 0, 0, 1.0000, 1, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_channel);

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_channel' AND COLUMN_NAME = 'group_names') = 0, 'ALTER TABLE relay_channel ADD COLUMN group_names VARCHAR(160) NOT NULL DEFAULT ''default'' AFTER api_key', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_channel' AND COLUMN_NAME = 'remark') = 0, 'ALTER TABLE relay_channel ADD COLUMN remark VARCHAR(500) NOT NULL DEFAULT '''' AFTER group_names', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS relay_group (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    ratio DECIMAL(10, 4) NOT NULL DEFAULT 1.0000,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
    );

INSERT INTO relay_group
(code, name, ratio, enabled, created_at, updated_at)
SELECT 'default', '默认分组', 1.0000, 1, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_group WHERE code = 'default');

CREATE TABLE IF NOT EXISTS relay_model (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           model VARCHAR(120) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    model_type VARCHAR(40) NOT NULL DEFAULT 'chat',
    input_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    output_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    cached_input_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    cache_creation_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    request_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    fixed_request_billing TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'available',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 10,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_relay_model_model (model),
    INDEX idx_relay_model_type_enabled (model_type, enabled),
    INDEX idx_relay_model_sort (sort_order, id)
    );

SET @relay_model_unique_model_index := (
    SELECT s.INDEX_NAME
    FROM INFORMATION_SCHEMA.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'relay_model'
      AND s.COLUMN_NAME = 'model'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
      AND (
          SELECT COUNT(*)
          FROM INFORMATION_SCHEMA.STATISTICS x
          WHERE x.TABLE_SCHEMA = s.TABLE_SCHEMA
            AND x.TABLE_NAME = s.TABLE_NAME
            AND x.INDEX_NAME = s.INDEX_NAME
      ) = 1
    LIMIT 1
);
SET @drop_relay_model_unique_model := IF(
    @relay_model_unique_model_index IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE relay_model DROP INDEX `', REPLACE(@relay_model_unique_model_index, '`', '``'), '`')
);
PREPARE stmt FROM @drop_relay_model_unique_model; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_relay_model_model_index := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_model' AND INDEX_NAME = 'idx_relay_model_model') = 0,
    'CREATE INDEX idx_relay_model_model ON relay_model (model)',
    'SELECT 1'
);
PREPARE stmt FROM @add_relay_model_model_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_model_cached_input := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_model' AND COLUMN_NAME = 'cached_input_price') = 0,
    'ALTER TABLE relay_model ADD COLUMN cached_input_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER output_price',
    'SELECT 1'
);
PREPARE stmt FROM @add_model_cached_input; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_model_cache_creation := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_model' AND COLUMN_NAME = 'cache_creation_price') = 0,
    'ALTER TABLE relay_model ADD COLUMN cache_creation_price DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER cached_input_price',
    'SELECT 1'
);
PREPARE stmt FROM @add_model_cache_creation; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_model_status := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_model' AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE relay_model ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''available'' AFTER request_price',
    'SELECT 1'
);
PREPARE stmt FROM @add_model_status; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_model' AND COLUMN_NAME = 'fixed_request_billing') = 0, 'ALTER TABLE relay_model ADD COLUMN fixed_request_billing TINYINT(1) NOT NULL DEFAULT 0 AFTER request_price', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'gpt-4o', 'GPT-4o', 'chat', 2.500000, 10.000000, 0.000000, 1, 10, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'gpt-4o');

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'gpt-4.1', 'GPT-4.1 / Code', 'code', 2.000000, 8.000000, 0.000000, 1, 20, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'gpt-4.1');

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'gpt-image-1', 'GPT Image 1', 'image', 0.000000, 0.000000, 0.040000, 1, 30, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'gpt-image-1');

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'gpt-image-2', 'GPT Image 2', 'image', 0.000000, 0.000000, 0.040000, 1, 31, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'gpt-image-2');

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'dall-e-3', 'DALL-E 3', 'image', 0.000000, 0.000000, 0.080000, 1, 40, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'dall-e-3');

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'text-embedding-3-large', 'Text Embedding 3 Large', 'embedding', 0.130000, 0.000000, 0.000000, 1, 50, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'text-embedding-3-large');

INSERT INTO relay_model
(model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
SELECT 'whisper-1', 'Whisper 1', 'audio', 0.000000, 0.000000, 0.006000, 1, 60, NOW(), NOW()
    WHERE NOT EXISTS (SELECT 1 FROM relay_model WHERE model = 'whisper-1');

CREATE TABLE IF NOT EXISTS relay_group_model (
                                                 id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                 group_id BIGINT NOT NULL,
                                                 model_id BIGINT NOT NULL,
                                                 created_at DATETIME NOT NULL,
                                                 UNIQUE KEY uk_relay_group_model (group_id, model_id),
    INDEX idx_relay_group_model_group (group_id),
    INDEX idx_relay_group_model_model (model_id)
    );

INSERT IGNORE INTO relay_group_model (group_id, model_id, created_at)
SELECT g.id, m.id, NOW()
FROM relay_group g
         JOIN relay_model m
WHERE g.code = 'default';

SET @has_group_allowed_models := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'relay_group'
      AND COLUMN_NAME = 'allowed_models'
);
SET @migrate_group_models := IF(
    @has_group_allowed_models > 0,
    'INSERT IGNORE INTO relay_group_model (group_id, model_id, created_at)
     SELECT g.id, m.id, NOW()
     FROM relay_group g
     JOIN relay_model m
       ON FIND_IN_SET(m.model, REPLACE(g.allowed_models, '' '', '''')) > 0
     WHERE g.allowed_models IS NOT NULL AND g.allowed_models <> ''''',
    'SELECT 1'
);
PREPARE stmt FROM @migrate_group_models;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS relay_channel_model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    upstream_model VARCHAR(120) NOT NULL DEFAULT '',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_relay_channel_model (channel_id, model_id),
    INDEX idx_relay_channel_model_channel (channel_id),
    INDEX idx_relay_channel_model_model (model_id),
    INDEX idx_relay_channel_model_enabled (enabled)
);

INSERT IGNORE INTO relay_channel_model (channel_id, model_id, upstream_model, enabled, created_at, updated_at)
SELECT c.id, m.id, m.model, 1, NOW(), NOW()
FROM relay_channel c
         JOIN relay_model m
WHERE c.enabled = 1;

CREATE TABLE IF NOT EXISTS relay_token (
                                           id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           user_id BIGINT NOT NULL,
                                           name VARCHAR(80) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    token_preview VARCHAR(40) NOT NULL,
    group_names VARCHAR(160) NOT NULL DEFAULT 'default',
    allowed_models TEXT,
    quota DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    used_quota DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    request_count BIGINT NOT NULL DEFAULT 0,
    token_count BIGINT NOT NULL DEFAULT 0,
    rpm_limit INT NOT NULL DEFAULT 0,
    tpm_limit INT NOT NULL DEFAULT 0,
    ip_whitelist VARCHAR(500) NOT NULL DEFAULT '',
    last_used_at DATETIME,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    expires_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_relay_token_user (user_id),
    INDEX idx_relay_token_enabled (enabled)
    );

SET @has_old_token_groups := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'relay_token'
      AND COLUMN_NAME = 'groups'
);
SET @has_token_group_names := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'relay_token'
      AND COLUMN_NAME = 'group_names'
);
SET @rename_token_groups := IF(
    @has_old_token_groups > 0 AND @has_token_group_names = 0,
    'ALTER TABLE relay_token CHANGE COLUMN `groups` group_names VARCHAR(160) NOT NULL DEFAULT ''default''',
    'SELECT 1'
);
PREPARE stmt FROM @rename_token_groups;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_token' AND COLUMN_NAME = 'tpm_limit') = 0, 'ALTER TABLE relay_token ADD COLUMN tpm_limit INT NOT NULL DEFAULT 0 AFTER rpm_limit', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_token' AND COLUMN_NAME = 'ip_whitelist') = 0, 'ALTER TABLE relay_token ADD COLUMN ip_whitelist VARCHAR(500) NOT NULL DEFAULT '''' AFTER tpm_limit', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_token' AND COLUMN_NAME = 'last_used_at') = 0, 'ALTER TABLE relay_token ADD COLUMN last_used_at DATETIME AFTER ip_whitelist', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS relay_usage_log (
                                               id BIGINT PRIMARY KEY AUTO_INCREMENT,
                                               user_id BIGINT NOT NULL,
                                               token_id BIGINT,
                                               channel_id BIGINT,
                                               token_name VARCHAR(80),
    channel_name VARCHAR(80),
    group_names VARCHAR(160) NOT NULL DEFAULT 'default',
    endpoint VARCHAR(80),
    model VARCHAR(120),
    model_type VARCHAR(40),
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    cached_tokens INT NOT NULL DEFAULT 0,
    cache_creation_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    input_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    output_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    cache_read_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    cache_creation_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    request_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    group_ratio DECIMAL(10, 4) NOT NULL DEFAULT 1.0000,
    channel_ratio DECIMAL(10, 4) NOT NULL DEFAULT 1.0000,
    cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000,
    status_code INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    user_agent VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    message VARCHAR(500),
    created_at DATETIME NOT NULL,
    INDEX idx_relay_usage_user_created (user_id, created_at),
    INDEX idx_relay_usage_model_created (model, created_at),
    INDEX idx_relay_usage_channel_created (channel_id, created_at)
    );

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'token_name') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN token_name VARCHAR(80) AFTER channel_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'channel_name') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN channel_name VARCHAR(80) AFTER token_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'group_names') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN group_names VARCHAR(160) NOT NULL DEFAULT ''default'' AFTER channel_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'endpoint') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN endpoint VARCHAR(80) AFTER group_names', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'cached_tokens') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN cached_tokens INT NOT NULL DEFAULT 0 AFTER completion_tokens', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'cache_creation_tokens') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN cache_creation_tokens INT NOT NULL DEFAULT 0 AFTER cached_tokens', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'input_cost') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN input_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER total_tokens', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'output_cost') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN output_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER input_cost', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'cache_read_cost') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN cache_read_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER output_cost', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'cache_creation_cost') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN cache_creation_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER cache_read_cost', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'request_cost') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN request_cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000 AFTER cache_creation_cost', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'group_ratio') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN group_ratio DECIMAL(10, 4) NOT NULL DEFAULT 1.0000 AFTER request_cost', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'channel_ratio') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN channel_ratio DECIMAL(10, 4) NOT NULL DEFAULT 1.0000 AFTER group_ratio', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'relay_usage_log' AND COLUMN_NAME = 'user_agent') = 0, 'ALTER TABLE relay_usage_log ADD COLUMN user_agent VARCHAR(500) AFTER duration_ms', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE relay_usage_log MODIFY COLUMN cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000;
ALTER TABLE relay_token MODIFY COLUMN used_quota DECIMAL(12, 6) NOT NULL DEFAULT 0.000000;
ALTER TABLE relay_token MODIFY COLUMN quota DECIMAL(12, 6) NOT NULL DEFAULT 0.000000;
ALTER TABLE `user` MODIFY COLUMN balance DECIMAL(12, 6) NOT NULL DEFAULT 0.000000;
ALTER TABLE payment_record MODIFY COLUMN amount DECIMAL(12, 6) NOT NULL;

SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'invitation_code') = 0, 'ALTER TABLE `user` ADD COLUMN invitation_code VARCHAR(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'inviter_id') = 0, 'ALTER TABLE `user` ADD COLUMN inviter_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'referral_balance') = 0, 'ALTER TABLE `user` ADD COLUMN referral_balance DECIMAL(12, 6) NOT NULL DEFAULT 0.000000', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_user_invitation_code') = 0, 'CREATE UNIQUE INDEX uk_user_invitation_code ON `user` (invitation_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_user_inviter_id') = 0, 'CREATE INDEX idx_user_inviter_id ON `user` (inviter_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_config' AND COLUMN_NAME = 'referral_rebate_rate') = 0, 'ALTER TABLE payment_config ADD COLUMN referral_rebate_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
    );
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'status') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT ''pending_review''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'reviewed_by') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN reviewed_by BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'reject_reason') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN reject_reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'withdraw_qr_code_url') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN withdraw_qr_code_url VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'withdraw_fail_reason') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN withdraw_fail_reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'reviewed_at') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN reviewed_at DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'referral_rebate_record' AND COLUMN_NAME = 'withdrawn_at') = 0, 'ALTER TABLE referral_rebate_record ADD COLUMN withdrawn_at DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS referral_withdraw_qr_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    qr_code_url VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_referral_withdraw_qr_user_channel (user_id, channel),
    INDEX idx_referral_withdraw_qr_user (user_id)
);

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
);

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
    );
