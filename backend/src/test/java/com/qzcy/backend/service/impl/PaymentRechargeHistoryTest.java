package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.mapper.PaymentRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class PaymentRechargeHistoryTest {
    @Test
    void filtersCurrentUserAndSuccessfulRechargesBeforePagination() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentRecord.class);
        PaymentRecordMapper mapper = mock(PaymentRecordMapper.class);
        PaymentServiceImpl service = new PaymentServiceImpl(null, mapper, null, null, null, null, null);
        when(mapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<PaymentRecord> page = invocation.getArgument(0);
            LambdaQueryWrapper<PaymentRecord> query = invocation.getArgument(1);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("user_id ="));
            assertTrue(sql.contains("AND status ="));
            assertTrue(sql.contains("AND type IN"));
            assertTrue(sql.contains("ORDER BY created_at DESC,id DESC"));
            assertEquals(Set.of(42L, "completed", "third_party", "alipay", "wxpay", "qqpay", "wechat"),
                    Set.copyOf(query.getParamNameValuePairs().values()));
            assertEquals(1, page.getCurrent());
            assertEquals(100, page.getSize());
            return page;
        });
        assertNotNull(service.completedRecharges(42L, -1, 9999));
        verify(mapper).selectPage(any(Page.class), any());
    }
}
