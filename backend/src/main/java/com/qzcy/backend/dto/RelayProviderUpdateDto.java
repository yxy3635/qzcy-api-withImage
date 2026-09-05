package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class RelayProviderUpdateDto {
    private Long id;
    private String name;
    private String apiBaseUrl;
    private String apiKey;
    private String channelRule;
    private Integer priority;
    private Integer weight;
    private Boolean enabled;
}
