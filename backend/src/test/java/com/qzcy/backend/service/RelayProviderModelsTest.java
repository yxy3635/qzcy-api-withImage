package com.qzcy.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qzcy.backend.dto.RelayProviderUpdateDto;
import com.qzcy.backend.entity.RelayChannelProvider;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RelayProviderModelsTest {
    @Test
    void inheritsLegacyModelsButEmptySelectionDisablesRouting() {
        var provider = new RelayChannelProvider();
        assertTrue(RelayProviderModels.supports(provider, 1L));
        provider.setModelIds("");
        assertFalse(RelayProviderModels.supports(provider, 1L));
        provider.setModelIds(RelayProviderModels.encode(List.of(1L, 12L, 1L)));
        assertTrue(RelayProviderModels.supports(provider, 12L));
        assertFalse(RelayProviderModels.supports(provider, 2L));
        assertEquals(List.of(1L, 12L), RelayProviderModels.decode(provider.getModelIds()));
        assertNull(RelayProviderModels.decode(null));
        assertEquals(List.of(), RelayProviderModels.decode(""));
    }

    @Test
    void distinguishesOldClientsFromExplicitlyRestoringAllModels() throws Exception {
        var json = new ObjectMapper();
        assertFalse(json.readValue("{}", RelayProviderUpdateDto.class).isModelSelectionProvided());
        var all = json.readValue("{\"modelIds\":null}", RelayProviderUpdateDto.class);
        assertTrue(all.isModelSelectionProvided());
        assertNull(all.getModelIds());
        var none = json.readValue("{\"modelIds\":[]}", RelayProviderUpdateDto.class);
        assertTrue(none.isModelSelectionProvided());
        assertTrue(none.getModelIds().isEmpty());
    }
}
