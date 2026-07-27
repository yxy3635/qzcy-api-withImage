package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminUserUsageDto {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Boolean banned;
    private BigDecimal balance;
    private LocalDateTime createdAt;
    private Long todayRequests;
    private Long yesterdayRequests;
    private BigDecimal todayCost;
    private BigDecimal yesterdayCost;
    private Long totalTokens;
    private BigDecimal totalCost;
    private BigDecimal totalRecharge;
}
