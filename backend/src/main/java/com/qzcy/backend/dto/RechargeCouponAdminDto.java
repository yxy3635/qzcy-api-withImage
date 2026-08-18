package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RechargeCouponAdminDto {
    private Long id;
    private String code;
    private BigDecimal discountPercent;
    private Integer maxUsesPerUser;
    private BigDecimal maxDiscountAmount;
    private Long usedCount;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
