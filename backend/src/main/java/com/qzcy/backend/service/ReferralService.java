package com.qzcy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ReferralInviteeDto;
import com.qzcy.backend.dto.ReferralOverviewDto;
import com.qzcy.backend.dto.ReferralRebateDto;
import com.qzcy.backend.dto.ReferralWithdrawRequestDto;
import com.qzcy.backend.entity.PaymentRecord;

import java.math.BigDecimal;

public interface ReferralService {
    Long inviterIdForCode(String invitationCode);
    String ensureInvitationCode(Long userId);
    ReferralOverviewDto enable(Long userId, String frontendBaseUrl);
    ReferralOverviewDto overview(Long userId, String frontendBaseUrl);
    Page<ReferralInviteeDto> invitees(Long userId, long page, long size);
    Page<ReferralRebateDto> rebates(Long userId, long page, long size);
    Page<ReferralRebateDto> adminRebates(long page, long size, String status);
    Page<ReferralWithdrawRequestDto> withdrawRequests(Long userId, long page, long size);
    Page<ReferralWithdrawRequestDto> adminWithdrawRequests(long page, long size, String status);
    void approve(Long id, Long adminId);
    void reject(Long id, Long adminId, String reason);
    void transferToBalance(Long userId, Long id);
    void requestWithdraw(Long userId, Long id, String qrCodeUrl);
    void requestAccountWithdraw(Long userId, BigDecimal amount, String channel, String qrCodeUrl);
    void markWithdrawSuccess(Long id, Long adminId);
    void markWithdrawFailed(Long id, Long adminId, String reason);
    void markAccountWithdrawSuccess(Long id, Long adminId);
    void markAccountWithdrawFailed(Long id, Long adminId, String reason);
    void rewardForRecharge(PaymentRecord paymentRecord);
}
