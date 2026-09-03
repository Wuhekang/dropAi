package com.dropai.rewrite.service.ppt.enhancement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private PptxBaselineInspector.DeckInventory inventory() {
        return new PptxBaselineInspector.DeckInventory("source", 10, 2, 12_192_000, 6_858_000,
            "academic-purple", List.of("7257FF", "E85BB5"), List.of(
                new PptxBaselineInspector.SlideInventory(1, "封面", "cover", 2, 0, 0, 4, List.of()),
                new PptxBaselineInspector.SlideInventory(2, "目录", "catalog", 2, 0, 0, 2, List.of())));
    }
}
