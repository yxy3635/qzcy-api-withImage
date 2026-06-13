package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qzcy.backend.dto.ImageGenerationConfigDto;
import com.qzcy.backend.dto.ImageGenerationConfigUpdateDto;
import com.qzcy.backend.entity.ImageGenerationConfig;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.ImageGenerationConfigMapper;
import com.qzcy.backend.service.ImageGenerationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageGenerationConfigServiceImpl implements ImageGenerationConfigService {
    private final ImageGenerationConfigMapper mapper;

    @Override
    public List<ImageGenerationConfigDto> adminList() {
        return mapper.selectList(orderWrapper()).stream().map(this::toDto).toList();
    }

    @Override
    public List<ImageGenerationConfigDto> publicList() {
        return mapper.selectList(orderWrapper().eq(ImageGenerationConfig::getEnabled, true))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ImageGenerationConfigDto update(Long id, ImageGenerationConfigUpdateDto dto) {
        ImageGenerationConfig config = mapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "生图配置不存在");
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            config.setName(dto.getName().trim());
        }
        if (dto.getModel() != null && !dto.getModel().isBlank()) {
            config.setModel(dto.getModel().trim());
        }
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            config.setApiKey(dto.getApiKey().trim());
        }
        if (dto.getApiBaseUrl() != null && !dto.getApiBaseUrl().isBlank()) {
            config.setApiBaseUrl(normalizeApiBaseUrl(dto.getApiBaseUrl()));
        }
        if (dto.getEndpointPath() != null && !dto.getEndpointPath().isBlank()) {
            config.setEndpointPath(normalizeEndpointPath(dto.getEndpointPath()));
        }
        if (dto.getSize() != null && !dto.getSize().isBlank()) {
            String size = dto.getSize().trim();
            if (!size.matches("^\\d{3,5}x\\d{3,5}$")) {
                throw new BusinessException(400, "尺寸格式应为 1024x1024");
            }
            config.setSize(size);
        }
        if (dto.getQuality() != null && !dto.getQuality().isBlank()) {
            config.setQuality(dto.getQuality().trim());
        }
        if (dto.getPrice() != null) {
            if (dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(400, "价格不能小于0");
            }
            config.setPrice(dto.getPrice());
        }
        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            config.setSortOrder(dto.getSortOrder());
        }
        mapper.updateById(config);
        return toDto(mapper.selectById(id));
    }

    @Override
    public ImageGenerationConfig requireEnabled(String code) {
        String normalized = code == null || code.isBlank() ? "1k" : code.trim().toLowerCase();
        ImageGenerationConfig config = mapper.selectOne(new LambdaQueryWrapper<ImageGenerationConfig>()
                .eq(ImageGenerationConfig::getCode, normalized)
                .eq(ImageGenerationConfig::getEnabled, true));
        if (config == null) {
            throw new BusinessException(400, "所选图像规格不可用");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BusinessException(500, "当前图像规格尚未配置OpenAI API Key");
        }
        return config;
    }

    private LambdaQueryWrapper<ImageGenerationConfig> orderWrapper() {
        return new LambdaQueryWrapper<ImageGenerationConfig>()
                .orderByAsc(ImageGenerationConfig::getSortOrder)
                .orderByAsc(ImageGenerationConfig::getId);
    }

    private ImageGenerationConfigDto toDto(ImageGenerationConfig config) {
        return new ImageGenerationConfigDto(
                config.getId(),
                config.getCode(),
                config.getName(),
                config.getModel(),
                mask(config.getApiKey()),
                config.getApiBaseUrl(),
                config.getEndpointPath(),
                config.getSize(),
                config.getQuality(),
                config.getPrice(),
                config.getEnabled(),
                config.getSortOrder()
        );
    }

    private String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 10) {
            return "已配置";
        }
        return apiKey.substring(0, 6) + "..." + apiKey.substring(apiKey.length() - 4);
    }

    private String normalizeEndpointPath(String endpointPath) {
        String normalized = endpointPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (!"/v1/images/generations".equals(normalized) && !"/v1/responses".equals(normalized)) {
            throw new BusinessException(400, "图像路径只支持 /v1/images/generations 或 /v1/responses");
        }
        return normalized;
    }

    private String normalizeApiBaseUrl(String apiBaseUrl) {
        String normalized = apiBaseUrl.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BusinessException(400, "API地址必须以 http:// 或 https:// 开头");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
