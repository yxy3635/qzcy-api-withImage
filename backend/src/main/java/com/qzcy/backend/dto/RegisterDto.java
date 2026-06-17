package com.qzcy.backend.dto;

import lombok.Data;

@Data
public class RegisterDto {
    private String username;
    private String email;
    private String password;
    private String code;
    private String inviteCode;
}
