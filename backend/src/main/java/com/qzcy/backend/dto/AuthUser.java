package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class AuthUser {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Boolean banned;
    private BigDecimal balance;
}
