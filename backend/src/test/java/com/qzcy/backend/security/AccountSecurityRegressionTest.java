package com.qzcy.backend.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.config.security.JwtAuthenticationFilter;
import com.qzcy.backend.dto.*;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import com.qzcy.backend.service.impl.*;
import com.qzcy.backend.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountSecurityRegressionTest {
    private static final String SECRET = "0a3e2c9b8d7f6a5e4c3b2d1098765432f0a1b2c3d4e5f60718293a4b5c6d7e8f";
    private final UserMapper users = mock(UserMapper.class);
    private final EmailCodeService codes = mock(EmailCodeService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtUtil jwt = new JwtUtil();
    private User user;
    private UserServiceImpl service;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        ReflectionTestUtils.setField(jwt, "secret", SECRET);
        ReflectionTestUtils.setField(jwt, "expirationMs", 86400000L);
        user = new User();
        user.setId(42L); user.setUsername("victim"); user.setEmail("victim@example.invalid");
        user.setRole("USER"); user.setBanned(false); user.setAuthVersion(0L);
        user.setPassword(encoder.encode("OriginalPassword!"));
        when(users.selectById(42L)).thenAnswer(call -> user);
        when(users.changePasswordSecurely(eq(42L), anyString(), anyString(), anyString())).thenAnswer(call -> {
            user.setPassword(call.getArgument(3)); user.setAuthVersion(user.getAuthVersion() + 1); return 1;
        });
        when(users.changeEmailSecurely(eq(42L), anyString(), anyString(), anyString())).thenAnswer(call -> {
            user.setEmail(call.getArgument(3)); user.setAuthVersion(user.getAuthVersion() + 1); return 1;
        });
        service = new UserServiceImpl(users, encoder, codes);
    }

    @Test void passwordChangeRevokesOldSessionAndFreshLoginWorks() throws Exception {
        String stolen = jwt.generateToken(user);
        assertTrue(authenticated(stolen));
        ChangePasswordDto change = new ChangePasswordDto();
        change.setOldPassword("OriginalPassword!"); change.setNewPassword("ReplacementPassword!");
        service.changePassword(42L, change);
        assertFalse(authenticated(stolen));
        assertTrue(authenticated(jwt.generateToken(user)));
        verify(users, never()).updateById(any(User.class));
    }

    @Test void passwordResetRevokesOldSession() throws Exception {
        String stolen = jwt.generateToken(user);
        when(users.selectOne(any())).thenReturn(user);
        ForgotPasswordDto reset = new ForgotPasswordDto();
        reset.setEmail(user.getEmail()); reset.setCode("123456"); reset.setNewPassword("ReplacementPassword!");
        auth().resetPassword(reset);
        assertFalse(authenticated(stolen));
        assertTrue(authenticated(jwt.generateToken(user)));
    }

    @Test void stolenSessionCannotChangeEmailWithoutOriginalPassword() {
        UserProfileDto profile = profile(); profile.setOldPassword(null);
        assertThrows(BusinessException.class, () -> service.updateProfile(42L, profile));
        verifyNoInteractions(codes);
        verify(users, never()).changeEmailSecurely(anyLong(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"change_email_current", "change_email_new"})
    void eitherInvalidEmailCodeBlocksRebinding(String scene) {
        doThrow(new BusinessException(400, "invalid code")).when(codes).verify(anyString(), eq(scene), any());
        assertThrows(BusinessException.class, () -> service.updateProfile(42L, profile()));
        verify(users, never()).changeEmailSecurely(anyLong(), any(), any(), any());
    }

    @Test void emailRebindingRequiresBothMailboxesAndRevokesSessions() throws Exception {
        String stolen = jwt.generateToken(user);
        service.updateProfile(42L, profile());
        verify(codes).verify("victim@example.invalid", "change_email_current", "111111");
        verify(codes).verify("new@example.invalid", "change_email_new", "222222");
        assertFalse(authenticated(stolen));
    }

    @Test void concurrentCredentialChangeDoesNotGetOverwritten() {
        when(users.changeEmailSecurely(anyLong(), anyString(), anyString(), anyString())).thenReturn(0);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.updateProfile(42L, profile())).getCode());
        assertEquals("victim@example.invalid", user.getEmail());
        verify(users, never()).updateById(any(User.class));
    }

    @Test void oldUnversionedTokensAreRejectedAndBalanceChangesDoNotLogOut() throws Exception {
        String legacy = Jwts.builder().subject(user.getUsername()).claim("userId", 42L)
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertFalse(authenticated(legacy));
        String fresh = jwt.generateToken(user);
        user.setVersion(57); user.setBalance(BigDecimal.ONE);
        assertTrue(authenticated(fresh));
    }

    @Test void unknownAndExistingEmailHaveSameInvalidCodeError() {
        doThrow(new BusinessException(400, "邮箱验证码无效或已过期")).when(codes).verify(anyString(), anyString(), any());
        ForgotPasswordDto reset = new ForgotPasswordDto(); reset.setNewPassword("ReplacementPassword!"); reset.setCode("000000");
        reset.setEmail(user.getEmail());
        var known = assertThrows(BusinessException.class, () -> auth().resetPassword(reset));
        reset.setEmail("unknown@example.invalid");
        var unknown = assertThrows(BusinessException.class, () -> auth().resetPassword(reset));
        assertEquals(known.getCode(), unknown.getCode()); assertEquals(known.getMessage(), unknown.getMessage());
        verify(users, never()).selectOne(any());
    }

    @Test void administratorPasswordResetAndBanRevokeSessions() throws Exception {
        when(users.revokeSessions(42L)).thenAnswer(call -> { user.setAuthVersion(user.getAuthVersion() + 1); return 1; });
        AdminServiceImpl admin = new AdminServiceImpl(users, mock(ImageRecordMapper.class), mock(PaymentRecordMapper.class),
                mock(RelayUsageLogMapper.class), mock(AdminStatsMapper.class), mock(AdminUserRankingCache.class), encoder);
        String stolen = jwt.generateToken(user);
        AdminUserUpdateDto request = new AdminUserUpdateDto(); request.setPassword("AdminReplacementPassword!");
        admin.updateUser(42L, request);
        assertFalse(authenticated(stolen));
        String beforeBan = jwt.generateToken(user);
        admin.updateBanStatus(42L, true, 1L); admin.updateBanStatus(42L, false, 1L);
        assertFalse(authenticated(beforeBan));
    }

    @Test void userJsonNeverExposesPasswordHashOrSessionVersion() throws Exception {
        var json = new ObjectMapper().valueToTree(user);
        assertFalse(json.has("password")); assertFalse(json.has("authVersion"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "short", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "imageCreater-development-secret-key-change-me-please-32"})
    void weakOrPublicSigningKeysPreventStartup(String value) {
        ReflectionTestUtils.setField(jwt, "secret", value);
        assertThrows(IllegalStateException.class, jwt::checkConfiguration);
    }

    @Test void strongSigningKeyWorksAndForgedSignatureFails() {
        assertDoesNotThrow(jwt::checkConfiguration);
        String token = jwt.generateToken(user);
        ReflectionTestUtils.setField(jwt, "secret", SECRET.substring(1) + "0");
        assertFalse(jwt.validateToken(token));
    }

    private AuthServiceImpl auth() {
        return new AuthServiceImpl(users, encoder, jwt, codes, mock(PaymentConfigService.class),
                mock(ReferralService.class), mock(RegistrationConfigService.class));
    }

    private UserProfileDto profile() {
        UserProfileDto p = new UserProfileDto(); p.setEmail("new@example.invalid");
        p.setOldPassword("OriginalPassword!"); p.setCurrentEmailCode("111111"); p.setNewEmailCode("222222"); return p;
    }

    private boolean authenticated(String token) throws Exception {
        SecurityContextHolder.clearContext();
        try {
            var request = new MockHttpServletRequest("GET", "/api/user/me");
            request.addHeader("Authorization", "Bearer " + token);
            new JwtAuthenticationFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            return SecurityContextHolder.getContext().getAuthentication() != null;
        } finally { SecurityContextHolder.clearContext(); }
    }
}
