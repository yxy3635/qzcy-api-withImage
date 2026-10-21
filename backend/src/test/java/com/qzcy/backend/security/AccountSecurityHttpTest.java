package com.qzcy.backend.security;

import com.qzcy.backend.config.security.*;
import com.qzcy.backend.controller.*;
import com.qzcy.backend.dto.*;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import com.qzcy.backend.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, UserController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AccountSecurityHttpTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean UserService profiles;
    @MockBean EmailCodeService codes;
    @MockBean AccountAttemptLimiter limiter;
    @MockBean UserMapper users;
    @MockBean ReferralService referrals;
    @MockBean ReferralWithdrawQrCodeMapper qrs;
    @MockBean JwtUtil jwt;
    @MockBean RelayCompressionService compression;

    @Test void directApiLoginIsThrottledBeforePasswordVerificationAndIgnoresRawForwardingHeaders() throws Exception {
        doThrow(new BusinessException(429, "limited")).when(limiter).login("victim", "198.51.100.8");
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("198.51.100.8"); return request; })
                .header("X-Forwarded-For", "127.0.0.1").header("X-Real-IP", "127.0.0.1")
                .contentType("application/json").content("{\"account\":\"victim\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429));
        verifyNoInteractions(auth);
    }

    @Test void anonymousApiCannotSendRebindingCodesOrChangeCredentials() throws Exception {
        for (String path : new String[]{"/api/user/email-code", "/api/user/password", "/api/user/logout"}) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/user/profile").contentType("application/json").content("{\"email\":\"attacker@example.invalid\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/email-code").contentType("application/json")
                .content("{\"email\":\"victim@example.invalid\",\"scene\":\"change_email_current\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(codes, profiles);
    }

    @Test void revokedJwtIsRejectedBeforeSensitiveControllerAndLogoutRevokesServerSessions() throws Exception {
        User user = new User(); user.setId(42L); user.setUsername("victim"); user.setRole("USER");
        when(jwt.validateToken("session")).thenReturn(true); when(jwt.getUserId("session")).thenReturn(42L);
        when(users.selectById(42L)).thenReturn(user);
        when(jwt.matchesUser("session", user)).thenReturn(false);
        mvc.perform(post("/api/user/password").header("Authorization", "Bearer session")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
        when(jwt.matchesUser("session", user)).thenReturn(true);
        mvc.perform(post("/api/user/logout").header("Authorization", "Bearer session"))
                .andExpect(status().isOk());
        verify(users).revokeSessions(42L);
    }

    @Test void currentMailboxComesFromAccountInsteadOfClientInput() throws Exception {
        User user = new User(); user.setId(42L); user.setUsername("victim"); user.setRole("USER");
        when(jwt.validateToken("session")).thenReturn(true); when(jwt.getUserId("session")).thenReturn(42L);
        when(users.selectById(42L)).thenReturn(user); when(jwt.matchesUser("session", user)).thenReturn(true);
        when(profiles.currentUser(42L)).thenReturn(new AuthUser(42L, "victim", "victim@example.invalid", "USER", false, null));
        mvc.perform(post("/api/user/email-code").header("Authorization", "Bearer session").contentType("application/json")
                .content("{\"email\":\"attacker@example.invalid\",\"scene\":\"change_email_current\"}"))
                .andExpect(status().isOk());
        verify(codes).sendCode(eq("victim@example.invalid"), eq("change_email_current"), anyString());
    }
}
