package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class GenerateDto {
    private String prompt;
    private String qualityCode;
    private String size;
}
