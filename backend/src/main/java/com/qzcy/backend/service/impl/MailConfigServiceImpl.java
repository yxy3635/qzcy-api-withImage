package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.dto.MailConfigDto;
import com.qzcy.backend.dto.MailConfigUpdateDto;
import com.qzcy.backend.entity.MailConfig;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.MailConfigMapper;
import com.qzcy.backend.service.MailConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MailConfigServiceImpl implements MailConfigService {
    private final MailConfigMapper mapper;

    @Override
    public MailConfig current() {
        MailConfig config = mapper.selectOne(new LambdaQueryWrapper<MailConfig>()
                .orderByAsc(MailConfig::getId)
                .last("LIMIT 1"));
        if (config != null) {
            return config;
        }
        MailConfig created = defaultConfig();
        mapper.insert(created);
        return created;
    }

    @Override
    public MailConfigDto adminDetail() {
        return toDto(current());
    }

    @Override
    public MailConfigDto update(MailConfigUpdateDto dto) {
        MailConfig config = current();
        if (dto.getHost() != null) {
            config.setHost(dto.getHost().trim());
        }
        if (dto.getPort() != null) {
            if (dto.getPort() <= 0 || dto.getPort() > 65535) {
                throw new BusinessException(400, "SMTP端口不正确");
            }
            config.setPort(dto.getPort());
        }
        if (dto.getUsername() != null) {
            config.setUsername(dto.getUsername().trim());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            config.setPassword(dto.getPassword().trim());
        }
        if (dto.getFromAddress() != null) {
            config.setFromAddress(dto.getFromAddress().trim());
        }
        if (dto.getSslEnabled() != null) {
            config.setSslEnabled(dto.getSslEnabled());
        }
        if (dto.getStarttlsEnabled() != null) {
            config.setStarttlsEnabled(dto.getStarttlsEnabled());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getDevReturnCode() != null) {
            config.setDevReturnCode(dto.getDevReturnCode());
        }
        validate(config);
        mapper.updateById(config);
        return toDto(mapper.selectById(config.getId()));
    }

    private MailConfig defaultConfig() {
        MailConfig config = new MailConfig();
        config.setHost("");
        config.setPort(587);
        config.setUsername("");
        config.setPassword("");
        config.setFromAddress("");
        config.setSslEnabled(false);
        config.setStarttlsEnabled(true);
        config.setEnabled(false);
        config.setDevReturnCode(true);
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        return config;
    }

    private void validate(MailConfig config) {
        if (Boolean.TRUE.equals(config.getEnabled())) {
            if (isBlank(config.getHost())) {
                throw new BusinessException(400, "启用邮件前需要填写SMTP服务器");
            }
            if (isBlank(config.getUsername())) {
                throw new BusinessException(400, "启用邮件前需要填写邮箱账号");
            }
            if (isBlank(config.getPassword())) {
                throw new BusinessException(400, "启用邮件前需要填写邮箱授权码");
            }
        }
    }

    private MailConfigDto toDto(MailConfig config) {
        return new MailConfigDto(
                config.getId(),
                config.getHost(),
                config.getPort(),
                config.getUsername(),
                config.getFromAddress(),
                config.getSslEnabled(),
                config.getStarttlsEnabled(),
                config.getEnabled(),
                config.getDevReturnCode(),
                !isBlank(config.getPassword())
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
