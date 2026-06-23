package com.qzcy.backend.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qzcy.backend.entity.ImageRecord;
import com.qzcy.backend.mapper.ImageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingImageCleanupRunner implements ApplicationRunner {
    private final ImageRecordMapper imageRecordMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureImageRecordErrorColumns();
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(30);
        int updated = imageRecordMapper.update(
                null,
                new LambdaUpdateWrapper<ImageRecord>()
                        .eq(ImageRecord::getStatus, "pending")
                        .lt(ImageRecord::getCreatedAt, staleBefore)
                        .set(ImageRecord::getStatus, "failed")
                        .set(ImageRecord::getErrorType, "timeout")
                        .set(ImageRecord::getErrorMessage, "生图任务超过 30 分钟未完成，系统启动时自动标记失败")
        );
        if (updated > 0) {
            log.warn("已清理启动前遗留的超时pending图像任务，count={}", updated);
        }
    }

    private void ensureImageRecordErrorColumns() {
        if (!tableExists("image_record")) {
            return;
        }
        addColumnIfMissing("generation_model", "VARCHAR(120)");
        addColumnIfMissing("request_url", "VARCHAR(500)");
        addColumnIfMissing("error_status_code", "INT");
        addColumnIfMissing("error_type", "VARCHAR(80)");
        addColumnIfMissing("error_message", "TEXT");
    }

    private void addColumnIfMissing(String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'image_record' AND column_name = ?",
                Integer.class,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE image_record ADD COLUMN " + columnName + " " + definition);
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
