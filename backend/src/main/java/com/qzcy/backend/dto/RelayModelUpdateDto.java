package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RelayModelUpdateDto {
    private String model;
    private String displayName;
    private String modelType;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private BigDecimal cachedInputPrice;
    private BigDecimal cacheCreationPrice;
    private BigDecimal cacheCreation1hPrice;
    private BigDecimal requestPrice;
    private Boolean fixedRequestBilling;
    private Long longContextThreshold;
    private String longContextBillingMode;
    private BigDecimal longContextMultiplier;
    private BigDecimal longContextInputPrice;
    private BigDecimal longContextOutputPrice;
    private BigDecimal longContextCachedInputPrice;
    private BigDecimal longContextCacheCreationPrice;
    private BigDecimal longContextCacheCreation1hPrice;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean cacheCreation1hPriceSet;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean longContextCacheCreation1hPriceSet;

    @com.fasterxml.jackson.annotation.JsonSetter("cacheCreation1hPrice")
    public void setCacheCreation1hPrice(BigDecimal value) {
        this.cacheCreation1hPrice = value;
        this.cacheCreation1hPriceSet = true;
    }

    @com.fasterxml.jackson.annotation.JsonSetter("longContextCacheCreation1hPrice")
    public void setLongContextCacheCreation1hPrice(BigDecimal value) {
        this.longContextCacheCreation1hPrice = value;
        this.longContextCacheCreation1hPriceSet = true;
    }

    private String status;
    private Boolean enabled;
    private Integer sortOrder;
}
