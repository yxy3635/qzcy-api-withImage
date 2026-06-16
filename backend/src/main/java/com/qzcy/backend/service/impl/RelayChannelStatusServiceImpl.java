package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.mapper.RelayChannelMapper;
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

@Service
@RequiredArgsConstructor
public class RelayChannelStatusServiceImpl implements RelayChannelStatusService {
    private final RelayChannelMapper channelMapper;

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
    public void syncOne(Long channelId) {
        RelayChannel channel = channelMapper.selectById(channelId);
        if (channel == null) return;
        channel.setStatus(check(channel) ? "available" : "failed");
        channelMapper.updateById(channel);
    }

    private boolean check(RelayChannel channel) {
        if (channel.getApiBaseUrl() == null || channel.getApiBaseUrl().isBlank()) return false;
        if (channel.getApiKey() == null || channel.getApiKey().isBlank()) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(relayUrl(channel.getApiBaseUrl(), "/v1/models")))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + channel.getApiKey())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
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
}
