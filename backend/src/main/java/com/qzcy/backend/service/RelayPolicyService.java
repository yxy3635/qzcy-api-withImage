package com.qzcy.backend.service;

import com.qzcy.backend.dto.relay.RelayContext;
import com.qzcy.backend.dto.relay.RelayCostBreakdown;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;

import java.math.BigDecimal;

public interface RelayPolicyService {
    RelayToken requireRelayToken(String authorization);
    RelayToken requireRelayToken(String authorization, String apiKeyHeader, String queryKey);
    void enforceIpAccess(RelayToken access, String clientIp);
    RelayModel requireModel(String model, String endpointType);
    void enforceTokenModelAccess(RelayToken access, String model);
    RelayGroup resolveGroup(String groupNames, RelayModel model);
    RelayChannel chooseChannel(RelayModel model, RelayGroup group);
    void enforceRateLimits(RelayToken access, RelayChannel channel);
    void ensureBalance(Long userId);
    void enforceQuota(RelayToken access, BigDecimal nextCost);
    RelayContext buildContext(String authorization, String apiKeyHeader, String queryKey, String clientIp, String endpointType, String requestedModel);
    RelayCostBreakdown estimateCost(RelayModel model, RelayChannel channel, RelayGroup group, com.fasterxml.jackson.databind.JsonNode responseBody);
}
