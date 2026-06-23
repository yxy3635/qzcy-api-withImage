package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorRequestLogDto {
    private Long id;
    private String source;
    private String tokenName;
    private String channelName;
    private String groupNames;
    private String endpoint;
    private String requestUrl;
    private String model;
    private String modelType;
    private Integer statusCode;
    private Long durationMs;
    private String userAgent;
    private String status;
    private String errorType;
    private String message;
    private String prompt;
    private LocalDateTime createdAt;
}
