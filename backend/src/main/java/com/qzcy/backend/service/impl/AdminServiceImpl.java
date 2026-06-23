package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.AdminImageRecordDto;
import com.qzcy.backend.dto.AdminRelayUsageLogDto;
import com.qzcy.backend.dto.AdminUserUpdateDto;
import com.qzcy.backend.dto.DashboardStats;
import com.qzcy.backend.entity.ImageRecord;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.AdminStatsMapper;
import com.qzcy.backend.mapper.ImageRecordMapper;
import com.qzcy.backend.mapper.PaymentRecordMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final UserMapper userMapper;
    private final ImageRecordMapper imageRecordMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RelayUsageLogMapper relayUsageLogMapper;
    private final AdminStatsMapper adminStatsMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public DashboardStats dashboard() {
        Long totalUsers = userMapper.selectCount(null);
        Long totalImages = imageRecordMapper.selectCount(null);
        Long todayImages = imageRecordMapper.selectCount(
                new LambdaQueryWrapper<ImageRecord>().ge(ImageRecord::getCreatedAt, LocalDate.now().atStartOfDay())
        );
        BigDecimal relaySiteCost = relayUsageLogMapper.totalCost();
        BigDecimal relayUpstreamCost = relayUsageLogMapper.totalUpstreamCost();
        BigDecimal relayProfit = (relaySiteCost == null ? BigDecimal.ZERO : relaySiteCost)
                .subtract(relayUpstreamCost == null ? BigDecimal.ZERO : relayUpstreamCost);
        BigDecimal todayRelayCost = zero(relayUsageLogMapper.todayCost());
        BigDecimal yesterdayRelayCost = zero(relayUsageLogMapper.yesterdayCost());
        BigDecimal todayRelayUpstreamCost = zero(relayUsageLogMapper.todayUpstreamCost());
        BigDecimal yesterdayRelayUpstreamCost = zero(relayUsageLogMapper.yesterdayUpstreamCost());
        return new DashboardStats(
                totalUsers,
                totalImages,
                todayImages,
                paymentRecordMapper.totalRevenue(),
                relaySiteCost,
                relayUpstreamCost,
                relayProfit,
                relayUsageLogMapper.todayRequests(),
                relayUsageLogMapper.yesterdayRequests(),
                relayUsageLogMapper.todayTokens(),
                relayUsageLogMapper.yesterdayTokens(),
                todayRelayCost,
                yesterdayRelayCost,
                todayRelayUpstreamCost,
                yesterdayRelayUpstreamCost,
                todayRelayCost.subtract(todayRelayUpstreamCost),
                yesterdayRelayCost.subtract(yesterdayRelayUpstreamCost),
                relayUsageLogMapper.channelProfits(),
                adminStatsMapper.recentRegistrations(),
                imageRecordMapper.generationTrend()
        );
    }

    @Override
    public Page<User> users(long page, long size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(keyword != null && !keyword.isBlank(), User::getUsername, keyword)
                .orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(Page.of(page, size), wrapper);
    }

    @Override
    public Page<AdminImageRecordDto> imageRecords(long page, long size, String keyword, String status) {
        return imageRecordMapper.adminImageRecords(Page.of(page, size), keyword, status);
    }

    @Override
    public Page<AdminRelayUsageLogDto> relayUsageRecords(long page, long size, String keyword, String status) {
        return relayUsageLogMapper.adminUsageLogs(Page.of(page, size), keyword, status);
    }

    @Override
    public User updateUser(Long id, AdminUserUpdateDto dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            String email = normalizeEmail(dto.getEmail());
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email)
                    .ne(User::getId, id));
            if (exists > 0) {
                throw new BusinessException(409, "邮箱已被其他用户使用");
            }
            user.setEmail(email);
        }
        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            if (!"USER".equals(dto.getRole()) && !"ADMIN".equals(dto.getRole())) {
                throw new BusinessException(400, "角色只允许USER或ADMIN");
            }
            user.setRole(dto.getRole());
        }
        if (dto.getBalance() != null) {
            if (dto.getBalance().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(400, "余额不能小于0");
            }
            user.setBalance(dto.getBalance());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6) {
                throw new BusinessException(400, "密码至少6位");
            }
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userMapper.updateById(user);
        return userMapper.selectById(id);
    }

    @Override
    public void updateRole(Long id, String role) {
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(400, "角色只允许USER或ADMIN");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setRole(role);
        userMapper.updateById(user);
    }

    @Override
    public void deleteUser(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BusinessException(400, "不能删除当前管理员账号");
        }
        userMapper.deleteById(id);
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        if (!normalized.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(400, "邮箱格式不正确");
        }
        return normalized;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
