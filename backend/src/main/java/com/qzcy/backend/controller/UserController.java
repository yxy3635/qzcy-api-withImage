package com.qzcy.backend.controller;

import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.AuthUser;
import com.qzcy.backend.dto.ChangePasswordDto;
import com.qzcy.backend.dto.ReferralActionDto;
import com.qzcy.backend.dto.ReferralInviteeDto;
import com.qzcy.backend.dto.ReferralOverviewDto;
import com.qzcy.backend.dto.ReferralRebateDto;
import com.qzcy.backend.dto.ReferralWithdrawRequestDto;
import com.qzcy.backend.entity.ReferralWithdrawQrCode;
import com.qzcy.backend.mapper.ReferralWithdrawQrCodeMapper;
import com.qzcy.backend.dto.UserProfileDto;
import com.qzcy.backend.service.ReferralService;
import com.qzcy.backend.service.UserService;
import com.qzcy.backend.util.SecurityUtil;
import com.qzcy.backend.util.UploadPathUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private static final Set<String> QR_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_QR_IMAGE_BYTES = 5 * 1024 * 1024;

    private final UserService userService;
    private final ReferralService referralService;
    private final ReferralWithdrawQrCodeMapper referralWithdrawQrCodeMapper;

    @Value("${app.upload.image-path:userImage/}")
    private String imagePath;

    @GetMapping("/balance")
    public ApiResponse<BigDecimal> balance() {
        return ApiResponse.success(userService.balance(SecurityUtil.current().userId()));
    }

    @GetMapping("/me")
    public ApiResponse<AuthUser> me() {
        return ApiResponse.success(userService.currentUser(SecurityUtil.current().userId()));
    }

    @PutMapping("/profile")
    public ApiResponse<AuthUser> updateProfile(@RequestBody UserProfileDto dto) {
        return ApiResponse.success(userService.updateProfile(SecurityUtil.current().userId(), dto));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordDto dto) {
        userService.changePassword(SecurityUtil.current().userId(), dto);
        return ApiResponse.success(null);
    }

    @GetMapping("/referral")
    public ApiResponse<ReferralOverviewDto> referral(HttpServletRequest request) {
        return ApiResponse.success(referralService.overview(SecurityUtil.current().userId(), frontendBaseUrl(request)));
    }

    @PostMapping("/referral/enable")
    public ApiResponse<ReferralOverviewDto> enableReferral(HttpServletRequest request) {
        return ApiResponse.success("邀请返利已开启", referralService.enable(SecurityUtil.current().userId(), frontendBaseUrl(request)));
    }

    @GetMapping("/referral/invitees")
    public ApiResponse<Page<ReferralInviteeDto>> referralInvitees(@RequestParam(defaultValue = "1") long page,
                                                                  @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(referralService.invitees(SecurityUtil.current().userId(), page, size));
    }

    @GetMapping("/referral/rebates")
    public ApiResponse<Page<ReferralRebateDto>> referralRebates(@RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(referralService.rebates(SecurityUtil.current().userId(), page, size));
    }

    @PostMapping("/referral/rebates/{id}/transfer")
    public ApiResponse<Void> transferReferralRebate(@PathVariable Long id) {
        referralService.transferToBalance(SecurityUtil.current().userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/referral/withdraw")
    public ApiResponse<Void> withdrawReferral(@RequestBody ReferralActionDto dto) {
        referralService.requestAccountWithdraw(SecurityUtil.current().userId(), dto.getAmount(), dto.getChannel(), dto.getQrCodeUrl());
        return ApiResponse.success(null);
    }

    @PostMapping("/referral/withdraw-qr")
    public ApiResponse<Map<String, String>> uploadWithdrawQr(@RequestParam("file") MultipartFile file,
                                                             @RequestParam("channel") String channel) {
        Long userId = SecurityUtil.current().userId();
        String normalizedChannel = normalizeWithdrawChannel(channel);
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "请上传收款二维码图片");
        }
        if (file.getSize() > MAX_QR_IMAGE_BYTES) {
            return ApiResponse.error(400, "收款二维码图片不能超过 5MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!QR_IMAGE_TYPES.contains(contentType)) {
            return ApiResponse.error(400, "收款二维码仅支持 PNG、JPG、WEBP 图片");
        }

        try {
            String extension = extensionForContentType(contentType);
            Path root = UploadPathUtil.resolveImageRoot(imagePath, UserController.class);
            Path userDir = root.resolve("referral-qr").resolve(String.valueOf(userId)).resolve(normalizedChannel).normalize();
            if (!userDir.startsWith(root)) {
                return ApiResponse.error(400, "上传路径不合法");
            }
            Files.createDirectories(userDir);
            String fileName = UUID.randomUUID() + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + extension;
            Files.write(userDir.resolve(fileName), file.getBytes(), StandardOpenOption.CREATE_NEW);
            String url = "/api/images/referral-qr/" + userId + "/" + normalizedChannel + "/" + fileName;
            saveWithdrawQrCode(userId, normalizedChannel, url);
            return ApiResponse.success(Map.of("url", url));
        } catch (IOException ex) {
            return ApiResponse.error(500, "收款二维码上传失败");
        }
    }

    @GetMapping("/referral/withdraws")
    public ApiResponse<Page<ReferralWithdrawRequestDto>> referralWithdraws(@RequestParam(defaultValue = "1") long page,
                                                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(referralService.withdrawRequests(SecurityUtil.current().userId(), page, size));
    }

    private String extensionForContentType(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".png";
    }

    private String normalizeWithdrawChannel(String channel) {
        String value = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        return "alipay".equals(value) ? "alipay" : "wechat";
    }

    private void saveWithdrawQrCode(Long userId, String channel, String url) {
        ReferralWithdrawQrCode existing = referralWithdrawQrCodeMapper.selectOne(new LambdaQueryWrapper<ReferralWithdrawQrCode>()
                .eq(ReferralWithdrawQrCode::getUserId, userId)
                .eq(ReferralWithdrawQrCode::getChannel, channel));
        if (existing == null) {
            ReferralWithdrawQrCode qrCode = new ReferralWithdrawQrCode();
            qrCode.setUserId(userId);
            qrCode.setChannel(channel);
            qrCode.setQrCodeUrl(url);
            referralWithdrawQrCodeMapper.insert(qrCode);
        } else {
            existing.setQrCodeUrl(url);
            referralWithdrawQrCodeMapper.updateById(existing);
        }
    }

    private String frontendBaseUrl(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return origin;
        }
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            return referer.replaceFirst("^(https?://[^/]+).*$", "$1");
        }
        String scheme = headerOrDefault(request, "X-Forwarded-Proto", request.getScheme());
        String host = headerOrDefault(request, "X-Forwarded-Host", request.getHeader("Host"));
        return scheme + "://" + host;
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
