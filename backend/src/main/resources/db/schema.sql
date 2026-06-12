CREATE DATABASE IF NOT EXISTS image_creator DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE image_creator;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(120) UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS image_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    prompt TEXT NOT NULL,
    generated_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    cost DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at DATETIME NOT NULL,
    INDEX idx_image_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_payment_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS image_generation_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    model VARCHAR(80) NOT NULL,
    api_key VARCHAR(255),
    api_base_url VARCHAR(255) NOT NULL DEFAULT 'https://api.openai.com',
    endpoint_path VARCHAR(80) NOT NULL DEFAULT '/v1/images/generations',
    size VARCHAR(30) NOT NULL,
    quality VARCHAR(30) NOT NULL,
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.10,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

INSERT IGNORE INTO image_generation_config
    (code, name, model, api_key, api_base_url, endpoint_path, size, quality, price, enabled, sort_order, created_at, updated_at)
VALUES
    ('1k', '1K 标准图像', 'dall-e-3', NULL, 'https://api.openai.com', '/v1/images/generations', '1024x1024', 'standard', 0.10, 1, 10, NOW(), NOW()),
    ('2k', '2K 高清图像', 'dall-e-3', NULL, 'https://api.openai.com', '/v1/images/generations', '1792x1024', 'hd', 0.20, 1, 20, NOW(), NOW()),
    ('4k', '4K 超清图像', 'dall-e-3', NULL, 'https://api.openai.com', '/v1/images/generations', '1792x1024', 'hd', 0.40, 1, 30, NOW(), NOW());

CREATE TABLE IF NOT EXISTS image_generation_metric (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    image_record_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    quality_code VARCHAR(20),
    duration_ms BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_metric_created (created_at),
    INDEX idx_metric_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS mail_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    host VARCHAR(120) NOT NULL DEFAULT '',
    port INT NOT NULL DEFAULT 587,
    username VARCHAR(160) NOT NULL DEFAULT '',
    password VARCHAR(255) NOT NULL DEFAULT '',
    from_address VARCHAR(160) NOT NULL DEFAULT '',
    ssl_enabled TINYINT(1) NOT NULL DEFAULT 0,
    starttls_enabled TINYINT(1) NOT NULL DEFAULT 1,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    dev_return_code TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

INSERT INTO mail_config
    (id, host, port, username, password, from_address, ssl_enabled, starttls_enabled, enabled, dev_return_code, created_at, updated_at)
SELECT 1, '', 587, '', '', '', 0, 1, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM mail_config);
