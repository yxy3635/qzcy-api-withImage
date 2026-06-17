package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_config")
public class PaymentConfig {
    private Long id;
    private String apiUrl;
    private String merchantId;
    private String merchantSecret;
    private BigDecimal registerGiftAmount;
    private BigDecimal referralRebateRate;
    private Boolean enabled;
    private Boolean alipayEnabled;
    private Boolean wxpayEnabled;
    private Boolean qqpayEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
