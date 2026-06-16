package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
