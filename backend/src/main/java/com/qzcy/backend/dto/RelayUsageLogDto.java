package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayUsageLogDto {
    private Long id;
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
    private BigDecimal cost;
    private Integer statusCode;
    private Long durationMs;
    private String userAgent;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
