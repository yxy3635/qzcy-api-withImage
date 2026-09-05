package com.qzcy.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayModelDto;
import com.qzcy.backend.dto.RelayChannelModelUpdateDto;
import com.qzcy.backend.dto.RelayModelUpdateDto;
import com.qzcy.backend.entity.RelayChannelModel;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.mapper.RelayChannelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayChannelProviderMapper;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.mapper.RelayTokenMapper;
import com.qzcy.backend.mapper.RelayUsageLogMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.RelayModelStatusCache;
import com.qzcy.backend.service.RelayProviderScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RelayServiceModelCreationTest {
    private RelayChannelModelMapper channelModelMapper;
    private RelayGroupModelMapper groupModelMapper;
    private RelayModelMapper modelMapper;
    private RelayServiceImpl service;

    @BeforeEach
    void setUp() {
        channelModelMapper = mock(RelayChannelModelMapper.class);
        groupModelMapper = mock(RelayGroupModelMapper.class);
        modelMapper = mock(RelayModelMapper.class);
        service = new RelayServiceImpl(
                mock(RelayChannelMapper.class),
                channelModelMapper,
                mock(RelayChannelProviderMapper.class),
                new RelayProviderScheduler(),
                mock(RelayGroupMapper.class),
                groupModelMapper,
                modelMapper,
                mock(RelayTokenMapper.class),
                mock(RelayUsageLogMapper.class),
                mock(UserMapper.class),
                new ObjectMapper(),
                mock(RelayModelStatusCache.class)
        );
    }

    @Test
    void createModelDoesNotAutomaticallyBindChannelsOrGroups() {
        RelayModelUpdateDto request = new RelayModelUpdateDto();
        request.setModel("provider/model-v1");
        request.setDisplayName("Model V1");

        when(modelMapper.insert(any(RelayModel.class))).thenAnswer(invocation -> {
            RelayModel model = invocation.getArgument(0);
            model.setId(42L);
            return 1;
        });
        when(modelMapper.selectById(42L)).thenAnswer(invocation -> {
            RelayModel model = new RelayModel();
            model.setId(42L);
            model.setModel("provider/model-v1");
            model.setDisplayName("Model V1");
            model.setModelType("chat");
            model.setEnabled(true);
            model.setStatus("available");
            model.setSortOrder(10);
            return model;
        });

        RelayModelDto created = service.createModel(request);

        assertEquals(42L, created.getId());
        assertEquals("provider/model-v1", created.getModel());
        verify(modelMapper).insert(any(RelayModel.class));
        verifyNoInteractions(channelModelMapper, groupModelMapper);
    }

    @Test
    void blankUpstreamModelIsStoredAsRawForwardingMapping() {
        RelayModel model = new RelayModel();
        model.setId(7L);
        model.setModel("internal-model");
        when(modelMapper.selectById(7L)).thenReturn(model);

        RelayChannelModelUpdateDto request = new RelayChannelModelUpdateDto();
        request.setModelId(7L);
        request.setUpstreamModel("  ");
        request.setEnabled(true);

        ReflectionTestUtils.invokeMethod(service, "replaceChannelModels", 9L, List.of(request));

        org.mockito.ArgumentCaptor<RelayChannelModel> captor = forClass(RelayChannelModel.class);
        verify(channelModelMapper).insert(captor.capture());
        assertEquals("", captor.getValue().getUpstreamModel());
    }
}
