package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.AdminImageRecordDto;
import com.qzcy.backend.dto.ErrorRequestLogDto;
import com.qzcy.backend.entity.ImageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ImageRecordMapper extends BaseMapper<ImageRecord> {
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS count FROM image_record WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> generationTrend();

    @Select("""
            SELECT ir.id,
                   ir.user_id AS userId,
                   u.username AS username,
                   ir.prompt,
                   ir.generated_image_url AS generatedImageUrl,
                   ir.status,
                   ir.cost,
                   ir.created_at AS createdAt
            FROM image_record ir
            LEFT JOIN `user` u ON ir.user_id = u.id
            WHERE (#{keyword} IS NULL OR #{keyword} = '' OR u.username LIKE CONCAT('%', #{keyword}, '%') OR ir.prompt LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR #{status} = '' OR ir.status = #{status})
            ORDER BY ir.created_at DESC
            """)
    Page<AdminImageRecordDto> adminImageRecords(Page<AdminImageRecordDto> page,
                                                @Param("keyword") String keyword,
                                                @Param("status") String status);

    @Select("""
            SELECT ir.id,
                   'image' AS source,
                   '生图任务' AS tokenName,
                   '' AS channelName,
                   '' AS groupNames,
                   '/v1/images/generations' AS endpoint,
                   ir.request_url AS requestUrl,
                   ir.generation_model AS model,
                   'image' AS modelType,
                   ir.error_status_code AS statusCode,
                   0 AS durationMs,
                   '' AS userAgent,
                   ir.status,
                   ir.error_type AS errorType,
                   ir.error_message AS message,
                   ir.prompt,
                   ir.created_at AS createdAt
            FROM image_record ir
            WHERE ir.user_id = #{userId}
              AND ir.status = 'failed'
              AND ir.error_message IS NOT NULL
              AND ir.error_message <> ''
            ORDER BY ir.created_at DESC
            LIMIT #{limit}
            """)
    List<ErrorRequestLogDto> imageErrorLogs(@Param("userId") Long userId, @Param("limit") int limit);
}
