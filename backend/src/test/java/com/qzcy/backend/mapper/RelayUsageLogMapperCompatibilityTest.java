package com.qzcy.backend.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayUsageLogMapperCompatibilityTest {

    @Test
    void recentCallsQueryUsesAnIndexedSingleModelLookup() throws NoSuchMethodException {
        Method method = RelayUsageLogMapper.class.getMethod("recentCallsForModel", String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value()).toLowerCase(Locale.ROOT);

        assertFalse(sql.contains("row_number"));
        assertFalse(sql.contains(" join "));
        assertTrue(sql.contains("where model = #{model}"));
        assertTrue(sql.contains("limit 20"));
    }
}
