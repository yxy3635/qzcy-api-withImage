package com.qzcy.backend.dto.relay;

import java.util.List;
import java.util.Map;

public record RelayDispatchResult(int statusCode, String contentType, String body, Map<String, List<String>> headers) {
    public RelayDispatchResult {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public RelayDispatchResult(int statusCode, String contentType, String body) {
        this(statusCode, contentType, body, Map.of());
    }
}
