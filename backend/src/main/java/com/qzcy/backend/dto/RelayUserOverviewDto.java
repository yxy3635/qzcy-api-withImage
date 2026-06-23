package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayUserOverviewDto {
    private BigDecimal balance;
    private List<RelayModelDto> models;
    private List<RelayTokenDto> tokens;
    private List<RelayChannelDto> channels;
    private List<RelayUsageLogDto> logs;
    private List<ErrorRequestLogDto> errorLogs;
    private List<RelayModelUsageDto> modelUsage;
    private List<RelayTrendDto> trend;
    private List<RelayGroupDto> groups;
    private Long totalRequests;
    private Long totalTokens;
    private BigDecimal totalCost;
    private Long averageDurationMs;
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalCachedTokens;
    private Long totalCacheCreationTokens;
    private Long todayRequests;
    private Long todayPromptTokens;
    private Long todayCompletionTokens;
    private Long todayTotalTokens;
    private BigDecimal todayCost;
    private Long currentRpm;
    private Long currentTpm;
}
