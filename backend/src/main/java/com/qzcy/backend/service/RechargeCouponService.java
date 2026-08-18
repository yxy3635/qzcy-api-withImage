package com.qzcy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.RechargeCouponAdminDto;
import com.qzcy.backend.dto.RechargeCouponPreviewDto;
import com.qzcy.backend.dto.RechargeCouponUpdateDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface RechargeCouponService {
    RechargeCouponPreviewDto preview(Long userId, BigDecimal amount, String code);

    RechargeCouponPreviewDto reserve(Long userId, BigDecimal amount, String code, Long paymentRecordId);

    Page<RechargeCouponAdminDto> adminPage(long page, long size, String keyword);

    RechargeCouponAdminDto create(RechargeCouponUpdateDto dto);

    RechargeCouponAdminDto update(Long id, RechargeCouponUpdateDto dto);

    void delete(Long id);

    String randomCode();

    void completeReservation(Long paymentRecordId);

    int releaseExpiredReservations(LocalDateTime before);
}
