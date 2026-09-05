package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayChannelTestAttemptDto;
import com.qzcy.backend.dto.RelayChannelTestRequestDto;
import com.qzcy.backend.dto.RelayChannelTestResultDto;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.service.RelayProviderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelayChannelTestServiceImplTest {
    private RelayChannelMapper channelMapper;
    private RelayChannelProviderMapper providerMapper;
    private RelayChannelModelMapper channelModelMapper;
    private RelayModelMapper modelMapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        channelMapper = mock(RelayChannelMapper.class);
        providerMapper = mock(RelayChannelProviderMapper.class);
        channelModelMapper = mock(RelayChannelModelMapper.class);
        modelMapper = mock(RelayModelMapper.class);
        objectMapper = new ObjectMapper();
    }

    private RelayChannel channel(long id, String name, String baseUrl, String key) {
        RelayChannel item = new RelayChannel();
        item.setId(id);
        item.setName(name);
        item.setApiBaseUrl(baseUrl);
        item.setApiKey(key);
        item.setChannelRule("openai");
        item.setPriority(10);
        item.setWeight(10);
        item.setScheduleStrategy("priority");
        item.setEnabled(true);
        return item;
    }

    private RelayChannelProvider provider(long id, String name, int priority, int weight, String status) {
        RelayChannelProvider item = new RelayChannelProvider();
        item.setId(id);
        item.setChannelId(1L);
        item.setName(name);
        item.setApiBaseUrl("https://p" + id + ".example.com");
        item.setApiKey("sk-" + id);
        item.setChannelRule("openai");
        item.setPriority(priority);
        item.setWeight(weight);
        item.setStatus(status);
        item.setEnabled(true);
        return item;
    }

    private RelayChannelModel binding(long channelId, long modelId, String upstreamModel) {
        RelayChannelModel item = new RelayChannelModel();
        item.setChannelId(channelId);
        item.setModelId(modelId);
        item.setUpstreamModel(upstreamModel);
        item.setEnabled(true);
        return item;
    }

    private RelayModel model(long id, String name) {
        RelayModel item = new RelayModel();
        item.setId(id);
        item.setModel(name);
        item.setDisplayName(name);
        item.setEnabled(true);
        return item;
    }

    @FunctionalInterface
    private interface UpstreamStub {
        String call(RelayChannelProvider provider, String rule, String upstreamModel, String prompt);
    }

    private RelayChannelTestServiceImpl serviceWithUpstream(UpstreamStub stub) {
        return new RelayChannelTestServiceImpl(
                channelMapper, providerMapper, channelModelMapper, modelMapper,
                new RelayProviderScheduler(), objectMapper) {
            @Override
            String callUpstream(com.qzcy.backend.entity.RelayChannel channel,
                                com.qzcy.backend.entity.RelayChannelProvider provider,
                                String rule, String upstreamModel, String prompt) {
                return stub.call(provider, rule, upstreamModel, prompt);
            }
        };
    }

    private RelayChannelTestRequestDto request(Long modelId, String prompt) {
        RelayChannelTestRequestDto dto = new RelayChannelTestRequestDto();
        dto.setModelId(modelId);
        dto.setPrompt(prompt);
        return dto;
    }

    @Test
    void extractContentSupportsOpenaiTextAndBlocksAndAnthropic() {
        ObjectMapper mapper = new ObjectMapper();
        String openaiText = RelayChannelTestServiceImpl.extractContent(
                mapper.valueToTree(java.util.Map.of("choices", List.of(java.util.Map.of(
                        "message", java.util.Map.of("content", "你好！"))))), false);
        assertEquals("你好！", openaiText);

        String openaiBlocks = RelayChannelTestServiceImpl.extractContent(mapper.valueToTree(java.util.Map.of(
                "choices", List.of(java.util.Map.of("message", java.util.Map.of("content", List.of(
                        java.util.Map.of("type", "text", "text", "第一段"),
                        java.util.Map.of("type", "text", "text", "第二段"))))))), false);
        assertEquals("第一段第二段", openaiBlocks);

        String anthropic = RelayChannelTestServiceImpl.extractContent(mapper.valueToTree(java.util.Map.of(
                "content", List.of(
                        java.util.Map.of("type", "text", "text", "Claude 回复"),
                        java.util.Map.of("type", "tool_use")))), true);
        assertEquals("Claude 回复", anthropic);

        String errorBody = RelayChannelTestServiceImpl.extractContent(
                mapper.valueToTree(java.util.Map.of("error", java.util.Map.of("message", "boom"))), false);
        assertEquals("", errorBody);
    }

    @Test
    void unboundModelIsRejected() {
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "main", "https://x.example.com", "sk-x"));
        when(channelModelMapper.selectByChannelAndModel(1L, 9L)).thenReturn(null);
        RelayChannelTestServiceImpl service = serviceWithUpstream((p, r, u, prompt) -> "ok");

        BusinessException error = assertThrows(BusinessException.class, () -> service.test(1L, request(9L, "hi")));
        assertEquals(400, error.getCode());
    }

    @Test
    void missingModelIdIsRejected() {
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "main", "https://x.example.com", "sk-x"));
        RelayChannelTestServiceImpl service = serviceWithUpstream((p, r, u, prompt) -> "ok");
        BusinessException error = assertThrows(BusinessException.class, () -> service.test(1L, request(null, "hi")));
        assertEquals(400, error.getCode());
    }

    @Test
    void legacyChannelWithoutProvidersFallsBackToChannelCredentials() {
        RelayChannel legacy = channel(1L, "老渠道", "https://legacy.example.com", "sk-legacy");
        legacy.setProvider("旧供应商");
        when(channelMapper.selectById(1L)).thenReturn(legacy);
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of());
        when(channelModelMapper.selectByChannelAndModel(1L, 5L)).thenReturn(binding(1L, 5L, "gpt-upstream"));
        when(modelMapper.selectById(5L)).thenReturn(model(5L, "gpt-public"));
        RelayChannelTestServiceImpl service = serviceWithUpstream(
                (p, r, u, prompt) -> u.equals("gpt-upstream") ? "旧渠道回复" : "");

        RelayChannelTestResultDto result = service.test(1L, request(5L, "hi"));

        assertTrue(result.isSuccess());
        assertEquals("旧供应商", result.getProviderName());
        assertEquals("gpt-upstream", result.getUpstreamModel());
        assertEquals("旧渠道回复", result.getContent());
    }

    @Test
    void channelWithProvidersButNoLegacyCredentialsIsRejected() {
        RelayChannel channel = channel(1L, "main", "", "");
        when(channelMapper.selectById(1L)).thenReturn(channel);
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(provider(11, "a", 0, 10, "failed")));
        when(channelModelMapper.selectByChannelAndModel(1L, 5L)).thenReturn(binding(1L, 5L, ""));
        when(modelMapper.selectById(5L)).thenReturn(model(5L, "gpt"));
        RelayChannelTestServiceImpl service = serviceWithUpstream((p, r, u, prompt) -> "ok");

        BusinessException error = assertThrows(BusinessException.class, () -> service.test(1L, request(5L, "hi")));
        assertEquals(400, error.getCode());
    }

    @Test
    void failedProviderFallsOverToNextAndRecordsAttempt() {
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "main", "https://x.example.com", "sk-x"));
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(
                provider(11, "first", 0, 10, "available"),
                provider(12, "second", 10, 10, "available")));
        when(channelModelMapper.selectByChannelAndModel(1L, 5L)).thenReturn(binding(1L, 5L, ""));
        when(modelMapper.selectById(5L)).thenReturn(model(5L, "gpt"));
        RelayChannelTestServiceImpl service = serviceWithUpstream((p, r, u, prompt) -> {
            if (p.getId() == 11L) throw new IllegalStateException("HTTP 502: bad gateway");
            assertEquals("gpt", u);
            return "第二供应商回复";
        });

        RelayChannelTestResultDto result = service.test(1L, request(5L, "hi"));

        assertTrue(result.isSuccess());
        assertEquals("second", result.getProviderName());
        assertEquals(12L, result.getProviderId());
        assertEquals("第二供应商回复", result.getContent());
        assertEquals(1, result.getAttempts().size());
        RelayChannelTestAttemptDto attempt = result.getAttempts().get(0);
        assertEquals("first", attempt.getProviderName());
        assertTrue(attempt.getError().contains("502"));
        // priority 策略下 first(0) 应排在 second(10) 前
        assertTrue(result.isSuccess() && result.getProviderId() == 12L);
        assertFalse(result.getContent().isBlank());
    }

    @Test
    void allFailedReturnsAttemptsSummary() {
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "main", "https://x.example.com", "sk-x"));
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(provider(11, "only", 0, 10, "available")));
        when(channelModelMapper.selectByChannelAndModel(1L, 5L)).thenReturn(binding(1L, 5L, ""));
        when(modelMapper.selectById(5L)).thenReturn(model(5L, "gpt"));
        RelayChannelTestServiceImpl service = serviceWithUpstream((p, r, u, prompt) -> {
            throw new IllegalStateException("HTTP 429: rate limited");
        });

        RelayChannelTestResultDto result = service.test(1L, request(5L, "hi"));

        assertFalse(result.isSuccess());
        assertEquals("全部供应商调用失败", result.getError());
        assertEquals(1, result.getAttempts().size());
        assertTrue(result.getAttempts().get(0).getError().contains("429"));
    }

    @Test
    void blankPromptFallsBackToDefault() {
        when(channelMapper.selectById(1L)).thenReturn(channel(1L, "main", "https://x.example.com", "sk-x"));
        when(providerMapper.selectByChannelId(1L)).thenReturn(List.of(provider(11, "a", 0, 10, "available")));
        when(channelModelMapper.selectByChannelAndModel(1L, 5L)).thenReturn(binding(1L, 5L, ""));
        when(modelMapper.selectById(5L)).thenReturn(model(5L, "gpt"));
        RelayChannelTestServiceImpl service = serviceWithUpstream((p, r, u, prompt) -> {
            assertEquals(RelayChannelTestServiceImpl.DEFAULT_PROMPT, prompt);
            return "ok";
        });

        assertTrue(service.test(1L, request(5L, "  ")).isSuccess());
    }
}
