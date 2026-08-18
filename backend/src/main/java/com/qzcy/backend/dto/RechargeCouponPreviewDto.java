package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeCouponPreviewDto {
    private boolean valid;
    private Long couponId;
    private String code;
    private BigDecimal discountPercent;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
    private Long remainingUses;
    private String message;
}
