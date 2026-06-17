package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("referral_withdraw_request")
public class ReferralWithdrawRequest {
    private Long id;
    private Long userId;
    private BigDecimal amount;
    private String channel;
    private String qrCodeUrl;
    private String status;
    private Long reviewedBy;
    private String failReason;
    private LocalDateTime reviewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
