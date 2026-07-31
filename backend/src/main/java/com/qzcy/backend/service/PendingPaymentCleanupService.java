package com.qzcy.backend.service;

import com.qzcy.backend.mapper.PaymentRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingPaymentCleanupService {
    private final PaymentRecordMapper paymentRecordMapper;

    @Scheduled(fixedDelayString = "${app.payment.pending-cleanup.interval-ms:900000}", initialDelay = 60_000)
    public void deleteExpiredPendingPayments() {
        int deleted = paymentRecordMapper.deleteExpiredPendingThirdPartyPayments(LocalDateTime.now().minusHours(24));
        if (deleted > 0) {
            log.info("Deleted expired pending third-party payment records: count={}", deleted);
        }
    }
}
