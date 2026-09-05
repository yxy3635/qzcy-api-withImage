package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayDashboardChannelDto {
    private Long id;
    private String name;
    private String status;
    private Boolean enabled;
    private Integer priority;
    private Integer weight;
    private String scheduleStrategy;
    private String groupNames;
    /** ok=有可用供应商; degraded=供应商状态未知; down=启用供应商全部失败/缺失; disabled=渠道停用 */
    private String health;
    private List<RelayDashboardProviderDto> providers;
    private Long requests24h;
    private Long errors24h;
    private Long avgDurationMs;
    private Long avgFirstTokenMs;
    private Long tokens24h;
    private BigDecimal cost24h;
    private LocalDateTime lastErrorAt;
    private Integer lastErrorCode;
}
