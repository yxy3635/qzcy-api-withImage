package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class DashboardStats {
    private Long totalUsers;
    private Long totalImages;
    private Long todayImages;
    private BigDecimal totalRevenue;
    private BigDecimal relaySiteCost;
    private BigDecimal relayUpstreamCost;
    private BigDecimal relayProfit;
    private Long todayRelayRequests;
    private Long yesterdayRelayRequests;
    private Long todayRelayTokens;
    private Long yesterdayRelayTokens;
    private BigDecimal todayRelayCost;
    private BigDecimal yesterdayRelayCost;
    private BigDecimal todayRelayUpstreamCost;
    private BigDecimal yesterdayRelayUpstreamCost;
    private BigDecimal todayRelayProfit;
    private BigDecimal yesterdayRelayProfit;
    private List<RelayChannelProfitDto> relayChannelProfits;
    private List<Map<String, Object>> recentRegistrations;
    private List<Map<String, Object>> generationTrend;
}
