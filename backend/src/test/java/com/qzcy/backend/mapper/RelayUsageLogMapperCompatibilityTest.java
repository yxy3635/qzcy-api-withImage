package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @Test
    void adminUserUsageUsesTokenTotalsAsBillingFallback() throws NoSuchMethodException {
        Method method = RelayUsageLogMapper.class.getMethod("adminUserUsage", Page.class, String.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value()).toLowerCase(Locale.ROOT);

        assertTrue(sql.contains("greatest(coalesce(usagesummary.totalcost"));
        assertTrue(sql.contains("greatest(coalesce(usagesummary.totaltokens"));
        assertTrue(sql.contains("from relay_token"));
        assertTrue(sql.contains("sum(coalesce(used_quota, 0))"));
        assertTrue(sql.contains("sum(coalesce(token_count, 0))"));
    }
}
