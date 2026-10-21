package com.qzcy.backend.service.impl;

import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.EmailCodeRateLimiter;
import com.qzcy.backend.service.EmailCodeService;
import com.qzcy.backend.service.MailDeliveryService;
import com.qzcy.backend.service.MailConfigService;
import com.qzcy.backend.service.RegistrationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private final MailConfigService mailConfigService;
    private final MailDeliveryService mailDeliveryService;
    private final EmailCodeRateLimiter rateLimiter;
    private final UserMapper userMapper;
    private final RegistrationConfigService registrationConfigService;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> sendCode(String email, String scene, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(scene);
        if ("register".equals(normalizedScene)) {
            registrationConfigService.requireRegistrationOpen();
        }
        var config = mailConfigService.current();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(503, "邮件服务未启用，请联系管理员");
        }
        rateLimiter.checkAndRecord(normalizedEmail, clientIp);
        if (!shouldSendForScene(normalizedEmail, normalizedScene)) {
            // Use the same success response to avoid exposing whether an email is registered.
            return Map.of("sent", true);
        }
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        codes.entrySet().removeIf(entry -> entry.getValue().expireAt().isBefore(LocalDateTime.now()));
        String codeKey = key(normalizedEmail, normalizedScene);
        CodeEntry entry = new CodeEntry(code, LocalDateTime.now().plusMinutes(10), 0);
        codes.put(codeKey, entry);
        try {
            mailDeliveryService.sendVerificationCode(normalizedEmail, normalizedScene, code);
        } catch (RuntimeException ex) {
            codes.remove(codeKey, entry);
            throw ex;
        }
        return Map.of("sent", true);
    }

    @Override
    public void verify(String email, String scene, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(scene);
        AtomicBoolean verified = new AtomicBoolean(false);
        String codeKey = key(normalizedEmail, normalizedScene);
        codes.compute(codeKey, (ignored, entry) -> {
            if (entry == null || entry.expireAt().isBefore(LocalDateTime.now())) {
                return null;
            }
            if (MessageDigest.isEqual(entry.code().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    (code == null ? "" : code.trim()).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                verified.set(true);
                return null;
            }
            if (entry.failedAttempts() + 1 >= MAX_VERIFY_ATTEMPTS) {
                return null;
            }
            return new CodeEntry(entry.code(), entry.expireAt(), entry.failedAttempts() + 1);
        });
        if (!verified.get()) {
            throw new BusinessException(400, "邮箱验证码无效或已过期");
        }
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        return normalized;
    }

    private String normalizeScene(String scene) {
        String normalized = scene == null ? "" : scene.trim();
        if (!Set.of("register", "forgot_password", "change_email_current", "change_email_new").contains(normalized)) {
            throw new BusinessException(400, "验证码场景无效");
        }
        return normalized;
    }

    private boolean shouldSendForScene(String email, String scene) {
        Long usersWithEmail = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        boolean exists = usersWithEmail != null && usersWithEmail > 0;
        return Set.of("register", "change_email_new").contains(scene) ? !exists : exists;
    }

    private String key(String email, String scene) {
        return scene + ":" + email;
    }

    private record CodeEntry(String code, LocalDateTime expireAt, int failedAttempts) {
    }
}
