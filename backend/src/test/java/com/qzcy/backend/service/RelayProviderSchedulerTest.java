package com.qzcy.backend.service;

import com.qzcy.backend.entity.RelayChannel;
import com.qzcy.backend.entity.RelayChannelProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayProviderSchedulerTest {
    private final RelayProviderScheduler scheduler = new RelayProviderScheduler();

    private RelayChannelProvider provider(long id, String name, int priority, int weight) {
        RelayChannelProvider item = new RelayChannelProvider();
        item.setId(id);
        item.setChannelId(1L);
        item.setName(name);
        item.setApiBaseUrl("https://p" + id + ".example.com");
        item.setApiKey("sk-" + id);
        item.setChannelRule("openai");
        item.setPriority(priority);
        item.setWeight(weight);
        item.setStatus("available");
        item.setEnabled(true);
        return item;
    }

    private RelayChannel channel(String strategy) {
        RelayChannel channel = new RelayChannel();
        channel.setId(1L);
        channel.setScheduleStrategy(strategy);
        return channel;
    }

    @Test
    void unknownStrategyFallsBackToWeightedRandom() {
        assertEquals(RelayProviderScheduler.STRATEGY_WEIGHTED_RANDOM, scheduler.normalizeStrategy(null));
        assertEquals(RelayProviderScheduler.STRATEGY_WEIGHTED_RANDOM, scheduler.normalizeStrategy("bogus"));
        assertEquals(RelayProviderScheduler.STRATEGY_SMOOTH_RR, scheduler.normalizeStrategy("Smooth_RR"));
        assertEquals(RelayProviderScheduler.STRATEGY_PRIORITY, scheduler.normalizeStrategy("priority"));
        assertEquals(RelayProviderScheduler.STRATEGY_LEAST_CONN, scheduler.normalizeStrategy("least_conn"));
    }

    @Test
    void weightedRandomKeepsHigherPrecedencePriorityTierFirst() {
        List<RelayChannelProvider> providers = List.of(
                provider(1, "backup", 20, 100),
                provider(2, "primary", 0, 1));
        for (int round = 0; round < 20; round++) {
            List<RelayChannelProvider> ordered = scheduler.order(channel("weighted_random"), providers);
            assertEquals(2L, ordered.get(0).getId());
            assertEquals(1L, ordered.get(1).getId());
        }
    }

    @Test
    void weightedRandomDistributesByWeightWithinTier() {
        List<RelayChannelProvider> providers = List.of(
                provider(1, "light", 0, 1),
                provider(2, "heavy", 0, 9));
        Map<Long, Integer> firstCounts = new HashMap<>();
        for (int round = 0; round < 1000; round++) {
            List<RelayChannelProvider> ordered = scheduler.order(channel("weighted_random"), providers);
            firstCounts.merge(ordered.get(0).getId(), 1, Integer::sum);
        }
        // 1:9 权重，1000 次里 heavy 应该明显占多数且比例接近 9:1。
        assertTrue(firstCounts.get(2L) > 800, "heavy should lead most rounds: " + firstCounts);
        assertTrue(firstCounts.get(1L) > 0, "light should still get some traffic: " + firstCounts);
    }

    @Test
    void smoothRoundRobinDistributesByWeight() {
        List<RelayChannelProvider> providers = List.of(
                provider(1, "a", 0, 1),
                provider(2, "b", 0, 3));
        Map<Long, Integer> firstCounts = new HashMap<>();
        for (int round = 0; round < 40; round++) {
            List<RelayChannelProvider> ordered = scheduler.order(channel("smooth_rr"), providers);
            firstCounts.merge(ordered.get(0).getId(), 1, Integer::sum);
        }
        assertEquals(10, firstCounts.get(1L));
        assertEquals(30, firstCounts.get(2L));
    }

    @Test
    void smoothRoundRobinResetRestartsSequence() {
        List<RelayChannelProvider> providers = List.of(
                provider(1, "a", 0, 1),
                provider(2, "b", 0, 3));
        // 未重置时 8 次的序列应严格按 1:3 平滑轮询。
        List<Long> sequence = new ArrayList<>();
        for (int round = 0; round < 8; round++) {
            sequence.add(scheduler.order(channel("smooth_rr"), providers).get(0).getId());
        }
        assertEquals(List.of(2L, 1L, 2L, 2L, 2L, 1L, 2L, 2L), sequence);
        scheduler.resetRoundRobin(1L);
        List<Long> restarted = new ArrayList<>();
        for (int round = 0; round < 4; round++) {
            restarted.add(scheduler.order(channel("smooth_rr"), providers).get(0).getId());
        }
        assertEquals(List.of(2L, 1L, 2L, 2L), restarted);
    }

    @Test
    void leastConnPrefersIdleProvider() {
        // idle 权重为 0、busy 权重非 0：忙碌时 idle 先（并发优先），空闲恢复后 busy 先（同并发组内零权重不参与随机）。
        List<RelayChannelProvider> providers = List.of(
                provider(1, "busy", 0, 10),
                provider(2, "idle", 0, 0));
        java.util.concurrent.atomic.AtomicInteger busyCount = scheduler.beginRequest(1L);
        assertEquals(2L, scheduler.order(channel("least_conn"), providers).get(0).getId());
        scheduler.endRequest(busyCount);
        assertEquals(1L, scheduler.order(channel("least_conn"), providers).get(0).getId());
        assertEquals(0, busyCount.get());
    }

    @Test
    void priorityStrategyIsStableAndDeterministic() {
        List<RelayChannelProvider> providers = List.of(
                provider(3, "c", 0, 1),
                provider(1, "a", 10, 100),
                provider(2, "b", 0, 50));
        // priority 0 层内 weight 降序（b=50 在 c=1 前），再是 priority 10 的 a。
        List<Long> expected = List.of(2L, 3L, 1L);
        for (int round = 0; round < 10; round++) {
            List<Long> ids = scheduler.order(channel("priority"), providers)
                    .stream().map(RelayChannelProvider::getId).toList();
            assertEquals(expected, ids);
        }
    }
}
