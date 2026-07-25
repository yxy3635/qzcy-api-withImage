package com.qzcy.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelayControllerProtocolTest {

    @Test
    void responsesCompactAcceptsCommonBaseUrlVariants() {
        Method method = Set.of(RelayController.class.getDeclaredMethods()).stream()
                .filter(candidate -> candidate.getName().equals("responsesCompact"))
                .findFirst()
                .orElseThrow();
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertEquals(
                Set.of("/responses/compact", "/v1/responses/compact", "/v1/v1/responses/compact"),
                Set.of(mapping.value())
        );
    }
}
