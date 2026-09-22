package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayTokenCreateDto;
import com.qzcy.backend.dto.RelayUserOverviewDto;
import com.qzcy.backend.entity.RelayToken;
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
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

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
                mock(RelayChannelProviderMapper.class),
                new RelayProviderScheduler(),
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
    void deletingKeyRevokesItWithoutDeletingHistory() {
        RelayToken stored = new RelayToken();
        stored.setId(7L);
        stored.setUserId(3L);
        when(tokenMapper.selectById(7L)).thenReturn(stored);
        when(tokenMapper.revokeAndDelete(3L, 7L)).thenReturn(1);
        service.deleteToken(3L, 7L);
        verify(tokenMapper).revokeAndDelete(3L, 7L);
        org.mockito.Mockito.verify(tokenMapper, org.mockito.Mockito.never()).deleteById(7L);
        org.mockito.Mockito.verifyNoInteractions(usageLogMapper);
    }

    @Test
    void deletingAnotherUsersKeyIsRejected() {
        RelayToken stored = new RelayToken();
        stored.setUserId(4L);
        when(tokenMapper.selectById(7L)).thenReturn(stored);
        org.junit.jupiter.api.Assertions.assertThrows(com.qzcy.backend.exception.BusinessException.class,
                () -> service.deleteToken(3L, 7L));
        org.mockito.Mockito.verify(tokenMapper, org.mockito.Mockito.never()).revokeAndDelete(3L, 7L);
    }

    @Test
    void accountTotalsNeverDropBelowTokenOrLogHistory() {
        RelayToken lifetime = new RelayToken();
        lifetime.setRequestCount(120L);
        lifetime.setTokenCount(3000L);
        lifetime.setUsedQuota(new BigDecimal("9.250000"));
        when(tokenMapper.lifetimeUsage(3L)).thenReturn(lifetime);
        when(usageLogMapper.userTotalRequests(3L)).thenReturn(150L);
        when(usageLogMapper.userTotalTokens(3L)).thenReturn(2500L);
        when(usageLogMapper.userTotalCost(3L)).thenReturn(new BigDecimal("8.750000"));

        RelayUserOverviewDto overview = new RelayUserOverviewDto();
        ReflectionTestUtils.invokeMethod(service, "fillUsageStats", overview, 3L);

        assertEquals(150L, overview.getTotalRequests());
        assertEquals(3000L, overview.getTotalTokens());
        assertEquals(new BigDecimal("9.250000"), overview.getTotalCost());
        RelayUserOverviewDto afterDelete = new RelayUserOverviewDto();
        ReflectionTestUtils.invokeMethod(service, "fillUsageStats", afterDelete, 3L);
        assertEquals(overview.getTotalRequests(), afterDelete.getTotalRequests());
        assertEquals(overview.getTotalTokens(), afterDelete.getTotalTokens());
        assertEquals(overview.getTotalCost(), afterDelete.getTotalCost());
    }
    @Test
    void blacklistUpdatesNormalizeAndCanBeClearedWithoutChangingUsage() {
        RelayToken stored = new RelayToken();
        stored.setId(7L);
        stored.setUserId(3L);
        when(tokenMapper.selectById(7L)).thenReturn(stored);
        RelayTokenCreateDto request = new RelayTokenCreateDto();
        request.setUserAgentBlacklist(" CoDeX_CLI_RS \r\n\nClaude-CLI\ncodex_cli_rs");
        service.updateToken(3L, 7L, request);
        request.setUserAgentBlacklist("");
        service.updateToken(3L, 7L, request);
        ArgumentCaptor<RelayToken> update = ArgumentCaptor.forClass(RelayToken.class);
        org.mockito.Mockito.verify(tokenMapper, org.mockito.Mockito.times(2)).updateById(update.capture());
        assertEquals("codex_cli_rs\nclaude-cli", update.getAllValues().get(0).getUserAgentBlacklist());
        assertEquals("", update.getAllValues().get(1).getUserAgentBlacklist());
        assertNull(update.getAllValues().get(0).getUsedQuota());
        assertNull(update.getAllValues().get(0).getExpiresAt());
    }

}
