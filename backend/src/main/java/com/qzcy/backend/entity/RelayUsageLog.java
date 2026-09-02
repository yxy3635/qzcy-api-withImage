package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("relay_usage_log")
public class RelayUsageLog {
    private Long id;
    private Long userId;
    private Long tokenId;
    private Long channelId;
    private String tokenName;
    private String channelName;
    @com.baomidou.mybatisplus.annotation.TableField("group_names")
    private String groupNames;
    private String endpoint;
    private String model;
    private String modelType;
    private String thinkingEffort;
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
    /** 流式请求收到上游首字节的耗时（毫秒）；非流式或未记录时为 null */
    private Long firstTokenMs;
    private String userAgent;
    private String status;
    private String message;
    private LocalDateTime createdAt;
}
