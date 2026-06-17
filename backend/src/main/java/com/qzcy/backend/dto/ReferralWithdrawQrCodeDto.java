package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferralWithdrawQrCodeDto {
    private String channel;
    private String qrCodeUrl;
}
