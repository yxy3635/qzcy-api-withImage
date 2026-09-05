package com.qzcy.backend.service;

import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.dto.relay.RelayDispatchResult;
import com.qzcy.backend.dto.relay.RelayStreamDispatchResult;

import java.util.Set;

public interface RelayDispatchService {
    RelayDispatchResult dispatch(RelayDispatchRequest request) throws Exception;
    RelayStreamDispatchResult dispatchStream(RelayDispatchRequest request) throws Exception;

    /**
     * 当前处于打开状态的熔断作用域（channelId:providerId，无供应商的老渠道为 channelId），
     * 供管理端仪表盘展示“熔断中”标记。
     */
    Set<String> openCircuitScopes();
}
