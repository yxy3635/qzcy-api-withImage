package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayModelUsageDto {
    private String model;
    private Long requests;
    private Long totalTokens;
    private BigDecimal cost;
}
