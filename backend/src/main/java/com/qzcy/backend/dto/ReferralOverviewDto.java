package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class ReferralOverviewDto {
    private Boolean enabled;
    private String invitationCode;
    private String invitationLink;
    private BigDecimal rebateRate;
    private Long invitedUsers;
    private BigDecimal inviteeRechargeTotal;
    private BigDecimal rebateTotal;
    private BigDecimal referralBalance;
    private BigDecimal pendingReviewAmount;
    private BigDecimal approvedAmount;
    private BigDecimal withdrawingAmount;
    private List<ReferralWithdrawQrCodeDto> withdrawQrCodes;
}
