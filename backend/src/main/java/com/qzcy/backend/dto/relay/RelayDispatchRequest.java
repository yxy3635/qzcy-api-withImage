package com.qzcy.backend.dto.relay;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

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
        List<RelayMultipartFile> files,
        Map<String, String> protocolHeaders
) {
    public RelayDispatchRequest {
        files = files == null ? List.of() : List.copyOf(files);
        protocolHeaders = protocolHeaders == null ? Map.of() : Map.copyOf(protocolHeaders);
    }

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
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, null, null, endpointType, upstreamPath, body, List.of(), Map.of());
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
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, anthropicVersion, anthropicBeta, endpointType, upstreamPath, body, List.of(), Map.of());
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
            ObjectNode body,
            Map<String, String> protocolHeaders
    ) {
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, anthropicVersion, anthropicBeta,
                endpointType, upstreamPath, body, List.of(), protocolHeaders);
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
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, null, null, endpointType, upstreamPath, body, files, Map.of());
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
            List<RelayMultipartFile> files,
            Map<String, String> protocolHeaders
    ) {
        this(authorization, apiKeyHeader, queryKey, userAgent, clientIp, null, null,
                endpointType, upstreamPath, body, files, protocolHeaders);
    }
}
