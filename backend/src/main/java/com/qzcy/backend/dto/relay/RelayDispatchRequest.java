package com.qzcy.backend.dto.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record RelayDispatchRequest(
        String authorization,
        String apiKeyHeader,
        String queryKey,
        String userAgent,
        String clientIp,
        String endpointType,
        String upstreamPath,
        ObjectNode body
) {
}
