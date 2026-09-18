package com.qzcy.backend.controller;

import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.RegistrationConfigDto;
import com.qzcy.backend.service.RegistrationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegistrationConfigController {
    private final RegistrationConfigService service;

    @GetMapping({"/api/auth/registration-config", "/api/admin/registration-config"})
    public ApiResponse<RegistrationConfigDto> detail() {
        return ApiResponse.success(service.current());
    }

    @PutMapping("/api/admin/registration-config")
    public ApiResponse<RegistrationConfigDto> update(@RequestBody RegistrationConfigDto dto) {
        return ApiResponse.success(service.update(dto));
    }
}
