package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayTokenDto {
    private Long id;
    private Long userId;
    private String username;
    private String name;
    private String tokenPreview;
    private String plainToken;
    private String groups;
    private String allowedModels;
    private BigDecimal quota;
    private BigDecimal usedQuota;
    private Long requestCount;
    private Long tokenCount;
    private Integer rpmLimit;
    private Integer tpmLimit;
    private String ipWhitelist;
    private Boolean enabled;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
