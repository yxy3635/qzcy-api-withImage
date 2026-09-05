package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayDashboardSummaryDto {
    private Integer channelsTotal;
    private Integer channelsAvailable;
    private Integer providersTotal;
    private Integer providersAvailable;
    private Long todayRequests;
    private Long todayErrors;
    private Double errorRate;
    private Long todayTokens;
    private BigDecimal todayCost;
    private Long currentRpm;
}
