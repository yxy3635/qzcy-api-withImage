package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayTrendDto {
    private String date;
    private Long requests;
    private Long promptTokens;
    private Long completionTokens;
    private Long cachedTokens;
    private Long cacheCreationTokens;
    private Long totalTokens;
    private BigDecimal cost;
}
