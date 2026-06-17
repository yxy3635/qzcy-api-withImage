package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReferralActionDto {
    private String reason;
    private String qrCodeUrl;
    private String channel;
    private BigDecimal amount;
}
