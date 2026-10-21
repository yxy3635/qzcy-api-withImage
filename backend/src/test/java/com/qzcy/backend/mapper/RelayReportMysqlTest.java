package com.qzcy.backend.mapper;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/** Temporary tables on one connection: never reads or modifies production usage. */
@EnabledIfEnvironmentVariable(named = "RELAY_USAGE_MYSQL_TEST_URL", matches = ".+")
class RelayReportMysqlTest {
    @Test
    void aggregateQueriesPreserveMoneyGroupIdentityAndTimeBoundaries() throws Exception {
        var datasource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",
                System.getenv("RELAY_USAGE_MYSQL_TEST_URL"), System.getenv("RELAY_USAGE_MYSQL_TEST_USER"), System.getenv("RELAY_USAGE_MYSQL_TEST_PASSWORD"));
        var config = new Configuration(new Environment("relay-report-test", new JdbcTransactionFactory(), datasource));
        config.addMapper(RelayReportMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            try (var statement = session.getConnection().createStatement()) {
                statement.execute("""
                        CREATE TEMPORARY TABLE relay_usage_log (
                            model VARCHAR(120), billing_group_code VARCHAR(160), group_names VARCHAR(160),
                            channel_id BIGINT, channel_name VARCHAR(80), provider_id BIGINT, provider_name VARCHAR(80),
                            status VARCHAR(20), status_code INT, created_at DATETIME, cost DECIMAL(12,6),
                            total_tokens INT DEFAULT 0, prompt_tokens INT DEFAULT 0, completion_tokens INT DEFAULT 0,
                            cached_tokens INT DEFAULT 0, cache_creation_tokens INT DEFAULT 0, cache_creation1h_tokens INT
                        )
                        """);
                statement.executeUpdate("""
                        INSERT INTO relay_usage_log
                        (model,billing_group_code,group_names,channel_id,channel_name,provider_id,provider_name,status,status_code,created_at,cost,total_tokens,prompt_tokens,completion_tokens,cached_tokens,cache_creation_tokens,cache_creation1h_tokens) VALUES
                        ('m','premium','default,premium',1,'old',11,'same','success',200,'2026-10-13 00:00:00',0.000123,150,100,50,30,10,10),
                        ('m','premium','default,premium',1,'new',11,'renamed','failed',200,'2026-10-13 09:00:00',0,0,0,0,0,0,NULL),
                        ('m',NULL,'default,premium',1,'new',12,'same','failed',502,'2026-10-13 09:00:00',0,0,0,0,0,0,NULL),
                        ('m',NULL,'premium',1,'old',11,'same','success',200,'2026-10-12 23:59:59',0.000456,10,5,5,0,0,NULL),
                        ('deleted',NULL,'archived',99,'deleted',91,'deleted','success',200,'2026-10-13 08:00:00',0.000789,5,3,2,0,0,NULL),
                        ('m',NULL,'premium',1,'new',NULL,NULL,'success',200,'2026-10-13 08:00:00',0.000001,1,1,0,0,0,NULL),
                        ('m','premium','premium',1,'new',11,'same','success',200,'2026-10-14 00:00:00',999,999,999,0,0,0,NULL)
                        """);
            }
            var mapper = session.getMapper(RelayReportMapper.class);
            var start = LocalDateTime.of(2026, 10, 13, 0, 0);
            var cutoff = start.plusHours(10);
            var aggregates = mapper.aggregates(start, cutoff);
            assertEquals(4, aggregates.size());
            assertEquals(5, aggregates.stream().mapToLong(row -> row.getToday().getRequests()).sum());
            assertEquals(6, aggregates.stream().mapToLong(row -> row.getTotal().getRequests()).sum());
            assertEquals(2, aggregates.stream().mapToLong(row -> row.getToday().getErrors()).sum());
            var premium = aggregates.stream().filter(row -> "group:premium".equals(row.getGroupKey()) && Long.valueOf(11).equals(row.getProviderId())).findFirst().orElseThrow();
            assertEquals(2, premium.getToday().getRequests()); assertEquals(3, premium.getTotal().getRequests());
            assertEquals(10, premium.getToday().getCacheCreation1hTokens());
            assertEquals(0, new BigDecimal("0.000579").compareTo(premium.getTotal().getCost()));
            assertTrue(aggregates.stream().anyMatch(row -> row.getGroupKey().equals("legacy:default,premium")));
            var hourly = mapper.hourly(start, cutoff);
            assertEquals(3, hourly.size());
            assertEquals("2026-10-13 00:00", hourly.get(0).getBucket());
            assertEquals(150, hourly.get(0).getMetrics().getTokens());
            var daily = mapper.daily(start.minusDays(29), cutoff);
            assertEquals(2, daily.size());
            assertEquals(0, new BigDecimal("0.000913").compareTo(daily.get(1).getMetrics().getCost()));
        }
    }
}
