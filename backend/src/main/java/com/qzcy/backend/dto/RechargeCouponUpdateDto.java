package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RechargeCouponUpdateDto {
    private String code;
    private BigDecimal discountPercent;
    private Integer maxUsesPerUser;
    private BigDecimal maxDiscountAmount;
    private Boolean enabled;
}
