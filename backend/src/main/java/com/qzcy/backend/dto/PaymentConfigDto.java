package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PaymentConfigDto {
    private Long id;
    private String apiUrl;
    private String merchantId;
    private BigDecimal registerGiftAmount;
    private BigDecimal referralRebateRate;
    private Boolean enabled;
    private Boolean alipayEnabled;
    private Boolean wxpayEnabled;
    private Boolean qqpayEnabled;
    private Boolean merchantSecretConfigured;
}
