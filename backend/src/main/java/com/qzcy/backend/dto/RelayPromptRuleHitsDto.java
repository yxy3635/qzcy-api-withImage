package com.qzcy.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RelayPromptRuleHitsDto(long total, long current, long pages, long hitCount, long userCount, List<UserHit> records) {
    public record UserHit(long userId, String username, long hitCount, LocalDateTime firstHitAt,
                          LocalDateTime lastHitAt, String endpoint, String model, String requestSummary, boolean banned) {}
}
