package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 仪表盘每渠道 24h 聚合行（MyBatis 按别名映射）。 */
@Data
public class RelayDashboardChannelStatsDto {
    private Long channelId;
    private Long requests;
    private Long errors;
    private Long avgDurationMs;
    private Long avgFirstTokenMs;
    private Long totalTokens;
    private BigDecimal cost;
    private LocalDateTime lastErrorAt;
}
