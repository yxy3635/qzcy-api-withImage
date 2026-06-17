package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("referral_rebate_record")
public class ReferralRebateRecord {
    private Long id;
    private Long inviterId;
    private Long inviteeId;
    private Long paymentRecordId;
    private BigDecimal rechargeAmount;
    private BigDecimal rebateRate;
    private BigDecimal rebateAmount;
    private String status;
    private Long reviewedBy;
    private String rejectReason;
    private String withdrawQrCodeUrl;
    private String withdrawFailReason;
    private LocalDateTime reviewedAt;
    private LocalDateTime withdrawnAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
