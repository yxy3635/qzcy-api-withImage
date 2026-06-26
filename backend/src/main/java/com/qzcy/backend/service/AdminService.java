package com.qzcy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.AdminImageRecordDto;
import com.qzcy.backend.dto.AdminRelayUsageLogDto;
import com.qzcy.backend.dto.AdminUserUpdateDto;
import com.qzcy.backend.dto.DashboardStats;
import com.qzcy.backend.entity.User;

public interface AdminService {
    DashboardStats dashboard();
    Page<User> users(long page, long size, String keyword);
    Page<AdminImageRecordDto> imageRecords(long page, long size, String keyword, String status);
    Page<AdminRelayUsageLogDto> relayUsageRecords(long page, long size, String keyword, String status);
    User updateUser(Long id, AdminUserUpdateDto dto);
    void updateRole(Long id, String role);
    void updateBanStatus(Long id, boolean banned, Long currentAdminId);
    void deleteUser(Long id, Long currentAdminId);
}
