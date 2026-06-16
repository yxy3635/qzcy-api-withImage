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
    private BigDecimal requestPrice;
    private Boolean fixedRequestBilling;
    private String status;
    private Boolean enabled;
    private Integer sortOrder;
}
