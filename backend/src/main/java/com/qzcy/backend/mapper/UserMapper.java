package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Update("UPDATE `user` SET balance = balance - #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId} AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE `user` SET balance = balance + #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE `user` SET referral_balance = referral_balance + #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId}")
    int addReferralBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Update("UPDATE `user` SET referral_balance = referral_balance - #{amount}, updated_at = NOW(), version = version + 1 WHERE id = #{userId} AND referral_balance >= #{amount}")
    int deductReferralBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
