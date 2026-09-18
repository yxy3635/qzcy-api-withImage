package com.qzcy.backend.service;

import com.qzcy.backend.dto.RegistrationConfigDto;
import com.qzcy.backend.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationConfigService {
    private final JdbcTemplate jdbcTemplate;

    public RegistrationConfigDto current() {
        return jdbcTemplate.queryForObject(
                "SELECT registration_closed, invitation_only FROM registration_config WHERE id = 1",
                (rs, rowNum) -> new RegistrationConfigDto(rs.getBoolean("registration_closed"), rs.getBoolean("invitation_only")));
    }

    public RegistrationConfigDto update(RegistrationConfigDto dto) {
        if (dto.registrationClosed() == null || dto.invitationOnly() == null) {
            throw new BusinessException(400, "请提供完整的注册设置");
        }
        jdbcTemplate.update("UPDATE registration_config SET registration_closed = ?, invitation_only = ? WHERE id = 1",
                dto.registrationClosed(), dto.invitationOnly());
        return dto;
    }

    public void requireRegistrationOpen() {
        requireOpen(current());
    }

    public void validateRegistration(String inviteCode) {
        RegistrationConfigDto config = current();
        requireOpen(config);
        if (Boolean.TRUE.equals(config.invitationOnly()) && (inviteCode == null || inviteCode.isBlank())) {
            throw new BusinessException(400, "当前仅限邀请码注册，请填写有效邀请码");
        }
    }

    private void requireOpen(RegistrationConfigDto config) {
        if (Boolean.TRUE.equals(config.registrationClosed())) {
            throw new BusinessException(403, "当前已关闭注册，请联系管理员");
        }
    }
}
