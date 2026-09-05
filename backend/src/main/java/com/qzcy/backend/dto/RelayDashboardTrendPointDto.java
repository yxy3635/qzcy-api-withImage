package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayDashboardTrendPointDto {
    /** 小时桶，格式 yyyy-MM-dd HH:00 */
    private String hour;
    private Long requests;
    private Long errors;
    private Long totalTokens;
    private BigDecimal cost;
}
