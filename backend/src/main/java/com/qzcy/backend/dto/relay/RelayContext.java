package com.qzcy.backend.dto.relay;

import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;

/**
 * 一次派发的候选上下文。provider 为当前候选的渠道内供应商；
 * 老数据（渠道自身凭证兜底）允许 provider 为空。
 */
public record RelayContext(
        RelayToken token,
        RelayModel model,
        RelayGroup group,
        RelayChannel channel,
        RelayChannelProvider provider,
        RelayChannelModel channelModel,
        String effectiveModelType
) {
}
