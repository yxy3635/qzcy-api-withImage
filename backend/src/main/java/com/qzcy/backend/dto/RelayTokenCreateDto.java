package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RelayTokenCreateDto {
    private String name;
    private String groups;
    private String allowedModels;
    private BigDecimal quota;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private String ipWhitelist;
    private Boolean enabled;
}
