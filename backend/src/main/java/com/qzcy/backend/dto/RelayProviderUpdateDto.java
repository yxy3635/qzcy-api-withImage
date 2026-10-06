package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class RelayProviderUpdateDto {
    private java.util.List<Long> modelIds;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean modelSelectionProvided;

    public void setModelIds(java.util.List<Long> modelIds) {
        this.modelIds = modelIds;
        this.modelSelectionProvided = true;
    }
    private Long id;
    private String name;
    private String apiBaseUrl;
    private String openaiBaseUrl;
    private String anthropicBaseUrl;
    private String apiKey;
    private String channelRule;
    private Integer priority;
    private Integer weight;
    private Boolean enabled;
}
