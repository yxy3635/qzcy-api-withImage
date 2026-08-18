package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.AdminPaymentRecordDto;
import com.qzcy.backend.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {
    @Select("SELECT COALESCE(SUM(amount), 0) FROM payment_record WHERE status = 'completed' AND type IN ('third_party','alipay','wxpay','qqpay','wechat')")
    BigDecimal totalRevenue();

    @Update("UPDATE payment_record SET status = 'completed' WHERE id = #{id} AND status = 'pending'")
    int markCompletedIfPending(@Param("id") Long id);

    @Delete("""
            DELETE FROM payment_record
            WHERE status = 'pending'
              AND type IN ('third_party', 'alipay', 'wxpay', 'qqpay', 'wechat')
              AND created_at < #{before}
            """)
    int deleteExpiredPendingThirdPartyPayments(@Param("before") java.time.LocalDateTime before);

    @Select("""
            SELECT p.id,
                   p.user_id AS userId,
                   u.username,
                   u.email,
                   p.amount,
                   p.recharge_amount AS rechargeAmount,
                   p.discount_amount AS discountAmount,
                   p.coupon_code AS couponCode,
                   p.type,
                   p.status,
                   p.remark,
                   p.created_at AS createdAt
            FROM payment_record p
            LEFT JOIN `user` u ON u.id = p.user_id
            WHERE p.type IN ('third_party', 'alipay', 'wxpay', 'qqpay', 'wechat')
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR u.email LIKE CONCAT('%', #{keyword}, '%')
                   OR CAST(p.user_id AS CHAR) = #{keyword}
                   OR CAST(p.id AS CHAR) = #{keyword})
              AND (#{status} IS NULL OR #{status} = '' OR p.status = #{status})
            ORDER BY p.created_at DESC
            """)
    Page<AdminPaymentRecordDto> adminRechargeRecords(Page<AdminPaymentRecordDto> page,
                                                     @Param("keyword") String keyword,
                                                     @Param("status") String status);
}
