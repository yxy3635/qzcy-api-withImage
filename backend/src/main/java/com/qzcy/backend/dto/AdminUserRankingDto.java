package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserRankingDto {
    private Long id;
    private String username;
    private String email;
    private Long totalTokens;
    private BigDecimal totalRecharge;
}
