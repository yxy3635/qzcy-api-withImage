package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ReferralInviteeDto;
import com.qzcy.backend.dto.ReferralRebateDto;
import com.qzcy.backend.entity.ReferralRebateRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface ReferralRebateRecordMapper extends BaseMapper<ReferralRebateRecord> {
    @Select("""
            SELECT u.id AS userId,
                   u.username AS username,
                   COALESCE(SUM(CASE
                       WHEN p.status = 'completed' AND p.type IN ('third_party','alipay','wxpay','qqpay','wechat')
                       THEN p.amount ELSE 0 END), 0) AS totalRecharge,
                   u.created_at AS registeredAt
            FROM `user` u
            LEFT JOIN payment_record p ON p.user_id = u.id
            WHERE u.inviter_id = #{inviterId}
            GROUP BY u.id, u.username, u.created_at
            ORDER BY u.created_at DESC
            """)
    Page<ReferralInviteeDto> invitees(Page<ReferralInviteeDto> page, @Param("inviterId") Long inviterId);

    @Select("SELECT COUNT(*) FROM `user` WHERE inviter_id = #{inviterId}")
    Long invitedUsers(@Param("inviterId") Long inviterId);

    @Select("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM payment_record p
            JOIN `user` u ON u.id = p.user_id
            WHERE u.inviter_id = #{inviterId}
              AND p.status = 'completed'
              AND p.type IN ('third_party','alipay','wxpay','qqpay','wechat')
            """)
    BigDecimal inviteeRechargeTotal(@Param("inviterId") Long inviterId);

    @Select("SELECT COALESCE(SUM(rebate_amount), 0) FROM referral_rebate_record WHERE inviter_id = #{inviterId}")
    BigDecimal rebateTotal(@Param("inviterId") Long inviterId);

    @Select("SELECT COALESCE(SUM(rebate_amount), 0) FROM referral_rebate_record WHERE inviter_id = #{inviterId} AND status = #{status}")
    BigDecimal rebateTotalByStatus(@Param("inviterId") Long inviterId, @Param("status") String status);

    @Select("""
            SELECT r.id,
                   r.inviter_id AS inviterId,
                   inviter.username AS inviterUsername,
                   r.invitee_id AS inviteeId,
                   invitee.username AS inviteeUsername,
                   r.recharge_amount AS rechargeAmount,
                   r.rebate_rate AS rebateRate,
                   r.rebate_amount AS rebateAmount,
                   r.status,
                   r.reject_reason AS rejectReason,
                   r.withdraw_qr_code_url AS withdrawQrCodeUrl,
                   r.withdraw_fail_reason AS withdrawFailReason,
                   r.reviewed_at AS reviewedAt,
                   r.withdrawn_at AS withdrawnAt,
                   r.created_at AS createdAt
            FROM referral_rebate_record r
            JOIN `user` inviter ON inviter.id = r.inviter_id
            JOIN `user` invitee ON invitee.id = r.invitee_id
            WHERE r.inviter_id = #{inviterId}
            ORDER BY r.created_at DESC
            """)
    Page<ReferralRebateDto> userRebates(Page<ReferralRebateDto> page, @Param("inviterId") Long inviterId);

    @Select("""
            SELECT r.id,
                   r.inviter_id AS inviterId,
                   inviter.username AS inviterUsername,
                   r.invitee_id AS inviteeId,
                   invitee.username AS inviteeUsername,
                   r.recharge_amount AS rechargeAmount,
                   r.rebate_rate AS rebateRate,
                   r.rebate_amount AS rebateAmount,
                   r.status,
                   r.reject_reason AS rejectReason,
                   r.withdraw_qr_code_url AS withdrawQrCodeUrl,
                   r.withdraw_fail_reason AS withdrawFailReason,
                   r.reviewed_at AS reviewedAt,
                   r.withdrawn_at AS withdrawnAt,
                   r.created_at AS createdAt
            FROM referral_rebate_record r
            JOIN `user` inviter ON inviter.id = r.inviter_id
            JOIN `user` invitee ON invitee.id = r.invitee_id
            WHERE (#{status} IS NULL OR #{status} = '' OR r.status = #{status})
            ORDER BY r.created_at DESC
            """)
    Page<ReferralRebateDto> adminRebates(Page<ReferralRebateDto> page, @Param("status") String status);
}
