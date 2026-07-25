package com.qzcy.backend.dto.relay;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

public record RelayStreamDispatchResult(int statusCode, String contentType, StreamingResponseBody body, Map<String, List<String>> headers) {
    public RelayStreamDispatchResult {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public RelayStreamDispatchResult(int statusCode, String contentType, StreamingResponseBody body) {
        this(statusCode, contentType, body, Map.of());
    }
}
