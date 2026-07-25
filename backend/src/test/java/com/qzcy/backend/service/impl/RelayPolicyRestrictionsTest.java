package com.qzcy.backend.service.impl;

import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelayPolicyRestrictionsTest {
    private RelayTokenMapper tokenMapper;
    private RelayUsageLogMapper usageLogMapper;
    private RelayPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenMapper = mock(RelayTokenMapper.class);
        usageLogMapper = mock(RelayUsageLogMapper.class);
        service = new RelayPolicyServiceImpl(
                tokenMapper,
                mock(RelayModelMapper.class),
                mock(RelayGroupMapper.class),
                mock(RelayGroupModelMapper.class),
                mock(RelayChannelMapper.class),
                mock(RelayChannelModelMapper.class),
                usageLogMapper,
                mock(UserMapper.class)
        );
    }

    @Test
    void ipWhitelistUsesExactAddressMatching() {
        RelayToken token = new RelayToken();
        token.setIpWhitelist("203.0.113.10,198.51.100.8");

        assertDoesNotThrow(() -> service.enforceIpAccess(token, "198.51.100.8"));
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.enforceIpAccess(token, "198.51.100.80")
        );

        assertEquals(403, error.getCode());
    }

    @Test
    void expiredTokenIsRejected() {
        RelayToken token = new RelayToken();
        token.setEnabled(true);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenMapper.selectOne(any())).thenReturn(token);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.requireRelayToken("Bearer expired-key")
        );

        assertEquals(401, error.getCode());
    }

    @Test
    void tokenRpmAndTpmLimitsAreEnforced() {
        RelayToken token = new RelayToken();
        token.setId(7L);
        token.setRpmLimit(60);
        token.setTpmLimit(100_000);
        RelayChannel channel = new RelayChannel();
        channel.setId(9L);
        channel.setRpmLimit(0);
        channel.setTpmLimit(0);

        when(usageLogMapper.tokenRequestsSince(eq(7L), any())).thenReturn(60L);
        BusinessException rpmError = assertThrows(
                BusinessException.class,
                () -> service.enforceRateLimits(token, channel)
        );
        assertEquals(429, rpmError.getCode());

        when(usageLogMapper.tokenRequestsSince(eq(7L), any())).thenReturn(59L);
        when(usageLogMapper.tokenTokensSince(eq(7L), any())).thenReturn(100_000L);
        BusinessException tpmError = assertThrows(
                BusinessException.class,
                () -> service.enforceRateLimits(token, channel)
        );
        assertEquals(429, tpmError.getCode());
    }

    @Test
    void quotaChecksCurrentUsagePlusNextRequestCost() {
        RelayToken token = new RelayToken();
        token.setQuota(new BigDecimal("10.000000"));
        token.setUsedQuota(new BigDecimal("9.900000"));

        assertDoesNotThrow(() -> service.enforceQuota(token, new BigDecimal("0.100000")));
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.enforceQuota(token, new BigDecimal("0.100001"))
        );

        assertEquals(402, error.getCode());
    }
}
