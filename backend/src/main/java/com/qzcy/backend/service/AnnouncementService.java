package com.qzcy.backend.service;

import com.qzcy.backend.dto.AnnouncementDto;
import com.qzcy.backend.dto.AnnouncementUpdateDto;

import java.util.List;

public interface AnnouncementService {
    List<AnnouncementDto> publicList();
    List<AnnouncementDto> adminList();
    AnnouncementDto create(AnnouncementUpdateDto dto);
    AnnouncementDto update(Long id, AnnouncementUpdateDto dto);
    void delete(Long id);
}
