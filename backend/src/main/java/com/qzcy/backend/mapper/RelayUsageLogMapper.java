package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.AdminRelayUsageLogDto;
import com.qzcy.backend.dto.RelayChannelProfitDto;
import com.qzcy.backend.dto.RelayModelUsageDto;
import com.qzcy.backend.dto.RelayTrendDto;
import com.qzcy.backend.entity.RelayUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RelayUsageLogMapper extends BaseMapper<RelayUsageLog> {
    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log")
    Long totalTokens();

    @Select("SELECT COALESCE(SUM(cost), 0) FROM relay_usage_log")
    BigDecimal totalCost();

    @Select("SELECT COUNT(*) FROM relay_usage_log WHERE DATE(created_at) = CURDATE()")
    Long todayRequests();

    @Select("SELECT COUNT(*) FROM relay_usage_log WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    Long yesterdayRequests();

    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log WHERE DATE(created_at) = CURDATE()")
    Long todayTokens();

    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    Long yesterdayTokens();

    @Select("SELECT COALESCE(SUM(cost), 0) FROM relay_usage_log WHERE DATE(created_at) = CURDATE()")
    BigDecimal todayCost();

    @Select("SELECT COALESCE(SUM(cost), 0) FROM relay_usage_log WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)")
    BigDecimal yesterdayCost();

    @Select("""
            SELECT COALESCE(SUM((input_cost + output_cost + cache_read_cost + cache_creation_cost + request_cost) * channel_ratio), 0)
            FROM relay_usage_log
            WHERE DATE(created_at) = CURDATE()
            """)
    BigDecimal todayUpstreamCost();

    @Select("""
            SELECT COALESCE(SUM((input_cost + output_cost + cache_read_cost + cache_creation_cost + request_cost) * channel_ratio), 0)
            FROM relay_usage_log
            WHERE DATE(created_at) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
            """)
    BigDecimal yesterdayUpstreamCost();

    @Select("SELECT COALESCE(SUM(prompt_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId}")
    Long userPromptTokens(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(completion_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId}")
    Long userCompletionTokens(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(cached_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId}")
    Long userCachedTokens(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(cache_creation_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId}")
    Long userCacheCreationTokens(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM relay_usage_log WHERE user_id = #{userId} AND created_at >= #{since}")
    Long userRequestsSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM relay_usage_log WHERE user_id = #{userId} AND DATE(created_at) = CURDATE()")
    Long userTodayRequests(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId} AND created_at >= #{since}")
    Long userTokensSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId} AND DATE(created_at) = CURDATE()")
    Long userTodayTokens(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(prompt_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId} AND created_at >= #{since}")
    Long userPromptTokensSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(prompt_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId} AND DATE(created_at) = CURDATE()")
    Long userTodayPromptTokens(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(completion_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId} AND created_at >= #{since}")
    Long userCompletionTokensSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(completion_tokens), 0) FROM relay_usage_log WHERE user_id = #{userId} AND DATE(created_at) = CURDATE()")
    Long userTodayCompletionTokens(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(cost), 0) FROM relay_usage_log WHERE user_id = #{userId} AND created_at >= #{since}")
    BigDecimal userCostSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(cost), 0) FROM relay_usage_log WHERE user_id = #{userId} AND DATE(created_at) = CURDATE()")
    BigDecimal userTodayCost(@Param("userId") Long userId);

    @Select("""
            SELECT COALESCE(SUM((input_cost + output_cost + cache_read_cost + cache_creation_cost + request_cost) * channel_ratio), 0)
            FROM relay_usage_log
            """)
    BigDecimal totalUpstreamCost();

    @Select("""
            SELECT channel_id AS channelId,
                   COALESCE(channel_name, 'Unknown') AS channelName,
                   COUNT(*) AS requests,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM((input_cost + output_cost + cache_read_cost + cache_creation_cost + request_cost) * channel_ratio), 0) AS upstreamCost,
                   COALESCE(SUM(cost), 0) AS siteCost,
                   COALESCE(SUM(cost), 0) - COALESCE(SUM((input_cost + output_cost + cache_read_cost + cache_creation_cost + request_cost) * channel_ratio), 0) AS profit
            FROM relay_usage_log
            GROUP BY channel_id, channel_name
            ORDER BY profit DESC, requests DESC
            LIMIT 20
            """)
    List<RelayChannelProfitDto> channelProfits();

    @Select("SELECT COALESCE(AVG(duration_ms), 0) FROM relay_usage_log WHERE user_id = #{userId}")
    Long averageDurationMs(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM relay_usage_log WHERE token_id = #{tokenId} AND created_at >= #{since}")
    Long tokenRequestsSince(@Param("tokenId") Long tokenId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log WHERE token_id = #{tokenId} AND created_at >= #{since}")
    Long tokenTokensSince(@Param("tokenId") Long tokenId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(cost), 0) FROM relay_usage_log WHERE token_id = #{tokenId} AND DATE(created_at) = CURDATE()")
    BigDecimal tokenTodayCost(@Param("tokenId") Long tokenId);

    @Select("SELECT COUNT(*) FROM relay_usage_log WHERE channel_id = #{channelId} AND created_at >= #{since}")
    Long channelRequestsSince(@Param("channelId") Long channelId, @Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(total_tokens), 0) FROM relay_usage_log WHERE channel_id = #{channelId} AND created_at >= #{since}")
    Long channelTokensSince(@Param("channelId") Long channelId, @Param("since") LocalDateTime since);

    @Select("""
            SELECT model AS model,
                   COUNT(*) AS requests,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM relay_usage_log
            WHERE user_id = #{userId}
            GROUP BY model
            ORDER BY requests DESC
            LIMIT 10
            """)
    List<RelayModelUsageDto> modelUsage(@Param("userId") Long userId);

    @Select("""
            SELECT DATE(created_at) AS date,
                   COUNT(*) AS requests,
                   COALESCE(SUM(prompt_tokens), 0) AS promptTokens,
                   COALESCE(SUM(completion_tokens), 0) AS completionTokens,
                   COALESCE(SUM(cached_tokens), 0) AS cachedTokens,
                   COALESCE(SUM(cache_creation_tokens), 0) AS cacheCreationTokens,
                   COALESCE(SUM(total_tokens), 0) AS totalTokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM relay_usage_log
            WHERE user_id = #{userId}
              AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at)
            """)
    List<RelayTrendDto> userTrend(@Param("userId") Long userId);

    @Select("""
            SELECT r.id,
                   r.user_id AS userId,
                   u.username AS username,
                   r.token_name AS tokenName,
                   r.channel_name AS channelName,
                   r.group_names AS groupNames,
                   r.endpoint,
                   r.model,
                   r.model_type AS modelType,
                   r.prompt_tokens AS promptTokens,
                   r.completion_tokens AS completionTokens,
                   r.cached_tokens AS cachedTokens,
                   r.cache_creation_tokens AS cacheCreationTokens,
                   r.total_tokens AS totalTokens,
                   r.input_cost AS inputCost,
                   r.output_cost AS outputCost,
                   r.cache_read_cost AS cacheReadCost,
                   r.cache_creation_cost AS cacheCreationCost,
                   r.request_cost AS requestCost,
                   r.group_ratio AS groupRatio,
                   r.channel_ratio AS channelRatio,
                   r.cost,
                   r.status_code AS statusCode,
                   r.duration_ms AS durationMs,
                   r.user_agent AS userAgent,
                   r.status,
                   r.message,
                   r.created_at AS createdAt
            FROM relay_usage_log r
            LEFT JOIN `user` u ON r.user_id = u.id
            WHERE (#{keyword} IS NULL OR #{keyword} = ''
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR r.token_name LIKE CONCAT('%', #{keyword}, '%')
                   OR r.channel_name LIKE CONCAT('%', #{keyword}, '%')
                   OR r.group_names LIKE CONCAT('%', #{keyword}, '%')
                   OR r.model LIKE CONCAT('%', #{keyword}, '%')
                   OR r.endpoint LIKE CONCAT('%', #{keyword}, '%')
                   OR r.user_agent LIKE CONCAT('%', #{keyword}, '%')
                   OR r.message LIKE CONCAT('%', #{keyword}, '%'))
              AND (#{status} IS NULL OR #{status} = '' OR r.status = #{status}
                   OR (#{status} = 'error' AND (r.status = 'failed' OR r.status_code >= 400)))
            ORDER BY r.created_at DESC
            """)
    Page<AdminRelayUsageLogDto> adminUsageLogs(Page<AdminRelayUsageLogDto> page,
                                               @Param("keyword") String keyword,
                                               @Param("status") String status);
}
