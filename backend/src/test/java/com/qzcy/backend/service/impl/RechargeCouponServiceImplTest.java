package com.qzcy.backend.service.impl;

import com.qzcy.backend.dto.RechargeCouponPreviewDto;
import com.qzcy.backend.entity.RechargeCoupon;
import com.qzcy.backend.mapper.RechargeCouponMapper;
import com.qzcy.backend.mapper.RechargeCouponUsageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RechargeCouponServiceImplTest {
    private RechargeCouponMapper couponMapper;
    private RechargeCouponUsageMapper usageMapper;
    private RechargeCouponServiceImpl service;

    @BeforeEach
    void setUp() {
        couponMapper = mock(RechargeCouponMapper.class);
        usageMapper = mock(RechargeCouponUsageMapper.class);
        service = new RechargeCouponServiceImpl(couponMapper, usageMapper);
    }

    @Test
    void capsDiscountAmountWithoutChangingCreditedAmount() {
        RechargeCoupon coupon = new RechargeCoupon();
        coupon.setId(7L);
        coupon.setCode("CAP2");
        coupon.setDiscountPercent(new BigDecimal("85"));
        coupon.setMaxUsesPerUser(0);
        coupon.setMaxDiscountAmount(new BigDecimal("2"));
        coupon.setEnabled(true);
        when(couponMapper.selectByCode("CAP2")).thenReturn(coupon);
        when(usageMapper.activeUseCount(7L, 11L)).thenReturn(0L);

        RechargeCouponPreviewDto preview = service.preview(11L, new BigDecimal("100"), "CAP2");

        assertEquals(new BigDecimal("100.000000"), preview.getOriginalAmount());
        assertEquals(new BigDecimal("2.000000"), preview.getDiscountAmount());
        assertEquals(new BigDecimal("98.00"), preview.getPayableAmount());
        assertEquals("优惠码已生效，已按优惠上限计算", preview.getMessage());
    }

    @Test
    void leavesDiscountUncappedWhenLimitIsDisabled() {
        RechargeCoupon coupon = new RechargeCoupon();
        coupon.setId(8L);
        coupon.setCode("NOCAP");
        coupon.setDiscountPercent(new BigDecimal("85"));
        coupon.setMaxUsesPerUser(0);
        coupon.setMaxDiscountAmount(BigDecimal.ZERO);
        coupon.setEnabled(true);
        when(couponMapper.selectByCode("NOCAP")).thenReturn(coupon);
        when(usageMapper.activeUseCount(8L, 11L)).thenReturn(0L);

        RechargeCouponPreviewDto preview = service.preview(11L, new BigDecimal("100"), "NOCAP");

        assertEquals(new BigDecimal("15.000000"), preview.getDiscountAmount());
        assertEquals(new BigDecimal("85.00"), preview.getPayableAmount());
        assertEquals("优惠码已生效", preview.getMessage());
    }

    @Test
    void releasesReservedUsageWhenPaymentIsCancelled() {
        service.releaseReservation(42L);

        verify(usageMapper).releaseByPaymentRecordId(42L);
    }

    @Test
    void deletesCouponEvenWhenItHasUsageRecords() {
        RechargeCoupon coupon = new RechargeCoupon();
        coupon.setId(9L);
        when(couponMapper.selectById(9L)).thenReturn(coupon);
        when(usageMapper.activeUseCountForCoupon(9L)).thenReturn(1L);

        service.delete(9L);

        verify(couponMapper).deleteById(9L);
    }
}
