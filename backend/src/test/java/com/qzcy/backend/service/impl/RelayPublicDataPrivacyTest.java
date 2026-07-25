package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayChannelModelDto;
import com.qzcy.backend.dto.RelayPublicChannelDto;
import com.qzcy.backend.dto.RelayUsageLogDto;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayUsageLog;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelayPublicDataPrivacyTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RelayChannelModelMapper channelModelMapper;
    private RelayServiceImpl service;

    @BeforeEach
    void setUp() {
        channelModelMapper = mock(RelayChannelModelMapper.class);
        service = new RelayServiceImpl(
                mock(RelayChannelMapper.class),
                channelModelMapper,
                mock(RelayGroupMapper.class),
                mock(RelayGroupModelMapper.class),
                mock(RelayModelMapper.class),
                mock(RelayTokenMapper.class),
                mock(RelayUsageLogMapper.class),
                mock(UserMapper.class),
                objectMapper
        );
    }

    @Test
    void publicChannelOmitsInternalRoutingConfiguration() throws Exception {
        RelayChannel channel = new RelayChannel();
        channel.setId(9L);
        channel.setName("自定义渠道 A");
        channel.setChannelRule("openai");
        channel.setApiBaseUrl("https://secret-upstream.example/v1");
        channel.setApiKey("sk-secret");
        channel.setGroupNames("default");
        channel.setStatus("available");
        channel.setPriority(1);
        channel.setWeight(99);
        channel.setRpmLimit(120);
        channel.setMaxConcurrency(8);
        channel.setPriceMultiplier(new BigDecimal("0.180"));
        channel.setEnabled(true);

        RelayChannelModelDto binding = new RelayChannelModelDto();
        binding.setModelId(7L);
        binding.setModel("provider/private-model-id");
        binding.setDisplayName("public-model");
        binding.setModelType("chat");
        binding.setUpstreamModel("provider/secret-routing-model");
        binding.setEnabled(true);
        when(channelModelMapper.modelsForChannel(9L)).thenReturn(List.of(binding));

        RelayPublicChannelDto result = ReflectionTestUtils.invokeMethod(service, "toPublicChannelDto", channel, 1);
        JsonNode json = objectMapper.valueToTree(result);

        assertEquals("自定义渠道 A", result.getName());
        assertEquals(120, result.getRpmLimit());
        assertEquals(8, result.getMaxConcurrency());
        assertEquals("public-model", result.getModels().get(0).getModel());
        assertFalse(json.has("apiBaseUrl"));
        assertFalse(json.has("apiKeyMasked"));
        assertFalse(json.has("priceMultiplier"));
        assertFalse(json.has("priority"));
        assertFalse(json.has("weight"));
        assertFalse(json.get("models").get(0).has("upstreamModel"));
        assertFalse(json.toString().contains("secret-upstream"));
        assertFalse(json.toString().contains("sk-secret"));
    }

    @Test
    void publicUsageLogOnlyReturnsUserFacingCharge() throws Exception {
        RelayUsageLog usage = new RelayUsageLog();
        usage.setId(11L);
        usage.setTokenName("my-key");
        usage.setChannelName("自定义渠道 A");
        usage.setGroupNames("default");
        usage.setEndpoint("/v1/chat/completions");
        usage.setModel("provider/private-model-id");
        usage.setModelType("chat");
        usage.setPromptTokens(100);
        usage.setCompletionTokens(20);
        usage.setTotalTokens(120);
        usage.setInputCost(new BigDecimal("0.001"));
        usage.setOutputCost(new BigDecimal("0.002"));
        usage.setGroupRatio(new BigDecimal("0.200"));
        usage.setChannelRatio(new BigDecimal("0.180"));
        usage.setCost(new BigDecimal("0.003"));
        usage.setStatusCode(502);
        usage.setStatus("failed");
        usage.setMessage("request to https://secret-upstream.example failed");

        RelayUsageLogDto result = ReflectionTestUtils.invokeMethod(service, "toUsageDto", usage, "public-model");
        JsonNode json = objectMapper.valueToTree(result);

        assertEquals("public-model", result.getModel());
        assertEquals("自定义渠道 A", result.getChannelName());
        assertEquals(new BigDecimal("0.003"), result.getCost());
        assertEquals("服务暂时不可用，请稍后重试", result.getMessage());
        assertTrue(json.has("channelName"));
        assertFalse(json.has("inputCost"));
        assertFalse(json.has("outputCost"));
        assertFalse(json.has("groupRatio"));
        assertFalse(json.has("channelRatio"));
        assertTrue(json.has("cost"));
        assertFalse(json.toString().contains("secret-upstream"));
    }
}
