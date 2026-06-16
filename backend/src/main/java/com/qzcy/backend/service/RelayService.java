package com.qzcy.backend.service;

import com.qzcy.backend.dto.RelayAdminOverviewDto;
import com.qzcy.backend.dto.RelayChannelDto;
import com.qzcy.backend.dto.RelayChannelUpdateDto;
import com.qzcy.backend.dto.RelayGroupDto;
import com.qzcy.backend.dto.RelayGroupUpdateDto;
import com.qzcy.backend.dto.RelayModelDto;
import com.qzcy.backend.dto.RelayModelUpdateDto;
import com.qzcy.backend.dto.RelayTokenCreateDto;
import com.qzcy.backend.dto.RelayTokenDto;
import com.qzcy.backend.dto.RelayUpstreamModelDto;
import com.qzcy.backend.dto.RelayUserOverviewDto;

import java.util.List;

public interface RelayService {
    RelayAdminOverviewDto adminOverview();
    RelayChannelDto createChannel(RelayChannelUpdateDto dto);
    RelayChannelDto updateChannel(Long id, RelayChannelUpdateDto dto);
    RelayGroupDto createGroup(RelayGroupUpdateDto dto);
    RelayGroupDto updateGroup(Long id, RelayGroupUpdateDto dto);
    void deleteGroup(Long id);
    RelayModelDto createModel(RelayModelUpdateDto dto);
    RelayModelDto updateModel(Long id, RelayModelUpdateDto dto);
    void deleteModel(Long id);
    List<RelayUpstreamModelDto> fetchUpstreamModels(Long channelId);
    RelayTokenDto createToken(Long userId, RelayTokenCreateDto dto);
    RelayTokenDto updateToken(Long userId, Long tokenId, RelayTokenCreateDto dto);
    void deleteToken(Long userId, Long tokenId);
    RelayUserOverviewDto userOverview(Long userId);
}
