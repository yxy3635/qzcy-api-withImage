package com.qzcy.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Public upstream errors are rebuilt from an allowlist; raw responses stay in admin logs. */
public final class UpstreamErrors {
    private UpstreamErrors() {}

    public static String message(int status) {
        return switch (status) {
            case 400, 422 -> "请求参数不符合要求，请调整后重试";
            case 401, 402, 403 -> "服务暂时不可用，请稍后重试或联系管理员";
            case 404 -> "请求的服务暂时不可用，请稍后重试";
            case 405 -> "请求方式不受支持，请检查后重试";
            case 408, 504, 524 -> "请求处理超时，请稍后重试";
            case 409 -> "请求发生冲突，请稍后重试";
            case 413 -> "请求内容过大，请缩小后重试";
            case 415 -> "请求内容格式不受支持，请调整后重试";
            case 429 -> "请求过于频繁，请稍后重试";
            default -> "服务暂时不可用，请稍后重试";
        };
    }

    public static String type(int status) {
        return switch (status) {
            case 400, 405, 409, 413, 415, 422 -> "invalid_request_error";
            case 404 -> "not_found_error";
            case 408, 504, 524 -> "timeout_error";
            case 429 -> "rate_limit_error";
            default -> "api_error";
        };
    }

    public static boolean isError(JsonNode body) {
        if (body == null || !body.isObject()) return false;
        return body.hasNonNull("error") || body.path("response").hasNonNull("error")
                || "error".equals(body.path("type").asText())
                || "response.failed".equals(body.path("type").asText())
                || "response.incomplete".equals(body.path("type").asText())
                || "failed".equals(body.path("status").asText())
                || "incomplete".equals(body.path("status").asText())
                || "error".equals(body.path("status").asText())
                || "failed".equals(body.path("response").path("status").asText())
                || "incomplete".equals(body.path("response").path("status").asText())
                || (body.path("code").isInt() && body.path("code").asInt() >= 400)
                || (body.path("success").isBoolean() && !body.path("success").asBoolean());
    }

    public static int status(int httpStatus, JsonNode body) {
        JsonNode error = body == null ? null : body.path("error");
        if (error != null && error.isMissingNode()) error = body.path("response").path("error");
        if (error != null && (error.isMissingNode() || error.isNull())) error = body;
        if (error != null && error.path("code").isInt()) {
            int code = error.path("code").asInt();
            if (code >= 400 && code <= 599) return code;
        }
        String code = error == null ? "" : error.path("code").asText("");
        String kind = error == null ? "" : error.path("type").asText("");
        String key = (code.isBlank() ? kind : code).toLowerCase(Locale.ROOT);
        return switch (key) {
            case "insufficient_quota", "quota_exceeded", "billing_hard_limit_reached", "insufficient_balance" -> 503;
            case "rate_limit_exceeded", "rate_limit_error", "too_many_requests" -> 429;
            case "content_policy_violation", "content_filter", "safety_violation", "invalid_request_error" -> 400;
            case "context_length_exceeded" -> 413;
            case "authentication_error", "invalid_api_key", "permission_error" -> 503;
            case "overloaded_error", "server_error", "api_error" -> httpStatus >= 400 ? httpStatus : 503;
            case "request_timeout", "gateway_timeout", "timeout_error" -> 504;
            default -> httpStatus >= 400 ? httpStatus : 502;
        };
    }

    public static ObjectNode body(ObjectMapper mapper, int status, String path, JsonNode raw) {
        int publicStatus = status(status, raw);
        ObjectNode result = mapper.createObjectNode();
        boolean anthropic = path != null && path.startsWith("/v1/messages");
        if (anthropic) result.put("type", "error");
        ObjectNode error = result.putObject("error");
        error.put("message", message(publicStatus));
        String errorType = type(publicStatus);
        if (anthropic && "timeout_error".equals(errorType)) errorType = "api_error";
        if (anthropic && publicStatus == 413) errorType = "request_too_large";
        error.put("type", errorType);
        error.put("code", publicStatus);
        return result;
    }

    /** Preserve generated output on failed responses, but never copy diagnostic fields. */
    public static ObjectNode publicFailure(ObjectMapper mapper, int status, String path, JsonNode raw) {
        ObjectNode safe = body(mapper, status, path, raw);
        if (raw != null && raw.isObject()) {
            for (String key : new String[]{"output", "content", "choices", "usage"}) {
                if (raw.hasNonNull(key)) safe.set(key, stripDiagnostics(raw.get(key)));
                else if (raw.path("response").hasNonNull(key)) safe.set(key, stripDiagnostics(raw.path("response").get(key)));
            }
        }
        return safe;
    }

    private static final Set<String> PRIVATE_FIELDS = Set.of("headers", "upstream", "upstream_url",
            "upstream_id", "provider", "provider_id", "provider_name", "base_url", "api_base_url",
            "request_url", "api_key", "authorization", "x-api-key");

    public static JsonNode stripDiagnostics(JsonNode raw) {
        if (raw == null) return null;
        JsonNode clean = raw.deepCopy();
        if (clean.isObject()) {
            ObjectNode obj = (ObjectNode) clean;
            var keys = new java.util.ArrayList<String>();
            obj.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                if (PRIVATE_FIELDS.contains(key.toLowerCase(Locale.ROOT))) obj.remove(key);
                else if ("error".equals(key) && obj.hasNonNull(key)) {
                    int code = status(502, obj);
                    ObjectNode safeError = obj.objectNode();
                    safeError.put("type", type(code)).put("code", code).put("message", message(code));
                    obj.set(key, safeError);
                // Tool inputs and JSON schemas are generated/user content, not gateway metadata.
                } else if (!Set.of("input", "arguments", "parameters", "schema").contains(key)) {
                    obj.set(key, stripDiagnostics(obj.get(key)));
                }
            }
        } else if (clean.isArray()) {
            var array = (com.fasterxml.jackson.databind.node.ArrayNode) clean;
            for (int i = 0; i < array.size(); i++) array.set(i, stripDiagnostics(array.get(i)));
        }
        return clean;
    }

    /** Buffer one SSE event so errors split across network/UTF-8 chunks cannot leak. */
    public static final class SseOutput extends OutputStream {
        private final OutputStream output;
        private final ObjectMapper mapper;
        private final String path;
        private final ByteArrayOutputStream event = new ByteArrayOutputStream();
        private boolean lineEmpty = true;
        private boolean downstreamFailed;

        public SseOutput(OutputStream output, ObjectMapper mapper, String path) {
            this.output = output;
            this.mapper = mapper;
            this.path = path;
        }

        @Override
        public void write(int value) throws IOException {
            event.write(value);
            if (value == '\n') {
                if (lineEmpty) emit();
                lineEmpty = true;
            } else if (value != '\r') {
                lineEmpty = false;
            }
            if (event.size() > 32 * 1024 * 1024) {
                event.reset();
                throw new IOException("Upstream SSE event exceeds size limit");
            }
        }

        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            for (int i = off; i < off + len; i++) write(bytes[i] & 0xff);
        }

        public void finish() throws IOException {
            if (event.size() > 0) emit();
        }

        public boolean downstreamFailed() { return downstreamFailed; }

        /** Only forward an interrupted event if its JSON is complete and can be inspected. */
        public void finishInterrupted() throws IOException {
            if (event.size() == 0) return;
            String raw = event.toString(StandardCharsets.UTF_8);
            StringBuilder data = new StringBuilder();
            for (String line : raw.split("\\R")) {
                if (line.startsWith("data:")) data.append(line.substring(5).stripLeading()).append('\n');
            }
            try {
                if (mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(data.toString()) != null) {
                    if (!raw.endsWith("\n")) write('\n');
                    write('\n');
                    return;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { }
            event.reset();
            lineEmpty = true;
        }

        private void emit() throws IOException {
            String raw = event.toString(StandardCharsets.UTF_8);
            StringBuilder data = new StringBuilder();
            String eventType = "";
            for (String line : raw.split("\\R")) {
                if (line.startsWith("data:")) data.append(line.substring(5).stripLeading()).append('\n');
                if (line.startsWith("event:")) eventType = line.substring(6).trim();
            }
            JsonNode json = null;
            try {
                json = mapper.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(data.isEmpty() ? raw : data.toString());
            } catch (Exception ignored) { }
            String jsonType = json == null ? "" : json.path("type").asText();
            // Account limits and session headers belong to the provider, not the API user.
            if (eventType.startsWith("codex.") || jsonType.startsWith("codex.")) {
                event.reset();
                return;
            }
            if (isError(json) || "error".equals(eventType) || "response.failed".equals(eventType)
                    || "response.incomplete".equals(eventType)
                    || (!eventType.isEmpty() && !eventType.matches("[A-Za-z0-9_.-]{1,100}"))
                    || (json == null && !data.isEmpty() && !"[DONE]".equals(data.toString().trim()))
                    || (json == null && data.isEmpty() && eventType.isEmpty() && !raw.isBlank()
                        && !raw.stripLeading().startsWith(":"))) {
                ObjectNode safe = publicFailure(mapper, 502, path, json);
                String kind = json == null ? eventType : json.path("type").asText(eventType);
                if (path != null && path.startsWith("/v1/responses")) {
                    ObjectNode error = (ObjectNode) safe.path("error");
                    error.put("code", error.path("type").asText());
                    if (!"response.failed".equals(kind) && !"response.incomplete".equals(kind)) {
                        safe.put("code", error.path("code").asText());
                        safe.put("message", error.path("message").asText());
                        safe.putNull("param");
                        safe.remove("error");
                        kind = "error";
                    }
                }
                if ("response.failed".equals(kind) || "response.incomplete".equals(kind)) {
                    ObjectNode response = mapper.createObjectNode();
                    JsonNode rawResponse = json == null ? null : json.path("response");
                    if (rawResponse != null) {
                        for (String key : new String[]{"output", "usage"}) {
                            if (rawResponse.hasNonNull(key)) response.set(key, stripDiagnostics(rawResponse.get(key)));
                        }
                    }
                    response.put("object", "response");
                    response.put("status", "response.incomplete".equals(kind) ? "incomplete" : "failed");
                    response.set("error", safe.remove("error"));
                    safe.set("response", response);
                }
                if ("error".equals(kind) || "response.failed".equals(kind) || "response.incomplete".equals(kind)) safe.put("type", kind);
                if (json != null && json.path("sequence_number").isNumber()) safe.set("sequence_number", json.get("sequence_number"));
                String label = "response.failed".equals(kind) || "response.incomplete".equals(kind) ? kind : "error";
                send(("event: " + label + "\ndata: " + safe + "\n\n").getBytes(StandardCharsets.UTF_8));
            } else {
                JsonNode clean = stripDiagnostics(json);
                if (json != null && !json.equals(clean)) {
                    String label = eventType.isBlank() ? "" : "event: " + eventType + "\n";
                    send((label + "data: " + clean + "\n\n").getBytes(StandardCharsets.UTF_8));
                } else if (data.isEmpty()) {
                    send(":\n\n".getBytes(StandardCharsets.UTF_8));
                } else send(event.toByteArray());
            }
            event.reset();
        }

        private void send(byte[] bytes) throws IOException {
            try {
                output.write(bytes);
                output.flush();
            } catch (IOException ex) {
                downstreamFailed = true;
                throw ex;
            }
        }
    }
}
