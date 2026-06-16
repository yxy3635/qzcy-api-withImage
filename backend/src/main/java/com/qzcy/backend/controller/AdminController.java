package com.qzcy.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.AdminImageRecordDto;
import com.qzcy.backend.dto.AdminUserUpdateDto;
import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.DashboardStats;
import com.qzcy.backend.dto.ImageGenerationConfigDto;
import com.qzcy.backend.dto.ImageGenerationConfigUpdateDto;
import com.qzcy.backend.dto.MailConfigDto;
import com.qzcy.backend.dto.MailConfigUpdateDto;
import com.qzcy.backend.dto.PaymentConfigDto;
import com.qzcy.backend.dto.PaymentConfigUpdateDto;
import com.qzcy.backend.dto.RelayAdminOverviewDto;
import com.qzcy.backend.dto.RelayChannelDto;
import com.qzcy.backend.dto.RelayChannelUpdateDto;
import com.qzcy.backend.dto.RelayGroupDto;
import com.qzcy.backend.dto.RelayGroupUpdateDto;
import com.qzcy.backend.dto.RelayModelDto;
import com.qzcy.backend.dto.RelayModelUpdateDto;
import com.qzcy.backend.dto.RelayUpstreamModelDto;
import com.qzcy.backend.dto.RoleUpdateDto;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.service.AdminService;
import com.qzcy.backend.service.ImageGenerationConfigService;
import com.qzcy.backend.service.MailConfigService;
import com.qzcy.backend.service.PaymentConfigService;
import com.qzcy.backend.service.RelayChannelStatusService;
import com.qzcy.backend.service.RelayService;
import com.qzcy.backend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final ImageGenerationConfigService imageGenerationConfigService;
    private final MailConfigService mailConfigService;
    private final PaymentConfigService paymentConfigService;
    private final RelayService relayService;
    private final RelayChannelStatusService relayChannelStatusService;

    @GetMapping("/dashboard")
    public ApiResponse<DashboardStats> dashboard() {
        return ApiResponse.success(adminService.dashboard());
    }

    @GetMapping("/users")
    public ApiResponse<Page<User>> users(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword) {
        return ApiResponse.success(adminService.users(page, size, keyword));
    }

    @GetMapping("/image-records")
    public ApiResponse<Page<AdminImageRecordDto>> imageRecords(@RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "10") long size,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String status) {
        return ApiResponse.success(adminService.imageRecords(page, size, keyword, status));
    }

    @GetMapping("/image-configs")
    public ApiResponse<java.util.List<ImageGenerationConfigDto>> imageConfigs() {
        return ApiResponse.success(imageGenerationConfigService.adminList());
    }

    @PutMapping("/image-configs/{id}")
    public ApiResponse<ImageGenerationConfigDto> updateImageConfig(@PathVariable Long id,
                                                                   @RequestBody ImageGenerationConfigUpdateDto dto) {
        return ApiResponse.success(imageGenerationConfigService.update(id, dto));
    }

    @GetMapping("/relay")
    public ApiResponse<RelayAdminOverviewDto> relayOverview() {
        return ApiResponse.success(relayService.adminOverview());
    }

    @PostMapping("/relay/channels")
    public ApiResponse<RelayChannelDto> createRelayChannel(@RequestBody RelayChannelUpdateDto dto) {
        return ApiResponse.success(relayService.createChannel(dto));
    }

    @PutMapping("/relay/channels/{id}")
    public ApiResponse<RelayChannelDto> updateRelayChannel(@PathVariable Long id,
                                                           @RequestBody RelayChannelUpdateDto dto) {
        return ApiResponse.success(relayService.updateChannel(id, dto));
    }

    @PostMapping("/relay/groups")
    public ApiResponse<RelayGroupDto> createRelayGroup(@RequestBody RelayGroupUpdateDto dto) {
        return ApiResponse.success(relayService.createGroup(dto));
    }

    @PutMapping("/relay/groups/{id}")
    public ApiResponse<RelayGroupDto> updateRelayGroup(@PathVariable Long id,
                                                       @RequestBody RelayGroupUpdateDto dto) {
        return ApiResponse.success(relayService.updateGroup(id, dto));
    }

    @DeleteMapping("/relay/groups/{id}")
    public ApiResponse<Void> deleteRelayGroup(@PathVariable Long id) {
        relayService.deleteGroup(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/relay/models")
    public ApiResponse<RelayModelDto> createRelayModel(@RequestBody RelayModelUpdateDto dto) {
        return ApiResponse.success(relayService.createModel(dto));
    }

    @PutMapping("/relay/models/{id}")
    public ApiResponse<RelayModelDto> updateRelayModel(@PathVariable Long id,
                                                       @RequestBody RelayModelUpdateDto dto) {
        return ApiResponse.success(relayService.updateModel(id, dto));
    }

    @DeleteMapping("/relay/models/{id}")
    public ApiResponse<Void> deleteRelayModel(@PathVariable Long id) {
        relayService.deleteModel(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/relay/channels/{id}/models/sync")
    public ApiResponse<java.util.List<RelayUpstreamModelDto>> syncRelayModels(@PathVariable Long id) {
        return ApiResponse.success(relayService.fetchUpstreamModels(id));
    }

    @PostMapping("/relay/channels/status/sync")
    public ApiResponse<Void> syncRelayChannelStatus() {
        relayChannelStatusService.syncAll();
        return ApiResponse.success(null);
    }

    @GetMapping("/mail-config")
    public ApiResponse<MailConfigDto> mailConfig() {
        return ApiResponse.success(mailConfigService.adminDetail());
    }

    @PutMapping("/mail-config")
    public ApiResponse<MailConfigDto> updateMailConfig(@RequestBody MailConfigUpdateDto dto) {
        return ApiResponse.success(mailConfigService.update(dto));
    }

    @GetMapping("/payment-config")
    public ApiResponse<PaymentConfigDto> paymentConfig() {
        return ApiResponse.success(paymentConfigService.adminDetail());
    }

    @PutMapping("/payment-config")
    public ApiResponse<PaymentConfigDto> updatePaymentConfig(@RequestBody PaymentConfigUpdateDto dto) {
        return ApiResponse.success(paymentConfigService.update(dto));
    }

    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable Long id, @RequestBody RoleUpdateDto dto) {
        adminService.updateRole(id, dto.getRole());
        return ApiResponse.success(null);
    }

    @PutMapping("/users/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody AdminUserUpdateDto dto) {
        return ApiResponse.success(adminService.updateUser(id, dto));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id, SecurityUtil.current().userId());
        return ApiResponse.success(null);
    }
}
