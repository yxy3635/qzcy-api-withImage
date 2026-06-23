package com.qzcy.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class GenerateDto {
    private String prompt;
    private String qualityCode;
    private String size;
    private List<String> referenceImages;
}
