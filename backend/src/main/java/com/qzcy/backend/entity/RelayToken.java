package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("relay_token")
public class RelayToken {
    private Long id;
    private Long userId;
    private String name;
    private String token;
    private String tokenPreview;
    @TableField("group_names")
    private String groupNames;
    private String allowedModels;
    private BigDecimal quota;
    private BigDecimal usedQuota;
    private Long requestCount;
    private Long tokenCount;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private String ipWhitelist;
    private LocalDateTime lastUsedAt;
    private Boolean enabled;
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
