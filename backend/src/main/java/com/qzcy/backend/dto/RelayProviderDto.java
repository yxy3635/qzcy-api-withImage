package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayProviderDto {
    private Long id;
    private Long channelId;
    private String name;
    private String apiBaseUrl;
    private String apiKeyMasked;
    private String channelRule;
    private Integer priority;
    private Integer weight;
    private String status;
    private Boolean enabled;
}
