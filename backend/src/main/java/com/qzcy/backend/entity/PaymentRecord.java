package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {
    private Long id;
    private Long userId;
    private BigDecimal amount;
    private BigDecimal rechargeAmount;
    private BigDecimal discountAmount;
    private Long couponId;
    private String couponCode;
    private BigDecimal couponDiscountPercent;
    private String type;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
}
