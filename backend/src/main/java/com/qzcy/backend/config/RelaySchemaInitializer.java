package com.qzcy.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RelaySchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        if (!tableExists("relay_channel")) {
            return;
        }
        addColumnIfMissing("relay_channel", "group_names", "VARCHAR(160) NOT NULL DEFAULT 'default'");
        addColumnIfMissing("relay_channel", "remark", "VARCHAR(500) NOT NULL DEFAULT ''");
        jdbcTemplate.update("UPDATE relay_channel SET group_names = 'default' WHERE group_names IS NULL OR group_names = ''");
        ensureRelayChannelModelTable();
        ensureRelayModelAllowsDuplicateNames();
        ensureGroupModelNamesUniqueWithinGroup();
        repairDefaultChannelUpstreamModelNames();
        ensureRelayPrecision();
        ensureRelayTokenIndexes();
        ensureGptImage2Model();
        ensureEmptyGroupsHaveModels();
        ensureChannelsHaveModels();
    }

    private void ensureRelayChannelModelTable() {
        jdbcTemplate.execute("""
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
                )
                """);
        addColumnIfMissing("relay_channel_model", "upstream_model", "VARCHAR(120) NOT NULL DEFAULT ''");
        addColumnIfMissing("relay_channel_model", "enabled", "TINYINT(1) NOT NULL DEFAULT 1");
        addColumnIfMissing("relay_channel_model", "updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureRelayModelAllowsDuplicateNames() {
        if (!tableExists("relay_model")) {
            return;
        }
        String uniqueIndex = jdbcTemplate.query("""
                        SELECT s.INDEX_NAME
                        FROM information_schema.statistics s
                        WHERE s.TABLE_SCHEMA = DATABASE()
                          AND s.TABLE_NAME = 'relay_model'
                          AND s.COLUMN_NAME = 'model'
                          AND s.NON_UNIQUE = 0
                          AND s.INDEX_NAME <> 'PRIMARY'
                          AND (
                              SELECT COUNT(*)
                              FROM information_schema.statistics x
                              WHERE x.TABLE_SCHEMA = s.TABLE_SCHEMA
                                AND x.TABLE_NAME = s.TABLE_NAME
                                AND x.INDEX_NAME = s.INDEX_NAME
                          ) = 1
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString(1) : null);
        if (uniqueIndex != null && !uniqueIndex.isBlank()) {
            jdbcTemplate.execute("ALTER TABLE relay_model DROP INDEX `" + uniqueIndex.replace("`", "``") + "`");
        }
        addIndexIfMissing("relay_model", "idx_relay_model_model", "CREATE INDEX idx_relay_model_model ON relay_model (model)");
    }

    private void ensureGroupModelNamesUniqueWithinGroup() {
        if (!tableExists("relay_group_model") || !tableExists("relay_model")) {
            return;
        }
        jdbcTemplate.update("""
                DELETE gm
                FROM relay_group_model gm
                JOIN relay_model m ON m.id = gm.model_id
                JOIN relay_group_model keep_gm ON keep_gm.group_id = gm.group_id
                JOIN relay_model keep_m ON keep_m.id = keep_gm.model_id
                WHERE keep_m.model = m.model
                  AND (
                      keep_m.sort_order < m.sort_order
                      OR (keep_m.sort_order = m.sort_order AND keep_m.id < m.id)
                )
                """);
    }

    private void repairDefaultChannelUpstreamModelNames() {
        if (!tableExists("relay_channel_model") || !tableExists("relay_model")) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE relay_channel_model cm
                JOIN relay_model m ON m.id = cm.model_id
                SET cm.upstream_model = m.model,
                    cm.updated_at = NOW()
                WHERE cm.upstream_model <> m.model
                  AND cm.created_at = cm.updated_at
                """);
    }

    private void ensureGptImage2Model() {
        if (!tableExists("relay_model")) {
            return;
        }
        addColumnIfMissing("relay_model", "fixed_request_billing", "TINYINT(1) NOT NULL DEFAULT 0");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM relay_model WHERE model = ?",
                Integer.class,
                "gpt-image-2"
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO relay_model
                    (model, display_name, model_type, input_price, output_price, request_price, enabled, sort_order, created_at, updated_at)
                VALUES
                    ('gpt-image-2', 'GPT Image 2', 'image', 0.000000, 0.000000, 0.040000, 1, 31, NOW(), NOW())
                """);
        attachModelToDefaultGroup("gpt-image-2");
        attachModelToAllChannels("gpt-image-2");
    }

    private void ensureRelayPrecision() {
        if (tableExists("relay_model")) {
            addColumnIfMissing("relay_model", "fixed_request_billing", "TINYINT(1) NOT NULL DEFAULT 0");
        }
        if (tableExists("relay_usage_log")) {
            addColumnIfMissing("relay_usage_log", "message", "VARCHAR(1000)");
            jdbcTemplate.execute("ALTER TABLE relay_usage_log MODIFY COLUMN message VARCHAR(1000)");
            jdbcTemplate.execute("ALTER TABLE relay_usage_log MODIFY COLUMN cost DECIMAL(12, 6) NOT NULL DEFAULT 0.000000");
        }
        if (tableExists("relay_token")) {
            jdbcTemplate.execute("ALTER TABLE relay_token MODIFY COLUMN used_quota DECIMAL(12, 6) NOT NULL DEFAULT 0.000000");
            jdbcTemplate.execute("ALTER TABLE relay_token MODIFY COLUMN quota DECIMAL(12, 6) NOT NULL DEFAULT 0.000000");
        }
        if (tableExists("user")) {
            jdbcTemplate.execute("ALTER TABLE `user` MODIFY COLUMN balance DECIMAL(12, 6) NOT NULL DEFAULT 0.000000");
        }
        if (tableExists("payment_record")) {
            jdbcTemplate.execute("ALTER TABLE payment_record MODIFY COLUMN amount DECIMAL(12, 6) NOT NULL");
        }
    }

    private void ensureRelayTokenIndexes() {
        if (!tableExists("relay_token")) {
            return;
        }
        addIndexIfMissing("relay_token", "idx_relay_token_value_enabled", "CREATE INDEX idx_relay_token_value_enabled ON relay_token (token, enabled)");
        if (tableExists("relay_usage_log")) {
            addIndexIfMissing("relay_usage_log", "idx_relay_usage_token_created", "CREATE INDEX idx_relay_usage_token_created ON relay_usage_log (token_id, created_at)");
        }
    }

    private void attachModelToDefaultGroup(String model) {
        if (!tableExists("relay_group") || !tableExists("relay_group_model")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO relay_group_model (group_id, model_id, created_at)
                SELECT g.id, m.id, NOW()
                FROM relay_group g
                JOIN relay_model m
                WHERE g.code = 'default' AND m.model = ?
                """, model);
    }

    private void attachModelToAllChannels(String model) {
        if (!tableExists("relay_channel") || !tableExists("relay_model") || !tableExists("relay_channel_model")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO relay_channel_model (channel_id, model_id, upstream_model, enabled, created_at, updated_at)
                SELECT c.id, m.id, m.model, 1, NOW(), NOW()
                FROM relay_channel c
                JOIN relay_model m
                WHERE m.model = ?
                """, model);
    }

    private void ensureEmptyGroupsHaveModels() {
        if (!tableExists("relay_group") || !tableExists("relay_model") || !tableExists("relay_group_model")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO relay_group_model (group_id, model_id, created_at)
                SELECT g.id, m.id, NOW()
                FROM relay_group g
                JOIN relay_model m
                WHERE g.enabled = 1
                  AND m.enabled = 1
                  AND NOT EXISTS (
                      SELECT 1
                      FROM relay_group_model gm
                      WHERE gm.group_id = g.id
                  )
                """);
    }

    private void ensureChannelsHaveModels() {
        if (!tableExists("relay_channel") || !tableExists("relay_model") || !tableExists("relay_channel_model")) {
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO relay_channel_model (channel_id, model_id, upstream_model, enabled, created_at, updated_at)
                SELECT c.id, m.id, m.model, 1, NOW(), NOW()
                FROM relay_channel c
                JOIN relay_model m
                WHERE c.enabled = 1
                  AND m.enabled = 1
                  AND NOT EXISTS (
                      SELECT 1
                      FROM relay_channel_model cm
                      WHERE cm.channel_id = c.id
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

    private void addIndexIfMissing(String tableName, String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
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
