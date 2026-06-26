package com.qzcy.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminUserUpdateDto {
    private String email;
    private String role;
    private Boolean banned;
    private BigDecimal balance;
    private String password;
}
