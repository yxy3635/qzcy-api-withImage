package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.qzcy.backend.dto.RegisterDto;
import com.qzcy.backend.dto.RegistrationConfigDto;
import com.qzcy.backend.entity.MailConfig;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.*;
import com.qzcy.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationPolicyTest {
    private final RegistrationConfigService policy = spy(new RegistrationConfigService(mock(JdbcTemplate.class)));
    private final UserMapper users = mock(UserMapper.class);
    private final EmailCodeService codes = mock(EmailCodeService.class);
    private final ReferralService referrals = mock(ReferralService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthServiceImpl auth = new AuthServiceImpl(users, encoder, mock(JwtUtil.class), codes,
            mock(PaymentConfigService.class), referrals, policy);
    private RegisterDto request;

    @BeforeEach
    void setUp() {
        request = new RegisterDto();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setCode("123456");
    }

    private void configure(boolean closed, boolean invitationOnly) {
        doReturn(new RegistrationConfigDto(closed, invitationOnly)).when(policy).current();
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, false", "true, true"})
    void closedRegistrationRejectsWithOrWithoutAnInvitation(boolean invitationOnly, boolean hasCode) {
        configure(true, invitationOnly);
        request.setInviteCode(hasCode ? "ABC123" : null);
        assertEquals(403, assertThrows(BusinessException.class, () -> auth.register(request)).getCode());
        verifyNoInteractions(users, codes, referrals, encoder);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void invitationOnlyRejectsMissingCodeBeforeConsumingEmailCode(String code) {
        configure(false, true);
        request.setInviteCode(code);
        assertThrows(BusinessException.class, () -> auth.register(request));
        verifyNoInteractions(users, codes, referrals, encoder);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidInvitationCannotCreateAccount(boolean invitationOnly) {
        configure(false, invitationOnly);
        request.setInviteCode("BAD123");
        when(referrals.inviterIdForCode("BAD123")).thenThrow(new BusinessException(400, "邀请码不存在"));
        assertThrows(BusinessException.class, () -> auth.register(request));
        verify(users, never()).insert(any(User.class));
        verifyNoInteractions(codes, encoder);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "false, true", "true, true"})
    void allowedRegistrationKeepsExistingInvitationBinding(boolean invitationOnly, boolean hasCode) {
        configure(false, invitationOnly);
        String code = hasCode ? "ABC123" : null;
        request.setInviteCode(code);
        when(referrals.inviterIdForCode(code)).thenReturn(hasCode ? 42L : null);
        when(encoder.encode(request.getPassword())).thenReturn("encoded");
        auth.register(request);
        verify(codes).verify("new@example.com", "register", "123456");
        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).insert(saved.capture());
        assertEquals(hasCode ? 42L : null, saved.getValue().getInviterId());
        assertEquals("encoded", saved.getValue().getPassword());
    }

    @Test
    void closingRegistrationTakesEffectOnNextRequest() {
        configure(false, false);
        assertDoesNotThrow(() -> policy.validateRegistration(null));
        configure(true, false);
        assertThrows(BusinessException.class, () -> auth.register(request));
        verifyNoInteractions(users, codes);
    }

    @Test
    void closedRegistrationBlocksRegistrationMailButAllowsPasswordRecovery() {
        configure(true, true);
        MailConfigService mailConfig = mock(MailConfigService.class);
        MailDeliveryService mail = mock(MailDeliveryService.class);
        EmailCodeRateLimiter limiter = mock(EmailCodeRateLimiter.class);
        EmailCodeServiceImpl service = new EmailCodeServiceImpl(mailConfig, mail, limiter, users, policy);

        assertThrows(BusinessException.class, () -> service.sendCode("new@example.com", "register", "127.0.0.1"));
        verifyNoInteractions(mail, limiter, users);

        when(users.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(mailConfig.current()).thenReturn(new MailConfig());
        assertEquals(true, service.sendCode("existing@example.com", "forgot_password", "127.0.0.1").get("sent"));
        verify(mail).sendVerificationCode(eq("existing@example.com"), eq("forgot_password"), anyString());
    }

    @Test
    void incompleteSettingsAreRejectedWithoutWriting() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RegistrationConfigService service = new RegistrationConfigService(jdbc);
        assertThrows(BusinessException.class, () -> service.update(new RegistrationConfigDto(null, true)));
        assertThrows(BusinessException.class, () -> service.update(new RegistrationConfigDto(false, null)));
        verifyNoInteractions(jdbc);
    }
}
