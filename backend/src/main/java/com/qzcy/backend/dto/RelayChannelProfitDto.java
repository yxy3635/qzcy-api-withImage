package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayChannelProfitDto {
    private Long channelId;
    private String channelName;
    private Long requests;
    private Long totalTokens;
    private BigDecimal upstreamCost;
    private BigDecimal siteCost;
    private BigDecimal profit;
}
