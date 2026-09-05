package com.qzcy.backend.service;

import com.qzcy.backend.dto.RelayChannelTestRequestDto;
import com.qzcy.backend.dto.RelayChannelTestResultDto;

public interface RelayChannelTestService {
    /** 按渠道调度规则对选定模型发起一次真实对话请求，返回回复与实际生效的供应商/模型。 */
    RelayChannelTestResultDto test(Long channelId, RelayChannelTestRequestDto request);
}
