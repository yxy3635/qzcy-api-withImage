package com.qzcy.backend.service;

import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 渠道内多供应商调度器。每种策略输出完整有序的供应商列表，
 * 交给 dispatch 的候选循环逐个尝试，因此失败转移天然按该顺序生效。
 *
 * <p>smooth_rr 的轮询状态与 least_conn 的在途计数都保存在本进程内存中，
 * 重启后从零开始不影响正确性。</p>
 */
@Component
@Slf4j
public class RelayProviderScheduler {
    public static final String STRATEGY_WEIGHTED_RANDOM = "weighted_random";
    public static final String STRATEGY_SMOOTH_RR = "smooth_rr";
    public static final String STRATEGY_LEAST_CONN = "least_conn";
    public static final String STRATEGY_PRIORITY = "priority";

    /** 平滑加权轮询状态：channelId -> (providerId -> current_weight)。管理员保存渠道时按渠道清空。 */
    private final Map<Long, Map<Long, Integer>> roundRobinState = new ConcurrentHashMap<>();
    /** 最小并发策略的在途请求计数：providerId -> 活跃请求数。 */
    private final Map<Long, AtomicInteger> activeRequests = new ConcurrentHashMap<>();

    public String normalizeStrategy(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case STRATEGY_SMOOTH_RR, STRATEGY_LEAST_CONN, STRATEGY_PRIORITY -> normalized;
            default -> STRATEGY_WEIGHTED_RANDOM;
        };
    }

    public List<RelayChannelProvider> order(RelayChannel channel, List<RelayChannelProvider> providers) {
        if (providers == null || providers.isEmpty()) return new ArrayList<>();
        if (providers.size() == 1) return new ArrayList<>(providers);
        String strategy = normalizeStrategy(channel == null ? null : channel.getScheduleStrategy());
        Long channelId = channel == null ? null : channel.getId();
        return switch (strategy) {
            case STRATEGY_PRIORITY -> priorityOrder(providers);
            case STRATEGY_SMOOTH_RR -> smoothRoundRobinOrder(channelId, providers);
            case STRATEGY_LEAST_CONN -> leastConnectionsOrder(providers);
            default -> weightedRandomOrder(providers);
        };
    }

    /** 渠道配置变更后清空该渠道的轮询状态，避免沿用已失效的供应商权重。 */
    public void resetRoundRobin(Long channelId) {
        if (channelId != null) {
            roundRobinState.remove(channelId);
        }
    }

    /** 最小并发策略：请求发起前计数 +1，返回递减用的句柄计数器。 */
    public AtomicInteger beginRequest(Long providerId) {
        AtomicInteger counter = activeRequests.computeIfAbsent(providerId, ignored -> new AtomicInteger());
        counter.incrementAndGet();
        return counter;
    }

    public void endRequest(AtomicInteger counter) {
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    int activeRequestsOf(Long providerId) {
        if (providerId == null) {
            return 0;
        }
        AtomicInteger counter = activeRequests.get(providerId);
        return counter == null ? 0 : counter.get();
    }

    /** 加权随机：供应商按 priority 分层，层内加权随机不放回洗牌（与跨渠道调度一致）。 */
    private List<RelayChannelProvider> weightedRandomOrder(List<RelayChannelProvider> providers) {
        List<RelayChannelProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparing(this::priorityOf));
        List<RelayChannelProvider> ordered = new ArrayList<>();
        int index = 0;
        while (index < sorted.size()) {
            int priority = priorityOf(sorted.get(index));
            int start = index;
            while (index < sorted.size() && priorityOf(sorted.get(index)) == priority) {
                index++;
            }
            ordered.addAll(weightedShuffle(sorted.subList(start, index)));
        }
        return ordered;
    }

    /** 平滑加权轮询（nginx 算法）：每次调用选出一个主供应商，其余按 priority/weight 排后用于失败转移。 */
    private List<RelayChannelProvider> smoothRoundRobinOrder(Long channelId, List<RelayChannelProvider> providers) {
        Map<Long, Integer> state = channelId == null
                ? new ConcurrentHashMap<>()
                : roundRobinState.computeIfAbsent(channelId, ignored -> new ConcurrentHashMap<>());
        state.keySet().removeIf(id -> providers.stream().noneMatch(item -> Objects.equals(item.getId(), id)));

        int totalWeight = 0;
        RelayChannelProvider selected = null;
        int selectedCurrent = Integer.MIN_VALUE;
        for (RelayChannelProvider provider : providers) {
            int weight = Math.max(0, weightOf(provider));
            totalWeight += weight;
            // 无 id 的兜底供应商不参与状态累计，按当前权重直接比较。
            int current = provider.getId() == null
                    ? weight
                    : state.merge(provider.getId(), weight, Integer::sum);
            if (current > selectedCurrent) {
                selectedCurrent = current;
                selected = provider;
            }
        }
        if (selected == null) return priorityOrder(providers);
        if (selected.getId() != null && totalWeight > 0) {
            state.merge(selected.getId(), -totalWeight, Integer::sum);
        }
        List<RelayChannelProvider> rest = new ArrayList<>(providers);
        rest.remove(selected);
        List<RelayChannelProvider> ordered = new ArrayList<>(providers.size());
        ordered.add(selected);
        ordered.addAll(priorityOrder(rest));
        return ordered;
    }

    /** 最小并发：按在途请求数升序，相同并发的做加权随机洗牌。 */
    private List<RelayChannelProvider> leastConnectionsOrder(List<RelayChannelProvider> providers) {
        List<RelayChannelProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator
                .comparingInt((RelayChannelProvider provider) -> activeRequestsOf(provider.getId()))
                .thenComparing(this::priorityOf));
        List<RelayChannelProvider> ordered = new ArrayList<>();
        int index = 0;
        while (index < sorted.size()) {
            int key = activeRequestsOf(sorted.get(index).getId());
            int start = index;
            while (index < sorted.size() && activeRequestsOf(sorted.get(index).getId()) == key) {
                index++;
            }
            ordered.addAll(weightedShuffle(sorted.subList(start, index)));
        }
        return ordered;
    }

    /** 严格优先级故障转移：固定顺序，永远先打第一个，失败才下一个。 */
    private List<RelayChannelProvider> priorityOrder(List<RelayChannelProvider> providers) {
        List<RelayChannelProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator
                .comparing(this::priorityOf)
                .thenComparing(Comparator.comparing(this::weightOf).reversed())
                .thenComparing(provider -> provider.getId() == null ? Long.MAX_VALUE : provider.getId()));
        return sorted;
    }

    /** 加权随机不放回洗牌；全零权重时退化为按列表顺序。 */
    private List<RelayChannelProvider> weightedShuffle(List<RelayChannelProvider> providers) {
        List<RelayChannelProvider> remaining = new ArrayList<>(providers);
        List<RelayChannelProvider> ordered = new ArrayList<>(providers.size());
        while (!remaining.isEmpty()) {
            int totalWeight = remaining.stream().mapToInt(this::weightOf).sum();
            RelayChannelProvider picked;
            if (totalWeight <= 0) {
                picked = remaining.get(0);
            } else {
                int value = ThreadLocalRandom.current().nextInt(totalWeight);
                int cursor = 0;
                picked = remaining.get(remaining.size() - 1);
                for (RelayChannelProvider provider : remaining) {
                    cursor += weightOf(provider);
                    if (value < cursor) {
                        picked = provider;
                        break;
                    }
                }
            }
            ordered.add(picked);
            remaining.remove(picked);
        }
        return ordered;
    }

    private int priorityOf(RelayChannelProvider provider) {
        return provider.getPriority() == null ? 0 : provider.getPriority();
    }

    private int weightOf(RelayChannelProvider provider) {
        return Math.max(0, provider.getWeight() == null ? 0 : provider.getWeight());
    }
}
