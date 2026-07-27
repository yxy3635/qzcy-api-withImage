package com.qzcy.backend.event;

import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.PaymentRecordMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class RechargeMailNotificationListener {
    private final UserMapper userMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final MailDeliveryService mailDeliveryService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRechargeSucceeded(RechargeSucceededEvent event) {
        try {
            User user = userMapper.selectById(event.userId());
            PaymentRecord record = paymentRecordMapper.selectById(event.paymentRecordId());
            mailDeliveryService.sendRechargeSucceeded(user, record);
        } catch (Exception ex) {
            log.warn("Recharge success email was not sent for payment record {}", event.paymentRecordId(), ex);
        }
    }
}
