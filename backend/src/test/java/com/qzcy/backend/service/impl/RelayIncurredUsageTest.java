package com.qzcy.backend.service.impl;

import com.qzcy.backend.service.RelayPromptRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.relay.*;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.math.BigDecimal;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelayIncurredUsageTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RelayCostBreakdown COST = new RelayCostBreakdown(BigDecimal.ONE,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
    private static final String PRIVATE = "https://private-provider.example sk-private-account";
    private static final String USAGE = "\"usage\":{\"input_tokens\":120,\"output_tokens\":18}";

    @ParameterizedTest
    @CsvSource({"200,false,false", "429,false,false", "502,false,false", "502,true,false",
            "200,true,true", "429,true,true", "502,true,true"})
    void billedJsonErrorIsSettledOnceWithoutFailoverAndKeepsPartialOutput(int status, boolean stream, boolean fixed) throws Exception {
        String raw = "{\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"" + PRIVATE
                + "\"}," + USAGE + ",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\","
                + "\"text\":\"已生成内容 https://example.org/help\"}]}]}}";
        var f = fixture(InputStream.nullInputStream(), "application/json");
        f.model.setFixedRequestBilling(fixed);
        when(f.response.statusCode()).thenReturn(status);
        when(f.response.body()).thenReturn(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        @SuppressWarnings("unchecked") HttpResponse<String> jsonResponse = mock(HttpResponse.class);
        when(jsonResponse.statusCode()).thenReturn(status);
        when(jsonResponse.body()).thenReturn(raw);
        var responseHeaders = f.response.headers();
        when(jsonResponse.headers()).thenReturn(responseHeaders);
        doReturn(stream ? f.response : jsonResponse).when(f.client).send(any(HttpRequest.class), any());
        // More than one candidate: charged errors must never silently trigger another bill.
        var context = new RelayContext(f.token, f.model, null, f.channel, null, null, "chat");
        when(f.policy.buildContexts(any(), any(), any(), any(), any(), any())).thenReturn(List.of(context, context));
        String result;
        if (stream) {
            var out = new ByteArrayOutputStream();
            f.service.dispatchStream(f.request).body().writeTo(out);
            result = out.toString(StandardCharsets.UTF_8);
        } else result = f.service.dispatch(f.request).body();
        assertTrue(result.contains("已生成内容"));
        assertTrue(result.contains("https://example.org/help"));
        assertFalse(result.contains("private-provider"));
        assertFalse(result.contains("sk-private"));
        assertTrue(result.contains(UpstreamErrors.type(status == 200 ? 502 : status)));
        assertEquals(COST.total(), f.saved().getCost());
        assertEquals(18, f.saved().getCompletionTokens());
        assertEquals("failed", f.saved().getStatus());
        verify(f.payments).deductBalanceOnly(3L, COST.total());
        verify(f.client).send(any(HttpRequest.class), any());
    }

    @ParameterizedTest
    @CsvSource({"response.failed", "response.incomplete"})
    void terminalErrorKeepsGeneratedOutputAndUsageButMasksDiagnostics(String kind) throws Exception {
        String events = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"前半段\"}\n\n"
                + "event: " + kind + "\ndata: {\"type\":\"" + kind + "\",\"response\":{"
                + USAGE + ",\"output\":[{\"text\":\"最后内容\"}],\"incomplete_details\":{\"reason\":\"" + PRIVATE
                + "\"},\"error\":{\"message\":\"" + PRIVATE + "\"}}}\n\n";
        var f = fixture(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)), "text/event-stream");
        var output = new ByteArrayOutputStream();
        f.service.dispatchStream(f.request).body().writeTo(output);
        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("前半段"));
        assertTrue(result.contains("最后内容"));
        assertTrue(result.contains("event: " + kind));
        assertFalse(result.contains("private-provider"));
        assertFalse(result.contains("sk-private"));
        verify(f.payments).deductBalanceOnly(3L, COST.total());
        assertEquals(120, f.saved().getPromptTokens());
        assertEquals("failed", f.saved().getStatus());
    }

    @Test
    void exhaustedQuotaDoesNotEraseIncurredFees() throws Exception {
        var f = fixture(new ByteArrayInputStream(("data: {\"type\":\"response.completed\",\"response\":{" + USAGE + "}}\n\n")
                .getBytes(StandardCharsets.UTF_8)), "text/event-stream");
        doThrow(new BusinessException(402, "quota exceeded")).when(f.policy).enforceQuota(any(), any());
        f.service.dispatchStream(f.request).body().writeTo(new ByteArrayOutputStream());
        verify(f.payments).deductBalanceOnly(3L, COST.total());
        var update = ArgumentCaptor.forClass(RelayToken.class);
        verify(f.tokens).updateById(update.capture());
        assertEquals(Boolean.FALSE, update.getValue().getEnabled());
        assertEquals(COST.total(), f.saved().getCost());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void disconnectedStreamHasHardDrainDeadlineAndKeepsEarlierUsage(boolean policyEnabled) throws Exception {
        var source = new InputStream() {
            final byte[] first = (policyEnabled
                    ? "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"正常内容\"}}],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":18}}\n\n"
                    : "data: {\"type\":\"response.in_progress\",\"response\":{" + USAGE + "}}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            boolean sent;
            boolean closed;
            @Override public synchronized int read(byte[] b, int off, int len) throws IOException {
                if (!sent) { sent = true; System.arraycopy(first, 0, b, off, first.length); return first.length; }
                while (!closed) {
                    try { wait(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException(ex); }
                }
                throw new IOException("closed by deadline");
            }
            @Override public int read() { throw new UnsupportedOperationException(); }
            @Override public synchronized void close() { closed = true; notifyAll(); }
        };
        var f = fixture(source, "text/event-stream");
        // Enabled Responses inspection holds headers until completion to allow a real 400.
        // Exercise live cancellation with a normal Chat answer, and the original Responses path with inspection off.
        var rules = mock(RelayPromptRuleService.class);
        when(rules.enabled()).thenReturn(policyEnabled);
        ReflectionTestUtils.setField(f.service, "promptRuleService", rules);
        var request = policyEnabled ? new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat",
                "/v1/chat/completions", f.request.body()) : f.request;
        ReflectionTestUtils.setField(f.service, "billingDrainTimeoutMs", 30L);
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IOException.class,
                () -> f.service.dispatchStream(request).body().writeTo(new OutputStream() {
                    @Override public void write(int b) throws IOException { throw new IOException("Broken pipe"); }
                })));
        assertEquals(18, f.saved().getCompletionTokens());
        verify(f.payments).deductBalanceOnly(3L, COST.total());
    }

    @Test
    void interruptedCompleteJsonDeltaIsDeliveredBeforeSafeError() throws Exception {
        String completeDelta = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"最后文字\"}";
        var f = fixture(new ByteArrayInputStream(completeDelta.getBytes(StandardCharsets.UTF_8)), "text/event-stream");
        var output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> f.service.dispatchStream(f.request).body().writeTo(output));
        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("最后文字"));
        assertTrue(result.indexOf("最后文字") < result.indexOf("event: error"));
        assertTrue(f.saved().getMessage().contains("\"billing_status\":\"unconfirmed\""));
        verifyNoInteractions(f.payments);
    }

    @Test
    void splitErrorsAndAccountMetadataNeverLeakWhileGeneratedLinksRemain() throws Exception {
        String events = "event: codex.response.metadata\ndata: {\"type\":\"codex.response.metadata\",\"headers\":{\"secret\":\"" + PRIVATE + "\"}}\n\n"
                + "event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"delta\":\"https://example.org\"}\n\n"
                + "event: error\ndata: plain private diagnostic " + PRIVATE + "\n\n";
        var out = new ByteArrayOutputStream();
        var writer = new UpstreamErrors.SseOutput(out, MAPPER, "/v1/responses");
        for (byte b : events.getBytes(StandardCharsets.UTF_8)) writer.write(b & 0xff);
        writer.finish();
        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("https://example.org"));
        assertFalse(result.contains("private"));
        assertFalse(result.contains("codex.response.metadata"));
        assertTrue(result.contains("api_error"));
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void failedResponseWithZeroUsageIsFreeEvenWithFixedPrice(boolean fixed) throws Exception {
        var f = fixture(new ByteArrayInputStream(("data: {\"type\":\"response.failed\",\"response\":{"
                + "\"usage\":{\"input_tokens\":0,\"output_tokens\":0},\"error\":{\"message\":\"" + PRIVATE + "\"}}}\n\n")
                .getBytes(StandardCharsets.UTF_8)), "text/event-stream");
        f.model.setFixedRequestBilling(fixed);
        f.service.dispatchStream(f.request).body().writeTo(new ByteArrayOutputStream());
        assertEquals(BigDecimal.ZERO, f.saved().getCost());
        verifyNoInteractions(f.payments);
    }

    @Test
    void generatedToolArgumentsArePreservedAndBareGatewayErrorsUseSseFraming() throws Exception {
        String good = "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"tool_use\","
                + "\"input\":{\"headers\":{\"Accept\":\"application/json\"},\"base_url\":\"https://example.org\"}}}\n\n";
        var out = new ByteArrayOutputStream();
        var writer = new UpstreamErrors.SseOutput(out, MAPPER, "/v1/messages");
        writer.write(good.getBytes(StandardCharsets.UTF_8));
        writer.write(("{\"error\":{\"message\":\"" + PRIVATE + "\"}}").getBytes(StandardCharsets.UTF_8));
        writer.finish();
        String result = out.toString(StandardCharsets.UTF_8);
        assertTrue(result.startsWith(good));
        assertTrue(result.contains("event: error\ndata:"));
        assertFalse(result.contains("private-provider"));
    }

    private record Fixture(RelayDispatchServiceImpl service, RelayDispatchRequest request,
                           RelayPolicyService policy, RelayUsageLogMapper logs, RelayTokenMapper tokens,
                           PaymentService payments, HttpClient client, HttpResponse<InputStream> response,
                           RelayToken token, RelayModel model, RelayChannel channel) {
        RelayUsageLog saved() {
            var captor = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(logs).insert(captor.capture());
            return captor.getValue();
        }
    }

    private Fixture fixture(InputStream input, String contentType) throws Exception {
        var policy = mock(RelayPolicyService.class);
        var logs = mock(RelayUsageLogMapper.class);
        var tokens = mock(RelayTokenMapper.class);
        var payments = mock(PaymentService.class);
        var token = new RelayToken(); token.setId(7L); token.setUserId(3L);
        var model = new RelayModel();
        var channel = new RelayChannel(); channel.setId(System.nanoTime()); channel.setChannelRule("openai");
        channel.setApiBaseUrl("http://127.0.0.1"); channel.setApiKey("test-only");
        var context = new RelayContext(token, model, null, channel, null, null, "chat");
        when(policy.buildContexts(any(), any(), any(), any(), any(), any())).thenReturn(List.of(context));
        when(policy.estimateCost(any(), any(), any(), any(), any())).thenReturn(COST);
        var service = new RelayDispatchServiceImpl(policy, new RelayProviderScheduler(), logs, tokens,
                mock(UserMapper.class), payments, MAPPER, new RelayPromptRuleService(null));
        var client = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (a, b) -> true));
        when(response.body()).thenReturn(input);
        doReturn(response).when(client).send(any(HttpRequest.class), any());
        ReflectionTestUtils.setField(service, "httpClient", client);
        var request = new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat", "/v1/responses",
                MAPPER.createObjectNode().put("model", "test").put("stream", true));
        return new Fixture(service, request, policy, logs, tokens, payments, client, response, token, model, channel);
    }
}
