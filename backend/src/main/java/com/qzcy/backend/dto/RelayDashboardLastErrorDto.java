package com.qzcy.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 仪表盘每渠道最近一次失败请求（MyBatis 按别名映射）。 */
@Data
public class RelayDashboardLastErrorDto {
    private Long channelId;
    private LocalDateTime lastErrorAt;
    private Integer lastErrorCode;
}
