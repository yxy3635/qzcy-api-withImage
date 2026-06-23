package com.qzcy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ImageEstimateDto;
import com.qzcy.backend.entity.ImageRecord;

import java.util.List;

public interface ImageService {
    ImageRecord submit(Long userId, String username, String prompt, String qualityCode, String size, List<String> referenceImages);
    ImageRecord detail(Long userId, Long imageRecordId);
    Page<ImageRecord> history(Long userId, long page, long size);
    ImageEstimateDto estimate();
    void delete(Long userId, Long imageRecordId);
}
