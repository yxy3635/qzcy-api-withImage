package com.qzcy.backend.controller;

import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.RelayReportDto;
import com.qzcy.backend.service.RelayReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/relay/reports")
@RequiredArgsConstructor
public class RelayReportController {
    private final RelayReportService reports;

    @GetMapping
    public ResponseEntity<ApiResponse<RelayReportDto>> report() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(reports.report()));
    }
}
