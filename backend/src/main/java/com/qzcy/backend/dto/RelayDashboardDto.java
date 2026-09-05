package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayDashboardDto {
    private RelayDashboardSummaryDto summary;
    private List<RelayDashboardTrendPointDto> trend;
    private List<RelayDashboardChannelDto> channels;
    private List<RelayDashboardErrorDto> recentErrors;
    private List<RelayModelUsageDto> topModels;
}
