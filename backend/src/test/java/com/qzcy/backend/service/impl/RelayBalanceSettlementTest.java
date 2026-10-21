package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.UserMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelayBalanceSettlementTest {
    @Test
    void concurrentIncurredChargesCanExhaustBalanceWithoutDroppingEitherCharge() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:relay-settlement;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.addMapper(UserMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, balance DECIMAL(12,6), version INT, updated_at TIMESTAMP)");
            sql.execute("INSERT INTO `user` (id,balance,version) VALUES (117,1,0)");
        }
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            Callable<Integer> debit = () -> {
                try (var session = factory.openSession(true)) {
                    ready.countDown();
                    if (!ready.await(3, TimeUnit.SECONDS)) throw new AssertionError("Calls did not start concurrently");
                    return session.getMapper(UserMapper.class).settleRelayBalance(117L, new BigDecimal("0.8"));
                }
            };
            var first = executor.submit(debit);
            var second = executor.submit(debit);
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
            assertEquals(1, second.get(5, TimeUnit.SECONDS));
            try (var connection = source.getConnection(); var sql = connection.createStatement();
                 var result = sql.executeQuery("SELECT balance, version FROM `user` WHERE id=117")) {
                assertTrue(result.next());
                assertEquals(0, new BigDecimal("-0.6").compareTo(result.getBigDecimal(1)));
                assertEquals(2, result.getInt(2));
            }
            try (var session = factory.openSession(true)) {
                assertEquals(0, session.getMapper(UserMapper.class).deductBalance(117L, BigDecimal.ONE),
                        "Normal prepaid deductions still reject insufficient balance");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void relayPaymentUsesIncurredSettlementAndRejectsInvalidAmounts() {
        var users = mock(UserMapper.class);
        when(users.settleRelayBalance(117L, BigDecimal.ONE)).thenReturn(1);
        var service = new PaymentServiceImpl(users, null, null, null, null, null, null);
        service.deductBalanceOnly(117L, BigDecimal.ONE);
        verify(users).settleRelayBalance(117L, BigDecimal.ONE);
        verify(users, never()).deductBalance(any(), any());
        assertThrows(BusinessException.class, () -> service.deductBalanceOnly(117L, BigDecimal.ZERO));
        assertThrows(BusinessException.class, () -> service.deductBalanceOnly(117L, new BigDecimal("-1")));
    }
}
