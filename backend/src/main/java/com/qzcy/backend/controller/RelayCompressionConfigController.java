package com.qzcy.backend.controller;

import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.RelayCompressionConfigDto;
import com.qzcy.backend.service.RelayCompressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/relay/compression-config")
public class RelayCompressionConfigController {
    private final RelayCompressionService service;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidConfig() {
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "请提供有效的压缩设置，内存预算必须是整数"));
    }

    @GetMapping
    public ApiResponse<RelayCompressionService.Status> detail() {
        return ApiResponse.success(service.status());
    }

    @PutMapping
    public ApiResponse<RelayCompressionService.Status> update(@RequestBody RelayCompressionConfigDto config) {
        return ApiResponse.success(service.update(config));
    }
}
