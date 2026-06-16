package com.qzcy.backend.dto.relay;

import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;

public record RelayContext(
        RelayToken token,
        RelayModel model,
        RelayGroup group,
        RelayChannel channel,
        String effectiveModelType
) {
}
