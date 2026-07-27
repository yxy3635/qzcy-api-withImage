package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayModelRecentCallDto {
    private Long id;
    private String model;
    private String status;
    private Integer statusCode;
    private Long durationMs;
    private LocalDateTime createdAt;
}
