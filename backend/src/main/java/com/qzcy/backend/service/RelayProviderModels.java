package com.qzcy.backend.service;

import com.qzcy.backend.entity.RelayChannelProvider;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class RelayProviderModels {
    private RelayProviderModels() {}
    // NULL inherits all channel models; an empty selection supports no models.
    public static boolean supports(RelayChannelProvider provider, Long modelId) {
        String selectedModels = selectedModels(provider);
        if (selectedModels == null) return true;
        if (modelId == null) return false;
        return Arrays.stream(selectedModels.split(",")).anyMatch(id -> id.trim().equals(modelId.toString()));
    }

    /** Partial deployments may still use the entity from before per-provider model selection. */
    private static String selectedModels(RelayChannelProvider provider) {
        try {
            return (String) provider.getClass().getMethod("getModelIds").invoke(provider);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot read provider model selection", ex);
        }
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
