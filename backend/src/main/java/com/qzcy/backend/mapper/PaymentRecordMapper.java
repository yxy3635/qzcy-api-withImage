package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
}
