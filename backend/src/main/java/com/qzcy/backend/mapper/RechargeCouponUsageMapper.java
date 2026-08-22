package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RechargeCouponUsage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface RechargeCouponUsageMapper extends BaseMapper<RechargeCouponUsage> {
    @Select("SELECT COUNT(*) FROM recharge_coupon_usage WHERE coupon_id = #{couponId} AND user_id = #{userId} AND status IN ('reserved', 'completed')")
    long activeUseCount(@Param("couponId") Long couponId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM recharge_coupon_usage WHERE coupon_id = #{couponId} AND status IN ('reserved', 'completed')")
    long activeUseCountForCoupon(@Param("couponId") Long couponId);

    @Select("SELECT COUNT(*) FROM recharge_coupon_usage WHERE coupon_id = #{couponId} AND status = 'completed'")
    long completedUseCount(@Param("couponId") Long couponId);

    @Insert("""
            INSERT INTO recharge_coupon_usage
                (coupon_id, user_id, payment_record_id, status, created_at)
            VALUES
                (#{couponId}, #{userId}, #{paymentRecordId}, 'reserved', NOW())
            """)
    int reserve(@Param("couponId") Long couponId,
                @Param("userId") Long userId,
                @Param("paymentRecordId") Long paymentRecordId);

    @Update("""
            UPDATE recharge_coupon_usage
            SET status = 'completed', completed_at = NOW()
            WHERE payment_record_id = #{paymentRecordId}
              AND status = 'reserved'
            """)
    int completeByPaymentRecordId(@Param("paymentRecordId") Long paymentRecordId);

    @Update("""
            UPDATE recharge_coupon_usage
            SET status = 'released'
            WHERE payment_record_id = #{paymentRecordId}
              AND status = 'reserved'
            """)
    int releaseByPaymentRecordId(@Param("paymentRecordId") Long paymentRecordId);

    @Delete("""
            DELETE u
            FROM recharge_coupon_usage u
            INNER JOIN payment_record p ON p.id = u.payment_record_id
            WHERE u.status = 'reserved'
              AND p.status = 'pending'
              AND p.created_at < #{before}
            """)
    int releaseExpiredReservations(@Param("before") LocalDateTime before);
}
