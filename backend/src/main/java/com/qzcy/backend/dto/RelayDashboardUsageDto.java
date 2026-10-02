package com.qzcy.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 今日按渠道、实际供应商聚合；空供应商表示旧日志或渠道直连。金额单位 USD。 */
@Data
public class RelayDashboardUsageDto {
    private Long channelId;
    private String channelName;
    private Long providerId;
    private String providerName;
    private Long requests;
    private Long errors;
    private BigDecimal cost;
    private BigDecimal upstreamCost;
}
