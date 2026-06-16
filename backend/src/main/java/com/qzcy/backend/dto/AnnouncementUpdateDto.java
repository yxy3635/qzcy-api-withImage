package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class AnnouncementUpdateDto {
    private String title;
    private String content;
    private Boolean enabled;
    private Boolean pinned;
    private Integer sortOrder;
}
