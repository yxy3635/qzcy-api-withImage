package com.qzcy.backend.mapper;

import com.qzcy.backend.dto.RelayReportDto.Aggregate;
import com.qzcy.backend.dto.RelayReportDto.TrendAggregate;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/** Only aggregate rows leave MySQL. Names never participate in identity grouping. */
@Mapper
public interface RelayReportMapper {
    @Select("""
            SELECT COALESCE(NULLIF(TRIM(model), ''), '未记录模型') AS model,
                   CASE
                       WHEN NULLIF(TRIM(billing_group_code), '') IS NOT NULL
                           THEN CONCAT('group:', TRIM(billing_group_code))
                       WHEN NULLIF(TRIM(group_names), '') IS NOT NULL AND group_names NOT LIKE '%,%'
                           THEN CONCAT('group:', TRIM(group_names))
                       ELSE CONCAT('legacy:', COALESCE(NULLIF(TRIM(group_names), ''), '未记录分组'))
                   END AS groupKey,
                   channel_id AS channelId, MAX(channel_name) AS channelName,
                   provider_id AS providerId, MAX(provider_name) AS providerName,
                   COALESCE(SUM(1), 0) AS total_requests,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN 1 ELSE 0 END), 0) AS today_requests,
                   COALESCE(SUM(CASE WHEN status = 'success' AND status_code BETWEEN 200 AND 299 THEN 0 ELSE 1 END), 0) AS total_errors,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN CASE WHEN status = 'success' AND status_code BETWEEN 200 AND 299 THEN 0 ELSE 1 END ELSE 0 END), 0) AS today_errors,
                   COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS total_tokens,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(total_tokens, 0) ELSE 0 END), 0) AS today_tokens,
                   COALESCE(SUM(COALESCE(prompt_tokens, 0)), 0) AS total_inputTokens,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(prompt_tokens, 0) ELSE 0 END), 0) AS today_inputTokens,
                   COALESCE(SUM(COALESCE(completion_tokens, 0)), 0) AS total_outputTokens,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(completion_tokens, 0) ELSE 0 END), 0) AS today_outputTokens,
                   COALESCE(SUM(COALESCE(cached_tokens, 0)), 0) AS total_cacheReadTokens,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(cached_tokens, 0) ELSE 0 END), 0) AS today_cacheReadTokens,
                   COALESCE(SUM(COALESCE(cache_creation_tokens, 0)), 0) AS total_cacheCreationTokens,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(cache_creation_tokens, 0) ELSE 0 END), 0) AS today_cacheCreationTokens,
                   COALESCE(SUM(COALESCE(cache_creation1h_tokens, 0)), 0) AS total_cacheCreation1hTokens,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(cache_creation1h_tokens, 0) ELSE 0 END), 0) AS today_cacheCreation1hTokens,
                   COALESCE(SUM(COALESCE(cost, 0)), 0) AS total_cost,
                   COALESCE(SUM(CASE WHEN created_at >= #{start} THEN COALESCE(cost, 0) ELSE 0 END), 0) AS today_cost
            FROM relay_usage_log
            WHERE created_at < #{cutoff}
            GROUP BY model, groupKey, channel_id, provider_id
            """)
    @Results({
            @Result(column = "model", property = "model"),
            @Result(column = "groupKey", property = "groupKey"),
            @Result(column = "channelId", property = "channelId"),
            @Result(column = "channelName", property = "channelName"),
            @Result(column = "providerId", property = "providerId"),
            @Result(column = "providerName", property = "providerName"),
            @Result(column = "total_requests", property = "total.requests"),
            @Result(column = "today_requests", property = "today.requests"),
            @Result(column = "total_errors", property = "total.errors"),
            @Result(column = "today_errors", property = "today.errors"),
            @Result(column = "total_tokens", property = "total.tokens"),
            @Result(column = "today_tokens", property = "today.tokens"),
            @Result(column = "total_inputTokens", property = "total.inputTokens"),
            @Result(column = "today_inputTokens", property = "today.inputTokens"),
            @Result(column = "total_outputTokens", property = "total.outputTokens"),
            @Result(column = "today_outputTokens", property = "today.outputTokens"),
            @Result(column = "total_cacheReadTokens", property = "total.cacheReadTokens"),
            @Result(column = "today_cacheReadTokens", property = "today.cacheReadTokens"),
            @Result(column = "total_cacheCreationTokens", property = "total.cacheCreationTokens"),
            @Result(column = "today_cacheCreationTokens", property = "today.cacheCreationTokens"),
            @Result(column = "total_cacheCreation1hTokens", property = "total.cacheCreation1hTokens"),
            @Result(column = "today_cacheCreation1hTokens", property = "today.cacheCreation1hTokens"),
            @Result(column = "total_cost", property = "total.cost"),
            @Result(column = "today_cost", property = "today.cost")
    })
    List<Aggregate> aggregates(@Param("start") LocalDateTime start, @Param("cutoff") LocalDateTime cutoff);

    String TREND_METRICS = """
                   COALESCE(SUM(1), 0) AS requests,
                   COALESCE(SUM(CASE WHEN status = 'success' AND status_code BETWEEN 200 AND 299 THEN 0 ELSE 1 END), 0) AS errors,
                   COALESCE(SUM(COALESCE(total_tokens, 0)), 0) AS tokens,
                   COALESCE(SUM(COALESCE(prompt_tokens, 0)), 0) AS inputTokens,
                   COALESCE(SUM(COALESCE(completion_tokens, 0)), 0) AS outputTokens,
                   COALESCE(SUM(COALESCE(cached_tokens, 0)), 0) AS cacheReadTokens,
                   COALESCE(SUM(COALESCE(cache_creation_tokens, 0)), 0) AS cacheCreationTokens,
                   COALESCE(SUM(COALESCE(cache_creation1h_tokens, 0)), 0) AS cacheCreation1hTokens,
                   COALESCE(SUM(COALESCE(cost, 0)), 0) AS cost
            FROM relay_usage_log
            WHERE created_at >= #{start} AND created_at < #{cutoff}
            GROUP BY bucket ORDER BY bucket
            """;

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00') AS bucket, " + TREND_METRICS)
    @Results(id = "reportTrend", value = {
            @Result(column = "bucket", property = "bucket"),
            @Result(column = "requests", property = "metrics.requests"),
            @Result(column = "errors", property = "metrics.errors"),
            @Result(column = "tokens", property = "metrics.tokens"),
            @Result(column = "inputTokens", property = "metrics.inputTokens"),
            @Result(column = "outputTokens", property = "metrics.outputTokens"),
            @Result(column = "cacheReadTokens", property = "metrics.cacheReadTokens"),
            @Result(column = "cacheCreationTokens", property = "metrics.cacheCreationTokens"),
            @Result(column = "cacheCreation1hTokens", property = "metrics.cacheCreation1hTokens"),
            @Result(column = "cost", property = "metrics.cost")
    })
    List<TrendAggregate> hourly(@Param("start") LocalDateTime start, @Param("cutoff") LocalDateTime cutoff);

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS bucket, " + TREND_METRICS)
    @ResultMap("reportTrend")
    List<TrendAggregate> daily(@Param("start") LocalDateTime start, @Param("cutoff") LocalDateTime cutoff);
}
