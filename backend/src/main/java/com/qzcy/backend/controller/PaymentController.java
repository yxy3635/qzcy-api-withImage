package com.qzcy.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.PaymentConfigDto;
import com.qzcy.backend.dto.RechargeDto;
import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.service.PaymentConfigService;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentConfigService paymentConfigService;

    @PostMapping("/recharge")
    public ApiResponse<Map<String, Object>> recharge(@RequestBody RechargeDto dto, HttpServletRequest request) {
        return ApiResponse.success(paymentService.recharge(SecurityUtil.current().userId(), dto, backendBaseUrl(request), frontendBaseUrl(request)));
    }

    @GetMapping("/config")
    public ApiResponse<PaymentConfigDto> config() {
        return ApiResponse.success(paymentConfigService.adminDetail());
    }

    @RequestMapping("/notify")
    @ResponseBody
    public String notify(@RequestParam Map<String, String> params) {
        return paymentService.handleNotify(params);
    }

    @GetMapping("/history")
    public ApiResponse<Page<PaymentRecord>> history(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(paymentService.history(SecurityUtil.current().userId(), page, size));
    }

    private String backendBaseUrl(HttpServletRequest request) {
        String scheme = headerOrDefault(request, "X-Forwarded-Proto", request.getScheme());
        String host = headerOrDefault(request, "X-Forwarded-Host", request.getHeader("Host"));
        return scheme + "://" + host;
    }

    private String frontendBaseUrl(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin;
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return referer.replaceFirst("^(https?://[^/]+).*$", "$1");
        }
        return backendBaseUrl(request);
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
