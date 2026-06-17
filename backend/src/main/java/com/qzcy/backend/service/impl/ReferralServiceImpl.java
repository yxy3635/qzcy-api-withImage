package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ReferralInviteeDto;
import com.qzcy.backend.dto.ReferralOverviewDto;
import com.qzcy.backend.dto.ReferralRebateDto;
import com.qzcy.backend.dto.ReferralWithdrawQrCodeDto;
import com.qzcy.backend.dto.ReferralWithdrawRequestDto;
import com.qzcy.backend.entity.PaymentConfig;
import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.entity.ReferralRebateRecord;
import com.qzcy.backend.entity.ReferralWithdrawRequest;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.PaymentRecordMapper;
import com.qzcy.backend.mapper.ReferralRebateRecordMapper;
import com.qzcy.backend.mapper.ReferralWithdrawQrCodeMapper;
import com.qzcy.backend.mapper.ReferralWithdrawRequestMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.PaymentConfigService;
import com.qzcy.backend.service.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final ReferralRebateRecordMapper referralRebateRecordMapper;
    private final ReferralWithdrawQrCodeMapper referralWithdrawQrCodeMapper;
    private final ReferralWithdrawRequestMapper referralWithdrawRequestMapper;
    private final PaymentConfigService paymentConfigService;

    @Override
    public Long inviterIdForCode(String invitationCode) {
        String code = normalizeCode(invitationCode);
        if (code.isBlank()) {
            return null;
        }
        if (!code.matches("^[A-Z0-9]{6}$")) {
            throw new BusinessException(400, "邀请码格式不正确");
        }
        User inviter = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getInvitationCode, code));
        if (inviter == null) {
            throw new BusinessException(400, "邀请码不存在");
        }
        return inviter.getId();
    }

    @Override
    @Transactional
    public String ensureInvitationCode(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (user.getInvitationCode() != null && !user.getInvitationCode().isBlank()) {
            return user.getInvitationCode();
        }
        for (int i = 0; i < 40; i++) {
            String code = randomCode();
            Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getInvitationCode, code));
            if (exists == 0) {
                User update = new User();
                update.setId(userId);
                update.setInvitationCode(code);
                userMapper.updateById(update);
                return code;
            }
        }
        throw new BusinessException(500, "邀请码生成失败，请稍后重试");
    }

    @Override
    public ReferralOverviewDto enable(Long userId, String frontendBaseUrl) {
        ensureInvitationCode(userId);
        return overview(userId, frontendBaseUrl);
    }

    @Override
    public ReferralOverviewDto overview(Long userId, String frontendBaseUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        String code = user.getInvitationCode();
        BigDecimal rate = paymentConfigService.referralRebateRate();
        boolean enabled = code != null && !code.isBlank();
        return new ReferralOverviewDto(
                enabled,
                code,
                enabled ? normalizeBaseUrl(frontendBaseUrl) + "/register?inviteCode=" + code : "",
                rate,
                valueOrZero(referralRebateRecordMapper.invitedUsers(userId)),
                valueOrZero(referralRebateRecordMapper.inviteeRechargeTotal(userId)),
                valueOrZero(referralRebateRecordMapper.rebateTotal(userId)),
                valueOrZero(user.getReferralBalance()),
                valueOrZero(referralRebateRecordMapper.rebateTotalByStatus(userId, "pending_review")),
                valueOrZero(referralRebateRecordMapper.rebateTotalByStatus(userId, "approved")),
                valueOrZero(referralWithdrawRequestMapper.totalByStatus(userId, "pending")),
                withdrawQrCodes(userId)
        );
    }

    @Override
    public Page<ReferralInviteeDto> invitees(Long userId, long page, long size) {
        return referralRebateRecordMapper.invitees(Page.of(page, size), userId);
    }

    @Override
    public Page<ReferralRebateDto> rebates(Long userId, long page, long size) {
        return referralRebateRecordMapper.userRebates(Page.of(page, size), userId);
    }

    @Override
    public Page<ReferralRebateDto> adminRebates(long page, long size, String status) {
        return referralRebateRecordMapper.adminRebates(Page.of(page, size), status);
    }

    @Override
    public Page<ReferralWithdrawRequestDto> withdrawRequests(Long userId, long page, long size) {
        return referralWithdrawRequestMapper.userPage(Page.of(page, size), userId);
    }

    @Override
    public Page<ReferralWithdrawRequestDto> adminWithdrawRequests(long page, long size, String status) {
        return referralWithdrawRequestMapper.adminPage(Page.of(page, size), status);
    }

    @Override
    @Transactional
    public void approve(Long id, Long adminId) {
        ReferralRebateRecord record = referralRecord(id);
        requireStatus(record, "pending_review");
        record.setStatus("approved");
        record.setReviewedBy(adminId);
        record.setReviewedAt(LocalDateTime.now());
        referralRebateRecordMapper.updateById(record);
        userMapper.addReferralBalance(record.getInviterId(), record.getRebateAmount());
    }

    @Override
    @Transactional
    public void reject(Long id, Long adminId, String reason) {
        ReferralRebateRecord record = referralRecord(id);
        requireStatus(record, "pending_review");
        record.setStatus("rejected");
        record.setReviewedBy(adminId);
        record.setRejectReason(normalizeReason(reason));
        record.setReviewedAt(LocalDateTime.now());
        referralRebateRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    public void transferToBalance(Long userId, Long id) {
        ReferralRebateRecord record = referralRecord(id);
        requireOwner(record, userId);
        requireStatus(record, "approved");
        int deducted = userMapper.deductReferralBalance(userId, record.getRebateAmount());
        if (deducted == 0) {
            throw new BusinessException(400, "返利账户余额不足");
        }
        userMapper.addBalance(userId, record.getRebateAmount());
        record.setStatus("transferred");
        referralRebateRecordMapper.updateById(record);

        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setUserId(userId);
        paymentRecord.setAmount(record.getRebateAmount());
        paymentRecord.setType("referral_rebate");
        paymentRecord.setStatus("completed");
        paymentRecord.setCreatedAt(LocalDateTime.now());
        paymentRecordMapper.insert(paymentRecord);
    }

    @Override
    @Transactional
    public void requestWithdraw(Long userId, Long id, String qrCodeUrl) {
        ReferralRebateRecord record = referralRecord(id);
        requireOwner(record, userId);
        requireStatus(record, "approved");
        String normalizedQrCodeUrl = normalizeQrCodeUrl(userId, qrCodeUrl);
        int deducted = userMapper.deductReferralBalance(userId, record.getRebateAmount());
        if (deducted == 0) {
            throw new BusinessException(400, "返利账户余额不足");
        }
        record.setStatus("withdraw_requested");
        record.setWithdrawQrCodeUrl(normalizedQrCodeUrl);
        referralRebateRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    public void requestAccountWithdraw(Long userId, BigDecimal amount, String channel, String qrCodeUrl) {
        BigDecimal normalizedAmount = normalizeWithdrawAmount(amount);
        String normalizedChannel = normalizeWithdrawChannel(channel);
        String normalizedQrCodeUrl = normalizeQrCodeUrl(userId, normalizedChannel, qrCodeUrl);
        int deducted = userMapper.deductReferralBalance(userId, normalizedAmount);
        if (deducted == 0) {
            throw new BusinessException(400, "返利账户余额不足");
        }

        ReferralWithdrawRequest request = new ReferralWithdrawRequest();
        request.setUserId(userId);
        request.setAmount(normalizedAmount);
        request.setChannel(normalizedChannel);
        request.setQrCodeUrl(normalizedQrCodeUrl);
        request.setStatus("pending");
        referralWithdrawRequestMapper.insert(request);
    }

    @Override
    @Transactional
    public void markWithdrawSuccess(Long id, Long adminId) {
        ReferralRebateRecord record = referralRecord(id);
        requireStatus(record, "withdraw_requested");
        record.setStatus("withdraw_paid");
        record.setReviewedBy(adminId);
        record.setWithdrawnAt(LocalDateTime.now());
        referralRebateRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    public void markWithdrawFailed(Long id, Long adminId, String reason) {
        ReferralRebateRecord record = referralRecord(id);
        requireStatus(record, "withdraw_requested");
        userMapper.addReferralBalance(record.getInviterId(), record.getRebateAmount());
        record.setStatus("withdraw_failed");
        record.setReviewedBy(adminId);
        record.setWithdrawFailReason(normalizeReason(reason));
        referralRebateRecordMapper.updateById(record);
    }

    @Override
    @Transactional
    public void markAccountWithdrawSuccess(Long id, Long adminId) {
        ReferralWithdrawRequest request = withdrawRequest(id);
        requireWithdrawStatus(request, "pending");
        request.setStatus("paid");
        request.setReviewedBy(adminId);
        request.setReviewedAt(LocalDateTime.now());
        referralWithdrawRequestMapper.updateById(request);
    }

    @Override
    @Transactional
    public void markAccountWithdrawFailed(Long id, Long adminId, String reason) {
        ReferralWithdrawRequest request = withdrawRequest(id);
        requireWithdrawStatus(request, "pending");
        userMapper.addReferralBalance(request.getUserId(), request.getAmount());
        request.setStatus("failed");
        request.setReviewedBy(adminId);
        request.setFailReason(normalizeReason(reason));
        request.setReviewedAt(LocalDateTime.now());
        referralWithdrawRequestMapper.updateById(request);
    }

    @Override
    @Transactional
    public void rewardForRecharge(PaymentRecord paymentRecord) {
        if (paymentRecord == null || paymentRecord.getId() == null || paymentRecord.getAmount() == null) {
            return;
        }
        User invitee = userMapper.selectById(paymentRecord.getUserId());
        if (invitee == null || invitee.getInviterId() == null) {
            return;
        }
        PaymentConfig config = paymentConfigService.current();
        BigDecimal rate = config.getReferralRebateRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Long exists = referralRebateRecordMapper.selectCount(new LambdaQueryWrapper<ReferralRebateRecord>()
                .eq(ReferralRebateRecord::getPaymentRecordId, paymentRecord.getId()));
        if (exists > 0) {
            return;
        }
        BigDecimal rebateAmount = paymentRecord.getAmount()
                .multiply(rate)
                .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        if (rebateAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ReferralRebateRecord record = new ReferralRebateRecord();
        record.setInviterId(invitee.getInviterId());
        record.setInviteeId(invitee.getId());
        record.setPaymentRecordId(paymentRecord.getId());
        record.setRechargeAmount(paymentRecord.getAmount());
        record.setRebateRate(rate);
        record.setRebateAmount(rebateAmount);
        record.setStatus("pending_review");
        referralRebateRecordMapper.insert(record);
    }

    private ReferralRebateRecord referralRecord(Long id) {
        ReferralRebateRecord record = referralRebateRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(404, "返利记录不存在");
        }
        return record;
    }

    private ReferralWithdrawRequest withdrawRequest(Long id) {
        ReferralWithdrawRequest request = referralWithdrawRequestMapper.selectById(id);
        if (request == null) {
            throw new BusinessException(404, "提现申请不存在");
        }
        return request;
    }

    private void requireOwner(ReferralRebateRecord record, Long userId) {
        if (!record.getInviterId().equals(userId)) {
            throw new BusinessException(403, "无权操作该返利记录");
        }
    }

    private void requireStatus(ReferralRebateRecord record, String status) {
        if (!status.equals(record.getStatus())) {
            throw new BusinessException(400, "当前状态不允许该操作");
        }
    }

    private void requireWithdrawStatus(ReferralWithdrawRequest request, String status) {
        if (!status.equals(request.getStatus())) {
            throw new BusinessException(400, "当前提现状态不允许该操作");
        }
    }

    private String normalizeReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String normalizeQrCodeUrl(Long userId, String qrCodeUrl) {
        String value = qrCodeUrl == null ? "" : qrCodeUrl.trim();
        if (value.isBlank()) {
            throw new BusinessException(400, "请上传收款二维码");
        }
        String expectedPrefix = "/api/images/referral-qr/" + userId + "/";
        if (!value.startsWith(expectedPrefix)) {
            throw new BusinessException(400, "请上传本人的收款二维码图片");
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String normalizeQrCodeUrl(Long userId, String channel, String qrCodeUrl) {
        String value = qrCodeUrl == null ? "" : qrCodeUrl.trim();
        if (value.isBlank()) {
            throw new BusinessException(400, "请上传收款二维码");
        }
        String expectedPrefix = "/api/images/referral-qr/" + userId + "/" + channel + "/";
        if (!value.startsWith(expectedPrefix)) {
            throw new BusinessException(400, "请上传本人的收款二维码图片");
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private BigDecimal normalizeWithdrawAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "提现金额必须大于 0");
        }
        return amount.setScale(6, RoundingMode.HALF_UP);
    }

    private String normalizeWithdrawChannel(String channel) {
        String value = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        if (!"wechat".equals(value) && !"alipay".equals(value)) {
            throw new BusinessException(400, "提现渠道仅支持微信或支付宝");
        }
        return value;
    }

    private List<ReferralWithdrawQrCodeDto> withdrawQrCodes(Long userId) {
        return referralWithdrawQrCodeMapper.selectList(new LambdaQueryWrapper<com.qzcy.backend.entity.ReferralWithdrawQrCode>()
                        .eq(com.qzcy.backend.entity.ReferralWithdrawQrCode::getUserId, userId))
                .stream()
                .map(item -> new ReferralWithdrawQrCodeDto(item.getChannel(), item.getQrCodeUrl()))
                .toList();
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int value = RANDOM.nextInt(36);
            builder.append((char) (value < 10 ? '0' + value : 'A' + value - 10));
        }
        return builder.toString();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
