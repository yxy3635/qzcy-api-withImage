package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.entity.RelayUsageLog;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayPolicyService;
import com.qzcy.backend.service.RelayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RelayTokenDeletionIntegrationTest {
    @Autowired RelayService service;
    @Autowired RelayPolicyService policy;
    @Autowired RelayTokenMapper tokens;
    @Autowired RelayUsageLogMapper logs;
    @Autowired UserMapper users;

    @Test
    void deletionPreservesUserAndAdminHistoryAndRevokesAccess() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername("deletion-" + unique);
        user.setEmail(unique + "@example.invalid");
        user.setPassword("test-only");
        user.setRole("user");
        user.setBalance(BigDecimal.TEN);
        users.insert(user);
        RelayToken token = new RelayToken();
        token.setUserId(user.getId());
        token.setName("test-key");
        token.setToken("sk-test-" + unique);
        token.setTokenPreview("sk-test-...");
        token.setEnabled(true);
        token.setUsedQuota(new BigDecimal("9.25"));
        token.setRequestCount(120L);
        token.setTokenCount(3000L);
        tokens.insert(token);
        RelayUsageLog log = new RelayUsageLog();
        log.setUserId(user.getId());
        log.setTokenId(token.getId());
        log.setTokenName(token.getName());
        log.setStatus("success");
        log.setTotalTokens(100);
        log.setPromptTokens(80);
        log.setCompletionTokens(20);
        log.setCost(BigDecimal.ONE);
        log.setCreatedAt(LocalDateTime.now());
        logs.insert(log);
        var before = service.userOverviewSection(user.getId(), "keys", 1, 20, null, null, null);
        var adminBefore = logs.adminUserUsage(Page.of(1, 10), user.getUsername()).getRecords().get(0);
        var totalCostBefore = logs.totalCost();
        var totalTokensBefore = logs.totalTokens();
        assertEquals(token.getId(), policy.requireRelayToken("Bearer " + token.getToken()).getId());

        service.deleteToken(user.getId(), token.getId());

        assertNull(tokens.selectById(token.getId()));
        assertTrue(tokens.selectList(new QueryWrapper<RelayToken>().eq("user_id", user.getId())).isEmpty());
        assertThrows(BusinessException.class, () -> policy.requireRelayToken("Bearer " + token.getToken()));
        assertThrows(BusinessException.class, () -> service.revealToken(user.getId(), token.getId()));
        var after = service.userOverviewSection(user.getId(), "keys", 1, 20, null, null, null);
        assertTrue(after.getTokens().isEmpty());
        assertEquals(before.getTotalCost(), after.getTotalCost());
        assertEquals(before.getTotalTokens(), after.getTotalTokens());
        assertEquals(before.getTotalRequests(), after.getTotalRequests());
        assertEquals(before.getTotalPromptTokens(), after.getTotalPromptTokens());
        assertEquals(before.getTotalCompletionTokens(), after.getTotalCompletionTokens());
        assertNotNull(logs.selectById(log.getId()));
        var adminAfter = logs.adminUserUsage(Page.of(1, 10), user.getUsername()).getRecords().get(0);
        assertEquals(adminBefore.getTotalCost(), adminAfter.getTotalCost());
        assertEquals(adminBefore.getTotalTokens(), adminAfter.getTotalTokens());
        assertEquals(totalCostBefore, logs.totalCost());
        assertEquals(totalTokensBefore, logs.totalTokens());

        // A request admitted before revocation may still complete and must be counted.
        tokens.incrementUsage(token.getId(), 1, 50, new BigDecimal("0.5"), LocalDateTime.now(), LocalDateTime.now());
        assertEquals(3050L, tokens.lifetimeUsage(user.getId()).getTokenCount());
        assertEquals(0, new BigDecimal("9.75").compareTo(tokens.lifetimeUsage(user.getId()).getUsedQuota()));
        assertNull(tokens.selectById(token.getId()));
    }
}
