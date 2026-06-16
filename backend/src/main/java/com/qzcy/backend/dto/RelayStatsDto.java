package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayStatsDto {
    private Long totalChannels;
    private Long activeChannels;
    private Long totalTokens;
    private Long activeTokens;
    private Long totalRequests;
    private Long totalTokensUsed;
    private BigDecimal totalCost;
}
