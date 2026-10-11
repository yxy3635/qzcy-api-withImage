package com.qzcy.backend.service;

import com.qzcy.backend.dto.RelayCompressionConfigDto;
import com.qzcy.backend.exception.BusinessException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Per-JVM admission budget for decoded bytes and bounded decoder workspace. */
@Service
public class RelayCompressionService implements CommandLineRunner {
    public static final int REQUEST_BUDGET_MB = 32;
    public static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    public static final int ZSTD_WINDOW_LOG_MAX = 23; // 8 MiB; remaining slot covers context and buffers.
    private final JdbcTemplate jdbcTemplate;
    private RelayCompressionConfigDto config = new RelayCompressionConfigDto(true, true, 128);
    private int reservedMb;
    private int activeRequests;

    public RelayCompressionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public synchronized void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS relay_compression_config (
                  id BIGINT PRIMARY KEY,
                  gzip_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  zstd_enabled TINYINT(1) NOT NULL DEFAULT 1,
                  memory_budget_mb INT NOT NULL DEFAULT 128
                )
                """);
        jdbcTemplate.update("INSERT IGNORE INTO relay_compression_config (id) VALUES (1)");
        RelayCompressionConfigDto stored = jdbcTemplate.queryForObject(
                "SELECT gzip_enabled, zstd_enabled, memory_budget_mb FROM relay_compression_config WHERE id = 1",
                (rs, row) -> new RelayCompressionConfigDto(rs.getBoolean(1), rs.getBoolean(2), rs.getInt(3)));
        validate(stored);
        config = stored;
    }

    public synchronized RelayCompressionConfigDto current() { return config; }

    public synchronized Status status() {
        return new Status(config.gzipEnabled(), config.zstdEnabled(), config.memoryBudgetMb(),
                reservedMb, activeRequests, REQUEST_BUDGET_MB, MAX_BODY_BYTES / 1024 / 1024, 8);
    }

    public synchronized Status update(RelayCompressionConfigDto next) {
        validate(next);
        jdbcTemplate.update("UPDATE relay_compression_config SET gzip_enabled = ?, zstd_enabled = ?, memory_budget_mb = ? WHERE id = 1",
                next.gzipEnabled(), next.zstdEnabled(), next.memoryBudgetMb());
        config = next; // Publish only after persistence succeeds.
        return status();
    }

    private void validate(RelayCompressionConfigDto value) {
        if (value == null || value.gzipEnabled() == null || value.zstdEnabled() == null
                || value.memoryBudgetMb() == null || value.memoryBudgetMb() < REQUEST_BUDGET_MB
                || value.memoryBudgetMb() > 1024) {
            throw new BusinessException(400, "请提供完整压缩设置，解压内存预算须为 32–1024 MB 的整数");
        }
    }

    public synchronized Lease acquire(String encoding) {
        boolean enabled = switch (encoding) {
            case "gzip" -> config.gzipEnabled();
            case "zstd" -> config.zstdEnabled();
            default -> false;
        };
        if (!enabled) throw new CompressionException(415, "unsupported_content_encoding", "请求压缩格式未启用或不受支持：" + encoding);
        if ((long) reservedMb + REQUEST_BUDGET_MB > config.memoryBudgetMb()) {
            throw new CompressionException(503, "decompression_budget_exhausted", "解压内存预算暂时不足，请稍后重试");
        }
        reservedMb += REQUEST_BUDGET_MB;
        activeRequests++;
        return new Lease();
    }

    public final class Lease implements AutoCloseable {
        private boolean closed;
        @Override
        public void close() {
            synchronized (RelayCompressionService.this) {
                if (!closed) {
                    closed = true;
                    reservedMb -= REQUEST_BUDGET_MB;
                    activeRequests--;
                }
            }
        }
    }

    public record Status(Boolean gzipEnabled, Boolean zstdEnabled, Integer memoryBudgetMb,
                         int reservedMb, int activeRequests, int requestBudgetMb, int maxBodyMb, int zstdWindowMb) {}

    public static class CompressionException extends RuntimeException {
        private final int status;
        private final String code;
        public CompressionException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
        public int status() { return status; }
        public String code() { return code; }
    }
}
