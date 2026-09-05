package com.qzcy.backend.dto;

import lombok.Data;

/** 渠道真实调用测试请求体。 */
@Data
public class RelayChannelTestRequestDto {
    private Long modelId;
    private String prompt;
}
