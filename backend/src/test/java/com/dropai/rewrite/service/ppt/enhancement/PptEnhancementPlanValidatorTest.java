package com.dropai.rewrite.service.ppt.enhancement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptEnhancementPlanValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PptEnhancementPlanValidator validator = new PptEnhancementPlanValidator();

    @Test
    void expandsOnlyCompleteAllowlistedTextLockedPlans() throws Exception {
        var inventory = inventory();
        var skill = new PptEnhancementSkillService.SkillBundle("ppt-enhancement", "1.0.0", "h".repeat(64), "rules", List.of());
        var audit = new PptEnhancementAiService.AiPlanResponse(null, "doubao_ark", "model", "request", true, "SUCCESS");
        String json = """
            {"schemaVersion":"1.0","sourcePptxSha256":"source","mode":"polish","profile":"balanced","textPolicy":"locked","slides":[
              {"slideNumber":1,"archetype":"cover","recipeId":"COVER_ACCENT"},
              {"slideNumber":2,"archetype":"catalog","recipeId":"AGENDA_RAIL"}
            ]}
            """;
        PptEnhancementPlan plan = validator.validateAndExpand(mapper.readTree(json), skill, audit, inventory, "balanced");

        assertEquals(2, plan.slides().size());
        assertTrue(plan.providerInvoked());
        assertTrue(plan.slides().stream().flatMap(slide -> slide.additions().stream()).noneMatch(PptEnhancementPlan.Addition::textBearing));
    }

    @Test
    void rejectsMissingSlidesAndArbitraryRecipes() throws Exception {
        var skill = new PptEnhancementSkillService.SkillBundle("ppt-enhancement", "1.0.0", "h".repeat(64), "rules", List.of());
        var audit = new PptEnhancementAiService.AiPlanResponse(null, "doubao_ark", "model", "request", true, "SUCCESS");
        String json = """
            {"schemaVersion":"1.0","sourcePptxSha256":"source","mode":"polish","profile":"balanced","textPolicy":"locked","slides":[
              {"slideNumber":1,"archetype":"cover","recipeId":"RUN_COMMAND"}
            ]}
            """;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> validator.validateAndExpand(mapper.readTree(json), skill, audit, inventory(), "balanced"));
        assertTrue(failure.getMessage().contains("页数不完整"));
    }

    @Test
    void imagePagesExpandToBackgroundOnlyWhileContentKeepsItsShapeAccent() throws Exception {
        var skill = new PptEnhancementSkillService.SkillBundle(
            "ppt-enhancement", "1.1.0", "h".repeat(64), "rules", List.of());
        var audit = new PptEnhancementAiService.AiPlanResponse(
            null, "doubao_ark", "model", "request", true, "SUCCESS");
        String json = """
            {"schemaVersion":"1.0","sourcePptxSha256":"source","mode":"polish","profile":"balanced","textPolicy":"locked","slides":[
              {"slideNumber":1,"archetype":"image","recipeId":"IMAGE_BACKGROUND"},
              {"slideNumber":2,"archetype":"content","recipeId":"CONTENT_RAIL"}
            ]}
            """;

        PptEnhancementPlan plan = validator.validateAndExpand(
            mapper.readTree(json), skill, audit, imageAndContentInventory(), "balanced");

        PptEnhancementPlan.SlidePlan image = plan.slides().get(0);
        assertEquals("IMAGE_BACKGROUND", image.recipeId());
        assertTrue(image.backgroundOnly(), "image页必须携带backgroundOnly=true的执行契约");
        assertEquals(List.of("background"), image.additions().stream()
            .map(PptEnhancementPlan.Addition::type).toList());
        assertTrue(image.additions().stream().noneMatch(PptEnhancementPlan.Addition::textBearing));

        PptEnhancementPlan.SlidePlan content = plan.slides().get(1);
        assertEquals("CONTENT_RAIL", content.recipeId());
        assertFalse(content.backgroundOnly(), "非image页不得被标记为backgroundOnly");
        assertEquals(List.of("shape"), content.additions().stream()
            .map(PptEnhancementPlan.Addition::type).toList());
    }

    @Test
    void rejectsAnyForegroundRecipeForAnImagePage() throws Exception {
        var skill = new PptEnhancementSkillService.SkillBundle(
            "ppt-enhancement", "1.1.0", "h".repeat(64), "rules", List.of());
        var audit = new PptEnhancementAiService.AiPlanResponse(
            null, "doubao_ark", "model", "request", true, "SUCCESS");
        String json = """
            {"schemaVersion":"1.0","sourcePptxSha256":"source","mode":"polish","profile":"balanced","textPolicy":"locked","slides":[
              {"slideNumber":1,"archetype":"image","recipeId":"IMAGE_FRAME"},
              {"slideNumber":2,"archetype":"content","recipeId":"CONTENT_RAIL"}
            ]}
            """;

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> validator.validateAndExpand(
                mapper.readTree(json), skill, audit, imageAndContentInventory(), "balanced"));

        assertTrue(failure.getMessage().contains("页面类型与美化配方不匹配"));
    }

    private PptxBaselineInspector.DeckInventory inventory() {
        return new PptxBaselineInspector.DeckInventory("source", 10, 2, 12_192_000, 6_858_000,
            "academic-purple", List.of("7257FF", "E85BB5"), List.of(
                new PptxBaselineInspector.SlideInventory(1, "封面", "cover", 2, 0, 0, 4, List.of()),
                new PptxBaselineInspector.SlideInventory(2, "目录", "catalog", 2, 0, 0, 2, List.of())));
    }

    private PptxBaselineInspector.DeckInventory imageAndContentInventory() {
        return new PptxBaselineInspector.DeckInventory("source", 10, 2, 12_192_000, 6_858_000,
            "academic-purple", List.of("7257FF", "E85BB5"), List.of(
                new PptxBaselineInspector.SlideInventory(
                    1, "成果截图", "image", 3, 1, 0, 8, List.of()),
                new PptxBaselineInspector.SlideInventory(
                    2, "系统方案", "content", 2, 0, 0, 8, List.of())));
    }
}
