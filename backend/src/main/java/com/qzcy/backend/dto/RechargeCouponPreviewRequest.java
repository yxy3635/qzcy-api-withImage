package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeCouponPreviewRequest {
    private BigDecimal amount;
    private String code;
}
