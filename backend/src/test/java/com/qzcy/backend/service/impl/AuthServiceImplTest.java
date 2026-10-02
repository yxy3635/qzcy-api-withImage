package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.LoginDto;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.*;
import com.qzcy.backend.util.JwtUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceImplTest {
    private UserMapper mapper;
    private JwtUtil jwt;
    private AuthServiceImpl service;
    private User user;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        mapper = mock(UserMapper.class);
        jwt = mock(JwtUtil.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        service = new AuthServiceImpl(mapper, encoder, jwt, mock(EmailCodeService.class),
                mock(PaymentConfigService.class), mock(ReferralService.class), mock(RegistrationConfigService.class));
        user = new User();
        user.setId(1L);
        user.setUsername("Alice");
        user.setEmail("alice@example.com");
        user.setPassword(encoder.encode(" secret123 "));
        user.setBanned(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"account\":\" ALICE@Example.COM \"}",
        "{\"username\":\" ALICE@Example.COM \"}",
        "{\"account\":\"alice@example.com\",\"username\":\"ignored\"}"
    })
    void emailLoginNormalizesAccountAndSupportsLegacyPayload(String payload) throws Exception {
        assertSuccessfulLogin(payload, "email", "alice@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"account\":\" Alice \"}", "{\"username\":\" Alice \"}"})
    void usernameLoginRemainsCompatible(String payload) throws Exception {
        assertSuccessfulLogin(payload, "username", "Alice");
    }

    private void assertSuccessfulLogin(String payload, String column, String value) throws Exception {
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<User> query = invocation.getArgument(0);
            assertEquals("(" + column + " = #{ew.paramNameValuePairs.MPGENVAL1})", query.getSqlSegment());
            assertEquals(value, query.getParamNameValuePairs().get("MPGENVAL1"));
            return user;
        });
        when(jwt.generateToken(user)).thenReturn("test-token");
        LoginDto dto = json.readValue(payload, LoginDto.class);
        dto.setPassword(" secret123 ");
        var result = service.login(dto);
        assertEquals("test-token", result.getToken());
        assertEquals(user.getEmail(), result.getUser().getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"account\":\" \"}",
        "{\"account\":\"alice@example.com\"}",
        "{\"account\":\"alice@example.com\",\"password\":\"\"}",
        "{\"password\":\"secret\"}"
    })
    void missingCredentialsAreRejectedWithoutQuery(String payload) throws Exception {
        LoginDto dto = json.readValue(payload, LoginDto.class);
        assertEquals(401, assertThrows(BusinessException.class, () -> service.login(dto)).getCode());
        verifyNoInteractions(mapper, jwt);
    }

    @Test
    void unknownAccountAndWrongPasswordHaveSameError() {
        LoginDto dto = credentials();
        var unknown = assertThrows(BusinessException.class, () -> service.login(dto));
        when(mapper.selectOne(any())).thenReturn(user);
        dto.setPassword("wrong-password");
        var wrong = assertThrows(BusinessException.class, () -> service.login(dto));
        assertEquals(401, wrong.getCode());
        assertEquals(unknown.getCode(), wrong.getCode());
        assertEquals(unknown.getMessage(), wrong.getMessage());
        verifyNoInteractions(jwt);
    }

    @Test
    void bannedUserCannotObtainToken() {
        user.setBanned(true);
        when(mapper.selectOne(any())).thenReturn(user);
        assertEquals(423, assertThrows(BusinessException.class, () -> service.login(credentials())).getCode());
        verifyNoInteractions(jwt);
    }

    private LoginDto credentials() {
        LoginDto dto = new LoginDto();
        dto.setAccount("alice@example.com");
        dto.setPassword(" secret123 ");
        return dto;
    }
}
