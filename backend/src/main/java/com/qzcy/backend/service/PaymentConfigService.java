package com.qzcy.backend.service;

import com.qzcy.backend.dto.PaymentConfigDto;
import com.qzcy.backend.dto.PaymentConfigUpdateDto;
import com.qzcy.backend.entity.PaymentConfig;

import java.math.BigDecimal;

public interface PaymentConfigService {
    PaymentConfig current();
    BigDecimal registerGiftAmount();
    BigDecimal referralRebateRate();
    PaymentConfigDto adminDetail();
    PaymentConfigDto update(PaymentConfigUpdateDto dto);
}
