package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReferralRebateDto {
    private Long id;
    private Long inviterId;
    private String inviterUsername;
    private Long inviteeId;
    private String inviteeUsername;
    private BigDecimal rechargeAmount;
    private BigDecimal rebateRate;
    private BigDecimal rebateAmount;
    private String status;
    private String rejectReason;
    private String withdrawQrCodeUrl;
    private String withdrawFailReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime withdrawnAt;
    private LocalDateTime createdAt;
}

