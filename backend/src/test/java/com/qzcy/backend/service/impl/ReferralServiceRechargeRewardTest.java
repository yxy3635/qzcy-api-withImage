package com.qzcy.backend.service.impl;

import com.qzcy.backend.entity.PaymentConfig;
import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.entity.ReferralRebateRecord;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.PaymentRecordMapper;
import com.qzcy.backend.mapper.ReferralRebateRecordMapper;
import com.qzcy.backend.mapper.ReferralWithdrawQrCodeMapper;
import com.qzcy.backend.mapper.ReferralWithdrawRequestMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.PaymentConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralServiceRechargeRewardTest {
    private UserMapper userMapper;
    private ReferralRebateRecordMapper referralRebateRecordMapper;
    private PaymentConfigService paymentConfigService;
    private ReferralServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        referralRebateRecordMapper = mock(ReferralRebateRecordMapper.class);
        paymentConfigService = mock(PaymentConfigService.class);
        service = new ReferralServiceImpl(
                userMapper,
                mock(PaymentRecordMapper.class),
                referralRebateRecordMapper,
                mock(ReferralWithdrawQrCodeMapper.class),
                mock(ReferralWithdrawRequestMapper.class),
                paymentConfigService
        );
    }

    @Test
    void calculatesReferralRewardFromCreditedAmountBeforeCoupon() {
        User invitee = new User();
        invitee.setId(11L);
        invitee.setInviterId(3L);
        when(userMapper.selectById(11L)).thenReturn(invitee);

        PaymentConfig config = new PaymentConfig();
        config.setReferralRebateRate(new BigDecimal("10"));
        when(paymentConfigService.current()).thenReturn(config);
        when(referralRebateRecordMapper.selectCount(any())).thenReturn(0L);

        PaymentRecord payment = new PaymentRecord();
        payment.setId(19L);
        payment.setUserId(11L);
        payment.setAmount(new BigDecimal("98.00"));
        payment.setRechargeAmount(new BigDecimal("100.000000"));
        payment.setDiscountAmount(new BigDecimal("2.000000"));

        service.rewardForRecharge(payment);

        var captor = org.mockito.ArgumentCaptor.forClass(ReferralRebateRecord.class);
        verify(referralRebateRecordMapper).insert(captor.capture());
        assertEquals(new BigDecimal("100.000000"), captor.getValue().getRechargeAmount());
        assertEquals(new BigDecimal("10.000000"), captor.getValue().getRebateAmount());
    }
}
