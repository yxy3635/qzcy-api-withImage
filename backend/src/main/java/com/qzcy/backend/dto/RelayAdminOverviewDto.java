package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayAdminOverviewDto {
    private RelayStatsDto stats;
    private List<RelayChannelDto> channels;
    private List<RelayTokenDto> tokens;
    private List<RelayModelDto> models;
    private List<RelayGroupDto> groups;
}
