package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class RelayChannelModelUpdateDto {
    private Long modelId;
    private String upstreamModel;
    private Boolean enabled;
}
