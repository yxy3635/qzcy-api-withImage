package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayModelStatusCache;
import com.qzcy.backend.service.RelayProviderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelayServiceTokenRevealTest {
    private RelayTokenMapper tokenMapper;
    private RelayServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenMapper = mock(RelayTokenMapper.class);
        service = new RelayServiceImpl(
                mock(RelayChannelMapper.class),
                mock(RelayChannelModelMapper.class),
                mock(RelayChannelProviderMapper.class),
                new RelayProviderScheduler(),
                mock(RelayGroupMapper.class),
                mock(RelayGroupModelMapper.class),
                mock(RelayModelMapper.class),
                tokenMapper,
                mock(RelayUsageLogMapper.class),
                mock(UserMapper.class),
                new ObjectMapper(),
                mock(RelayModelStatusCache.class)
        );
    }

    @Test
    void revealsOnlyTheCurrentUsersToken() {
        RelayToken token = new RelayToken();
        token.setId(8L);
        token.setUserId(15L);
        token.setToken("sk-ic-secret");
        when(tokenMapper.selectById(8L)).thenReturn(token);

        assertEquals("sk-ic-secret", service.revealToken(15L, 8L));
    }

    @Test
    void refusesToRevealAnotherUsersToken() {
        RelayToken token = new RelayToken();
        token.setId(8L);
        token.setUserId(15L);
        when(tokenMapper.selectById(8L)).thenReturn(token);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.revealToken(16L, 8L));
        assertEquals(404, exception.getCode());
    }
}
