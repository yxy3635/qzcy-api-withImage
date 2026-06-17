package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentConfigUpdateDto {
    private String apiUrl;
    private String merchantId;
    private String merchantSecret;
    private BigDecimal registerGiftAmount;
    private BigDecimal referralRebateRate;
    private Boolean enabled;
    private Boolean alipayEnabled;
    private Boolean wxpayEnabled;
    private Boolean qqpayEnabled;
}
