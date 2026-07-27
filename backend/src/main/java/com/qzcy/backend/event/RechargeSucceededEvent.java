package com.qzcy.backend.event;

public record RechargeSucceededEvent(Long userId, Long paymentRecordId) {
}
