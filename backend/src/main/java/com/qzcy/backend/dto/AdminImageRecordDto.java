package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminImageRecordDto {
    private Long id;
    private Long userId;
    private String username;
    private String prompt;
    private String generatedImageUrl;
    private String status;
    private String generationModel;
    private String requestUrl;
    private Integer errorStatusCode;
    private String errorType;
    private String errorMessage;
    private BigDecimal cost;
    private LocalDateTime createdAt;
}
