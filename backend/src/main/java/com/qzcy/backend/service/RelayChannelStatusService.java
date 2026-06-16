package com.qzcy.backend.service;

public interface RelayChannelStatusService {
    void syncAll();
    void syncOne(Long channelId);
}
