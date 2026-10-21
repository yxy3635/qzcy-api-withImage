package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Update("UPDATE `user` SET auth_version = auth_version + 1 WHERE id = #{userId}")
    int revokeSessions(@Param("userId") Long userId);

    @Update("UPDATE `user` SET password = #{replacement}, auth_version = auth_version + 1, updated_at = NOW() "
            + "WHERE id = #{userId} AND BINARY password = BINARY #{expectedPassword} AND email <=> #{expectedEmail}")
    int changePasswordSecurely(@Param("userId") Long userId, @Param("expectedPassword") String expectedPassword,
                              @Param("expectedEmail") String expectedEmail, @Param("replacement") String replacement);

    @Update("UPDATE `user` SET email = #{replacement}, auth_version = auth_version + 1, updated_at = NOW() "
            + "WHERE id = #{userId} AND BINARY password = BINARY #{expectedPassword} AND email <=> #{expectedEmail}")
    int changeEmailSecurely(@Param("userId") Long userId, @Param("expectedPassword") String expectedPassword,
                           @Param("expectedEmail") String expectedEmail, @Param("replacement") String replacement);

    @Select("SELECT id FROM `user` WHERE id = #{userId} FOR UPDATE")
    Long lockUserForRecharge(@Param("userId") Long userId);

    @Update("UPDATE `user` SET balance = balance - #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    // Incurred relay usage must be settled even if concurrent calls exhausted the balance.
    @Update("UPDATE `user` SET balance = balance - #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId}")
    int settleRelayBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE `user` SET balance = balance + #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE `user` SET referral_balance = referral_balance + #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId}")
    int addReferralBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE `user` SET referral_balance = referral_balance - #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId} AND referral_balance >= #{amount}")
    int deductReferralBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
