package com.qzcy.backend.config;

import com.qzcy.backend.controller.RelayController;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Type;

/** Release the compressed-body slot before dispatching an upstream request/SSE response. */
@ControllerAdvice(assignableTypes = RelayController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RelayCompressionBodyAdvice extends RequestBodyAdviceAdapter {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidJson(HttpServletRequest request) {
        Map<String, String> error = Map.of("type", "invalid_request_error", "code", "invalid_json",
                "message", "请求体必须是有效的 JSON 对象");
        return ResponseEntity.badRequest().body(request.getRequestURI().contains("/messages")
                ? Map.of("type", "error", "error", error) : Map.of("error", error));
    }

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type,
                                Class<? extends HttpMessageConverter<?>> converter) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object decoded = attributes.getRequest().getAttribute(RelayCompressionFilter.BODY_ATTRIBUTE);
            if (decoded instanceof RelayCompressionFilter.DecodedBody resource) resource.close();
        }
        return body;
    }
}
