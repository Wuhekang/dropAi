package com.dropai.rewrite.service.ppt.enhancement;

import com.dropai.rewrite.config.PptEnhancementProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptEnhancementAiServiceContractTest {
    @Test
    void batchValidationAcceptsTheAllowlistedArchetypeRecipePair() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PptEnhancementAiService service = new PptEnhancementAiService(properties(), mapper, RestClient.builder());

        assertDoesNotThrow(() -> service.validateBatch(mapper.readTree(plan("cover", "COVER_ACCENT")),
            inventory(), "balanced", 1, 1));
    }

    @Test
    void batchValidationRejectsInvalidArchetypeRecipePairBeforeAProviderAttemptCanSucceed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PptEnhancementAiService service = new PptEnhancementAiService(properties(), mapper, RestClient.builder());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> service.validateBatch(mapper.readTree(plan("content", "RUN_COMMAND")),
                inventory(), "balanced", 1, 1));

        assertTrue(failure.getMessage().contains("页面类型与美化配方不匹配"));
    }

    @Test
    void batchValidationRejectsUnknownArchetypeEvenWhenPageNumbersAreComplete() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PptEnhancementAiService service = new PptEnhancementAiService(properties(), mapper, RestClient.builder());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> service.validateBatch(mapper.readTree(plan("process", "CONTENT_RAIL")),
                inventory(), "balanced", 1, 1));

        assertTrue(failure.getMessage().contains("未知页面类型"));
    }

    @Test
    void batchValidationRejectsAValidRecipeWhenItContradictsTheBaselineArchetype() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PptEnhancementAiService service = new PptEnhancementAiService(properties(), mapper, RestClient.builder());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> service.validateBatch(mapper.readTree(plan("section", "SECTION_MOTIF")),
                inventory(), "balanced", 1, 1));

        assertTrue(failure.getMessage().contains("页面分类与基础PPT结构不一致"));
    }

    @Test
    void authoritativeFallbackCorrectsOnlyStructuralFieldsAfterProviderRetriesAreExhausted() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PptEnhancementAiService service = new PptEnhancementAiService(properties(), mapper, RestClient.builder());
        var raw = mapper.readTree(plan("section", "SECTION_MOTIF"));

        int corrections = service.applyAuthoritativeStructure(raw, inventory(), 1, 1);

        assertTrue(corrections > 0);
        assertDoesNotThrow(() -> service.validateBatch(raw, inventory(), "balanced", 1, 1));
        assertTrue(raw.path("slides").path(0).path("archetype").asText().equals("cover"));
        assertTrue(raw.path("slides").path(0).path("recipeId").asText().equals("COVER_ACCENT"));
    }

    private PptEnhancementProperties properties() {
        return new PptEnhancementProperties(new MockEnvironment()
            .withProperty("DOKIAI_PPT_ENHANCEMENT_ENABLED", "true")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_ARK_API_KEY", "test-key-not-a-secret")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_MODEL", "test-model")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_BASE_URL", "https://mock.local")
            .withProperty("DOKIAI_PPT_ENHANCEMENT_MAX_RETRIES", "1"));
    }

    private PptxBaselineInspector.DeckInventory inventory() {
        return new PptxBaselineInspector.DeckInventory(
            "source", 10, 1, 12_192_000, 6_858_000, "academic-purple",
            List.of("7257FF", "E85BB5"),
            List.of(new PptxBaselineInspector.SlideInventory(
                1, "封面", "cover", 2, 0, 0, 2, List.of())));
    }

    private String plan(String archetype, String recipe) {
        return """
            {"schemaVersion":"1.0","sourcePptxSha256":"source","mode":"polish",
             "profile":"balanced","textPolicy":"locked","slides":[
              {"slideNumber":1,"archetype":"%s","recipeId":"%s"}]}
            """.formatted(archetype, recipe);
    }
}
