package com.qzcy.backend.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record RelayReportDto(Snapshot snapshot, String cacheSource) {
    public record Snapshot(@JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate date, String timezone,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant generatedAt,
                           @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
                           Metrics today, Metrics total, Map<String, List<Row>> dimensions,
                           List<TrendPoint> hourly, List<TrendPoint> daily) {}
    public record Row(String key, String name, String detail, boolean legacy, Metrics today, Metrics total) {}
    public record TrendPoint(String bucket, Metrics metrics) {}

    @Data
    public static class Metrics {
        private long requests;
        private long errors;
        private long tokens;
        private long inputTokens;
        private long outputTokens;
        private long cacheReadTokens;
        private long cacheCreationTokens;
        private long cacheCreation1hTokens;
        private BigDecimal cost = BigDecimal.ZERO;

        public void add(Metrics other) {
            requests += other.requests;
            errors += other.errors;
            tokens += other.tokens;
            inputTokens += other.inputTokens;
            outputTokens += other.outputTokens;
            cacheReadTokens += other.cacheReadTokens;
            cacheCreationTokens += other.cacheCreationTokens;
            cacheCreation1hTokens += other.cacheCreation1hTokens;
            cost = cost.add(other.cost);
        }
    }

    @Data
    public static class Aggregate {
        private String model;
        private String groupKey;
        private Long channelId;
        private String channelName;
        private Long providerId;
        private String providerName;
        private Metrics today = new Metrics();
        private Metrics total = new Metrics();
    }

    @Data
    public static class TrendAggregate {
        private String bucket;
        private Metrics metrics = new Metrics();
    }
}
