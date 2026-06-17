package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReferralWithdrawRequestDto {
    private Long id;
    private Long userId;
    private String username;
    private BigDecimal amount;
    private String channel;
    private String qrCodeUrl;
    private String status;
    private String failReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
