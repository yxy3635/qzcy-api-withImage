package com.qzcy.backend.dto.relay;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record RelayStreamDispatchResult(int statusCode, String contentType, StreamingResponseBody body) {
}
