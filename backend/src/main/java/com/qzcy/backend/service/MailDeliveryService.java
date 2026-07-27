package com.qzcy.backend.service;

import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.entity.User;

public interface MailDeliveryService {
    void sendVerificationCode(String email, String scene, String code);
    void sendRechargeSucceeded(User user, PaymentRecord record);
    void sendTest(String recipient);
}
