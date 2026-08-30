package com.dropai.rewrite.service.ppt.rendering.plan.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanFreezer;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.MeasurementTestSupport;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidationContext;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidator;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.ValidatedSlideRenderPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPlanStateTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void draftAndValidatedStatesDefensivelyCopyAtEveryBoundary() throws Exception {
        ObjectNode source = validPlan();
        DraftSlideRenderPlan draft = DraftSlideRenderPlan.of(source);
        String presentationId = source.path("presentationId").asText();
        source.put("presentationId", "mutated-source");
        ObjectNode escapedDraft = draft.document();
        escapedDraft.put("presentationId", "mutated-copy");
        assertEquals(presentationId, draft.document().path("presentationId").asText());

        var result = new RenderPlanValidator().validate(draft, context(draft.document()));
        assertTrue(result.valid(), () -> result.issues().toString());
        ValidatedSlideRenderPlan validated = result.accept();
        escapedDraft.put("presentationId", "mutated-after-validation");
        validated.document().put("presentationId", "escaped-validation");
        assertEquals(presentationId, validated.document().path("presentationId").asText());

        FrozenSlideRenderPlan frozen = new RenderPlanFreezer().freeze(validated);
        byte[] escapedBytes = frozen.canonicalBytes();
        escapedBytes[0] = 'X';
        assertEquals(presentationId, frozen.document().path("presentationId").asText());
        assertFalse(Arrays.stream(ValidatedSlideRenderPlan.class.getDeclaredConstructors())
                .anyMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        assertFalse(Arrays.stream(FrozenSlideRenderPlan.class.getDeclaredMethods())
                .anyMatch(method -> Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers())
                        && FrozenSlideRenderPlan.class.equals(method.getReturnType())));
    }

    private RenderPlanValidationContext context(ObjectNode plan) {
        return new RenderPlanValidationContext(
                plan.path("presentationId").asText(),
                plan.path("sourceTreeHash").asText(),
                plan.path("engine").path("themeHash").asText(),
                plan.path("engine").path("layoutCatalogHash").asText(),
                plan.path("engine").path("fontProfileHash").asText(),
                expectedEngine(plan),
                expectedFontProfile(plan),
                MeasurementTestSupport.exactProfile(),
                MeasurementTestSupport.textMetrics(),
                plan.path("slideSize").path("widthEmu").asLong(),
                plan.path("slideSize").path("heightEmu").asLong(),
                List.of("page_06"),
                Map.of("page_06", new RenderPlanValidationContext.PageExpectation(
                        plan.path("slides").get(0).path("pageType").asText(),
                        plan.path("slides").get(0).path("layoutId").asText())),
                LayoutIds.ALL,
                Map.of(),
                Set.of("slideTitle", "imageFrame"),
                Set.of("typography.styles.slideTitle", "components.imageFrame"),
                Map.of("figure_4_10", plan.path("assets").get(0).path("sha256").asText()),
                RenderPlanValidationContext.SafeArea.none(),
                new RenderPlanValidationContext.StatusStyleExpectation(
                        "#237A52", "#9A6200", "#B63A3A", "#FFFFFF"),
                Map.of("slideTitle", 2_000),
                1_000);
    }

    private RenderPlanValidationContext.EngineExpectation expectedEngine(ObjectNode plan) {
        var engine = plan.path("engine");
        return new RenderPlanValidationContext.EngineExpectation(
                engine.path("engineVersion").asText(), engine.path("themeId").asText(),
                engine.path("themeVersion").asText(), engine.path("layoutCatalogVersion").asText());
    }

    private RenderPlanValidationContext.FontProfileExpectation expectedFontProfile(ObjectNode plan) {
        var profile = plan.path("engine").path("resolvedFontProfile");
        Map<String, RenderPlanValidationContext.FontFaceExpectation> faces = new java.util.LinkedHashMap<>();
        profile.path("faces").forEach(face -> faces.put(
                face.path("fontFaceId").asText(),
                new RenderPlanValidationContext.FontFaceExpectation(
                        face.path("role").asText(), face.path("weight").asInt(),
                        face.path("selectedFamily").asText(), face.path("postScriptName").asText(),
                        face.path("fontSource").asText(), face.path("fontFingerprint").asText(),
                        face.path("fallbackApplied").asBoolean())));
        return new RenderPlanValidationContext.FontProfileExpectation(
                profile.path("profileId").asText(),
                profile.path("measurementEngineVersion").asText(), faces);
    }

    private ObjectNode validPlan() throws IOException {
        String resource = "ppt/rendering-contract/v1/valid/render-plan.valid.json";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing resource " + resource);
            }
            return (ObjectNode) MAPPER.readTree(input);
        }
    }
}
