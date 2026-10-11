package com.qzcy.backend.config;

import com.qzcy.backend.config.security.*;
import com.qzcy.backend.controller.*;
import com.qzcy.backend.dto.relay.RelayDispatchResult;
import com.qzcy.backend.entity.User;
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

@WebMvcTest({RelayController.class, RelayCompressionConfigController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RelayCompressionFilter.class, RelayCompressionBodyAdvice.class})
class RelayCompressionSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean RelayService relayService;
    @MockBean RelayDispatchService dispatch;
    @MockBean RelayPolicyService policy;
    @MockBean RelayChannelStatusService channelStatus;
    @MockBean RelayModelMapper models;
    @MockBean RelayGroupMapper groups;
    @MockBean RelayGroupModelMapper groupModels;
    @MockBean RelayChannelModelMapper channelModels;
    @MockBean RelayUsageLogMapper usage;
    @MockBean UserMapper users;
    @MockBean RelayCompressionService compression;
    @MockBean JwtUtil jwt;

    @Test
    void aliasesReachRelayAuthenticationWithoutWebsiteJwt() throws Exception {
        when(dispatch.dispatch(any())).thenReturn(new RelayDispatchResult(200, "application/json", "{}"));
        for (String path : new String[]{"/api/messages", "/api/messages/count_tokens", "/api/responses", "/api/responses/compact"}) {
            mvc.perform(post(path).header("Authorization", "Bearer relay-key").contentType("application/json").content("{}"))
                    .andExpect(status().isOk());
        }
        verify(jwt, never()).validateToken("relay-key");
        verify(dispatch, times(4)).dispatch(any());
    }

    @Test
    void compressionSettingsRequireAdministrator() throws Exception {
        String path = "/api/admin/relay/compression-config";
        mvc.perform(get(path)).andExpect(status().isForbidden());
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setRole("USER");
        when(jwt.validateToken("website-jwt")).thenReturn(true);
        when(jwt.getUserId("website-jwt")).thenReturn(1L);
        when(users.selectById(1L)).thenReturn(user);
        mvc.perform(get(path).header("Authorization", "Bearer website-jwt")).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", "Bearer website-jwt").contentType("application/json")
                .content("{\"gzipEnabled\":true,\"zstdEnabled\":true,\"memoryBudgetMb\":128}"))
                .andExpect(status().isForbidden());
        user.setRole("ADMIN");
        mvc.perform(get(path).header("Authorization", "Bearer website-jwt")).andExpect(status().isOk());
        mvc.perform(put(path).header("Authorization", "Bearer website-jwt").contentType("application/json")
                .content("{\"gzipEnabled\":true,\"zstdEnabled\":true,\"memoryBudgetMb\":128}"))
                .andExpect(status().isOk());
    }
}
