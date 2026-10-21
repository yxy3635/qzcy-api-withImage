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
import com.qzcy.backend.service.AccountAttemptLimiter;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.util.SecurityAudit;
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
    private final AccountAttemptLimiter attemptLimiter;

    @PostMapping("/email-code")
    public ApiResponse<Object> sendEmailCode(@RequestBody EmailCodeDto dto, HttpServletRequest request) {
        if (!"register".equals(dto.getScene()) && !"forgot_password".equals(dto.getScene())) {
            throw new BusinessException(400, "验证码场景无效");
        }
        return ApiResponse.success(emailCodeService.sendCode(dto.getEmail(), dto.getScene(), request.getRemoteAddr()));
    }

    @PostMapping("/register")
    public ApiResponse<AuthUser> register(@RequestBody RegisterDto dto) {
        return ApiResponse.success("注册成功", authService.register(dto));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginDto dto, HttpServletRequest request) {
        try {
            attemptLimiter.login(dto.getAccount() == null ? dto.getUsername() : dto.getAccount(), request.getRemoteAddr());
            LoginResponse result = authService.login(dto);
            SecurityAudit.record("login", 200, result.getUser().getId(), request);
            return ApiResponse.success(result);
        } catch (BusinessException ex) {
            SecurityAudit.record("login", ex.getCode(), null, request);
            throw ex;
        }
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@RequestBody ForgotPasswordDto dto, HttpServletRequest request) {
        try {
            attemptLimiter.sensitive(dto.getEmail(), request.getRemoteAddr());
            authService.resetPassword(dto);
            SecurityAudit.record("password_reset", 200, null, request);
        } catch (BusinessException ex) {
            SecurityAudit.record("password_reset", ex.getCode(), null, request);
            throw ex;
        }
        return ApiResponse.success(null);
    }

}
