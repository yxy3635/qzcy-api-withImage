package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.dto.PaymentConfigDto;
import com.qzcy.backend.dto.PaymentConfigUpdateDto;
import com.qzcy.backend.entity.PaymentConfig;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.PaymentConfigMapper;
import com.qzcy.backend.service.PaymentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentConfigServiceImpl implements PaymentConfigService {
    private final PaymentConfigMapper mapper;

    @Override
    public PaymentConfig current() {
        PaymentConfig config = mapper.selectOne(new LambdaQueryWrapper<PaymentConfig>()
                .orderByAsc(PaymentConfig::getId)
                .last("LIMIT 1"));
        if (config != null) {
            return config;
        }
        PaymentConfig created = new PaymentConfig();
        created.setApiUrl("");
        created.setMerchantId("");
        created.setMerchantSecret("");
        created.setRegisterGiftAmount(BigDecimal.ZERO);
        created.setReferralRebateRate(BigDecimal.ZERO);
        created.setEnabled(false);
        created.setAlipayEnabled(true);
        created.setWxpayEnabled(true);
        created.setQqpayEnabled(false);
        mapper.insert(created);
        return created;
    }

    @Override
    public BigDecimal registerGiftAmount() {
        BigDecimal amount = current().getRegisterGiftAmount();
        return amount == null || amount.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : amount;
    }

    @Override
    public BigDecimal referralRebateRate() {
        BigDecimal rate = current().getReferralRebateRate();
        return rate == null || rate.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : rate;
    }

    @Override
    public PaymentConfigDto adminDetail() {
        return toDto(current());
    }

    @Override
    public PaymentConfigDto update(PaymentConfigUpdateDto dto) {
        PaymentConfig config = current();
        if (dto.getApiUrl() != null) {
            config.setApiUrl(dto.getApiUrl().trim());
        }
        if (dto.getMerchantId() != null) {
            config.setMerchantId(dto.getMerchantId().trim());
        }
        if (dto.getMerchantSecret() != null && !dto.getMerchantSecret().isBlank()) {
            config.setMerchantSecret(dto.getMerchantSecret().trim());
        }
        if (dto.getRegisterGiftAmount() != null) {
            if (dto.getRegisterGiftAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(400, "注册赠送金额不能小于0");
            }
            config.setRegisterGiftAmount(dto.getRegisterGiftAmount());
        }
        if (dto.getReferralRebateRate() != null) {
            if (dto.getReferralRebateRate().compareTo(BigDecimal.ZERO) < 0
                    || dto.getReferralRebateRate().compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException(400, "邀请返利比例必须在0-100之间");
            }
            config.setReferralRebateRate(dto.getReferralRebateRate());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getAlipayEnabled() != null) {
            config.setAlipayEnabled(dto.getAlipayEnabled());
        }
        if (dto.getWxpayEnabled() != null) {
            config.setWxpayEnabled(dto.getWxpayEnabled());
        }
        if (dto.getQqpayEnabled() != null) {
            config.setQqpayEnabled(dto.getQqpayEnabled());
        }
        validate(config);
        mapper.updateById(config);
        return toDto(mapper.selectById(config.getId()));
    }

    private void validate(PaymentConfig config) {
        if (Boolean.TRUE.equals(config.getEnabled())) {
            if (isBlank(config.getApiUrl())) {
                throw new BusinessException(400, "启用支付前需要填写接口地址");
            }
            if (isBlank(config.getMerchantId())) {
                throw new BusinessException(400, "启用支付前需要填写商户ID");
            }
            if (isBlank(config.getMerchantSecret())) {
                throw new BusinessException(400, "启用支付前需要填写商户密钥");
            }
            if (!Boolean.TRUE.equals(config.getAlipayEnabled())
                    && !Boolean.TRUE.equals(config.getWxpayEnabled())
                    && !Boolean.TRUE.equals(config.getQqpayEnabled())) {
                throw new BusinessException(400, "至少启用一种支付方式");
            }
        }
    }

    private PaymentConfigDto toDto(PaymentConfig config) {
        return new PaymentConfigDto(
                config.getId(),
                config.getApiUrl(),
                config.getMerchantId(),
                config.getRegisterGiftAmount(),
                config.getReferralRebateRate() == null ? BigDecimal.ZERO : config.getReferralRebateRate(),
                config.getEnabled(),
                Boolean.TRUE.equals(config.getAlipayEnabled()),
                Boolean.TRUE.equals(config.getWxpayEnabled()),
                Boolean.TRUE.equals(config.getQqpayEnabled()),
                !isBlank(config.getMerchantSecret())
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
