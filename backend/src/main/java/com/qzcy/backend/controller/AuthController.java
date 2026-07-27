package com.qzcy.backend.controller;

import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.AuthUser;
import com.qzcy.backend.dto.EmailCodeDto;
import com.qzcy.backend.dto.ForgotPasswordDto;
import com.qzcy.backend.dto.LoginDto;
import com.qzcy.backend.dto.LoginResponse;
import com.qzcy.backend.dto.RegisterDto;
import com.qzcy.backend.service.AuthService;
import com.qzcy.backend.service.EmailCodeService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    @PostMapping("/email-code")
    public ApiResponse<Object> sendEmailCode(@RequestBody EmailCodeDto dto, HttpServletRequest request) {
        return ApiResponse.success(emailCodeService.sendCode(dto.getEmail(), dto.getScene(), clientIp(request)));
    }

    @PostMapping("/register")
    public ApiResponse<AuthUser> register(@RequestBody RegisterDto dto) {
        return ApiResponse.success("注册成功", authService.register(dto));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginDto dto) {
        return ApiResponse.success(authService.login(dto));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordDto dto) {
        authService.resetPassword(dto);
        return ApiResponse.success(null);
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if ("127.0.0.1".equals(remoteAddress) || "0:0:0:0:0:0:0:1".equals(remoteAddress) || "::1".equals(remoteAddress)) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return remoteAddress;
    }
}
