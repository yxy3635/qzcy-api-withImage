package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RelayToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface RelayTokenMapper extends BaseMapper<RelayToken> {
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
