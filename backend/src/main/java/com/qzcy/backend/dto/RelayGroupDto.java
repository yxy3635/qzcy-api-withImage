package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayGroupDto {
    private Long id;
    private String code;
    private String name;
    private BigDecimal ratio;
    private Boolean enabled;
    private List<Long> modelIds;
}
