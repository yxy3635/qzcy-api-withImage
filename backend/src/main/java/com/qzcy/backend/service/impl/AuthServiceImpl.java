package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.dto.AuthUser;
import com.qzcy.backend.dto.ForgotPasswordDto;
import com.qzcy.backend.dto.LoginDto;
import com.qzcy.backend.dto.LoginResponse;
import com.qzcy.backend.dto.RegisterDto;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.AuthService;
import com.qzcy.backend.service.EmailCodeService;
import com.qzcy.backend.service.PaymentConfigService;
import com.qzcy.backend.service.ReferralService;
import com.qzcy.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailCodeService emailCodeService;
    private final PaymentConfigService paymentConfigService;
    private final ReferralService referralService;

    @Override
    public AuthUser register(RegisterDto dto) {
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (!username.matches("^[A-Za-z0-9]{3,20}$")) {
            throw new BusinessException(400, "用户名只能包含英文和数字，长度3-20位");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new BusinessException(400, "密码至少6位");
        }
        String email = normalizeEmail(dto.getEmail());
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailCount > 0) {
            throw new BusinessException(409, "邮箱已被注册");
        }
        Long inviterId = referralService.inviterIdForCode(dto.getInviteCode());
        emailCodeService.verify(email, "register", dto.getCode());
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole("USER");
        user.setBanned(false);
        user.setBalance(paymentConfigService.registerGiftAmount());
        user.setInviterId(inviterId);
        user.setVersion(0);
        userMapper.insert(user);
        return toAuthUser(user);
    }

    @Override
    public LoginResponse login(LoginDto dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new BusinessException(423, "账号已被封禁，无法登录和使用网站功能");
        }
        return new LoginResponse(jwtUtil.generateToken(user), toAuthUser(user));
    }

    @Override
    public void resetPassword(ForgotPasswordDto dto) {
        String email = normalizeEmail(dto.getEmail());
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码至少6位");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(404, "邮箱对应用户不存在");
        }
        emailCodeService.verify(email, "forgot_password", dto.getCode());
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
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
