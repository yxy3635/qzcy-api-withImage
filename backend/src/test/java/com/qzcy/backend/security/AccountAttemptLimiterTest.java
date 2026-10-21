package com.qzcy.backend.security;

import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.service.AccountAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AccountAttemptLimiterTest {
    private final DriverManagerDataSource data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    private final JdbcTemplate jdbc = new JdbcTemplate(data);

    private AccountAttemptLimiter limiter() {
        var factory = new ProxyFactory(new AccountAttemptLimiter(jdbc));
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        return (AccountAttemptLimiter) factory.getProxy();
    }

    private void schema() {
        jdbc.execute("CREATE TABLE auth_attempt_bucket (bucket_key VARCHAR(100) PRIMARY KEY, attempts INT NOT NULL, expires_at BIGINT NOT NULL)");
    }

    @Test void concurrentReplicasShareAnAtomicAccountLimit() throws Exception {
        schema(); var one = limiter(); var two = limiter();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 40; i++) {
                final int index = i;
                tasks.add(() -> {
                    try { (index % 2 == 0 ? one : two).login(" ViCtIm ", "198.51.100." + index); return true; }
                    catch (BusinessException ex) { assertEquals(429, ex.getCode()); return false; }
                });
            }
            long allowed = 0;
            for (var future : pool.invokeAll(tasks)) if (future.get()) allowed++;
            assertEquals(20, allowed);
            assertEquals(429, assertThrows(BusinessException.class, () -> limiter().login("victim", "another-ip")).getCode());
        } finally { pool.shutdownNow(); }
    }

    @Test void rotatingAccountsStillHitsIpLimit() {
        schema(); var service = limiter();
        for (int i = 0; i < 100; i++) service.login("user-" + i, "198.51.100.8");
        assertEquals(429, assertThrows(BusinessException.class, () -> service.login("extra-user", "198.51.100.8")).getCode());
        assertDoesNotThrow(() -> service.login("other-user", "198.51.100.9"));
    }

    @Test void sensitiveOperationsHaveAnIndependentLimitAndDbFailureCannotBypassIt() {
        schema(); var service = limiter();
        for (int i = 0; i < 10; i++) service.sensitive("user:42", "198.51.100.8");
        assertEquals(429, assertThrows(BusinessException.class, () -> service.sensitive("user:42", "198.51.100.9")).getCode());
        assertDoesNotThrow(() -> service.login("user:42", "198.51.100.8"));
        jdbc.execute("DROP TABLE auth_attempt_bucket");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> service.login("someone", "198.51.100.10"));
    }
}
