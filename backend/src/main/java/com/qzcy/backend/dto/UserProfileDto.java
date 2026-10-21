package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class UserProfileDto {
    private String email;
    private String oldPassword;
    private String currentEmailCode;
    private String newEmailCode;
}
