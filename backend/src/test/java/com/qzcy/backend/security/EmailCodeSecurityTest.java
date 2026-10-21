package com.qzcy.backend.security;

import com.qzcy.backend.entity.MailConfig;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.*;
import com.qzcy.backend.service.impl.EmailCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailCodeSecurityTest {
    private final MailConfigService configService = mock(MailConfigService.class);
    private final MailDeliveryService delivery = mock(MailDeliveryService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final AtomicReference<String> code = new AtomicReference<>();
    private final MailConfig config = new MailConfig();
    private final EmailCodeServiceImpl service = new EmailCodeServiceImpl(configService, delivery,
            new EmailCodeRateLimiter(), users, mock(RegistrationConfigService.class));

    @BeforeEach void setup() {
        config.setEnabled(true); config.setDevReturnCode(true);
        when(configService.current()).thenReturn(config);
        when(users.selectCount(any())).thenReturn(1L);
        doAnswer(call -> { code.set(call.getArgument(2)); return null; }).when(delivery).sendVerificationCode(anyString(), anyString(), anyString());
    }

    @Test void disabledSmtpNeverReturnsDebugCodeEvenForLoopback() {
        config.setEnabled(false);
        assertEquals(503, assertThrows(BusinessException.class,
                () -> service.sendCode("victim@example.invalid", "forgot_password", "127.0.0.1")).getCode());
        verifyNoInteractions(delivery);
    }

    @Test void codesAreNotInResponseAndCannotBeReusedOrCrossScenes() {
        var result = service.sendCode("victim@example.invalid", "forgot_password", "127.0.0.1");
        assertFalse(result.containsKey("devCode"));
        assertThrows(BusinessException.class, () -> service.verify("victim@example.invalid", "change_email_current", code.get()));
        assertDoesNotThrow(() -> service.verify("victim@example.invalid", "forgot_password", code.get()));
        assertThrows(BusinessException.class, () -> service.verify("victim@example.invalid", "forgot_password", code.get()));
    }

    @Test void deliveryFailureInvalidatesUndeliveredCode() {
        doAnswer(call -> { code.set(call.getArgument(2)); throw new BusinessException(500, "SMTP failed"); })
                .when(delivery).sendVerificationCode(anyString(), anyString(), anyString());
        assertThrows(BusinessException.class, () -> service.sendCode("victim@example.invalid", "forgot_password", "198.51.100.8"));
        assertThrows(BusinessException.class, () -> service.verify("victim@example.invalid", "forgot_password", code.get()));
    }
}
