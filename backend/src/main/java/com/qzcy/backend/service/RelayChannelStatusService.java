package com.qzcy.backend.service;

public interface RelayChannelStatusService {
    void syncAll();
    String syncOne(Long channelId);
}
