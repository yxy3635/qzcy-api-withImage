package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.RechargeCouponAdminDto;
import com.qzcy.backend.entity.RechargeCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RechargeCouponMapper extends BaseMapper<RechargeCoupon> {
    @Select("SELECT * FROM recharge_coupon WHERE code = #{code} LIMIT 1")
    RechargeCoupon selectByCode(@Param("code") String code);

    @Select("SELECT * FROM recharge_coupon WHERE code = #{code} LIMIT 1 FOR UPDATE")
    RechargeCoupon selectByCodeForUpdate(@Param("code") String code);

    @Select("SELECT * FROM recharge_coupon WHERE id = #{id} FOR UPDATE")
    RechargeCoupon selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM recharge_coupon WHERE code = #{code} AND id <> #{id}")
    long countByCodeExcludingId(@Param("code") String code, @Param("id") Long id);

    @Select("""
            SELECT c.id,
                   c.code,
                   c.discount_percent AS discountPercent,
                   c.max_uses_per_user AS maxUsesPerUser,
                   c.max_discount_amount AS maxDiscountAmount,
                   COALESCE(SUM(CASE WHEN u.status = 'completed' THEN 1 ELSE 0 END), 0) AS usedCount,
                   c.enabled,
                   c.created_at AS createdAt,
                   c.updated_at AS updatedAt
            FROM recharge_coupon c
            LEFT JOIN recharge_coupon_usage u ON u.coupon_id = c.id
            WHERE (#{keyword} IS NULL OR #{keyword} = '' OR c.code LIKE CONCAT('%', #{keyword}, '%'))
            GROUP BY c.id, c.code, c.discount_percent, c.max_uses_per_user, c.max_discount_amount, c.enabled, c.created_at, c.updated_at
            ORDER BY c.created_at DESC, c.id DESC
            """)
    Page<RechargeCouponAdminDto> adminPage(Page<RechargeCouponAdminDto> page, @Param("keyword") String keyword);
}
