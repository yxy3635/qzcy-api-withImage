package com.qzcy.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayTokenCreateDto;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import com.qzcy.backend.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelayTokenGroupSecurityTest {
    private final RelayTokenMapper tokens = mock(RelayTokenMapper.class);
    private final RelayGroupMapper groups = mock(RelayGroupMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final RelayServiceImpl service = new RelayServiceImpl(mock(RelayChannelMapper.class), mock(RelayChannelModelMapper.class),
            mock(RelayChannelProviderMapper.class), new RelayProviderScheduler(), groups, mock(RelayGroupModelMapper.class),
            mock(RelayModelMapper.class), tokens, mock(RelayUsageLogMapper.class), users, new ObjectMapper(), mock(RelayModelStatusCache.class));
    private final RelayPolicyServiceImpl policy = new RelayPolicyServiceImpl(tokens, mock(RelayModelMapper.class), groups,
            mock(RelayGroupModelMapper.class), mock(RelayChannelMapper.class), mock(RelayChannelModelMapper.class),
            mock(RelayChannelProviderMapper.class), new RelayProviderScheduler(), mock(RelayUsageLogMapper.class), users);
    private RelayToken token;

    @BeforeEach void setup() {
        User owner = new User(); owner.setId(42L); owner.setBanned(false);
        when(users.selectById(42L)).thenReturn(owner);
        token = new RelayToken(); token.setId(7L); token.setUserId(42L); token.setEnabled(true); token.setGroupNames("pro");
        when(tokens.selectById(7L)).thenReturn(token);
        when(tokens.selectOne(any())).thenReturn(token);
    }

    @ParameterizedTest @ValueSource(strings = {"*", "default,pro", "pro,image", "", "unknown", "disabled"})
    void apiCannotCreateOrChangeToUnselectableGroups(String value) {
        RelayTokenCreateDto dto = new RelayTokenCreateDto(); dto.setGroups(value);
        assertThrows(BusinessException.class, () -> service.createToken(42L, dto));
        assertThrows(BusinessException.class, () -> service.updateToken(42L, 7L, dto));
        verify(tokens, never()).insert(any(RelayToken.class));
        verify(tokens, never()).updateById(any(RelayToken.class));
    }

    @ParameterizedTest @ValueSource(strings = {"*", "default,pro", "pro,image", "unknown", "disabled"})
    void existingMalformedKeysCannotCallRelayEvenBeforeTheyAreDeleted(String value) {
        token.setGroupNames(value);
        assertEquals(403, assertThrows(BusinessException.class, () -> policy.requireRelayToken("Bearer existing-test-key")).getCode());
    }

    @Test void enabledSingleGroupRemainsUsableAndCannotAccessAnotherUsersKey() {
        RelayGroup group = new RelayGroup(); group.setCode("pro"); group.setEnabled(true);
        when(groups.selectOne(any())).thenReturn(group);
        assertSame(token, policy.requireRelayToken("Bearer existing-test-key"));
        RelayTokenCreateDto dto = new RelayTokenCreateDto(); dto.setGroups(" pro ");
        assertDoesNotThrow(() -> service.updateToken(42L, 7L, dto));
        assertEquals(404, assertThrows(BusinessException.class, () -> service.updateToken(43L, 7L, dto)).getCode());
    }
}
