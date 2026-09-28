package com.qzcy.backend.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAccountInitializerTest {
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
