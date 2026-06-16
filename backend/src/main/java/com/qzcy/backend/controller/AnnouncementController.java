package com.qzcy.backend.controller;

import com.qzcy.backend.dto.AnnouncementDto;
import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService announcementService;

    @GetMapping
    public ApiResponse<List<AnnouncementDto>> list() {
        return ApiResponse.success(announcementService.publicList());
    }
}
