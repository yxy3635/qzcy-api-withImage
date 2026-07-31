package com.qzcy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserRankingsDto {
    private List<AdminUserRankingDto> recharge;
    private List<AdminUserRankingDto> tokens;
}
