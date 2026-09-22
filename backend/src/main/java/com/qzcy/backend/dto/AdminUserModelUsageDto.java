package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 用户当日按模型 + 分组聚合的调用量。 */
@Data
public class AdminUserModelUsageDto {
    private String model;
    private String groupNames;
    private Long requests;
    private Long totalTokens;
    private BigDecimal cost;
}
