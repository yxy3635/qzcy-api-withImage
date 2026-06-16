package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qzcy.backend.dto.AnnouncementDto;
import com.qzcy.backend.dto.AnnouncementUpdateDto;
import com.qzcy.backend.entity.Announcement;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.AnnouncementMapper;
import com.qzcy.backend.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {
    private final AnnouncementMapper announcementMapper;

    @Override
    public List<AnnouncementDto> publicList() {
        return announcementMapper.selectList(new QueryWrapper<Announcement>()
                        .eq("enabled", true)
                        .orderByDesc("pinned")
                        .orderByAsc("sort_order")
                        .orderByDesc("published_at")
                        .orderByDesc("id")
                        .last("LIMIT 10"))
                .stream().map(this::toDto).toList();
    }

    @Override
    public List<AnnouncementDto> adminList() {
        return announcementMapper.selectList(new QueryWrapper<Announcement>()
                        .orderByDesc("pinned")
                        .orderByAsc("sort_order")
                        .orderByDesc("published_at")
                        .orderByDesc("id"))
                .stream().map(this::toDto).toList();
    }

    @Override
    public AnnouncementDto create(AnnouncementUpdateDto dto) {
        Announcement item = new Announcement();
        apply(item, dto);
        if (item.getEnabled() == null) item.setEnabled(true);
        if (item.getPinned() == null) item.setPinned(false);
        if (item.getSortOrder() == null) item.setSortOrder(10);
        if (item.getPublishedAt() == null) item.setPublishedAt(LocalDateTime.now());
        announcementMapper.insert(item);
        return toDto(announcementMapper.selectById(item.getId()));
    }

    @Override
    public AnnouncementDto update(Long id, AnnouncementUpdateDto dto) {
        Announcement item = announcementMapper.selectById(id);
        if (item == null) throw new BusinessException(404, "Announcement not found");
        apply(item, dto);
        announcementMapper.updateById(item);
        return toDto(announcementMapper.selectById(id));
    }

    @Override
    public void delete(Long id) {
        announcementMapper.deleteById(id);
    }

    private void apply(Announcement item, AnnouncementUpdateDto dto) {
        if (dto.getTitle() != null) item.setTitle(dto.getTitle().trim());
        if (dto.getContent() != null) item.setContent(dto.getContent().trim());
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new BusinessException(400, "公告标题不能为空");
        }
        if (item.getContent() == null || item.getContent().isBlank()) {
            throw new BusinessException(400, "公告内容不能为空");
        }
        if (dto.getEnabled() != null) item.setEnabled(dto.getEnabled());
        if (dto.getPinned() != null) item.setPinned(dto.getPinned());
        if (dto.getSortOrder() != null) item.setSortOrder(dto.getSortOrder());
        if (Boolean.TRUE.equals(item.getEnabled()) && item.getPublishedAt() == null) {
            item.setPublishedAt(LocalDateTime.now());
        }
    }

    private AnnouncementDto toDto(Announcement item) {
        return new AnnouncementDto(item.getId(), item.getTitle(), item.getContent(), item.getEnabled(),
                item.getPinned(), item.getSortOrder(), item.getPublishedAt(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
