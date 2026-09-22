package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 管理员仪表盘近 N 天按天聚合的中转调用趋势。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRelayDailyTrendDto {
    /** 日期，格式 yyyy-MM-dd */
    private String date;
    private Long requests;
    private Long tokens;
    private BigDecimal cost;
    private BigDecimal profit;
}
