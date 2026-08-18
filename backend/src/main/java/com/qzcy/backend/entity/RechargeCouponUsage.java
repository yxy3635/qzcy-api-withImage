package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recharge_coupon_usage")
public class RechargeCouponUsage {
    private Long id;
    private Long couponId;
    private Long userId;
    private Long paymentRecordId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
