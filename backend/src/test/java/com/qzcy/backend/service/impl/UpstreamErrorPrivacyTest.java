package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.relay.*;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpstreamErrorPrivacyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String SECRET = "上游余额不足，超出限制 https://private-provider.example sk-private-key";
    private static final RelayCostBreakdown ZERO = new RelayCostBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    @ParameterizedTest
    @CsvSource({"400,false", "401,false", "402,false", "429,false", "524,false", "599,false",
            "400,true", "401,true", "402,true", "429,true", "524,true", "599,true", "200,false", "200,true"})
    void relayResponsesMaskErrorsButAdminLogKeepsOriginal(int httpStatus, boolean stream) throws Exception {
        String raw = "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"" + SECRET + "\"},\"extra\":\"private metadata\"}";
        var upstream = upstream(httpStatus, "application/json", raw);
        try {
            var logs = mock(RelayUsageLogMapper.class);
            var service = relay(upstream, logs);
            var request = request(stream ? "/v1/messages" : "/v1/images/edits", stream);
            String result;
            if (stream) {
                var response = service.dispatchStream(request);
                assertEquals(httpStatus == 200 ? 503 : httpStatus, response.statusCode());
                assertEquals("application/json", response.contentType());
                var bytes = new ByteArrayOutputStream();
                response.body().writeTo(bytes);
                result = bytes.toString(StandardCharsets.UTF_8);
            } else {
                var response = service.dispatch(request);
                assertEquals(httpStatus == 200 ? 503 : httpStatus, response.statusCode());
                result = response.body();
            }
            assertEquals("服务暂时不可用，请稍后重试", MAPPER.readTree(result).path("error").path("message").asText());
            assertFalse(result.contains("private"));
            assertFalse(result.contains("余额不足"));
            var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(logs).insert(saved.capture());
            assertTrue(saved.getValue().getMessage().contains(SECRET));
            assertEquals("failed", saved.getValue().getStatus());
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void htmlUpstreamErrorIsLoggedAndReturnedAsFixedJson() throws Exception {
        var upstream = upstream(524, "text/html", "<html>" + SECRET + "</html>");
        try {
            var logs = mock(RelayUsageLogMapper.class);
            var result = relay(upstream, logs).dispatch(request("/v1/chat/completions", false));
            assertEquals("application/json", result.contentType());
            assertEquals("请求处理超时，请稍后重试", MAPPER.readTree(result.body()).path("error").path("message").asText());
            var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(logs).insert(saved.capture());
            assertTrue(saved.getValue().getMessage().contains(SECRET));
        } finally { upstream.stop(0); }
    }

    @ParameterizedTest
    @CsvSource({"/v1/chat/completions,false", "/v1/messages,false", "/v1/responses,true", "/v1/responses,false"})
    void errorsInsideSuccessfulSseAreMaskedAndLogged(String path, boolean responses) throws Exception {
        String event = responses
                ? "{\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"" + SECRET + "\"}}}"
                : "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"" + SECRET + "\"}}";
        String good = "data: {\"choices\":[{\"delta\":{\"content\":\"正常内容\"}}]}\r\n\r\n";
        var upstream = upstream(200, "text/event-stream", good + "event: " + (responses ? "response.failed" : "error")
                + "\r\ndata: " + event + "\r\n\r\n");
        try {
            var logs = mock(RelayUsageLogMapper.class);
            var response = relay(upstream, logs).dispatchStream(request(path, true));
            var bytes = new ByteArrayOutputStream();
            response.body().writeTo(bytes);
            String result = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(result.startsWith(good));
            assertTrue(result.contains("请求过于频繁，请稍后重试"));
            assertFalse(result.contains("private"));
            assertFalse(result.contains("余额不足"));
            if (responses) assertTrue(result.contains("response.failed"));
            if (path.equals("/v1/responses") && !responses) {
                String payload = result.substring(good.length()).split("data: ")[1].trim();
                assertEquals("请求过于频繁，请稍后重试", MAPPER.readTree(payload).path("message").asText());
            }
            var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(logs).insert(saved.capture());
            assertEquals(429, saved.getValue().getStatusCode());
            assertTrue(saved.getValue().getMessage().contains(SECRET));
        } finally { upstream.stop(0); }
    }

    @Test
    void sseHandlesSplitUtf8MultilineErrorsAndIncompleteFinalEvent() throws Exception {
        String event = "event: error\ndata: {\"error\": {\n"
                + "data: \"code\":\"rate_limit_exceeded\",\"message\":\"" + SECRET + "\"}}";
        var bytes = new ByteArrayOutputStream();
        var output = new UpstreamErrors.SseOutput(bytes, MAPPER, "/v1/messages");
        for (byte value : event.getBytes(StandardCharsets.UTF_8)) output.write(value & 0xff);
        assertEquals(0, bytes.size());
        output.finish();
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("请求过于频繁，请稍后重试"));
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("private"));
    }

    @Test
    void imageDetailAndHistoryMaskStoredUpstreamErrorsWithoutChangingAdminData() {
        var records = mock(ImageRecordMapper.class);
        var service = image(records);
        var record = new ImageRecord();
        record.setId(9L);
        record.setUserId(3L);
        record.setStatus("failed");
        record.setRequestUrl("https://private-provider.example");
        record.setErrorStatusCode(402);
        record.setErrorType("insufficient_quota");
        record.setErrorMessage(SECRET);
        when(records.selectById(9L)).thenReturn(record);
        Page<ImageRecord> page = Page.of(1, 10);
        page.setRecords(List.of(record));
        when(records.selectPage(any(Page.class), any())).thenReturn(page);
        for (var result : List.of(service.detail(3L, 9L), service.history(3L, 1, 10).getRecords().get(0))) {
            assertFalse(result.getErrorMessage().contains("余额不足"));
            assertEquals("", result.getRequestUrl());
            assertEquals("api_error", result.getErrorType());
        }
        assertEquals(SECRET, record.getErrorMessage());
        assertEquals("insufficient_quota", record.getErrorType());
        assertEquals("https://private-provider.example", record.getRequestUrl());
        verify(records, never()).updateById(any(ImageRecord.class));
    }

    @Test
    void localImageAndRelayBusinessErrorsRemainUnchanged() throws Exception {
        var records = mock(ImageRecordMapper.class);
        var image = image(records);
        var record = new ImageRecord();
        record.setId(9L);
        record.setUserId(3L);
        record.setErrorMessage("项目本地保存失败");
        record.setErrorType("IOException");
        when(records.selectById(9L)).thenReturn(record);
        assertEquals("项目本地保存失败", image.detail(3L, 9L).getErrorMessage());
        assertEquals("提示词不能为空", assertThrows(BusinessException.class,
                () -> image.submit(3L, "user", "", "", "", List.of())).getMessage());
        var policy = mock(RelayPolicyService.class);
        when(policy.buildContexts(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(402, "你的账户余额不足，请充值"));
        var relay = new RelayDispatchServiceImpl(policy, new RelayProviderScheduler(), null, null, null, null, MAPPER);
        var request = request("/v1/chat/completions", false);
        assertEquals("你的账户余额不足，请充值", assertThrows(BusinessException.class, () -> relay.dispatch(request)).getMessage());
        assertEquals("你的账户余额不足，请充值", assertThrows(BusinessException.class, () -> relay.dispatchStream(request)).getMessage());
    }

    @ParameterizedTest
    @CsvSource({"429", "200"})
    void imageGenerationPersistsRawHttpErrorThenReturnsSafePollingResult(int httpStatus) throws Exception {
        String raw = "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"" + SECRET + "\"}}";
        var upstream = upstream(httpStatus, "application/json", raw);
        try {
            var records = mock(ImageRecordMapper.class);
            var configService = mock(ImageGenerationConfigService.class);
            var payments = mock(PaymentService.class);
            var config = new ImageGenerationConfig();
            config.setCode("test");
            config.setModel("test-image");
            config.setSize("1024x1024");
            config.setApiBaseUrl("http://127.0.0.1:" + upstream.getAddress().getPort());
            config.setApiKey("test-only");
            config.setPrice(BigDecimal.ONE);
            when(configService.requireEnabled("test")).thenReturn(config);
            doAnswer(call -> {
                ImageRecord record = call.getArgument(0);
                record.setId(9L);
                when(records.selectById(9L)).thenReturn(record);
                return 1;
            }).when(records).insert(any(ImageRecord.class));
            var service = new ImageServiceImpl(records, mock(ImageGenerationMetricMapper.class), payments,
                    configService, new RestTemplateBuilder(), MAPPER, Runnable::run);
            service.submit(3L, "test", "draw", "test", "1024x1024", List.of());
            var stored = records.selectById(9L);
            assertEquals("failed", stored.getStatus());
            assertEquals(raw, stored.getErrorMessage());
            assertEquals(httpStatus == 200 ? 503 : httpStatus, stored.getErrorStatusCode());
            String result = MAPPER.writeValueAsString(service.detail(3L, 9L));
            assertFalse(result.contains("private"));
            assertFalse(result.contains("余额不足"));
            verify(payments).refundBalance(3L, BigDecimal.ONE);
        } finally { upstream.stop(0); }
    }

    @Test
    void remoteImageDownloadErrorsAreKeptOnlyInAdminData() throws Exception {
        var upstream = upstream(403, "application/json", "{\"error\":{\"message\":\"" + SECRET + "\"}}");
        try {
            var records = mock(ImageRecordMapper.class);
            var stored = new ImageRecord();
            stored.setUserId(3L);
            when(records.selectById(9L)).thenReturn(stored);
            var service = image(records);
            assertThrows(org.springframework.web.client.RestClientResponseException.class,
                    () -> ReflectionTestUtils.invokeMethod(service, "saveRemoteImage", 9L, "test",
                            "http://127.0.0.1:" + upstream.getAddress().getPort()));
            assertTrue(stored.getErrorMessage().contains(SECRET));
            assertEquals(403, stored.getErrorStatusCode());
            assertFalse(service.detail(3L, 9L).getErrorMessage().contains("private"));
        } finally { upstream.stop(0); }
    }

    private static ImageServiceImpl image(ImageRecordMapper records) {
        return new ImageServiceImpl(records, mock(ImageGenerationMetricMapper.class), mock(PaymentService.class),
                mock(ImageGenerationConfigService.class), new RestTemplateBuilder(), MAPPER, Runnable::run);
    }

    private static RelayDispatchRequest request(String path, boolean stream) {
        return new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat", path,
                MAPPER.createObjectNode().put("model", "test").put("stream", stream));
    }

    private static RelayDispatchServiceImpl relay(HttpServer upstream, RelayUsageLogMapper logs) {
        var policy = mock(RelayPolicyService.class);
        var channel = new RelayChannel();
        channel.setId(System.nanoTime());
        channel.setApiBaseUrl("http://127.0.0.1:" + upstream.getAddress().getPort());
        channel.setApiKey("test-only");
        channel.setChannelRule("openai");
        var token = new RelayToken();
        token.setId(7L);
        token.setUserId(3L);
        var provider = new RelayChannelProvider();
        provider.setApiKey("test-only");
        provider.setOpenaiBaseUrl(channel.getApiBaseUrl());
        provider.setAnthropicBaseUrl(channel.getApiBaseUrl());
        var context = new RelayContext(token, new RelayModel(), null, channel, provider, null, "chat");
        when(policy.buildContexts(any(), any(), any(), any(), any(), any())).thenReturn(List.of(context));
        when(policy.estimateCost(any(), any(), any(), any(), any())).thenReturn(ZERO);
        return new RelayDispatchServiceImpl(policy, new RelayProviderScheduler(), logs, mock(RelayTokenMapper.class),
                mock(UserMapper.class), mock(PaymentService.class), MAPPER);
    }

    private static HttpServer upstream(int status, String type, String body) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", type);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < bytes.length; i += 7) {
                    out.write(bytes, i, Math.min(7, bytes.length - i));
                    out.flush();
                }
            }
        });
        server.start();
        return server;
    }
}
