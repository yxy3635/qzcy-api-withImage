package com.qzcy.backend.service;

import com.qzcy.backend.dto.RelayReportDto;
import com.qzcy.backend.dto.RelayReportDto.*;
import com.qzcy.backend.entity.*;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.*;
import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RelayReportService {
    private final RelayReportMapper mapper;
    private final RelayModelMapper models;
    private final RelayGroupMapper groups;
    private final RelayChannelMapper channels;
    private final RelayChannelProviderMapper providers;
    private final RelayReportCache cache;
    private Clock clock = Clock.systemDefaultZone();
    private Snapshot localSnapshot;

    // Coalesce cache misses within this instance, including during Redis outages.
    public synchronized RelayReportDto report() {
        LocalDate date = LocalDate.now(clock);
        if (fresh(localSnapshot, date)) return new RelayReportDto(localSnapshot, "local");
        String key = "imagecreater:admin:relay-report:v1:" + clock.getZone().getId() + ":" + date;
        Snapshot saved = cache.get(key);
        if (fresh(saved, date)) {
            localSnapshot = saved;
            return new RelayReportDto(saved, "redis");
        }
        String owner = UUID.randomUUID().toString();
        RelayReportCache.LockState lock = cache.acquire(key, owner);
        if (lock == RelayReportCache.LockState.BUSY) {
            // Another instance is aggregating. Do not launch another full-history scan.
            throw new BusinessException(503, "报表正在生成，请稍后重新加载");
        }
        try {
            // A peer may have completed between our first read and acquiring the lock.
            if (lock == RelayReportCache.LockState.ACQUIRED) {
                saved = cache.get(key);
                if (fresh(saved, date)) {
                    localSnapshot = saved;
                    return new RelayReportDto(saved, "redis");
                }
            }
            Snapshot snapshot = build(date);
            cache.put(key, snapshot);
            localSnapshot = snapshot;
            return new RelayReportDto(snapshot, "database");
        } finally {
            if (lock == RelayReportCache.LockState.ACQUIRED) cache.release(key, owner);
        }
    }

    private boolean fresh(Snapshot snapshot, LocalDate date) {
        return snapshot != null && date.equals(snapshot.date()) && clock.instant().isBefore(snapshot.expiresAt());
    }

    private Snapshot build(LocalDate date) {
        Instant asOf = clock.instant();
        LocalDateTime cutoff = LocalDateTime.ofInstant(asOf, clock.getZone());
        LocalDateTime start = date.atStartOfDay();
        Map<String, Map<String, Row>> dimensions = new LinkedHashMap<>();
        for (String dimension : List.of("model", "group", "channel", "provider")) {
            dimensions.put(dimension, new LinkedHashMap<>());
        }
        Map<Long, String> channelNames = new HashMap<>();
        for (RelayModel model : models.selectList(new QueryWrapper<RelayModel>()
                .select("id", "model", "display_name").orderByAsc("id"))) {
            String code = text(model.getModel(), "未记录模型");
            addRow(dimensions.get("model"), code, text(model.getDisplayName(), code), code, false);
        }
        for (RelayGroup group : groups.selectList(new QueryWrapper<RelayGroup>()
                .select("id", "code", "name"))) {
            addRow(dimensions.get("group"), "group:" + group.getCode(), text(group.getName(), group.getCode()), group.getCode(), false);
        }
        for (RelayChannel channel : channels.selectList(new QueryWrapper<RelayChannel>()
                .select("id", "name"))) {
            channelNames.put(channel.getId(), text(channel.getName(), "渠道 #" + channel.getId()));
            addRow(dimensions.get("channel"), String.valueOf(channel.getId()), channelNames.get(channel.getId()), "ID " + channel.getId(), false);
        }
        for (RelayChannelProvider provider : providers.selectList(new QueryWrapper<RelayChannelProvider>()
                .select("id", "channel_id", "name"))) {
            addRow(dimensions.get("provider"), "provider:" + provider.getId(), text(provider.getName(), "供应商 #" + provider.getId()),
                    channelNames.getOrDefault(provider.getChannelId(), "已删除渠道 #" + provider.getChannelId()) + " · ID " + provider.getId(), false);
        }
        Metrics today = new Metrics();
        Metrics total = new Metrics();
        for (Aggregate aggregate : mapper.aggregates(start, cutoff)) {
            today.add(aggregate.getToday());
            total.add(aggregate.getTotal());
            String model = aggregate.getModel();
            merge(dimensions.get("model"), model, model, model, false, aggregate);
            String groupKey = aggregate.getGroupKey();
            boolean legacy = groupKey.startsWith("legacy:");
            String groupCode = groupKey.substring(groupKey.indexOf(':') + 1);
            merge(dimensions.get("group"), groupKey, legacy ? "旧记录待确认" : groupCode, groupCode, legacy, aggregate);
            String channelId = aggregate.getChannelId() == null ? "unassigned" : String.valueOf(aggregate.getChannelId());
            String channelName = channelNames.getOrDefault(aggregate.getChannelId(), text(aggregate.getChannelName(), "未归属渠道"));
            merge(dimensions.get("channel"), channelId, channelName, "历史渠道 · " + channelId, false, aggregate);
            String providerKey = aggregate.getProviderId() == null ? "direct:" + channelId : "provider:" + aggregate.getProviderId();
            merge(dimensions.get("provider"), providerKey, text(aggregate.getProviderName(), "未归属 / 渠道直连"),
                    channelName + (aggregate.getProviderId() == null ? " · 旧记录或直连" : " · ID " + aggregate.getProviderId()), false, aggregate);
        }
        Map<String, List<Row>> result = new LinkedHashMap<>();
        dimensions.forEach((key, rows) -> result.put(key, rows.values().stream()
                .sorted(Comparator.comparingLong((Row row) -> row.today().getRequests()).reversed().thenComparing(Row::key)).toList()));
        List<TrendPoint> hourly = fillTrend(mapper.hourly(start, cutoff), date, true);
        List<TrendPoint> daily = fillTrend(mapper.daily(date.minusDays(29).atStartOfDay(), cutoff), date, false);
        return new Snapshot(date, clock.getZone().getId(), asOf, clock.instant().plus(RelayReportCache.TTL), today, total, result, hourly, daily);
    }

    private List<TrendPoint> fillTrend(List<TrendAggregate> aggregates, LocalDate date, boolean hourly) {
        Map<String, Metrics> lookup = new HashMap<>();
        for (TrendAggregate aggregate : aggregates) lookup.put(aggregate.getBucket(), aggregate.getMetrics());
        List<TrendPoint> result = new ArrayList<>();
        for (int i = 0; i < (hourly ? 24 : 30); i++) {
            String bucket = hourly ? date.atTime(i, 0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : date.minusDays(29 - i).toString();
            result.add(new TrendPoint(bucket, lookup.getOrDefault(bucket, new Metrics())));
        }
        return result;
    }

    private Row addRow(Map<String, Row> rows, String key, String name, String detail, boolean legacy) {
        return rows.computeIfAbsent(key, ignored -> new Row(key, name, detail, legacy, new Metrics(), new Metrics()));
    }

    private void merge(Map<String, Row> rows, String key, String name, String detail, boolean legacy, Aggregate aggregate) {
        Row row = addRow(rows, key, name, detail, legacy);
        row.today().add(aggregate.getToday());
        row.total().add(aggregate.getTotal());
    }

    private String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
