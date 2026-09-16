package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RelayToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface RelayTokenMapper extends BaseMapper<RelayToken> {
    /** Revoke credentials without destroying the historical counters or log references. */
    @Update("""
            UPDATE relay_token
            SET deleted = 1, enabled = 0, token = CONCAT('deleted-', id), token_preview = '', updated_at = NOW()
            WHERE id = #{tokenId} AND user_id = #{userId} AND deleted = 0
            """)
    int revokeAndDelete(@Param("userId") Long userId, @Param("tokenId") Long tokenId);

    /** Raw SQL intentionally includes logically deleted keys in lifetime account totals. */
    @Select("""
            SELECT COALESCE(SUM(request_count), 0) AS request_count,
                   COALESCE(SUM(token_count), 0) AS token_count,
                   COALESCE(SUM(used_quota), 0) AS used_quota
            FROM relay_token WHERE user_id = #{userId}
            """)
    RelayToken lifetimeUsage(@Param("userId") Long userId);

    /**
     * Increment usage counters in the database so concurrent relay requests cannot
     * overwrite each other's totals with stale entity snapshots.
     */
    @Update("""
            UPDATE relay_token
            SET request_count = COALESCE(request_count, 0) + #{requestCount},
                token_count = COALESCE(token_count, 0) + #{tokenCount},
                used_quota = COALESCE(used_quota, 0) + #{usedQuota},
                last_used_at = #{lastUsedAt},
                updated_at = #{updatedAt}
            WHERE id = #{tokenId}
            """)
    int incrementUsage(@Param("tokenId") Long tokenId,
                       @Param("requestCount") long requestCount,
                       @Param("tokenCount") long tokenCount,
                       @Param("usedQuota") BigDecimal usedQuota,
                       @Param("lastUsedAt") LocalDateTime lastUsedAt,
                       @Param("updatedAt") LocalDateTime updatedAt);
}
