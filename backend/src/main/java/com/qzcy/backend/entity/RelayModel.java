package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("relay_model")
public class RelayModel {
    private Long id;
    private String model;
    private String displayName;
    private String modelType;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private BigDecimal cachedInputPrice;
    private BigDecimal cacheCreationPrice;
    private BigDecimal requestPrice;
    private Boolean fixedRequestBilling;
    private Long longContextThreshold;
    private String longContextBillingMode;
    private BigDecimal longContextMultiplier;
    private BigDecimal longContextInputPrice;
    private BigDecimal longContextOutputPrice;
    private BigDecimal longContextCachedInputPrice;
    private BigDecimal longContextCacheCreationPrice;
    private String status;
    private Boolean enabled;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
