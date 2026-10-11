package com.qzcy.backend.dto.relay;

import com.fasterxml.jackson.databind.JsonNode;

/** Input totals include cache; billable input excludes separately priced cache. */
public record RelayUsage(
        long inputTokens,
        long billableInputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheCreationTokens,
        long totalTokens
) {
    public static RelayUsage fromResponse(JsonNode responseBody, String upstreamPath) {
        JsonNode usage = responseBody.path("usage");
        long input = firstNumber(usage, "prompt_tokens", "input_tokens_total", "input_tokens");
        long output = firstNumber(usage, "completion_tokens", "output_tokens");
        long cacheRead = cacheTokens(usage, "cached_tokens", "cache_read_tokens",
                "cache_read_input_tokens", "prompt_cache_hit_tokens");
        long cacheCreation = cacheTokens(usage, "cache_creation_tokens", "cached_creation_tokens",
                "cache_creation_input_tokens");

        // Endpoint semantics decide ambiguous flat fields. OpenAI totals and
        // locally estimated usage remain inclusive even on a Messages endpoint.
        boolean separateCache = upstreamPath != null && upstreamPath.startsWith("/v1/messages")
                && !usage.path("estimated").asBoolean(false)
                && !usage.path("prompt_tokens").isNumber()
                && !usage.path("input_tokens_total").isNumber()
                && !usage.path("input_tokens_details").isObject();
        long billableInput = separateCache ? input : Math.max(0, input - cacheRead - cacheCreation);
        long totalInput = separateCache ? input + cacheRead + cacheCreation : input;
        return new RelayUsage(totalInput, billableInput, output, cacheRead, cacheCreation, totalInput + output);
    }

    private static long cacheTokens(JsonNode usage, String... names) {
        for (JsonNode source : new JsonNode[]{usage.path("prompt_tokens_details"),
                usage.path("input_tokens_details"), usage}) {
            for (String name : names) {
                if (source.path(name).isNumber()) return Math.max(0, source.path(name).asLong());
            }
        }
        return 0;
    }

    private static long firstNumber(JsonNode usage, String... names) {
        for (String name : names) {
            if (usage.path(name).isNumber()) return Math.max(0, usage.path(name).asLong());
        }
        return 0;
    }
}
