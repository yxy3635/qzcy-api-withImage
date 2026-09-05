package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayDashboardErrorDto {
    private Long id;
    private String channelName;
    private String model;
    private Integer statusCode;
    private Long durationMs;
    private String message;
    private LocalDateTime createdAt;
}
