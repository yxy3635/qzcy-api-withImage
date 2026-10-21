package com.qzcy.backend.util;

import com.qzcy.backend.exception.BusinessException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void requireNewPassword(String value) {
        if (value == null || value.length() < 10 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(400, "新密码至少10位，且UTF-8编码不能超过72字节");
        }
    }
}
