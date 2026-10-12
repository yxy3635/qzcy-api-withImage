package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayModelUpdateDto;
import com.qzcy.backend.dto.relay.RelayUsage;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.service.RelayProviderScheduler;
import com.qzcy.backend.mapper.RelayModelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Examples follow https://platform.claude.com/docs/en/build-with-claude/prompt-caching. */
class RelayClaudeCacheBillingTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MESSAGES = "/v1/messages";
    private final RelayPolicyServiceImpl policy = new RelayPolicyServiceImpl(null, null, null, null,
            null, null, null, new RelayProviderScheduler(), null, null);

    static RelayModel model() {
        var model = new RelayModel();
        model.setInputPrice(new BigDecimal("3"));
        model.setOutputPrice(new BigDecimal("15"));
        model.setCachedInputPrice(new BigDecimal("0.3"));
        model.setCacheCreationPrice(new BigDecimal("3.75"));
        return model;
    }

    static JsonNode response(long fiveMinute, long oneHour, boolean aggregate) {
        var body = MAPPER.createObjectNode();
        var usage = body.putObject("usage");
        usage.put("input_tokens", 2048).put("output_tokens", 503).put("cache_read_input_tokens", 1800);
        if (aggregate) usage.put("cache_creation_input_tokens", fiveMinute + oneHour);
        usage.putObject("cache_creation").put("ephemeral_5m_input_tokens", fiveMinute)
                .put("ephemeral_1h_input_tokens", oneHour);
        return body;
    }

    @ParameterizedTest
    @CsvSource({"1000,0,0.003750", "0,1000,0.006000", "148,100,0.001155"})
    void standardTtlRatesAndTotalInput(long fiveMinute, long oneHour, String expected) {
        for (boolean aggregate : new boolean[]{true, false}) {
            var body = response(fiveMinute, oneHour, aggregate);
            var usage = RelayUsage.fromResponse(body, MESSAGES);
            assertEquals(fiveMinute, usage.cacheCreation5mTokens());
            assertEquals(oneHour, usage.cacheCreation1hTokens());
            assertEquals(3848 + fiveMinute + oneHour, usage.inputTokens());
            assertEquals(2048, usage.billableInputTokens());
            assertEquals(4351 + fiveMinute + oneHour, usage.totalTokens());
            var cost = policy.estimateCost(model(), new RelayChannel(), new RelayGroup(), body, MESSAGES);
            money(expected, cost.cacheCreation());
            money("0.006144", cost.input());
            money("0.007545", cost.output());
            money("0.000540", cost.cacheRead());
            assertEquals(0, cost.cacheCreation5m().add(cost.cacheCreation1h()).compareTo(cost.cacheCreation()));
            assertEquals(0, new BigDecimal("0.014229").add(new BigDecimal(expected)).compareTo(cost.total()));
        }
    }

    @Test
    void explicitHourlyPriceAndZeroOverrideAutomaticPrice() {
        var model = model();
        model.setCacheCreation1hPrice(new BigDecimal("8"));
        var body = response(600, 400, true);
        money("0.005450", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).cacheCreation());
        model.setCacheCreation1hPrice(BigDecimal.ZERO);
        money("0.002250", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).cacheCreation());
    }

    @Test
    void longContextIncludesCacheAndAppliesEachTtlTier() {
        var model = model();
        model.setLongContextThreshold(4096L);
        model.setLongContextBillingMode("multiplier");
        model.setLongContextMultiplier(new BigDecimal("2"));
        var body = response(148, 100, true); // 2048 + 1800 + 248 = 4096.
        money("0.030768", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).total());
        model.setCacheCreation1hPrice(new BigDecimal("8"));
        money("0.001600", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).cacheCreation1h());
        model.setLongContextBillingMode("price");
        model.setCacheCreation1hPrice(null);
        model.setLongContextInputPrice(new BigDecimal("6"));
        model.setLongContextCacheCreationPrice(new BigDecimal("7.5"));
        money("0.002310", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).cacheCreation());
        model.setLongContextCacheCreation1hPrice(new BigDecimal("20"));
        money("0.003110", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).cacheCreation());
        model.setLongContextCacheCreation1hPrice(BigDecimal.ZERO);
        money("0.001110", policy.estimateCost(model, new RelayChannel(), null, body, MESSAGES).cacheCreation());
    }

    @Test
    void legacyTotalKeepsExistingRateAndExplicitZeroIsAuthoritative() throws Exception {
        var legacy = MAPPER.readTree("{\"usage\":{\"input_tokens\":10,\"cache_creation_input_tokens\":1000}}");
        money("0.003750", policy.estimateCost(model(), new RelayChannel(), null, legacy, MESSAGES).cacheCreation());
        var zero = response(600, 400, false);
        ((com.fasterxml.jackson.databind.node.ObjectNode) zero.path("usage")).put("cache_creation_input_tokens", 0);
        assertEquals(0, RelayUsage.fromResponse(zero, MESSAGES).cacheCreationTokens());
        money("0", policy.estimateCost(model(), new RelayChannel(), null, zero, MESSAGES).cacheCreation());
    }

    @Test
    void partialAndInconsistentDetailsNeverChargeMoreThanAggregate() throws Exception {
        var body = MAPPER.readTree("""
                {"usage":{"input_tokens":10,"cache_creation_input_tokens":1000,
                  "cache_creation":{"ephemeral_1h_input_tokens":400}}}
                """);
        var usage = RelayUsage.fromResponse(body, MESSAGES);
        assertEquals(600, usage.cacheCreation5mTokens());
        assertEquals(400, usage.cacheCreation1hTokens());
        var inconsistent = response(1000, 1000, false);
        ((com.fasterxml.jackson.databind.node.ObjectNode) inconsistent.path("usage")).put("cache_creation_input_tokens", 100);
        var clamped = RelayUsage.fromResponse(inconsistent, MESSAGES);
        assertEquals(100, clamped.cacheCreation5mTokens() + clamped.cacheCreation1hTokens());
    }

    @ParameterizedTest
    @CsvSource({"/v1/chat/completions,prompt_tokens,prompt_tokens_details", "/v1/responses,input_tokens,input_tokens_details"})
    void openaiCompatibleDetailsDoNotDoubleCountInput(String path, String inputName, String detailsName) {
        var body = MAPPER.createObjectNode();
        var usage = body.putObject("usage");
        usage.put(inputName, 4096).put("output_tokens", 503);
        var details = usage.putObject(detailsName);
        details.put("cached_tokens", 1800);
        details.putObject("cache_creation").put("ephemeral_5m_input_tokens", 148)
                .put("ephemeral_1h_input_tokens", 100);
        assertEquals(2048, RelayUsage.fromResponse(body, path).billableInputTokens());
        money("0.015384", policy.estimateCost(model(), new RelayChannel(), null, body, path).total());
    }

    @Test
    void groupChannelRatiosAndFixedBillingStillApply() {
        var channel = new RelayChannel();
        channel.setPriceMultiplier(new BigDecimal("0.5"));
        var group = new RelayGroup();
        group.setRatio(new BigDecimal("0.25"));
        var body = response(148, 100, true);
        money("0.007692", policy.estimateCost(model(), channel, group, body, MESSAGES).total());
        var model = model();
        model.setFixedRequestBilling(true);
        model.setRequestPrice(new BigDecimal("0.1"));
        money("0.1", policy.estimateCost(model, channel, group, body, MESSAGES).total());
    }

    @Test
    void omittedNullablePricesAndExplicitNullAreDistinguishable() throws Exception {
        var omitted = MAPPER.readValue("{}", RelayModelUpdateDto.class);
        assertFalse(omitted.isCacheCreation1hPriceSet());
        assertFalse(omitted.isLongContextCacheCreation1hPriceSet());
        var cleared = MAPPER.readValue("{\"cacheCreation1hPrice\":null,\"longContextCacheCreation1hPrice\":null}", RelayModelUpdateDto.class);
        assertTrue(cleared.isCacheCreation1hPriceSet());
        assertTrue(cleared.isLongContextCacheCreation1hPriceSet());
        assertNull(cleared.getCacheCreation1hPrice());
        var zero = MAPPER.readValue("{\"cacheCreation1hPrice\":0}", RelayModelUpdateDto.class);
        assertTrue(zero.isCacheCreation1hPriceSet());
        money("0", zero.getCacheCreation1hPrice());
    }

    @Test
    void modelUpdateKeepsOmittedPricesAndClearsExplicitNullInPersistenceAndPublicDtos() throws Exception {
        var mapper = mock(RelayModelMapper.class);
        var model = model();
        model.setId(7L);
        model.setModel("claude-test");
        model.setCacheCreation1hPrice(new BigDecimal("8"));
        model.setLongContextCacheCreation1hPrice(new BigDecimal("16"));
        when(mapper.selectById(7L)).thenReturn(model);
        var service = new RelayServiceImpl(null, null, null, new RelayProviderScheduler(), null,
                null, mapper, null, null, null, MAPPER, null);
        var omitted = service.updateModel(7L, MAPPER.readValue("{\"enabled\":false}", RelayModelUpdateDto.class));
        money("8", omitted.getCacheCreation1hPrice());
        money("16", omitted.getLongContextCacheCreation1hPrice());
        var cleared = service.updateModel(7L, MAPPER.readValue(
                "{\"cacheCreation1hPrice\":null,\"longContextCacheCreation1hPrice\":null}", RelayModelUpdateDto.class));
        assertNull(cleared.getCacheCreation1hPrice());
        assertNull(cleared.getLongContextCacheCreation1hPrice());
        verify(mapper, times(2)).updateById(model);
        // MyBatis must write explicit NULL rather than skipping nullable prices.
        var annotation = RelayModel.class.getDeclaredField("cacheCreation1hPrice")
                .getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        assertEquals(com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS, annotation.updateStrategy());
        var publicDto = (com.qzcy.backend.dto.RelayModelDto) org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(service, "toPublicModelDto", model);
        assertNotNull(publicDto);
        assertNull(publicDto.getCacheCreation1hPrice());
    }

    static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "Expected " + expected + ", got " + actual);
    }
}
