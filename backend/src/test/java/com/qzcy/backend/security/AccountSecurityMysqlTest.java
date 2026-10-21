package com.qzcy.backend.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.qzcy.backend.mapper.UserMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Uses connection-local temporary tables only, never a production user row. */
@EnabledIfEnvironmentVariable(named = "ACCOUNT_SECURITY_MYSQL_TEST_URL", matches = ".+")
class AccountSecurityMysqlTest {
    @Test void atomicCredentialUpdatesDoNotRestoreStalePasswordsOrBalances() throws Exception {
        var source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", System.getenv("ACCOUNT_SECURITY_MYSQL_TEST_URL"),
                System.getenv("ACCOUNT_SECURITY_MYSQL_TEST_USER"), System.getenv("ACCOUNT_SECURITY_MYSQL_TEST_PASSWORD"));
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("security-test", new JdbcTransactionFactory(), source));
        configuration.addMapper(UserMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession()) {
            var connection = session.getConnection();
            try (var sql = connection.createStatement()) {
                sql.execute("""
                        CREATE TEMPORARY TABLE `user` (
                            id BIGINT PRIMARY KEY, password VARCHAR(255), email VARCHAR(255),
                            auth_version BIGINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
                            balance DECIMAL(12,6) DEFAULT 0, updated_at DATETIME
                        ) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                        """);
                sql.executeUpdate("INSERT INTO `user` (id,password,email,balance) VALUES (42,'HashCaseSensitive','victim@example.invalid',10),(43,'NoEmailHash',NULL,1)");
            }
            UserMapper users = session.getMapper(UserMapper.class);
            assertEquals(0, users.changePasswordSecurely(42L, "hashcasesensitive", "victim@example.invalid", "Wrong"));
            assertEquals(1, users.changePasswordSecurely(42L, "HashCaseSensitive", "victim@example.invalid", "ReplacementHash"));
            assertEquals(0, users.changeEmailSecurely(42L, "HashCaseSensitive", "victim@example.invalid", "attacker@example.invalid"));
            assertEquals(1, users.addBalance(42L, java.math.BigDecimal.ONE));
            assertEquals(1, users.changeEmailSecurely(42L, "ReplacementHash", "victim@example.invalid", "new@example.invalid"));
            assertEquals(1, users.revokeSessions(42L));
            assertEquals(1, users.changeEmailSecurely(43L, "NoEmailHash", null, "first@example.invalid"));
            try (var sql = connection.createStatement(); var result = sql.executeQuery("SELECT password,email,auth_version,balance,version FROM `user` WHERE id=42")) {
                assertTrue(result.next()); assertEquals("ReplacementHash", result.getString("password"));
                assertEquals("new@example.invalid", result.getString("email"));
                assertEquals(3L, result.getLong("auth_version")); assertEquals(1, result.getInt("version"));
                assertEquals(0, new java.math.BigDecimal("11").compareTo(result.getBigDecimal("balance")));
            }
        }
    }
}
