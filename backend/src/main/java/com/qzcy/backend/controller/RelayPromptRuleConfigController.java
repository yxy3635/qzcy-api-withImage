package com.qzcy.backend.controller;

import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.RelayPromptRuleConfigDto;
import com.qzcy.backend.dto.RelayPromptRuleHitsDto;
import com.qzcy.backend.service.RelayPromptRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/relay/prompt-rule-config")
public class RelayPromptRuleConfigController {
    private final RelayPromptRuleService service;

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidConfig() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(ApiResponse.error(400, "提示词设置格式无效，自动封禁阈值须为 1–1000 次的整数"));
    }

    @GetMapping("/hits")
    public ResponseEntity<ApiResponse<RelayPromptRuleHitsDto>> hits(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.hits(page, size, keyword)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RelayPromptRuleService.Status>> detail() {
        return response(service.status());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<RelayPromptRuleService.Status>> update(@RequestBody RelayPromptRuleConfigDto config) {
        return response(service.update(config));
    }

    private ResponseEntity<ApiResponse<RelayPromptRuleService.Status>> response(RelayPromptRuleService.Status status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(status));
    }
}
