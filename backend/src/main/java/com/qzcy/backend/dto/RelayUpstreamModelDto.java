package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayUpstreamModelDto {
    private String id;
    private String ownedBy;
    private Boolean configured;
}
