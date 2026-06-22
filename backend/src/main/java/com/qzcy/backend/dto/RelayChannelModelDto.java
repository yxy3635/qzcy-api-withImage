package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayChannelModelDto {
    private Long id;
    private Long channelId;
    private Long modelId;
    private String model;
    private String displayName;
    private String modelType;
    private String upstreamModel;
    private Boolean enabled;
}
