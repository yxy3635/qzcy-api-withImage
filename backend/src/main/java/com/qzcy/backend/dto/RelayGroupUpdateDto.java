package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RelayGroupUpdateDto {
    private String code;
    private String name;
    private BigDecimal ratio;
    private Boolean enabled;
}
