package com.dropai.rewrite.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptPropertiesTest {
    @Test
    void reusesTheConfiguredDoubaoRuntimeForPptSkillExecution() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "DOUBAO_API_KEY", "test-key",
            "DOUBAO_MODEL", "doubao-test-model"
        )));

        PptProperties properties = new PptProperties(environment);

        assertEquals("doubao_ark", properties.provider());
        assertEquals("doubao-test-model", properties.model());
        assertTrue(properties.configured());
    }

    @Test
    void reusesResolvedSpringDoubaoPropertiesWhenDedicatedPptOverridesAreAbsent() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "ai.doubao.api-key", "spring-test-key",
            "ai.doubao.model", "spring-doubao-model"
        )));

        PptProperties properties = new PptProperties(environment);

        assertEquals("spring-doubao-model", properties.model());
        assertTrue(properties.configured());
    }
}
