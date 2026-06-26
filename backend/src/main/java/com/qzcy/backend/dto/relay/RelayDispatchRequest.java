package com.qzcy.backend.dto.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public record RelayDispatchRequest(
        String authorization,
        String apiKeyHeader,
        String queryKey,
        String userAgent,
        String clientIp,
        String anthropicVersion,
        String anthropicBeta,
        String endpointType,
        String upstreamPath,
        ObjectNode body,
        List<RelayMultipartFile> files
) {
    public RelayDispatchRequest(
            String authorization,
            String apiKeyHeader,
            String queryKey,
            String userAgent,
            String clientIp,
            String endpointType,
            String upstreamPath,
            ObjectNode body
    ) {
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, null, null, endpointType, upstreamPath, body, List.of());
    }

    public RelayDispatchRequest(
            String authorization,
            String apiKeyHeader,
            String queryKey,
            String userAgent,
            String clientIp,
            String anthropicVersion,
            String anthropicBeta,
            String endpointType,
            String upstreamPath,
            ObjectNode body
    ) {
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, anthropicVersion, anthropicBeta, endpointType, upstreamPath, body, List.of());
    }

    public RelayDispatchRequest(
            String authorization,
            String apiKeyHeader,
            String queryKey,
            String userAgent,
            String clientIp,
            String endpointType,
            String upstreamPath,
            ObjectNode body,
            List<RelayMultipartFile> files
    ) {
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, null, null, endpointType, upstreamPath, body, files);
    }
}
