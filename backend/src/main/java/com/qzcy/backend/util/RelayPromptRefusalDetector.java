package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/** Evidence of the configured refusal reply, not a verdict that the user attacked the system. */
public final class RelayPromptRefusalDetector {
    public static final String REFUSAL = "抱歉，该请求违反平台使用规范";
    private static final int TEXT_LIMIT = 256;

    private RelayPromptRefusalDetector() {}

    public static boolean matches(JsonNode body, String path) {
        if (body == null || body.hasNonNull("error")) return false;
        if ("/v1/chat/completions".equals(path) || "/v1/completions".equals(path)) {
            for (JsonNode choice : body.path("choices")) {
                JsonNode message = choice.path("message");
                if (isRefusal(text(message.path("content"))) || isRefusal(message.path("refusal").asText(""))
                        || isRefusal(choice.path("text").asText(""))) return true;
            }
        } else if ("/v1/messages".equals(path)) {
            return isRefusal(text(body.path("content")));
        } else if ("/v1/responses".equals(path)) {
            for (JsonNode item : body.path("output")) {
                if ("message".equals(item.path("type").asText()) && "assistant".equals(item.path("role").asText())
                        && isRefusal(text(item.path("content")))) return true;
            }
        }
        return false;
    }

    private static String text(JsonNode node) {
        if (node.isTextual()) return node.asText();
        StringBuilder result = new StringBuilder();
        if (node.isArray()) for (JsonNode block : node) {
            String type = block.path("type").asText();
            if ("text".equals(type) || "output_text".equals(type)) result.append(block.path("text").asText(""));
            else if ("refusal".equals(type)) result.append(block.path("refusal").asText(""));
            if (result.length() > TEXT_LIMIT) return "";
        }
        return result.toString();
    }

    private static boolean isRefusal(String text) {
        if (text == null || text.length() > TEXT_LIMIT) return false;
        return REFUSAL.replace("，", "").equals(text.replaceAll("[\\s\\p{P}\\p{Z}]+", ""));
    }

    /** Bounded output-only accumulation; works across UTF-8/SSE/chunk and token boundaries. */
    public static final class Stream {
        private final Map<String, StringBuilder> outputs = new HashMap<>();
        private boolean completedRefusal;

        public void accept(JsonNode event, String path) {
            if (event == null || event.hasNonNull("error")) return;
            if ("/v1/chat/completions".equals(path) || "/v1/completions".equals(path)) {
                for (JsonNode choice : event.path("choices")) {
                    String key = "choice:" + choice.path("index").asInt();
                    append(key, choice.path("delta").path("content").asText(""));
                    append(key, choice.path("delta").path("refusal").asText(""));
                    append(key, choice.path("text").asText(""));
                }
            } else if ("/v1/messages".equals(path)) {
                String type = event.path("type").asText();
                if ("content_block_start".equals(type) && "text".equals(event.path("content_block").path("type").asText()))
                    append("message", event.path("content_block").path("text").asText(""));
                if ("content_block_delta".equals(type) && "text_delta".equals(event.path("delta").path("type").asText()))
                    append("message", event.path("delta").path("text").asText(""));
            } else if ("/v1/responses".equals(path)) {
                String type = event.path("type").asText();
                if ("response.output_text.delta".equals(type) || "response.refusal.delta".equals(type))
                    append("output:" + event.path("output_index").asInt(), event.path("delta").asText(""));
                if ("response.completed".equals(type)) completedRefusal |= matches(event.path("response"), path);
            }
        }

        private void append(String key, String value) {
            if (value.isEmpty() || (outputs.size() >= 128 && !outputs.containsKey(key))) return;
            StringBuilder output = outputs.computeIfAbsent(key, ignored -> new StringBuilder());
            if (output.length() <= TEXT_LIMIT) output.append(value, 0, Math.min(value.length(), TEXT_LIMIT + 1 - output.length()));
        }

        public boolean matched() {
            return completedRefusal || outputs.values().stream().anyMatch(value -> isRefusal(value.toString()));
        }

        /** A regular textual answer can be streamed as soon as it diverges from the refusal. */
        public boolean hasNonRefusalText() {
            return !outputs.isEmpty() && outputs.values().stream().allMatch(value -> {
                String normalized = value.toString().replaceAll("[\\s\\p{P}\\p{Z}]+", "");
                return !REFUSAL.replace("，", "").startsWith(normalized);
            });
        }
    }
}
