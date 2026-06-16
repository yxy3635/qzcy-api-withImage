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
        jdbcTemplate.update("UPDATE relay_channel SET group_names = 'default' WHERE group_names IS NULL OR group_names = ''");
        ensureRelayPrecision();
        ensureGptImage2Model();
        ensureEmptyGroupsHaveModels();
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
    }

    private void ensureRelayPrecision() {
        if (tableExists("relay_model")) {
            addColumnIfMissing("relay_model", "fixed_request_billing", "TINYINT(1) NOT NULL DEFAULT 0");
        }
        if (tableExists("relay_usage_log")) {
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
