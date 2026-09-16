package com.qzcy.backend.service;

import com.qzcy.backend.entity.RelayChannelProvider;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolve explicit format URLs, falling back only for unmigrated providers. */
public final class RelayProviderFormats {
    private RelayProviderFormats() {}

    public static Map<String, String> urls(RelayChannelProvider provider) {
        Map<String, String> urls = new LinkedHashMap<>();
        if (provider.getOpenaiBaseUrl() == null && provider.getAnthropicBaseUrl() == null) {
            put(urls, "anthropic".equalsIgnoreCase(provider.getChannelRule()) ? "anthropic" : "openai", provider.getApiBaseUrl());
        } else {
            put(urls, "openai", provider.getOpenaiBaseUrl());
            put(urls, "anthropic", provider.getAnthropicBaseUrl());
        }
        return urls;
    }

    private static void put(Map<String, String> urls, String format, String url) {
        if (url != null && !url.isBlank()) urls.put(format, url.trim());
    }
}
