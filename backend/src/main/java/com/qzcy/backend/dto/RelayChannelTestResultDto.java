package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 渠道真实调用测试结果：成功时带供应商/实际模型/回复，失败时带逐个尝试明细。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayChannelTestResultDto {
    private boolean success;
    private String providerName;
    private Long providerId;
    private String rule;
    private String model;
    private String upstreamModel;
    private long latencyMs;
    private String content;
    private String error;
    private List<RelayChannelTestAttemptDto> attempts;
}
