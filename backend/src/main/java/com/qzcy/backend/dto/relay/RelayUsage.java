package com.qzcy.backend.dto.relay;

import com.fasterxml.jackson.databind.JsonNode;

/** Input totals include cache; billable input excludes separately priced cache. */
public record RelayUsage(
        long inputTokens,
        long billableInputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheCreationTokens,
        long cacheCreation5mTokens,
        long cacheCreation1hTokens,
        long totalTokens
) {
    public static RelayUsage fromResponse(JsonNode responseBody, String upstreamPath) {
        JsonNode usage = responseBody.path("usage");
        long input = firstNumber(usage, "prompt_tokens", "input_tokens_total", "input_tokens");
        long output = firstNumber(usage, "completion_tokens", "output_tokens");
        long cacheRead = cacheTokens(usage, "cached_tokens", "cache_read_tokens",
                "cache_read_input_tokens", "prompt_cache_hit_tokens");
        JsonNode aggregateCreation = cacheNumber(usage, "cache_creation_tokens", "cached_creation_tokens",
                "cache_creation_input_tokens");
        JsonNode creationDetails = creationDetails(usage);
        long fiveMinute = Math.max(0, creationDetails.path("ephemeral_5m_input_tokens").asLong());
        long oneHour = Math.max(0, creationDetails.path("ephemeral_1h_input_tokens").asLong());
        long cacheCreation = aggregateCreation.isNumber()
                ? Math.max(0, aggregateCreation.asLong()) : fiveMinute + oneHour;
        // The aggregate is authoritative when present (including zero). Legacy
        // totals without a TTL breakdown retain the existing/default 5m rate.
        // A partial breakdown assigns the remainder to 5m, never billing twice.
        oneHour = Math.min(oneHour, cacheCreation);
        fiveMinute = cacheCreation - oneHour;

        // Endpoint semantics decide ambiguous flat fields. OpenAI totals and
        // locally estimated usage remain inclusive even on a Messages endpoint.
        boolean separateCache = upstreamPath != null && upstreamPath.startsWith("/v1/messages")
                && !usage.path("estimated").asBoolean(false)
                && !usage.path("prompt_tokens").isNumber()
                && !usage.path("input_tokens_total").isNumber()
                && !usage.path("input_tokens_details").isObject();
        long billableInput = separateCache ? input : Math.max(0, input - cacheRead - cacheCreation);
        long totalInput = separateCache ? input + cacheRead + cacheCreation : input;
        return new RelayUsage(totalInput, billableInput, output, cacheRead, cacheCreation,
                fiveMinute, oneHour, totalInput + output);
    }

    private static long cacheTokens(JsonNode usage, String... names) {
        return Math.max(0, cacheNumber(usage, names).asLong());
    }

    private static JsonNode cacheNumber(JsonNode usage, String... names) {
        for (JsonNode source : new JsonNode[]{usage.path("prompt_tokens_details"),
                usage.path("input_tokens_details"), usage}) {
            for (String name : names) {
                if (source.path(name).isNumber()) return source.path(name);
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static JsonNode creationDetails(JsonNode usage) {
        for (JsonNode source : new JsonNode[]{usage.path("prompt_tokens_details"),
                usage.path("input_tokens_details"), usage}) {
            JsonNode details = source.path("cache_creation");
            if (details.path("ephemeral_5m_input_tokens").isNumber()
                    || details.path("ephemeral_1h_input_tokens").isNumber()) return details;
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static long firstNumber(JsonNode usage, String... names) {
        for (String name : names) {
            if (usage.path(name).isNumber()) return Math.max(0, usage.path(name).asLong());
        }
        return 0;
    }
}
