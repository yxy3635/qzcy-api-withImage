package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminUserGiftDto {
    private BigDecimal amount;
    private String remark;
}
