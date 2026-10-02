package com.qzcy.backend.mapper;

import com.qzcy.backend.dto.RelayDashboardUsageDto;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 在同一连接内用临时表验证 MySQL 方言与聚合；不会读写站点已有日志。 */
@EnabledIfEnvironmentVariable(named = "RELAY_USAGE_MYSQL_TEST_URL", matches = ".+")
class RelayTodayUsageMysqlTest {
    @Test
    void naturalDayQueryGroupsByIdsAndRetainsLegacyAndArchivedUsage() throws Exception {
        var datasource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                System.getenv("RELAY_USAGE_MYSQL_TEST_URL"),
                System.getenv("RELAY_USAGE_MYSQL_TEST_USER"),
                System.getenv("RELAY_USAGE_MYSQL_TEST_PASSWORD"));
        var configuration = new Configuration(new Environment("today-usage-test", new JdbcTransactionFactory(), datasource));
        configuration.addMapper(RelayUsageLogMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var connection = session.getConnection();
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMPORARY TABLE relay_usage_log (
                            channel_id BIGINT, channel_name VARCHAR(80), provider_id BIGINT, provider_name VARCHAR(80),
                            status VARCHAR(20), status_code INT, created_at DATETIME,
                            cost DECIMAL(12,6), input_cost DECIMAL(12,6), output_cost DECIMAL(12,6),
                            cache_read_cost DECIMAL(12,6), cache_creation_cost DECIMAL(12,6),
                            request_cost DECIMAL(12,6), channel_ratio DECIMAL(10,4)
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO relay_usage_log VALUES
                        (1, 'old name', 11, 'old provider', 'success', 200, CURDATE(), 0.010123, 0, 0, 0, 0, 0.005000, 2),
                        (1, 'new name', 11, 'new provider', 'failed', 502, DATE_ADD(CURDATE(), INTERVAL 1 HOUR), 0, 0, 0, 0, 0, 0, 2),
                        (1, 'new name', NULL, NULL, 'success', 200, CURDATE(), 0.020000, 0, 0, 0, 0, 0.010000, 1),
                        (99, 'deleted channel', 91, 'deleted provider', 'success', 200, CURDATE(), 0.030000, 0, 0, 0, 0, 0.015000, 1),
                        (2, 'another channel', 21, 'new provider', 'failed', 200, CURDATE(), 0, 0, 0, 0, 0, 0, 1),
                        (1, 'yesterday', 11, 'provider', 'success', 200, DATE_SUB(CURDATE(), INTERVAL 1 SECOND), 9, 9, 0, 0, 0, 0, 1),
                        (1, 'tomorrow', 11, 'provider', 'success', 200, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 9, 9, 0, 0, 0, 0, 1)
                        """);
            }
            List<RelayDashboardUsageDto> usage = session.getMapper(RelayUsageLogMapper.class).dashboardTodayUsage();
            assertEquals(4, usage.size());
            assertEquals(5L, usage.stream().mapToLong(RelayDashboardUsageDto::getRequests).sum());
            BigDecimal total = usage.stream().map(RelayDashboardUsageDto::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, new BigDecimal("0.060123").compareTo(total));
            RelayDashboardUsageDto provider = usage.stream().filter(row -> Long.valueOf(11).equals(row.getProviderId())).findFirst().orElseThrow();
            assertEquals(2L, provider.getRequests());
            assertEquals(1L, provider.getErrors());
            assertEquals(0, new BigDecimal("0.010000").compareTo(provider.getUpstreamCost()));
            assertEquals(1L, usage.stream().filter(row -> row.getProviderId() == null).findFirst().orElseThrow().getRequests());
            assertEquals(1L, usage.stream().filter(row -> Long.valueOf(99).equals(row.getChannelId())).findFirst().orElseThrow().getRequests());
            assertEquals(1L, usage.stream().filter(row -> Long.valueOf(2).equals(row.getChannelId())).findFirst().orElseThrow().getErrors());
        }
    }
}
