package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.regex.Pattern;

/** Classifies the current task, without treating cache misses or old summaries as compaction. */
public final class RelayRequestKind {
    private static final Pattern COMPACT_ENDPOINT = Pattern.compile("(?:^|/)responses/compact/?$");
    private static final Pattern SUMMARY_TASK = Pattern.compile(
            "^(?:please\\s+)?(?:summari[sz]e|compress|compact|create\\s+(?:a\\s+)?(?:detailed\\s+)?summary|"
                    + "provide\\s+(?:a\\s+)?(?:detailed\\s+)?summary|"
                    + "write\\s+(?:a\\s+)?(?:detailed\\s+)?summary|"
                    + "your task is to create a (?:detailed )?summary|"
                    + "you are (?:a helpful ai assistant )?tasked with summarizing)\\b");
    private static final Pattern CONVERSATION = Pattern.compile(
            "\\b(?:conversations?|chat history|conversation history|context so far|context above)\\b");
    private static final Pattern CHINESE_TASK = Pattern.compile(
            "^(?:请|请你|帮我|请帮我)?(?:对)?(?:总结|压缩|精简|概括|生成.{0,8}摘要)|"
                    + "^(?:请|请你|帮我|请帮我)?(?:将|把|对)(?:以上|之前|当前|上述|这段|本次|整个).{0,12}(?:对话|会话|上下文|聊天记录).{0,8}(?:总结|压缩|精简|概括)");

    private RelayRequestKind() { }

    /** Recognize generated protocol blocks, never compacted input history or merely enabled settings. */
    public static boolean hasGeneratedCompaction(JsonNode response) {
        if (response == null) return false;
        if ("compaction".equals(response.path("type").asText())
                || "response.compaction".equals(response.path("object").asText())) return true;
        for (String field : new String[]{"content", "output"}) {
            JsonNode blocks = response.path(field);
            if (blocks.isArray()) {
                for (JsonNode block : blocks) {
                    if ("compaction".equals(block.path("type").asText())) return true;
                }
            }
        }
        return "compaction".equals(response.path("content_block").path("type").asText())
                || "compaction".equals(response.path("item").path("type").asText())
                || hasGeneratedCompaction(response.get("response"))
                || hasGeneratedCompaction(response.get("message"));
    }

    public static boolean isCompaction(String endpoint, JsonNode body) {
        String path = endpoint == null ? "" : endpoint.trim().split("[?#]", 2)[0];
        if (COMPACT_ENDPOINT.matcher(path).find()) return true;
        // Summarization prompts apply only to conversational protocols.
        if (!(path.endsWith("/messages") || path.endsWith("/chat/completions")
                || path.endsWith("/responses")) || body == null) return false;

        JsonNode messages = body.path("messages");
        if (!messages.isArray()) messages = body.path("input");
        if (messages.isArray()) {
            // Inspect only the latest user turn: a prior compact prompt stays in history.
            for (int i = messages.size() - 1; i >= 0; i--) {
                JsonNode message = messages.get(i);
                if ("user".equals(message.path("role").asText())) {
                    if (isSummaryTask(text(message.path("content")))) return true;
                    break;
                }
            }
            for (JsonNode message : messages) {
                String role = message.path("role").asText();
                if (("system".equals(role) || "developer".equals(role))
                        && isSummaryTask(text(message.path("content")))) return true;
            }
        } else if (body.path("input").isTextual() && isSummaryTask(body.path("input").asText())) {
            return true;
        }
        return isSummaryTask(text(body.path("system"))) || isSummaryTask(text(body.path("instructions")));
    }

    private static boolean isSummaryTask(String value) {
        String prefix = value.stripLeading();
        prefix = prefix.substring(0, Math.min(prefix.length(), 512)).toLowerCase(Locale.ROOT);
        if (prefix.startsWith("you are performing a context checkpoint compaction.")) return true;
        if (prefix.startsWith("provide a detailed prompt for continuing our conversation above")
                || prefix.startsWith("summarize what we did so far")) return true;
        if (SUMMARY_TASK.matcher(prefix).find() && CONVERSATION.matcher(prefix).find()) return true;
        return CHINESE_TASK.matcher(prefix).find()
                && (prefix.contains("对话") || prefix.contains("会话")
                || prefix.contains("上下文") || prefix.contains("聊天记录"));
    }

    private static String text(JsonNode content) {
        if (content.isTextual()) return content.asText();
        if (!content.isArray()) return "";
        StringBuilder result = new StringBuilder();
        for (JsonNode block : content) {
            String type = block.path("type").asText();
            if ("text".equals(type) || "input_text".equals(type)) {
                result.append(block.path("text").asText("")).append('\n');
                if (result.length() >= 512) break;
            }
        }
        return result.toString();
    }
}
