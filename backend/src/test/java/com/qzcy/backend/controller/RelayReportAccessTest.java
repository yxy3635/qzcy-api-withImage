package com.qzcy.backend.controller;

import com.qzcy.backend.config.security.JwtAuthenticationFilter;
import com.qzcy.backend.config.security.SecurityConfig;
import com.qzcy.backend.dto.RelayReportDto;
import com.qzcy.backend.dto.RelayReportDto.*;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayReportService;
import com.qzcy.backend.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RelayReportAccessTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private RelayReportService reports;

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class TestConfig {
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean UserMapper users() { return mock(UserMapper.class); }
        @Bean RelayReportService reports() { return mock(RelayReportService.class); }
        @Bean JwtAuthenticationFilter jwtFilter(JwtUtil jwt, UserMapper users) { return new JwtAuthenticationFilter(jwt, users); }
        @Bean RelayReportController controller(RelayReportService reports) { return new RelayReportController(reports); }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("report-test",
                Map.of("app.jwt.secret", "report-test-secret-key-for-tests-only-32", "app.jwt.expiration-ms", "86400000")));
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        reports = context.getBean(RelayReportService.class);
        var jwt = context.getBean(JwtUtil.class); var users = context.getBean(UserMapper.class);
        when(jwt.validateToken("admin-test")).thenReturn(true);
        when(jwt.validateToken("user-test")).thenReturn(true);
        when(jwt.getUserId("admin-test")).thenReturn(1L); when(jwt.getUserId("user-test")).thenReturn(2L);
        var admin = new User(); admin.setId(1L); admin.setUsername("admin"); admin.setRole("ADMIN");
        var user = new User(); user.setId(2L); user.setUsername("user"); user.setRole("USER");
        when(users.selectById(1L)).thenReturn(admin); when(users.selectById(2L)).thenReturn(user);
        when(jwt.matchesUser("admin-test", admin)).thenReturn(true);
        when(jwt.matchesUser("user-test", user)).thenReturn(true);
        var now = Instant.parse("2026-10-13T02:00:00Z");
        when(reports.report()).thenReturn(new RelayReportDto(new Snapshot(LocalDate.of(2026, 10, 13), "Asia/Shanghai", now, now.plusSeconds(3600),
                new Metrics(), new Metrics(), Map.of(), List.of(), List.of()), "redis"));
    }

    @AfterEach void close() { context.close(); }

    @Test void anonymousCannotReadFinancialReport() throws Exception {
        mvc.perform(get("/api/admin/relay/reports")).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }
    @Test void ordinaryUserCannotReadFinancialReport() throws Exception {
        mvc.perform(get("/api/admin/relay/reports").header("Authorization", "Bearer user-test")).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }
    @Test void administratorCanReadSnapshotWithBrowserCachingDisabled() throws Exception {
        mvc.perform(get("/api/admin/relay/reports").header("Authorization", "Bearer admin-test"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.cacheSource").value("redis"))
                .andExpect(jsonPath("$.data.snapshot.generatedAt").value("2026-10-13T02:00:00Z"))
                .andExpect(jsonPath("$.data.snapshot.date").value("2026-10-13"));
        verify(reports).report();
    }
}
