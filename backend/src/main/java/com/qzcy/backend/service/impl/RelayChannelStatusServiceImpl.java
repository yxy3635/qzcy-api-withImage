package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelProvider;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.service.RelayChannelStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 健康探测粒度为渠道内供应商：每个启用的供应商单独探测并写回自身 status；
 * 渠道 status 聚合——任一供应商可用即 available，全部不可用（或无可用供应商）才 failed，
 * 与调度侧“failed 供应商被剔除、failed 渠道整体剔除”的规则对应。
 */
@Service
@RequiredArgsConstructor
public class RelayChannelStatusServiceImpl implements RelayChannelStatusService {
    private final RelayChannelMapper channelMapper;
    private final RelayChannelProviderMapper providerMapper;

    @Scheduled(fixedDelay = 600_000, initialDelay = 30_000)
    public void scheduledSync() {
        syncAll();
    }

    @Override
    public void syncAll() {
        List<RelayChannel> channels = channelMapper.selectList(new QueryWrapper<RelayChannel>().eq("enabled", true));
        channels.forEach(channel -> {
            try {
                syncOne(channel.getId());
            } catch (Exception ignored) {
                // Keep scheduled status checks best-effort.
            }
        });
    }

    @Override
    public String syncOne(Long channelId) {
        RelayChannel channel = channelMapper.selectById(channelId);
        if (channel == null) return "unknown";
        boolean anyAvailable = false;
        boolean anyChecked = false;
        List<RelayChannelProvider> providers = providerMapper.selectByChannelId(channelId);
        for (RelayChannelProvider provider : providers) {
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                continue;
            }
            boolean available = check(provider.getApiBaseUrl(), provider.getApiKey(), provider.getChannelRule());
            provider.setStatus(available ? "available" : "failed");
            provider.setUpdatedAt(java.time.LocalDateTime.now());
            providerMapper.updateById(provider);
            anyChecked = true;
            anyAvailable |= available;
        }
        if (!anyChecked) {
            if (providers.isEmpty()) {
                // 无供应商记录的老渠道：回退探测渠道自身凭证。
                anyAvailable = check(channel.getApiBaseUrl(), channel.getApiKey(), channel.getChannelRule());
            } else {
                // 有供应商记录但全部停用：渠道当前无法承载流量。
                anyAvailable = false;
            }
        }
        channel.setStatus(anyAvailable ? "available" : "failed");
        channel.setUpdatedAt(java.time.LocalDateTime.now());
        channelMapper.updateById(channel);
        return channel.getStatus();
    }

    private boolean check(String apiBaseUrl, String apiKey, String channelRule) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) return false;
        if (apiKey == null || apiKey.isBlank()) return false;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(relayUrl(apiBaseUrl, "/v1/models")))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET();
            applyAuthHeaders(builder, apiKey, channelRule);
            HttpRequest request = builder.build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    private String relayUrl(String apiBaseUrl, String path) {
        String baseUrl = apiBaseUrl.trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        if (baseUrl.endsWith("/v1") && path.startsWith("/v1/")) path = path.substring(3);
        return baseUrl + path;
    }

    private void applyAuthHeaders(HttpRequest.Builder builder, String apiKey, String channelRule) {
        if ("anthropic".equalsIgnoreCase(channelRule)) {
            builder.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
            return;
        }
        builder.header("Authorization", "Bearer " + apiKey);
    }
}
