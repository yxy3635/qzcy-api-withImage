package com.qzcy.backend.service;

import com.qzcy.backend.entity.RelayChannelProvider;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class RelayProviderModels {
    private RelayProviderModels() {}
    // NULL inherits all channel models; an empty selection supports no models.
    public static boolean supports(RelayChannelProvider provider, Long modelId) {
        if (provider.getModelIds() == null) return true;
        if (modelId == null) return false;
        return Arrays.stream(provider.getModelIds().split(",")).anyMatch(id -> id.trim().equals(modelId.toString()));
    }
    public static List<Long> decode(String value) {
        if (value == null) return null;
        if (value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).map(Long::valueOf).distinct().toList();
    }
    public static String encode(List<Long> ids) {
        return ids == null ? null : ids.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
    }
}
