package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RelayGroupUpdateDto {
    private String code;
    private String name;
    private BigDecimal ratio;
    private Boolean enabled;
    private List<Long> modelIds;
}
