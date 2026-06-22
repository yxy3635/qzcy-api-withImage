package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayChannelDto {
    private Long id;
    private String name;
    private String provider;
    private String apiBaseUrl;
    private String apiKeyMasked;
    private String groupNames;
    private String remark;
    private String status;
    private Integer priority;
    private Integer weight;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private BigDecimal priceMultiplier;
    private Boolean enabled;
    private List<RelayChannelModelDto> models;
}
