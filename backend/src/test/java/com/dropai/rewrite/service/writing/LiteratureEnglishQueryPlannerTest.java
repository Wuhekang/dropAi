package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteratureEnglishQueryPlannerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesValidatedPlanAndBuildsBoundedSingleRequest() {
        DoubaoProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        LiteratureEnglishQueryPlanner planner = planner(properties, (endpoint, apiKey, request) -> {
            calls.incrementAndGet();
            assertEquals("https://example.test/chat/completions", endpoint);
            assertEquals("test-key", apiKey);
            assertEquals("test-text-model", request.get("model"));
            assertEquals(false, request.get("stream"));
            assertEquals(0, request.get("temperature"));
            assertEquals(256, request.get("max_tokens"));
            assertEquals(Map.of("type", "disabled"), request.get("thinking"));
            assertEquals(Map.of("type", "json_object"), request.get("response_format"));
            return response("{\"englishQuery\":\"Evolution of oracle bone script forms\","
                    + "\"coreTerms\":[\"oracle bone script\",\"script forms\"]}");
        });

        Optional<LiteratureEnglishQueryPlanner.Plan> result = planner.plan("甲骨文字形流变考");

        assertTrue(result.isPresent());
        assertEquals("Evolution of oracle bone script forms", result.orElseThrow().englishQuery());
        assertEquals(java.util.List.of("oracle bone script", "script forms"), result.orElseThrow().coreTerms());
        assertEquals(1, calls.get());
    }

    @Test
    void malformedOrChineseModelOutputFailsClosed() {
        DoubaoProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        LiteratureEnglishQueryPlanner planner = planner(properties, (endpoint, apiKey, request) -> {
            int call = calls.incrementAndGet();
            return call == 1
                    ? response("not-json")
                    : response("{\"englishQuery\":\"甲骨文字形流变\",\"coreTerms\":[\"甲骨文\"]}");
        });

        assertFalse(planner.plan("非法响应测试一").isPresent());
        assertFalse(planner.plan("非法响应测试二").isPresent());
        assertEquals(2, calls.get());
    }

    @Test
    void rejectsGenericSingleTokenAndTermsNotCopiedFromQuery() {
        DoubaoProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        LiteratureEnglishQueryPlanner planner = planner(properties, (endpoint, apiKey, request) -> {
            int call = calls.incrementAndGet();
            return call == 1
                    ? response("{\"englishQuery\":\"Blockchain\",\"coreTerms\":[\"blockchain\"]}")
                    : response("{\"englishQuery\":\"Evolution of oracle bone script forms\","
                    + "\"coreTerms\":[\"oracle malware payload\"]}");
        });

        assertFalse(planner.plan("单词响应测试").isPresent());
        assertFalse(planner.plan("注入术语测试").isPresent());
        assertEquals(2, calls.get());
    }

    @Test
    void disabledOrMissingKeyNeverCallsTransport() {
        AtomicInteger calls = new AtomicInteger();
        DoubaoProperties missingKey = configuredProperties();
        missingKey.setApiKey("  ");
        LiteratureEnglishQueryPlanner noKeyPlanner = planner(missingKey,
                (endpoint, apiKey, request) -> calls.incrementAndGet() + "");

        DoubaoProperties disabled = configuredProperties();
        disabled.setEnabled(false);
        LiteratureEnglishQueryPlanner disabledPlanner = planner(disabled,
                (endpoint, apiKey, request) -> calls.incrementAndGet() + "");

        assertFalse(noKeyPlanner.plan("甲骨文字形流变考").isPresent());
        assertFalse(disabledPlanner.plan("甲骨文字形流变考").isPresent());
        assertEquals(0, calls.get());
    }

    @Test
    void cachesSuccessfulAndFailedPlans() {
        DoubaoProperties properties = configuredProperties();
        AtomicInteger successCalls = new AtomicInteger();
        LiteratureEnglishQueryPlanner successful = planner(properties, (endpoint, apiKey, request) -> {
            successCalls.incrementAndGet();
            return response("{\"englishQuery\":\"Evolution of oracle bone script\","
                    + "\"coreTerms\":[\"oracle bone script\"]}");
        });

        assertTrue(successful.plan("甲骨文字形流变考").isPresent());
        assertTrue(successful.plan(" 甲骨文字形流变考 ").isPresent());
        assertEquals(1, successCalls.get());

        AtomicInteger failureCalls = new AtomicInteger();
        LiteratureEnglishQueryPlanner failed = planner(properties, (endpoint, apiKey, request) -> {
            failureCalls.incrementAndGet();
            throw new IllegalStateException("simulated failure without credentials");
        });

        assertFalse(failed.plan("冷门选题").isPresent());
        assertFalse(failed.plan("冷门选题").isPresent());
        assertEquals(1, failureCalls.get());
    }

    @Test
    void rejectsOversizedInputWithoutCallingTransport() {
        DoubaoProperties properties = configuredProperties();
        AtomicInteger calls = new AtomicInteger();
        LiteratureEnglishQueryPlanner planner = planner(properties,
                (endpoint, apiKey, request) -> calls.incrementAndGet() + "");

        assertFalse(planner.plan("甲".repeat(LiteratureEnglishQueryPlanner.MAX_TITLE_LENGTH + 1)).isPresent());
        assertEquals(0, calls.get());
    }

    private LiteratureEnglishQueryPlanner planner(DoubaoProperties properties,
                                                   LiteratureEnglishQueryPlanner.PlannerTransport transport) {
        return new LiteratureEnglishQueryPlanner(properties, new DoubaoModelRouter(properties), objectMapper,
                transport, Clock.systemUTC());
    }

    private DoubaoProperties configuredProperties() {
        DoubaoProperties properties = new DoubaoProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setEndpoint("https://example.test/chat/completions");
        properties.setTextModel("test-text-model");
        return properties;
    }

    private String response(String content) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "choices", java.util.List.of(Map.of(
                            "message", Map.of("content", content)
                    ))
            ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
