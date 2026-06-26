package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.dto.AuthUser;
import com.qzcy.backend.dto.ChangePasswordDto;
import com.qzcy.backend.dto.UserProfileDto;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

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
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .ne(User::getId, userId));
        if (exists > 0) {
            throw new BusinessException(409, "邮箱已被其他用户使用");
        }
        user.setEmail(email);
        userMapper.updateById(user);
        return toAuthUser(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordDto dto) {
        User user = user(userId);
        if (dto.getOldPassword() == null || !passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(401, "原密码错误");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码至少6位");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
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
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!normalized.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        return normalized;
    }
}
