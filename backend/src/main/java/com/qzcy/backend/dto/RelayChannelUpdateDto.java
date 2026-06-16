package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RelayChannelUpdateDto {
    private String name;
    private String provider;
    private String apiBaseUrl;
    private String apiKey;
    private String groupNames;
    private Integer priority;
    private Integer weight;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private BigDecimal priceMultiplier;
    private Boolean enabled;
}
