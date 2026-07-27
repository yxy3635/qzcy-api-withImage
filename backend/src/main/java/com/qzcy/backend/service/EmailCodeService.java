package com.qzcy.backend.service;

import java.util.Map;

public interface EmailCodeService {
    default Map<String, Object> sendCode(String email, String scene) {
        return sendCode(email, scene, "unknown");
    }
    Map<String, Object> sendCode(String email, String scene, String clientIp);
    void verify(String email, String scene, String code);
}
