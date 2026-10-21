package com.qzcy.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements CommandLineRunner {
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    @Value("${app.admin.initial-password:}")
    private String initialPassword;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Long admins = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, "ADMIN")
        );
        if (admins > 0) {
            return;
        }

        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, DEFAULT_ADMIN_USERNAME)
        );
        if (exists > 0) {
            return;
        }

        com.qzcy.backend.util.PasswordPolicy.requireNewPassword(initialPassword);
        User admin = new User();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(initialPassword));
        admin.setRole("ADMIN");
        admin.setBanned(false);
        admin.setBalance(BigDecimal.ZERO);
        admin.setVersion(0);
        userMapper.insert(admin);
    }
}
