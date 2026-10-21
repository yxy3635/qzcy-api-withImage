package com.qzcy.backend.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.UserMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAccountInitializerTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "short"})
    void renamedAdminSkipsBootstrapEvenWithInvalidInitialPassword(String password) throws Exception {
        assertExistingAccountSkipsBootstrap("renamed-owner", "ADMIN", password);
    }

    @Test
    void ordinaryUserNamedAdminIsNotOverwrittenOrPromoted() throws Exception {
        assertExistingAccountSkipsBootstrap("admin", "USER", "");
    }

    private void assertExistingAccountSkipsBootstrap(String username, String role, String password) throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:admin-bootstrap-" + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.addMapper(UserMapper.class);
        var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE `user` (id BIGINT PRIMARY KEY, username VARCHAR(64), role VARCHAR(16), banned BOOLEAN)");
            try (var insert = connection.prepareStatement("INSERT INTO `user` VALUES (117, ?, ?, TRUE)")) {
                insert.setString(1, username);
                insert.setString(2, role);
                insert.executeUpdate();
            }
            try (var session = factory.openSession(true)) {
                var mapper = spy(session.getMapper(UserMapper.class));
                var encoder = mock(BCryptPasswordEncoder.class);
                var initializer = new AdminAccountInitializer(mapper, encoder);
                ReflectionTestUtils.setField(initializer, "initialPassword", password);
                assertDoesNotThrow(() -> initializer.run());
                verify(mapper, never()).insert(any(User.class));
                verifyNoInteractions(encoder);
                if ("ADMIN".equals(role)) {
                    verify(mapper, times(1)).selectCount(any(Wrapper.class));
                }
            }
            try (var result = sql.executeQuery("SELECT username, role, banned FROM `user`")) {
                assertTrue(result.next());
                assertEquals(username, result.getString(1));
                assertEquals(role, result.getString(2));
                assertTrue(result.getBoolean(3));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void createsAdminWithConfiguredPassword() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        var encoder = new BCryptPasswordEncoder();
        var initializer = new AdminAccountInitializer(mapper, encoder);
        ReflectionTestUtils.setField(initializer, "initialPassword", "deployment-password-123");
        initializer.run();
        var user = ArgumentCaptor.forClass(User.class);
        verify(mapper).insert(user.capture());
        assertEquals("admin", user.getValue().getUsername());
        assertEquals("ADMIN", user.getValue().getRole());
        assertTrue(encoder.matches("deployment-password-123", user.getValue().getPassword()));
    }

    @Test
    void restartDoesNotOverwriteExistingAdmin() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        var encoder = mock(BCryptPasswordEncoder.class);
        var initializer = new AdminAccountInitializer(mapper, encoder);
        ReflectionTestUtils.setField(initializer, "initialPassword", "changed-env-password");
        initializer.run();
        verify(mapper, never()).insert(any(User.class));
        verifyNoInteractions(encoder);
    }
}
