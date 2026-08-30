package com.dropai.rewrite.service.ppt.rendering.canonical.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.MeasurementTestSupport;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidationContext;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidator;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.ValidatedSlideRenderPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPlanCanonicalizationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void canonicalFormSortsObjectsPreservesArraysUsesLfAndStableHash() throws Exception {
        ObjectNode first = (ObjectNode) MAPPER.readTree(
                "{\"z\":25,\"a\":{\"d\":1,\"b\":2},\"slides\":[{\"index\":2},{\"index\":1}]}");
        ObjectNode second = (ObjectNode) MAPPER.readTree(
                "{\"slides\":[{\"index\":2},{\"index\":1}],\"a\":{\"b\":2,\"d\":1},\"z\":25}");
        RenderPlanCanonicalizer canonicalizer = new RenderPlanCanonicalizer();

        assertEquals(canonicalizer.canonicalize(first), canonicalizer.canonicalize(second));
        assertEquals("{\"a\":{\"b\":2,\"d\":1},\"slides\":[{\"index\":2},{\"index\":1}],\"z\":25}\n",
                canonicalizer.canonicalize(first));

        RenderPlanFreezer freezer = new RenderPlanFreezer(canonicalizer);
        FrozenSlideRenderPlan frozen = freezer.freeze(validated(validPlan()));
        RenderPlanHasher hasher = new RenderPlanHasher();
        assertEquals(hasher.hash(frozen), hasher.hash(frozen));
        assertTrue(hasher.hash(frozen).matches("sha256:[a-f0-9]{64}"));
        assertEquals('\n', frozen.canonicalBytes()[frozen.canonicalBytes().length - 1]);
    }

    @Test
    void rejectsFloatingPointGeometryBeforeHashing() throws Exception {
        ObjectNode invalid = (ObjectNode) MAPPER.readTree(
                "{\"schemaVersion\":\"render-plan.v1\",\"xEmu\":0.5}");
        assertThrows(IllegalArgumentException.class,
                () -> new RenderPlanCanonicalizer().canonicalize(invalid));
    }

    @Test
    void frozenPlanDoesNotLeakMutableJsonOrByteArrays() throws Exception {
        ObjectNode source = validPlan();
        FrozenSlideRenderPlan frozen = new RenderPlanFreezer()
                .freeze(validated(source));
        String original = frozen.canonicalDocument();

        String presentationId = source.path("presentationId").asText();
        source.put("presentationId", "changed");
        frozen.document().put("schemaVersion", "escaped");
        byte[] escapedBytes = frozen.canonicalBytes();
        escapedBytes[0] = 'X';

        assertEquals(original, frozen.canonicalDocument());
        assertNotEquals('X', frozen.canonicalBytes()[0]);
        assertEquals(presentationId, frozen.document().path("presentationId").asText());
        assertEquals(original.getBytes(StandardCharsets.UTF_8).length, frozen.canonicalBytes().length);
    }

    private ValidatedSlideRenderPlan validated(ObjectNode plan) {
        var result = new RenderPlanValidator().validate(DraftSlideRenderPlan.of(plan), context(plan));
        assertTrue(result.valid(), () -> result.issues().toString());
        return result.accept();
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
