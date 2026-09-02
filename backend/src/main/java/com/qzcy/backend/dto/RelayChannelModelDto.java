package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayChannelModelDto {
    private Long id;
    private Long channelId;
    private Long modelId;
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
    private String upstreamModel;
    private Boolean enabled;
}
