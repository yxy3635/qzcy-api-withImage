package com.qzcy.backend.service.impl;

import com.qzcy.backend.service.RelayPromptRuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.relay.*;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.mapper.*;
import com.qzcy.backend.service.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.math.BigDecimal;
import java.net.http.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RelayStreamCompletionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RelayCostBreakdown COST = new RelayCostBreakdown(BigDecimal.ONE,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
    private static final String START = "event: response.created\ndata: {\"type\":\"response.created\"}\n\n";
    private static final String COMPLETED = "event: response.completed\ndata: {\"type\":\"response.completed\","
            + "\"response\":{\"usage\":{\"input_tokens\":120,\"output_tokens\":18}}}\n\n";

    @ParameterizedTest
    @CsvSource({"/v1/chat/completions", "/v1/messages", "/v1/responses"})
    void completeRefusalReturnsHttp400AndMarksUserOnce(String path) throws Exception {
        String events;
        if (path.endsWith("/chat/completions")) {
            events = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"抱歉，该请求违反平台\"}}]}\n\n"
                    + "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"使用规范\"}}]}\n\n"
                    + "data: [DONE]\n\n";
        } else if (path.endsWith("/messages")) {
            events = "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"抱歉，该请求违反平台使用规范\"}}\n\n"
                    + "data: {\"type\":\"message_stop\"}\n\n";
        } else {
            events = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"抱歉，该请求违反平台使用规范\",\"output_index\":0}\n\n" + COMPLETED;
        }
        var fixture = fixture(path, new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
        var audit = mock(RelayPromptRuleService.class);
        when(audit.enabled()).thenReturn(true);
        ReflectionTestUtils.setField(fixture.service, "promptRuleService", audit);
        var output = new ByteArrayOutputStream();
        var result = fixture.service.dispatchStream(fixture.request);
        assertEquals(400, result.statusCode());
        assertEquals("application/json", result.contentType());
        assertEquals(0, fixture.inFlight.get());
        result.body().writeTo(output);
        assertEquals(com.qzcy.backend.util.RelayPromptPolicyError.MESSAGE,
                MAPPER.readTree(output.toByteArray()).path("error").path("message").asText());
        assertEquals(400, fixture.saved().getStatusCode());
        verify(fixture.payments).deductBalanceOnly(3L, COST.total());
        verify(audit).recordRefusal(anyString(), eq(3L), eq(7L), eq(path), eq("test"), same(fixture.request.body()), eq(true), eq(true));
    }

    @Test
    void disablingRuleLeavesRefusalStreamUnblocked() throws Exception {
        String events = "data: {\"choices\":[{\"delta\":{\"content\":\"抱歉，该请求违反平台使用规范\"}}]}\n\n"
                + "data: [DONE]\n\n";
        var fixture = fixture("/v1/chat/completions", new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
        var rules = mock(RelayPromptRuleService.class);
        when(rules.enabled()).thenReturn(false);
        ReflectionTestUtils.setField(fixture.service, "promptRuleService", rules);
        var result = fixture.service.dispatchStream(fixture.request);
        assertEquals(200, result.statusCode());
        var output = new ByteArrayOutputStream();
        result.body().writeTo(output);
        assertEquals(events, output.toString(StandardCharsets.UTF_8));
        assertEquals(200, fixture.saved().getStatusCode());
        verify(rules).recordRefusal(anyString(), eq(3L), eq(7L), anyString(), anyString(), any(), eq(false), eq(true));
    }

    @ParameterizedTest
    @CsvSource({"/v1/chat/completions", "/v1/messages", "/v1/responses"})
    void ordinaryProtocolSummaryIsRecordedAsCompaction(String path) throws Exception {
        String events = path.endsWith("/messages")
                ? "data: {\"type\":\"message_stop\"}\n\n"
                : path.endsWith("/responses") ? COMPLETED : "data: [DONE]\n\n";
        var fixture = fixture(path, new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
        fixture.request.body().putArray(path.endsWith("/responses") ? "input" : "messages")
                .addObject().put("role", "user").put("content",
                        "Your task is to create a detailed summary of the conversation so far.");
        fixture.service.dispatchStream(fixture.request).body().writeTo(new ByteArrayOutputStream());
        assertEquals(Boolean.TRUE, fixture.saved().getContextCompaction());
        assertEquals(path, fixture.saved().getEndpoint());
    }

    @ParameterizedTest
    @CsvSource({"/v1/messages,200", "/v1/messages,400", "/v1/chat/completions,200",
            "/v1/chat/completions,400", "/v1/responses,200", "/v1/responses,400"})
    void nonStreamingSummaryKeepsItsClassificationOnSuccessAndFailure(String path, int status) throws Exception {
        var fixture = fixture(path, new ByteArrayInputStream(new byte[0]));
        fixture.request.body().put("stream", false).putArray(path.endsWith("/responses") ? "input" : "messages")
                .addObject().put("role", "user").put("content", "Summarize the conversation so far.");
        var client = (HttpClient) ReflectionTestUtils.getField(fixture.service, "httpClient");
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(response.body()).thenReturn(status == 200
                ? "{\"usage\":{\"input_tokens\":120,\"output_tokens\":18}}"
                : "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad input\"}}");
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        assertEquals(status, fixture.service.dispatch(fixture.request).statusCode());
        assertEquals(Boolean.TRUE, fixture.saved().getContextCompaction());
        assertEquals(status, fixture.saved().getStatusCode());
    }

    @ParameterizedTest
    @CsvSource({"/v1/messages", "/v1/responses"})
    void generatedProtocolCompactionIsRecordedEvenWithAnOrdinaryUserPrompt(String path) throws Exception {
        String events = path.endsWith("/messages")
                ? "data: {\"type\":\"content_block_start\",\"content_block\":{\"type\":\"compaction\"}}\n\n"
                    + "data: {\"type\":\"message_stop\"}\n\n"
                : "data: {\"type\":\"response.output_item.added\",\"item\":{\"type\":\"compaction\"}}\n\n" + COMPLETED;
        var fixture = fixture(path, new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
        fixture.request.body().putArray("messages").addObject().put("role", "user").put("content", "Continue.");
        fixture.service.dispatchStream(fixture.request).body().writeTo(new ByteArrayOutputStream());
        assertEquals(Boolean.TRUE, fixture.saved().getContextCompaction());
    }

    @ParameterizedTest
    @CsvSource({"/v1/messages,true", "/v1/messages,false", "/v1/responses,true", "/v1/responses,false"})
    void nonStreamingGeneratedCompactionSurvivesMissingUsage(String path, boolean includeUsage) throws Exception {
        var fixture = fixture(path, new ByteArrayInputStream(new byte[0]));
        var body = MAPPER.createObjectNode();
        body.putArray(path.endsWith("/messages") ? "content" : "output").addObject().put("type", "compaction");
        if (includeUsage) body.putObject("usage").put("input_tokens", 120).put("output_tokens", 18);
        var client = (HttpClient) ReflectionTestUtils.getField(fixture.service, "httpClient");
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
        when(response.body()).thenReturn(body.toString());
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        fixture.service.dispatch(fixture.request);
        assertEquals(Boolean.TRUE, fixture.saved().getContextCompaction());
    }

    @Test
    void completedResponseStopsReadingWithoutWaitingForHttpEof() throws Exception {
        var input = new OpenAfterEvents(START + COMPLETED, 8192);
        var fixture = fixture("/v1/responses", input);
        var output = new ByteArrayOutputStream();
        fixture.service.dispatchStream(fixture.request).body().writeTo(output);
        assertEquals(START + COMPLETED, output.toString(StandardCharsets.UTF_8));
        assertTrue(input.closed);
        assertFalse(input.readAfterEvents);
        var usage = fixture.saved();
        assertEquals("success", usage.getStatus());
        assertEquals(Boolean.FALSE, usage.getContextCompaction());
        assertEquals(120, usage.getPromptTokens());
        assertEquals(18, usage.getCompletionTokens());
        verify(fixture.payments).deductBalanceOnly(3L, COST.total());
        assertEquals(0, fixture.inFlight.get());
    }

    @Test
    void splitTerminalEventAndUtf8AreForwardedIntact() throws Exception {
        String content = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"正常输出\"}\r\n\r\n";
        String events = START + content + COMPLETED;
        var input = new OpenAfterEvents(events, 1);
        var fixture = fixture("/v1/responses", input);
        var output = new ByteArrayOutputStream();
        fixture.service.dispatchStream(fixture.request).body().writeTo(output);
        assertEquals(events, output.toString(StandardCharsets.UTF_8));
        assertFalse(input.readAfterEvents);
        assertEquals("success", fixture.saved().getStatus());
    }

    @Test
    void chatFinishReasonDoesNotDropLaterUsageFrame() throws Exception {
        String events = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":18}}\n\n"
                + "data: [DONE]\n\n";
        var input = new OpenAfterEvents(events, 7);
        var fixture = fixture("/v1/chat/completions", input);
        var output = new ByteArrayOutputStream();
        fixture.service.dispatchStream(fixture.request).body().writeTo(output);
        assertEquals(events, output.toString(StandardCharsets.UTF_8));
        assertEquals(18, fixture.saved().getCompletionTokens());
        assertFalse(input.readAfterEvents);
    }

    @ParameterizedTest
    @CsvSource({"/v1/messages,message_stop,200", "/v1/responses,response.incomplete,502",
            "/v1/responses,response.failed,502", "/v1/responses,error,502"})
    void terminalEventsEndTheStreamAndSetItsStatus(String path, String type, int status) throws Exception {
        String events = "event: " + type + "\ndata: {\"type\":\"" + type + "\"}\n\n";
        var input = new OpenAfterEvents(events, 3);
        var fixture = fixture(path, input);
        fixture.service.dispatchStream(fixture.request).body().writeTo(new ByteArrayOutputStream());
        assertFalse(input.readAfterEvents);
        assertEquals(status, fixture.saved().getStatusCode());
        if (status >= 400) {
            assertEquals(BigDecimal.ZERO, fixture.saved().getCost());
            verifyNoInteractions(fixture.payments);
        }
    }

    @Test
    void truncatedResponsesFailAndOnlyActualUsageIsBilled() throws Exception {
        for (String events : List.of(START, START + "data: [DONE]\n\n", COMPLETED.stripTrailing())) {
            var fixture = fixture("/v1/responses",
                    new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)));
            var output = new ByteArrayOutputStream();
            assertThrows(IOException.class,
                    () -> fixture.service.dispatchStream(fixture.request).body().writeTo(output));
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("event: error\n"));
            assertEquals(502, fixture.saved().getStatusCode());
            if (events.contains("input_tokens")) {
                assertEquals(COST.total(), fixture.saved().getCost());
                verify(fixture.payments).deductBalanceOnly(3L, COST.total());
            } else {
                assertEquals(BigDecimal.ZERO, fixture.saved().getCost());
                verifyNoInteractions(fixture.payments);
            }
            assertEquals(0, fixture.inFlight.get());
        }
    }

    @Test
    void upstreamReadFailureSendsSafeErrorAndChargesActualUsage() throws Exception {
        String events = "data: {\"type\":\"response.in_progress\",\"response\":{\"usage\":{\"input_tokens\":120}}}\n\n";
        var fixture = fixture("/v1/responses", new OpenAfterEvents(events, 8192));
        var output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> fixture.service.dispatchStream(fixture.request).body().writeTo(output));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("event: error\n"));
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("HTTP body remains open"));
        assertEquals(120, fixture.saved().getPromptTokens());
        assertEquals(COST.total(), fixture.saved().getCost());
        verify(fixture.payments).deductBalanceOnly(3L, COST.total());
    }

    @ParameterizedTest
    @CsvSource({"21,16", "20,15", "23,18", "7,6"})
    void heartbeatOnlyClientDisconnectDoesNotBecomeSuccessfulTokenUsage(int heartbeatCount, int legacyEstimate) throws Exception {
        String heartbeats = ":\n\n".repeat(heartbeatCount);
        // The previous ASCII fallback counted these bytes as 16, 15, 18, 6
        // output tokens, matching the four production incidents exactly.
        assertEquals(legacyEstimate, (int) Math.ceil(heartbeats.length() / 4.0));
        var fixture = fixture("/v1/responses", new OpenAfterEvents(heartbeats, 3));
        var output = new ByteArrayOutputStream() {
            @Override public void flush() throws IOException {
                if (size() >= heartbeats.length()) throw new IOException("Broken pipe");
            }
        };
        assertThrows(IOException.class, () -> fixture.service.dispatchStream(fixture.request).body().writeTo(output));
        var usage = fixture.saved();
        assertEquals("failed", usage.getStatus());
        assertEquals(0, usage.getCompletionTokens());
        assertEquals(BigDecimal.ZERO, usage.getCost());
        verifyNoInteractions(fixture.payments);

        var estimated = (com.fasterxml.jackson.databind.JsonNode) ReflectionTestUtils.invokeMethod(
                fixture.service, "withEstimatedUsage", fixture.request.body(), heartbeats);
        assertEquals(0, estimated.path("usage").path("completion_tokens").asInt());
    }

    @Test
    void downstreamWriteFailureDrainsUsageAndChargesWhileKeepingFailedStatus() throws Exception {
        var input = new OpenAfterEvents(START + COMPLETED, 8192);
        var fixture = fixture("/v1/responses", input);
        var output = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("Client disconnected"); }
        };
        assertThrows(IOException.class, () -> fixture.service.dispatchStream(fixture.request).body().writeTo(output));
        assertEquals("failed", fixture.saved().getStatus());
        assertEquals(COST.total(), fixture.saved().getCost());
        assertEquals(18, fixture.saved().getCompletionTokens());
        assertTrue(fixture.saved().getMessage().contains("downstream_error"));
        verify(fixture.payments).deductBalanceOnly(3L, COST.total());
        assertTrue(input.closed);
        assertEquals(0, fixture.inFlight.get());
    }

    @Test
    void terminalEventDoesNotForwardTrailingPartialData() throws Exception {
        String events = COMPLETED + "data: private trailing junk";
        var fixture = fixture("/v1/responses", new OpenAfterEvents(events, 8192));
        var output = new ByteArrayOutputStream();
        fixture.service.dispatchStream(fixture.request).body().writeTo(output);
        assertEquals(COMPLETED, output.toString(StandardCharsets.UTF_8));
        assertEquals("success", fixture.saved().getStatus());
    }

    @Test
    void completedEventLargerThanOneMegabyteStillEndsTheStream() throws Exception {
        String events = "data: {\"type\":\"response.completed\",\"response\":{\"output\":\""
                + "a".repeat(1024 * 1024 + 10) + "\",\"usage\":{\"input_tokens\":120,\"output_tokens\":18}}}\n\n";
        var input = new OpenAfterEvents(events, 8192);
        var fixture = fixture("/v1/responses", input);
        var output = new ByteArrayOutputStream();
        fixture.service.dispatchStream(fixture.request).body().writeTo(output);
        assertEquals(events, output.toString(StandardCharsets.UTF_8));
        assertFalse(input.readAfterEvents);
        assertEquals(18, fixture.saved().getCompletionTokens());
    }

    @Test
    void realHttpBodyCanStayOpenWhileRelayCompletes() throws Exception {
        var releaseUpstream = new CountDownLatch(1);
        var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newSingleThreadExecutor();
        upstream.createContext("/v1/responses", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write((START + COMPLETED).getBytes(StandardCharsets.UTF_8));
                output.flush();
                try {
                    releaseUpstream.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        upstream.start();
        try {
            var fixture = fixture("/v1/responses", InputStream.nullInputStream());
            fixture.channel.setApiBaseUrl("http://127.0.0.1:" + upstream.getAddress().getPort());
            fixture.provider.setOpenaiBaseUrl(fixture.channel.getApiBaseUrl());
            ReflectionTestUtils.setField(fixture.service, "httpClient", HttpClient.newHttpClient());
            var future = executor.submit(() -> {
                var output = new ByteArrayOutputStream();
                fixture.service.dispatchStream(fixture.request).body().writeTo(output);
                return output.toString(StandardCharsets.UTF_8);
            });
            assertEquals(START + COMPLETED, future.get(3, TimeUnit.SECONDS));
            assertEquals(1, releaseUpstream.getCount(), "Upstream has not closed its HTTP body");
            assertEquals("success", fixture.saved().getStatus());
            assertEquals(0, fixture.inFlight.get());
        } finally {
            releaseUpstream.countDown();
            upstream.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void cancellingOneOfThreeConcurrentStreamsDoesNotBlockOrCloseTheOthers() throws Exception {
        var allStarted = new CountDownLatch(3);
        var releaseSuccessful = new CountDownLatch(1);
        var releaseBodies = new CountDownLatch(1);
        var releaseCancelled = new CountDownLatch(1);
        var upgrades = new ConcurrentLinkedQueue<String>();
        var connections = ConcurrentHashMap.<Integer>newKeySet();
        var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var upstreamExecutor = Executors.newFixedThreadPool(3);
        var callers = Executors.newFixedThreadPool(3);
        upstream.setExecutor(upstreamExecutor);
        upstream.createContext("/v1/responses", exchange -> {
            String marker = MAPPER.readTree(exchange.getRequestBody()).path("test_case").asText();
            upgrades.add(Objects.toString(exchange.getRequestHeaders().getFirst("Upgrade"), ""));
            connections.add(exchange.getRemoteAddress().getPort());
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(START.getBytes(StandardCharsets.UTF_8));
                output.flush();
                allStarted.countDown();
                if (!"cancelled".equals(marker)) {
                    if (!releaseSuccessful.await(10, TimeUnit.SECONDS)) return;
                    String delta = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"" + marker + "\"}\n\n";
                    output.write((delta + COMPLETED).getBytes(StandardCharsets.UTF_8));
                    output.flush();
                } else {
                    if (!releaseCancelled.await(10, TimeUnit.SECONDS)) return;
                    output.write(COMPLETED.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                releaseBodies.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        upstream.start();
        try {
            var fixture = fixture("/v1/responses", InputStream.nullInputStream());
            fixture.channel.setApiBaseUrl("http://127.0.0.1:" + upstream.getAddress().getPort());
            fixture.provider.setOpenaiBaseUrl(fixture.channel.getApiBaseUrl());
            ReflectionTestUtils.setField(fixture.service, "httpClient", HttpClient.newHttpClient());
            java.util.function.Function<String, RelayDispatchRequest> requestFor = marker ->
                    new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat", "/v1/responses",
                            MAPPER.createObjectNode().put("model", "test").put("stream", true).put("test_case", marker));
            var cancelled = callers.submit(() -> assertThrows(IOException.class, () ->
                    fixture.service.dispatchStream(requestFor.apply("cancelled")).body().writeTo(new OutputStream() {
                        @Override public void write(int value) throws IOException { throw new IOException("Client cancelled"); }
                    })));
            var successful = new ArrayList<Future<String>>();
            for (String marker : List.of("first", "second")) {
                successful.add(callers.submit(() -> {
                    var output = new ByteArrayOutputStream();
                    fixture.service.dispatchStream(requestFor.apply(marker)).body().writeTo(output);
                    return output.toString(StandardCharsets.UTF_8);
                }));
            }
            assertTrue(allStarted.await(5, TimeUnit.SECONDS), "All three upstream streams must start concurrently");
            assertFalse(cancelled.isDone(), "Cancelled client still drains its upstream usage");
            assertEquals(3, fixture.inFlight.get());
            assertTrue(successful.stream().noneMatch(Future::isDone), "Other streams stay open after cancellation");
            assertEquals(3, connections.size(), "Concurrent SSE requests use separate HTTP/1.1 connections");
            assertTrue(upgrades.stream().allMatch(String::isEmpty), "SSE must not negotiate an h2c upgrade");
            releaseSuccessful.countDown();
            for (int i = 0; i < successful.size(); i++) {
                String output = successful.get(i).get(3, TimeUnit.SECONDS);
                assertTrue(output.contains("\"delta\":\"" + List.of("first", "second").get(i) + "\""));
                assertTrue(output.endsWith(COMPLETED));
            }
            releaseCancelled.countDown();
            cancelled.get(3, TimeUnit.SECONDS);
            assertEquals(1, releaseBodies.getCount(), "Completion does not wait for upstream EOF");
            var saved = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(fixture.logs, times(3)).insert(saved.capture());
            assertEquals(1, saved.getAllValues().stream().filter(item -> "failed".equals(item.getStatus())
                    && COST.total().compareTo(item.getCost()) == 0).count());
            assertEquals(2, saved.getAllValues().stream().filter(item -> "success".equals(item.getStatus())).count());
            verify(fixture.payments, times(3)).deductBalanceOnly(3L, COST.total());
            assertEquals(0, fixture.inFlight.get());
        } finally {
            releaseSuccessful.countDown();
            releaseCancelled.countDown();
            releaseBodies.countDown();
            upstream.stop(0);
            upstreamExecutor.shutdownNow();
            callers.shutdownNow();
        }
    }

    @Test
    void streamOverrideDoesNotEnableH2cForOrdinaryPlainHttpRequests() throws Exception {
        var fixture = fixture("/v1/responses", InputStream.nullInputStream());
        var context = new RelayContext(new RelayToken(), new RelayModel(), null,
                fixture.channel, fixture.provider, null, "chat");
        HttpRequest streaming = ReflectionTestUtils.invokeMethod(fixture.service, "upstreamRequest",
                fixture.request.body(), context, "/v1/responses", "", "", Map.of());
        assertEquals(HttpClient.Version.HTTP_1_1, streaming.version().orElseThrow());
        ReflectionTestUtils.setField(fixture.service, "streamHttpVersion", HttpClient.Version.HTTP_2);
        HttpRequest overridden = ReflectionTestUtils.invokeMethod(fixture.service, "upstreamRequest",
                fixture.request.body(), context, "/v1/responses", "", "", Map.of());
        assertEquals(HttpClient.Version.HTTP_2, overridden.version().orElseThrow());
        HttpRequest nonStream = ReflectionTestUtils.invokeMethod(fixture.service, "upstreamRequest",
                fixture.request.body().deepCopy().put("stream", false), context, "/v1/responses", "", "", Map.of());
        assertEquals(HttpClient.Version.HTTP_1_1, nonStream.version().orElseThrow());
        fixture.provider.setApiBaseUrl("https://example.com");
        HttpRequest secureNonStream = ReflectionTestUtils.invokeMethod(fixture.service, "upstreamRequest",
                fixture.request.body().deepCopy().put("stream", false), context, "/v1/responses", "", "", Map.of());
        assertTrue(secureNonStream.version().isEmpty(), "HTTPS retains TLS protocol negotiation");
    }

    private record Fixture(RelayDispatchServiceImpl service, RelayDispatchRequest request,
                           RelayUsageLogMapper logs, PaymentService payments,
                           java.util.concurrent.atomic.AtomicInteger inFlight,
                           RelayChannel channel, RelayChannelProvider provider) {
        RelayUsageLog saved() {
            var captor = ArgumentCaptor.forClass(RelayUsageLog.class);
            verify(logs).insert(captor.capture());
            return captor.getValue();
        }
    }

    private Fixture fixture(String path, InputStream input) throws Exception {
        var policy = mock(RelayPolicyService.class);
        var logs = mock(RelayUsageLogMapper.class);
        var payments = mock(PaymentService.class);
        var token = new RelayToken();
        token.setId(7L);
        token.setUserId(3L);
        var channel = new RelayChannel();
        channel.setId(System.nanoTime());
        channel.setChannelRule("openai");
        channel.setApiBaseUrl("http://127.0.0.1");
        channel.setApiKey("test-only");
        var provider = new RelayChannelProvider();
        provider.setId(System.nanoTime());
        provider.setOpenaiBaseUrl(channel.getApiBaseUrl());
        provider.setAnthropicBaseUrl(channel.getApiBaseUrl());
        var context = new RelayContext(token, new RelayModel(), null, channel, provider, null, "chat");
        when(policy.buildContexts(any(), any(), any(), any(), any(), any())).thenReturn(List.of(context));
        when(policy.estimateCost(any(), any(), any(), any(), any())).thenReturn(COST);
        var scheduler = new RelayProviderScheduler();
        var counter = scheduler.beginRequest(provider.getId());
        scheduler.endRequest(counter);
        var service = new RelayDispatchServiceImpl(policy, scheduler, logs, mock(RelayTokenMapper.class),
                mock(UserMapper.class), payments, MAPPER, new RelayPromptRuleService(null));
        var client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("text/event-stream")), (a, b) -> true));
        when(response.body()).thenReturn(input);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
                .thenReturn(response);
        ReflectionTestUtils.setField(service, "httpClient", client);
        var request = new RelayDispatchRequest("Bearer test", "", "", "test", "127.0.0.1", "chat", path,
                MAPPER.createObjectNode().put("model", "test").put("stream", true));
        return new Fixture(service, request, logs, payments, counter, channel, provider);
    }

    /** Simulates a server keeping its HTTP body open after sending a terminal event. */
    private static final class OpenAfterEvents extends InputStream {
        private final byte[] bytes;
        private final int chunkSize;
        private int offset;
        private boolean closed;
        private boolean readAfterEvents;

        OpenAfterEvents(String events, int chunkSize) {
            bytes = events.getBytes(StandardCharsets.UTF_8);
            this.chunkSize = chunkSize;
        }

        @Override public int read(byte[] target, int start, int length) throws IOException {
            if (offset == bytes.length) {
                readAfterEvents = true;
                throw new IOException("HTTP body remains open after terminal event");
            }
            int size = Math.min(Math.min(length, chunkSize), bytes.length - offset);
            System.arraycopy(bytes, offset, target, start, size);
            offset += size;
            return size;
        }

        @Override public int read() throws IOException {
            byte[] value = new byte[1];
            return read(value, 0, 1) == -1 ? -1 : value[0] & 0xff;
        }

        @Override public void close() { closed = true; }
    }
}
