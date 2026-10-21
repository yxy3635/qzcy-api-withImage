package com.qzcy.backend.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.RelayPromptRuleHitsDto;
import com.qzcy.backend.util.RelayPromptAuditSummary;
import com.qzcy.backend.dto.RelayPromptRuleConfigDto;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.util.RelayPromptRules;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Service
@Slf4j
public class RelayPromptRuleService implements CommandLineRunner {
    public static final int MAX_CONTENT_LENGTH = 16000;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate auditTransaction;
    private volatile RelayPromptRuleConfigDto config = new RelayPromptRuleConfigDto(true, RelayPromptRules.DEFAULT_CONTENT);

    public RelayPromptRuleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditTransaction = jdbcTemplate == null ? null
                : new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public synchronized void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS relay_prompt_rule_config (
                  id BIGINT PRIMARY KEY,
                  enabled TINYINT(1) NOT NULL DEFAULT 1,
                  content TEXT NOT NULL,
                  auto_ban_threshold INT NOT NULL DEFAULT 3
                )
                """);
        if (!hasThresholdColumn()) {
            try {
                jdbcTemplate.execute("ALTER TABLE relay_prompt_rule_config ADD COLUMN auto_ban_threshold INT NOT NULL DEFAULT 3");
            } catch (DataAccessException ex) {
                if (!hasThresholdColumn()) throw ex;
            }
        }
        jdbcTemplate.update("INSERT IGNORE INTO relay_prompt_rule_config (id, enabled, content) VALUES (1, 1, ?)",
                RelayPromptRules.DEFAULT_CONTENT);
        RelayPromptRuleConfigDto stored = jdbcTemplate.queryForObject(
                "SELECT enabled, content, auto_ban_threshold FROM relay_prompt_rule_config WHERE id = 1",
                (rs, row) -> new RelayPromptRuleConfigDto(rs.getBoolean(1), rs.getString(2), rs.getInt(3)));
        validate(stored);
        config = stored;
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS relay_prompt_rule_hit (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  request_id VARCHAR(36) NOT NULL UNIQUE,
                  user_id BIGINT NOT NULL,
                  token_id BIGINT NULL,
                  endpoint VARCHAR(80) NOT NULL,
                  model VARCHAR(120) NOT NULL,
                  request_summary VARCHAR(500) NOT NULL,
                  created_at DATETIME NOT NULL,
                  INDEX idx_prompt_hit_user (user_id, id),
                  INDEX idx_prompt_hit_time (created_at)
                )
                """);
    }

    public Status status() {
        RelayPromptRuleConfigDto snapshot = config;
        return new Status(snapshot.enabled(), snapshot.content(), RelayPromptRules.DEFAULT_CONTENT, MAX_CONTENT_LENGTH, snapshot.autoBanThreshold());
    }

    public synchronized Status update(RelayPromptRuleConfigDto next) {
        validate(next);
        int updated = jdbcTemplate.update("UPDATE relay_prompt_rule_config SET enabled = ?, content = ?, auto_ban_threshold = ? WHERE id = 1",
                next.enabled(), next.content(), next.autoBanThreshold());
        if (updated != 1) throw new IllegalStateException("提示词规则配置尚未初始化");
        config = next; // Publish settings atomically, only after persistence succeeds.
        return status();
    }

    public void apply(ObjectNode outbound, String path) {
        RelayPromptRuleConfigDto snapshot = config;
        if (snapshot.enabled()) RelayPromptRules.apply(outbound, path, snapshot.content());
    }

    public boolean enabled() { return config.enabled(); }

    public String imagePrompt(String original) {
        RelayPromptRuleConfigDto snapshot = config;
        return snapshot.enabled() ? RelayPromptRules.prepend(snapshot.content(), original) : original;
    }

    public void recordRefusal(String requestId, Long userId, Long tokenId, String endpoint, String model,
                              ObjectNode originalBody, boolean ruleEnabledForRequest, boolean refusalMatched) {
        if (!ruleEnabledForRequest || !refusalMatched || userId == null) return;
        int threshold = config.autoBanThreshold();
        try {
            auditTransaction.executeWithoutResult(transaction -> {
                // Serialize hits for one account, including across backend instances.
                var userRows = jdbcTemplate.queryForList("SELECT id FROM `user` WHERE id = ? FOR UPDATE", Long.class, userId);
                int inserted = jdbcTemplate.update("""
                    INSERT IGNORE INTO relay_prompt_rule_hit
                      (request_id, user_id, token_id, endpoint, model, request_summary, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, requestId, userId, tokenId, limit(endpoint, 80), limit(model, 120),
                    RelayPromptAuditSummary.from(originalBody, endpoint), java.time.LocalDateTime.now());
                if (inserted == 0 || userRows.isEmpty()) return;
                Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM relay_prompt_rule_hit WHERE user_id = ?", Long.class, userId);
                if (count != null && count >= threshold) {
                    // Revoke existing sessions as with an administrator's manual ban.
                    jdbcTemplate.update("UPDATE `user` SET banned = 1, auth_version = auth_version + 1 WHERE id = ? AND banned = 0", userId);
                }
            });
        } catch (Exception ex) {
            // Auditing must never break a response, stream completion or billing.
            log.warn("Prompt rule audit persistence failed userId={} requestId={}", userId, requestId);
        }
    }

    public RelayPromptRuleHitsDto hits(long page, int size, String keyword) {
        if (page < 1 || page > 1000000 || size < 1 || size > 100 || (keyword != null && keyword.length() > 100))
            throw new BusinessException(400, "分页参数无效，页大小须为 1–100，搜索内容最多 100 个字符");
        String search = keyword == null ? "" : keyword.strip();
        String where = " WHERE (? = '' OR LOCATE(?, COALESCE(u.username, '')) > 0 OR CAST(h.user_id AS CHAR) = ?) ";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT h.user_id) FROM relay_prompt_rule_hit h LEFT JOIN `user` u ON u.id = h.user_id" + where,
                Long.class, search, search, search);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM relay_prompt_rule_hit", Long.class);
        Long users = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT user_id) FROM relay_prompt_rule_hit", Long.class);
        List<RelayPromptRuleHitsDto.UserHit> records = jdbcTemplate.query("""
                SELECT h.user_id, COALESCE(u.username, '') AS username, a.hit_count, a.first_hit_at,
                       h.created_at, h.endpoint, h.model, h.request_summary, COALESCE(u.banned, 0)
                FROM (SELECT user_id, COUNT(*) AS hit_count, MIN(created_at) AS first_hit_at, MAX(id) AS latest_id
                      FROM relay_prompt_rule_hit GROUP BY user_id) a
                JOIN relay_prompt_rule_hit h ON h.id = a.latest_id
                LEFT JOIN `user` u ON u.id = h.user_id
                """ + where + " ORDER BY h.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new RelayPromptRuleHitsDto.UserHit(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getTimestamp(4).toLocalDateTime(), rs.getTimestamp(5).toLocalDateTime(),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getBoolean(9)), search, search, search, size, (page - 1) * size);
        return new RelayPromptRuleHitsDto(total, page, (total + size - 1) / size, count, users, records);
    }

    private String limit(String value, int length) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), length));
    }

    private void validate(RelayPromptRuleConfigDto value) {
        if (value != null && (value.autoBanThreshold() < 1 || value.autoBanThreshold() > 1000))
            throw new BusinessException(400, "自动封禁阈值须为 1–1000 次的整数");
        if (value == null || value.enabled() == null || value.content() == null
                || value.content().length() > MAX_CONTENT_LENGTH
                || (value.enabled() && value.content().isBlank())) {
            throw new BusinessException(400, "请提供完整提示词规则设置；启用时内容不能为空，最多 16000 个字符");
        }
    }

    private boolean hasThresholdColumn() {
        return Boolean.TRUE.equals(jdbcTemplate.query("SELECT * FROM relay_prompt_rule_config WHERE 1 = 0", rs -> {
            var metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                if ("auto_ban_threshold".equalsIgnoreCase(metadata.getColumnName(i))) return true;
            }
            return false;
        }));
    }

    public record Status(boolean enabled, String content, String defaultContent, int maxContentLength, int autoBanThreshold) {
        public Status(boolean enabled, String content, String defaultContent, int maxContentLength) {
            this(enabled, content, defaultContent, maxContentLength, 3);
        }
    }
}
