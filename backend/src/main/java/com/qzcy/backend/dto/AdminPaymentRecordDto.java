package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminPaymentRecordDto {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private BigDecimal amount;
    private BigDecimal rechargeAmount;
    private BigDecimal discountAmount;
    private String couponCode;
    private String type;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
}
