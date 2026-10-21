package com.qzcy.backend.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayGroupMapper;

public final class RelayTokenGroupPolicy {
    private RelayTokenGroupPolicy() {}

    public static String requireSingleEnabledGroup(String input, RelayGroupMapper groups) {
        String code = input == null ? "default" : input.trim();
        if (code.isEmpty() || code.length() > 80 || code.contains(",") || code.contains("*")
                || code.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(400, "API密钥必须选择一个有效分组，不支持通配符或多个分组");
        }
        RelayGroup group = groups.selectOne(new QueryWrapper<RelayGroup>().eq("code", code).eq("enabled", true));
        if (group == null || !Boolean.TRUE.equals(group.getEnabled())) {
            throw new BusinessException(400, "分组不存在或已停用，请重新选择");
        }
        return group.getCode();
    }
}
