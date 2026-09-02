package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayTokenCreateDto;
import com.qzcy.backend.dto.RelayTokenDto;
import com.qzcy.backend.dto.RelayUserOverviewDto;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayModelStatusCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayServiceUsageTotalsTest {
    private RelayTokenMapper tokenMapper;
    private RelayUsageLogMapper usageLogMapper;
    private RelayServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenMapper = mock(RelayTokenMapper.class);
        usageLogMapper = mock(RelayUsageLogMapper.class);
        service = new RelayServiceImpl(
                mock(RelayChannelMapper.class),
                mock(RelayChannelModelMapper.class),
                mock(RelayGroupMapper.class),
                mock(RelayGroupModelMapper.class),
                mock(RelayModelMapper.class),
                tokenMapper,
                usageLogMapper,
                mock(UserMapper.class),
                new ObjectMapper(),
                mock(RelayModelStatusCache.class)
        );
    }

    @Test
    void tokenSettingsUpdateDoesNotWriteUsageSnapshot() {
        RelayToken stored = new RelayToken();
        stored.setId(7L);
        stored.setUserId(3L);
        stored.setUsedQuota(new BigDecimal("12.500000"));
        stored.setRequestCount(80L);
        stored.setTokenCount(9000L);
        when(tokenMapper.selectById(7L)).thenReturn(stored);

        RelayTokenCreateDto request = new RelayTokenCreateDto();
        request.setEnabled(false);
        service.updateToken(3L, 7L, request);

        ArgumentCaptor<RelayToken> update = ArgumentCaptor.forClass(RelayToken.class);
        verify(tokenMapper).updateById(update.capture());
        assertEquals(7L, update.getValue().getId());
        assertEquals(false, update.getValue().getEnabled());
        assertNull(update.getValue().getUsedQuota());
        assertNull(update.getValue().getRequestCount());
        assertNull(update.getValue().getTokenCount());
    }

    @Test
    void accountTotalsNeverDropBelowTokenOrLogHistory() {
        RelayTokenDto token = new RelayTokenDto();
        token.setRequestCount(120L);
        token.setTokenCount(3000L);
        token.setUsedQuota(new BigDecimal("9.250000"));
        when(usageLogMapper.userTotalRequests(3L)).thenReturn(150L);
        when(usageLogMapper.userTotalTokens(3L)).thenReturn(2500L);
        when(usageLogMapper.userTotalCost(3L)).thenReturn(new BigDecimal("8.750000"));

        RelayUserOverviewDto overview = new RelayUserOverviewDto();
        ReflectionTestUtils.invokeMethod(service, "fillUsageStats", overview, 3L, List.of(token));

        assertEquals(150L, overview.getTotalRequests());
        assertEquals(3000L, overview.getTotalTokens());
        assertEquals(new BigDecimal("9.250000"), overview.getTotalCost());
    }
}
