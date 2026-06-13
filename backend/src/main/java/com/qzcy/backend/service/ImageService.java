package com.qzcy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ImageEstimateDto;
import com.qzcy.backend.entity.ImageRecord;

public interface ImageService {
    ImageRecord submit(Long userId, String username, String prompt, String qualityCode, String size);
    ImageRecord detail(Long userId, Long imageRecordId);
    Page<ImageRecord> history(Long userId, long page, long size);
    ImageEstimateDto estimate();
    void delete(Long userId, Long imageRecordId);
}
