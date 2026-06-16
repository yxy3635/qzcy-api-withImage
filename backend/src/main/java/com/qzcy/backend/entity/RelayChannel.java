package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("relay_channel")
public class RelayChannel {
    private Long id;
    private String name;
    private String provider;
    private String apiBaseUrl;
    private String apiKey;
    private String groupNames;
    private String status;
    private Integer priority;
    private Integer weight;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private BigDecimal priceMultiplier;
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
