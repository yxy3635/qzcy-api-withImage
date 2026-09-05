package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个供应商的尝试记录（失败转移时逐个展示）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayChannelTestAttemptDto {
    private String providerName;
    private String error;
    private long latencyMs;
}
