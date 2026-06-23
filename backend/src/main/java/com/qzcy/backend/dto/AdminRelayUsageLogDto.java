package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminRelayUsageLogDto {
    private Long id;
    private Long userId;
    private String username;
    private String tokenName;
    private String channelName;
    private String groupNames;
    private String endpoint;
    private String model;
    private String modelType;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer cachedTokens;
    private Integer cacheCreationTokens;
    private Integer totalTokens;
    private BigDecimal inputCost;
    private BigDecimal outputCost;
    private BigDecimal cacheReadCost;
    private BigDecimal cacheCreationCost;
    private BigDecimal requestCost;
    private BigDecimal groupRatio;
    private BigDecimal channelRatio;
    private BigDecimal cost;
    private Integer statusCode;
    private Long durationMs;
    private String userAgent;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
