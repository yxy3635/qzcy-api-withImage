package com.qzcy.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class RelayPromptPolicyError {
    public static final String MESSAGE = "当前触发系统安全策略限制，请立刻停止当前行为。当前账号已被标记观察，如果继续会自动封禁该账号。如有误判，请联系管理员。";

    private RelayPromptPolicyError() {}

    public static ObjectNode body(ObjectMapper mapper, String path) {
        ObjectNode body = mapper.createObjectNode();
        if (path != null && path.startsWith("/v1/messages")) body.put("type", "error");
        body.putObject("error").put("message", MESSAGE).put("type", "invalid_request_error")
                .put("code", "security_policy_violation").put("status", 400);
        return body;
    }
}
