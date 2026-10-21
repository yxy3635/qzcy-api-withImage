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

@WebMvcTest({RelayController.class, RelayCompressionConfigController.class, RelayPromptRuleConfigController.class})
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
    @MockBean RelayPromptRuleService promptRules;
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
        when(jwt.matchesUser("website-jwt", user)).thenReturn(true);
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

    @Test
    void promptRuleSettingsRequireAdministratorAndDisableBrowserCaching() throws Exception {
        String path = "/api/admin/relay/prompt-rule-config";
        mvc.perform(get(path)).andExpect(status().isForbidden());
        mvc.perform(get(path + "/hits")).andExpect(status().isForbidden());
        mvc.perform(put(path).contentType("application/json").content("{\"enabled\":false,\"content\":\"rule\"}"))
                .andExpect(status().isForbidden());
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setRole("USER");
        when(jwt.validateToken("website-jwt")).thenReturn(true);
        when(jwt.getUserId("website-jwt")).thenReturn(1L);
        when(users.selectById(1L)).thenReturn(user);
        when(jwt.matchesUser("website-jwt", user)).thenReturn(true);
        mvc.perform(get(path).header("Authorization", "Bearer website-jwt")).andExpect(status().isForbidden());
        mvc.perform(get(path + "/hits").header("Authorization", "Bearer website-jwt")).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization", "Bearer website-jwt").contentType("application/json")
                .content("{\"enabled\":false,\"content\":\"rule\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(promptRules);
        user.setRole("ADMIN");
        var saved = new RelayPromptRuleService.Status(false, "rule", "default", 16000);
        when(promptRules.status()).thenReturn(saved);
        when(promptRules.update(any())).thenReturn(saved);
        when(promptRules.hits(1, 10, "")).thenReturn(new com.qzcy.backend.dto.RelayPromptRuleHitsDto(0, 1, 0, 0, 0, java.util.List.of()));
        mvc.perform(get(path + "/hits").header("Authorization", "Bearer website-jwt"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.userCount").value(0));
        mvc.perform(get(path).header("Authorization", "Bearer website-jwt"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.autoBanThreshold").value(3))
                .andExpect(jsonPath("$.data.content").value("rule"));
        mvc.perform(put(path).header("Authorization", "Bearer website-jwt").contentType("application/json")
                .content("{\"enabled\":false,\"content\":\"rule\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        verify(promptRules).update(new com.qzcy.backend.dto.RelayPromptRuleConfigDto(false, "rule"));
        mvc.perform(put(path).header("Authorization", "Bearer website-jwt").contentType("application/json")
                .content("{\"enabled\":false,\"content\":\"rule\",\"autoBanThreshold\":5}"))
                .andExpect(status().isOk());
        verify(promptRules).update(new com.qzcy.backend.dto.RelayPromptRuleConfigDto(false, "rule", 5));
        mvc.perform(put(path).header("Authorization", "Bearer website-jwt").contentType("application/json")
                .content("{\"enabled\":true,\"content\":\"rule\",\"autoBanThreshold\":2.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void relayControllerPreservesPolicy400ForJsonAndStreamingRequests() throws Exception {
        String body = com.qzcy.backend.util.RelayPromptPolicyError.body(new com.fasterxml.jackson.databind.ObjectMapper(), "/v1/chat/completions").toString();
        when(dispatch.dispatch(any())).thenReturn(new RelayDispatchResult(400, "application/json", body));
        when(dispatch.dispatchStream(any())).thenReturn(new com.qzcy.backend.dto.relay.RelayStreamDispatchResult(400,
                "application/json", output -> output.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        for (boolean stream : new boolean[]{false, true}) {
            mvc.perform(post("/api/v1/chat/completions").header("Authorization", "Bearer relay-key").contentType("application/json")
                    .content("{\"model\":\"test\",\"messages\":[],\"stream\":" + stream + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("security_policy_violation"))
                    .andExpect(jsonPath("$.error.message").value(com.qzcy.backend.util.RelayPromptPolicyError.MESSAGE));
        }
    }
}
