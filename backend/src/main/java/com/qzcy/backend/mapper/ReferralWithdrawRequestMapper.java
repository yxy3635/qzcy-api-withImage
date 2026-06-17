package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ReferralWithdrawRequestDto;
import com.qzcy.backend.entity.ReferralWithdrawRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ReferralWithdrawRequestMapper extends BaseMapper<ReferralWithdrawRequest> {
    @Select("SELECT COALESCE(SUM(amount), 0) FROM referral_withdraw_request WHERE user_id = #{userId} AND status = #{status}")
    BigDecimal totalByStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            SELECT r.id,
                   r.user_id AS userId,
                   u.username AS username,
                   r.amount,
                   r.channel,
                   r.qr_code_url AS qrCodeUrl,
                   r.status,
                   r.fail_reason AS failReason,
                   r.reviewed_at AS reviewedAt,
                   r.created_at AS createdAt
            FROM referral_withdraw_request r
            JOIN `user` u ON u.id = r.user_id
            WHERE (#{status} IS NULL OR #{status} = '' OR r.status = #{status})
            ORDER BY r.created_at DESC
            """)
    Page<ReferralWithdrawRequestDto> adminPage(Page<ReferralWithdrawRequestDto> page, @Param("status") String status);

    @Select("""
            SELECT r.id,
                   r.user_id AS userId,
                   u.username AS username,
                   r.amount,
                   r.channel,
                   r.qr_code_url AS qrCodeUrl,
                   r.status,
                   r.fail_reason AS failReason,
                   r.reviewed_at AS reviewedAt,
                   r.created_at AS createdAt
            FROM referral_withdraw_request r
            JOIN `user` u ON u.id = r.user_id
            WHERE r.user_id = #{userId}
            ORDER BY r.created_at DESC
            """)
    Page<ReferralWithdrawRequestDto> userPage(Page<ReferralWithdrawRequestDto> page, @Param("userId") Long userId);
}
