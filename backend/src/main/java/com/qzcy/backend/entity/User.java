package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {
    private Long id;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    private String role;
    private Boolean banned;
    private BigDecimal balance;
    private BigDecimal referralBalance;
    private String invitationCode;
    private Long inviterId;
    private Integer version;
    @JsonIgnore
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Long authVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
