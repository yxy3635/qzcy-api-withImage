package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/** Keep a bounded latest-user text excerpt; exclude history, tools, URLs and credentials. */
public final class RelayPromptAuditSummary {
    private static final Pattern CREDENTIAL = Pattern.compile("(?i)(?:bearer\\s+\\S+|sk-[a-z0-9_-]+|(?:api[_-]?key|authorization|password|密码|token)\\s*[:=：]\\s*[\\\"']?[^\\s\\\"',;]+)");
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|data:)[^\\s\\\"'<>]+");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private RelayPromptAuditSummary() {}

    public static String from(JsonNode body, String path) {
        JsonNode source = body.path("prompt");
        if ("/v1/responses".equals(path)) source = body.path("input");
        else if ("/v1/messages".equals(path) || "/v1/chat/completions".equals(path)) source = body.path("messages");
        if (source.isArray() && ("/v1/responses".equals(path) || path.endsWith("/messages") || path.endsWith("/chat/completions"))) {
            JsonNode latest = null;
            for (JsonNode message : source) if ("user".equals(message.path("role").asText())) latest = message.path("content");
            source = latest;
        }
        StringBuilder text = new StringBuilder();
        collect(source, text);
        String safe = CREDENTIAL.matcher(text).replaceAll("[凭证已隐藏]");
        safe = URL.matcher(safe).replaceAll("[链接已隐藏]");
        safe = EMAIL.matcher(safe).replaceAll("[邮箱已隐藏]").replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        return safe.length() <= 240 ? safe : safe.substring(0, 240) + "…";
    }

    private static void collect(JsonNode node, StringBuilder target) {
        if (node == null || target.length() > 8192) return;
        if (node.isTextual()) target.append(node.asText(), 0, Math.min(node.asText().length(), 8192 - target.length())).append(' ');
        else if (node.isArray()) for (JsonNode item : node) collect(item, target);
        else if (node.isObject() && ("text".equals(node.path("type").asText()) || "input_text".equals(node.path("type").asText())))
            collect(node.path("text"), target);
    }
}
