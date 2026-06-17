package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReferralInviteeDto {
    private Long userId;
    private String username;
    private BigDecimal totalRecharge;
    private LocalDateTime registeredAt;
}
