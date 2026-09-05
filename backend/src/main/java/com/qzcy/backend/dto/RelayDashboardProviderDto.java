package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayDashboardProviderDto {
    private Long id;
    private String name;
    private String channelRule;
    private String status;
    private Boolean enabled;
    private Boolean circuitOpen;
}
