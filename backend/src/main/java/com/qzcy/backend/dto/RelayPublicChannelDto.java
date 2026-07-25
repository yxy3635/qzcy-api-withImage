package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelayPublicChannelDto {
    private Long id;
    private String name;
    private String channelRule;
    private String groupNames;
    private String status;
    private Integer rpmLimit;
    private Integer maxConcurrency;
    private Boolean enabled;
    private List<RelayPublicChannelModelDto> models;
}
