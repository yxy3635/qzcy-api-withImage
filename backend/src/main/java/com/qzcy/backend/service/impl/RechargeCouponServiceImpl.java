package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.RechargeCouponAdminDto;
import com.qzcy.backend.dto.RechargeCouponPreviewDto;
import com.qzcy.backend.dto.RechargeCouponUpdateDto;
import com.qzcy.backend.entity.RechargeCoupon;
import com.qzcy.backend.entity.RechargeCouponUsage;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RechargeCouponMapper;
import com.qzcy.backend.mapper.RechargeCouponUsageMapper;
import com.qzcy.backend.service.RechargeCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RechargeCouponServiceImpl implements RechargeCouponService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final RechargeCouponMapper couponMapper;
    private final RechargeCouponUsageMapper usageMapper;

    @Override
    public RechargeCouponPreviewDto preview(Long userId, BigDecimal amount, String code) {
        BigDecimal originalAmount = normalizeAmount(amount);
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isBlank()) {
            return basePreview(originalAmount);
        }
        RechargeCoupon coupon = couponMapper.selectByCode(normalizedCode);
        if (coupon == null) {
            return invalidPreview(normalizedCode, originalAmount, "优惠码不存在");
        }
        if (!Boolean.TRUE.equals(coupon.getEnabled())) {
            return invalidPreview(normalizedCode, originalAmount, "优惠码已停用");
        }
        long activeUses = usageMapper.activeUseCount(coupon.getId(), userId);
        if (reachedLimit(coupon, activeUses)) {
            return invalidPreview(normalizedCode, originalAmount, "该优惠码已达到你的使用次数上限");
        }
        return toPreview(coupon, originalAmount, activeUses);
    }

    @Override
    @Transactional
    public RechargeCouponPreviewDto reserve(Long userId, BigDecimal amount, String code, Long paymentRecordId) {
        BigDecimal originalAmount = normalizeAmount(amount);
        String normalizedCode = normalizeCode(code);
        if (normalizedCode.isBlank()) {
            return basePreview(originalAmount);
        }
        if (paymentRecordId == null) {
            throw new BusinessException(500, "支付订单创建失败");
        }
        RechargeCoupon coupon = couponMapper.selectByCodeForUpdate(normalizedCode);
        if (coupon == null) {
            throw new BusinessException(400, "优惠码不存在");
        }
        if (!Boolean.TRUE.equals(coupon.getEnabled())) {
            throw new BusinessException(400, "优惠码已停用");
        }
        long activeUses = usageMapper.activeUseCount(coupon.getId(), userId);
        if (reachedLimit(coupon, activeUses)) {
            throw new BusinessException(400, "该优惠码已达到你的使用次数上限");
        }
        RechargeCouponPreviewDto preview = toPreview(coupon, originalAmount, activeUses);
        RechargeCouponUsage usage = new RechargeCouponUsage();
        usage.setCouponId(coupon.getId());
        usage.setUserId(userId);
        usage.setPaymentRecordId(paymentRecordId);
        usage.setStatus("reserved");
        usage.setCreatedAt(LocalDateTime.now());
        try {
            usageMapper.insert(usage);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(400, "该支付订单已绑定优惠码，请勿重复提交");
        }
        return preview;
    }

    @Override
    public Page<RechargeCouponAdminDto> adminPage(long page, long size, String keyword) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(100, Math.max(1, size));
        return couponMapper.adminPage(Page.of(safePage, safeSize), blankToNull(keyword));
    }

    @Override
    @Transactional
    public RechargeCouponAdminDto create(RechargeCouponUpdateDto dto) {
        NormalizedDraft draft = normalizeDraft(dto);
        if (couponMapper.selectByCode(draft.code()) != null) {
            throw new BusinessException(409, "优惠码已存在");
        }
        RechargeCoupon coupon = new RechargeCoupon();
        apply(coupon, draft, true);
        try {
            couponMapper.insert(coupon);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "优惠码已存在");
        }
        return toAdminDto(couponMapper.selectById(coupon.getId()));
    }

    @Override
    @Transactional
    public RechargeCouponAdminDto update(Long id, RechargeCouponUpdateDto dto) {
        RechargeCoupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException(404, "优惠码不存在");
        }
        NormalizedDraft draft = normalizeDraft(dto);
        long usedCount = usageMapper.activeUseCountForCoupon(id);
        if (!coupon.getCode().equals(draft.code()) && usedCount > 0) {
            throw new BusinessException(400, "优惠码已经产生使用记录，不能修改编码");
        }
        if (couponMapper.countByCodeExcludingId(draft.code(), id) > 0) {
            throw new BusinessException(409, "优惠码已存在");
        }
        apply(coupon, draft, false);
        couponMapper.updateById(coupon);
        return toAdminDto(couponMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        RechargeCoupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException(404, "优惠码不存在");
        }
        if (usageMapper.activeUseCountForCoupon(id) > 0) {
            throw new BusinessException(400, "优惠码已经产生使用记录，请停用而不要删除");
        }
        couponMapper.deleteById(id);
    }

    @Override
    public String randomCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder builder = new StringBuilder(8);
            for (int index = 0; index < 8; index++) {
                builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = builder.toString();
            if (couponMapper.selectByCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException(500, "优惠码生成失败，请稍后重试");
    }

    @Override
    @Transactional
    public void completeReservation(Long paymentRecordId) {
        if (paymentRecordId != null) {
            usageMapper.completeByPaymentRecordId(paymentRecordId);
        }
    }

    @Override
    @Transactional
    public int releaseExpiredReservations(LocalDateTime before) {
        return usageMapper.releaseExpiredReservations(before);
    }

    private RechargeCouponPreviewDto toPreview(RechargeCoupon coupon, BigDecimal originalAmount, long activeUses) {
        BigDecimal discountPercent = coupon.getDiscountPercent().setScale(2, RoundingMode.HALF_UP);
        BigDecimal payableAmount = originalAmount
                .multiply(discountPercent)
                .divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        if (payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "优惠后支付金额不能低于 0.01 元");
        }
        BigDecimal discountAmount = originalAmount.subtract(payableAmount).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
        BigDecimal maxDiscountAmount = coupon.getMaxDiscountAmount();
        boolean capped = maxDiscountAmount != null
                && maxDiscountAmount.compareTo(BigDecimal.ZERO) > 0
                && discountAmount.compareTo(maxDiscountAmount) > 0;
        if (capped) {
            // Round the payable amount up so currency rounding never exceeds the configured cap.
            payableAmount = originalAmount.subtract(maxDiscountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.CEILING);
            if (payableAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "优惠后支付金额不能低于 0.01 元");
            }
            discountAmount = originalAmount.subtract(payableAmount).max(BigDecimal.ZERO).setScale(6, RoundingMode.HALF_UP);
        }
        Long remainingUses = coupon.getMaxUsesPerUser() == null || coupon.getMaxUsesPerUser() <= 0
                ? null
                : Math.max(0L, coupon.getMaxUsesPerUser() - activeUses);
        return new RechargeCouponPreviewDto(true, coupon.getId(), coupon.getCode(), discountPercent, originalAmount,
                discountAmount, payableAmount, remainingUses, capped ? "优惠码已生效，已按优惠上限计算" : "优惠码已生效");
    }

    private RechargeCouponPreviewDto basePreview(BigDecimal amount) {
        return new RechargeCouponPreviewDto(true, null, "", new BigDecimal("100.00"), amount,
                BigDecimal.ZERO.setScale(6), amount.setScale(2, RoundingMode.HALF_UP), null, "");
    }

    private RechargeCouponPreviewDto invalidPreview(String code, BigDecimal amount, String message) {
        return new RechargeCouponPreviewDto(false, null, code, null, amount, BigDecimal.ZERO.setScale(6),
                amount.setScale(2, RoundingMode.HALF_UP), null, message);
    }

    private boolean reachedLimit(RechargeCoupon coupon, long activeUses) {
        return coupon.getMaxUsesPerUser() != null && coupon.getMaxUsesPerUser() > 0
                && activeUses >= coupon.getMaxUsesPerUser();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "充值金额必须大于0");
        }
        return amount.setScale(6, RoundingMode.HALF_UP);
    }

    private NormalizedDraft normalizeDraft(RechargeCouponUpdateDto dto) {
        String code = normalizeCode(dto == null ? null : dto.getCode());
        BigDecimal discountPercent = dto == null ? null : dto.getDiscountPercent();
        Integer maxUsesPerUser = dto == null ? null : dto.getMaxUsesPerUser();
        BigDecimal maxDiscountAmount = dto == null ? null : dto.getMaxDiscountAmount();
        if (code.isBlank()) {
            throw new BusinessException(400, "优惠码不能为空");
        }
        if (code.length() > 255) {
            throw new BusinessException(400, "优惠码不能超过255个字符");
        }
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0
                || discountPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException(400, "实付比例必须大于0且不超过100");
        }
        if (maxUsesPerUser == null || maxUsesPerUser < 0) {
            throw new BusinessException(400, "每用户最多使用次数不能小于0");
        }
        if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "优惠上限不能小于0");
        }
        BigDecimal normalizedMaxDiscountAmount = maxDiscountAmount == null
                ? BigDecimal.ZERO.setScale(6)
                : maxDiscountAmount.setScale(6, RoundingMode.HALF_UP);
        return new NormalizedDraft(code, discountPercent.setScale(2, RoundingMode.HALF_UP), maxUsesPerUser,
                normalizedMaxDiscountAmount,
                dto == null || dto.getEnabled() == null || dto.getEnabled());
    }

    private void apply(RechargeCoupon coupon, NormalizedDraft draft, boolean creating) {
        coupon.setCode(draft.code());
        coupon.setDiscountPercent(draft.discountPercent());
        coupon.setMaxUsesPerUser(draft.maxUsesPerUser());
        coupon.setMaxDiscountAmount(draft.maxDiscountAmount());
        coupon.setEnabled(draft.enabled());
        if (creating) {
            coupon.setCreatedAt(LocalDateTime.now());
        }
        coupon.setUpdatedAt(LocalDateTime.now());
    }

    private RechargeCouponAdminDto toAdminDto(RechargeCoupon coupon) {
        RechargeCouponAdminDto dto = new RechargeCouponAdminDto();
        dto.setId(coupon.getId());
        dto.setCode(coupon.getCode());
        dto.setDiscountPercent(coupon.getDiscountPercent());
        dto.setMaxUsesPerUser(coupon.getMaxUsesPerUser());
        dto.setMaxDiscountAmount(coupon.getMaxDiscountAmount());
        dto.setUsedCount(usageMapper.completedUseCount(coupon.getId()));
        dto.setEnabled(coupon.getEnabled());
        dto.setCreatedAt(coupon.getCreatedAt());
        dto.setUpdatedAt(coupon.getUpdatedAt());
        return dto;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim();
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private record NormalizedDraft(String code, BigDecimal discountPercent, Integer maxUsesPerUser,
                                   BigDecimal maxDiscountAmount, Boolean enabled) {
    }
}
