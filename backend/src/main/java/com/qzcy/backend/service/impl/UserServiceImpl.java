package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.dto.AuthUser;
import com.qzcy.backend.dto.ChangePasswordDto;
import com.qzcy.backend.dto.UserProfileDto;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.UserService;
import com.qzcy.backend.service.EmailCodeService;
import com.qzcy.backend.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailCodeService emailCodeService;

    @Override
    public BigDecimal balance(Long userId) {
        return user(userId).getBalance();
    }

    @Override
    public AuthUser currentUser(Long userId) {
        User user = user(userId);
        return toAuthUser(user);
    }

    @Override
    public AuthUser updateProfile(Long userId, UserProfileDto dto) {
        User user = user(userId);
        String email = normalizeEmail(dto.getEmail());
        if (email.equalsIgnoreCase(user.getEmail())) return toAuthUser(user);
        requirePassword(dto.getOldPassword(), user);
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .ne(User::getId, userId));
        if (exists > 0) {
            throw new BusinessException(409, "邮箱已被其他用户使用");
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            emailCodeService.verify(user.getEmail(), "change_email_current", dto.getCurrentEmailCode());
        }
        emailCodeService.verify(email, "change_email_new", dto.getNewEmailCode());
        if (userMapper.changeEmailSecurely(userId, user.getPassword(), user.getEmail(), email) != 1) {
            throw new BusinessException(409, "账号资料已变化，请重新验证");
        }
        user.setEmail(email);
        return toAuthUser(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDto dto) {
        User user = user(userId);
        requirePassword(dto.getOldPassword(), user);
        PasswordPolicy.requireNewPassword(dto.getNewPassword());
        if (userMapper.changePasswordSecurely(userId, user.getPassword(), user.getEmail(),
                passwordEncoder.encode(dto.getNewPassword())) != 1) {
            throw new BusinessException(409, "账号资料已变化，请重新登录后操作");
        }
    }

    private void requirePassword(String password, User user) {
        if (password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
    }

    private User user(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new BusinessException(423, "账号已被封禁，无法使用网站功能");
        }
        return user;
    }

    private AuthUser toAuthUser(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getBanned(), user.getBalance());
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        return normalized;
    }
}
