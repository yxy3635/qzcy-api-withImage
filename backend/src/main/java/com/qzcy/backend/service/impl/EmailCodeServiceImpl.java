package com.qzcy.backend.service.impl;

import com.qzcy.backend.entity.MailConfig;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.service.EmailCodeService;
import com.qzcy.backend.service.MailConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MailConfigService mailConfigService;
    private final Map<String, CodeEntry> codes = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> sendCode(String email, String scene) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(scene);
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        codes.put(key(normalizedEmail, normalizedScene), new CodeEntry(code, LocalDateTime.now().plusMinutes(10)));

        MailConfig config = mailConfigService.current();
        if (Boolean.TRUE.equals(config.getEnabled()) && config.getHost() != null && !config.getHost().isBlank()) {
            JavaMailSenderImpl mailSender = mailSender(config);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(config.getFromAddress() == null || config.getFromAddress().isBlank() ? config.getUsername() : config.getFromAddress());
            message.setTo(normalizedEmail);
            message.setSubject("imageCreater 邮箱验证码");
            message.setText("你的验证码是：" + code + "，10分钟内有效。");
            mailSender.send(message);
        }

        if (Boolean.TRUE.equals(config.getDevReturnCode()) || !Boolean.TRUE.equals(config.getEnabled())) {
            return Map.of("sent", true, "devCode", code);
        }
        return Map.of("sent", true);
    }

    @Override
    public void verify(String email, String scene, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedScene = normalizeScene(scene);
        CodeEntry entry = codes.get(key(normalizedEmail, normalizedScene));
        if (entry == null || entry.expireAt().isBefore(LocalDateTime.now()) || !entry.code().equals(code)) {
            throw new BusinessException(400, "邮箱验证码无效或已过期");
        }
        codes.remove(key(normalizedEmail, normalizedScene));
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!normalized.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        return normalized;
    }

    private String normalizeScene(String scene) {
        String normalized = scene == null ? "" : scene.trim();
        if (!"register".equals(normalized) && !"forgot_password".equals(normalized)) {
            throw new BusinessException(400, "验证码场景无效");
        }
        return normalized;
    }

    private String key(String email, String scene) {
        return scene + ":" + email;
    }

    private JavaMailSenderImpl mailSender(MailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort() == null ? 587 : config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(config.getSslEnabled())));
        properties.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(config.getStarttlsEnabled())));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private record CodeEntry(String code, LocalDateTime expireAt) {
    }
}
