package com.dropai.rewrite.service.ppt.rendering.contract.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderingContractExamplesTest {
    @Test
    void rejectsUnknownRootAndNestedProperties() {
        ObjectNode theme = theme();
        theme.put("paperTitle", "禁止进入主题");
        assertRejected("theme.v1.schema.json", theme, "additionalProperties");

        ObjectNode nested = theme();
        ((ObjectNode) nested.path("slide")).put("sourceChapter", "第一章");
        assertRejected("theme.v1.schema.json", nested, "additionalProperties");

        ObjectNode badToken = theme();
        ((ObjectNode) badToken.path("components").path("keyPointCard"))
                .put("fillToken", "colors.notExisting.value");
        assertRejected("theme.v1.schema.json", badToken, "enum");
    }

    @Test
    void rejectsUnknownPageTypeLayoutAndBadHash() {
        ObjectNode plan = plan();
        ((ObjectNode) firstSlide(plan)).put("pageType", "VIDEO");
        assertRejected("render-plan.v1.schema.json", plan, "enum");

        plan = plan();
        ((ObjectNode) firstSlide(plan)).put("layoutId", "unknown-layout.v1");
        assertRejected("render-plan.v1.schema.json", plan, "enum");

        plan = plan();
        plan.put("sourceTreeHash", "sha256:ABC");
        assertRejected("render-plan.v1.schema.json", plan, "pattern");
    }

    @Test
    void rejectsInvalidGeometryAndMissingElementPayloads() {
        ObjectNode plan = plan();
        ObjectNode text = (ObjectNode) firstElement(plan);
        text.put("xEmu", -1);
        assertRejected("render-plan.v1.schema.json", plan, "minimum");

        plan = plan();
        ((ObjectNode) firstElement(plan)).put("widthEmu", 0);
        assertRejected("render-plan.v1.schema.json", plan, "minimum");

        plan = plan();
        ((ObjectNode) firstElement(plan)).remove("text");
        assertRejected("render-plan.v1.schema.json", plan, "required");

        plan = plan();
        ObjectNode image = (ObjectNode) firstSlide(plan).path("elements").get(1);
        image.remove("assetId");
        assertRejected("render-plan.v1.schema.json", plan, "required");
    }

    @Test
    void elementKindsAreMutuallyExclusive() {
        ObjectNode plan = plan();
        ((ObjectNode) firstElement(plan)).put("assetId", "figure_4_10");
        assertTrue(RenderingContractTestSupport.validate("render-plan.v1.schema.json", plan).size() > 0);

        plan = plan();
        ((ObjectNode) firstSlide(plan).path("elements").get(1)).put("text", "图片元素不得携带文本字段");
        assertTrue(RenderingContractTestSupport.validate("render-plan.v1.schema.json", plan).size() > 0);
    }

    @Test
    void rejectsStretchAndUnresolvedCropRules() {
        ObjectNode plan = plan();
        ObjectNode image = (ObjectNode) firstSlide(plan).path("elements").get(1);
        image.put("fitMode", "STRETCH");
        assertRejected("render-plan.v1.schema.json", plan, "enum");

        plan = plan();
        image = (ObjectNode) firstSlide(plan).path("elements").get(1);
        image.put("fitMode", "COVER");
        image.put("cropAllowed", true);
        assertRejected("render-plan.v1.schema.json", plan, "required");

        plan = plan();
        image = (ObjectNode) firstSlide(plan).path("elements").get(1);
        image.put("cropAllowed", true);
        assertRejected("render-plan.v1.schema.json", plan, "const");
    }

    @Test
    void acceptsCoverOnlyWhenCropIsFullyResolved() {
        ObjectNode plan = plan();
        ObjectNode image = (ObjectNode) firstSlide(plan).path("elements").get(1);
        image.put("fitMode", "COVER");
        image.put("cropAllowed", true);
        ObjectNode crop = image.putObject("sourceCrop");
        crop.put("leftPermille", 50);
        crop.put("topPermille", 0);
        crop.put("rightPermille", 50);
        crop.put("bottomPermille", 0);
        assertTrue(RenderingContractTestSupport.validate("render-plan.v1.schema.json", plan).isEmpty(),
                () -> RenderingContractTestSupport.validate("render-plan.v1.schema.json", plan).toString());
    }

    @Test
    void rejectsAbsoluteTraversalAndBackslashAssetPaths() {
        for (String path : new String[]{"C:/temp/figure.png", "/tmp/figure.png", "assets/../secret.png", "assets\\figure.png"}) {
            ObjectNode plan = plan();
            ((ObjectNode) plan.path("assets").get(0)).put("bundlePath", path);
            assertRejected("render-plan.v1.schema.json", plan, "pattern");
        }
    }

    @Test
    void rejectsUnknownQualityCodeAndAutoFixFields() {
        ObjectNode report = report();
        ((ObjectNode) report.path("issues").get(0)).put("code", "PPT-QA-999");
        assertRejected("quality-report.v1.schema.json", report, "enum");

        report = report();
        ((ObjectNode) report.path("issues").get(0)).put("autoFixed", true);
        assertRejected("quality-report.v1.schema.json", report, "additionalProperties");
    }

    @Test
    void allCommittedExamplesAvoidExplicitNulls() {
        String[] files = {
                "valid/theme.valid.json",
                "valid/layout-recipe.valid.json",
                "valid/render-plan.valid.json",
                "valid/quality-report.valid.json",
                "invalid/theme.unknown-property.json",
                "invalid/layout-recipe.unknown-enum.json",
                "invalid/render-plan.invalid-geometry.json",
                "invalid/render-plan.invalid-element.json",
                "invalid/quality-report.invalid-code.json"
        };
        for (String file : files) {
            assertFalse(containsNull(RenderingContractTestSupport.read(RenderingContractTestSupport.ROOT + file)),
                    () -> file + " must omit unavailable values instead of using null");
        }
    }

    private static ObjectNode theme() {
        return (ObjectNode) RenderingContractTestSupport.mutableCopyOfValid("theme.valid.json");
    }

    private static ObjectNode plan() {
        return (ObjectNode) RenderingContractTestSupport.mutableCopyOfValid("render-plan.valid.json");
    }

    private static ObjectNode report() {
        return (ObjectNode) RenderingContractTestSupport.mutableCopyOfValid("quality-report.valid.json");
    }

    private static JsonNode firstSlide(ObjectNode plan) {
        return plan.path("slides").get(0);
    }

    private static JsonNode firstElement(ObjectNode plan) {
        return firstSlide(plan).path("elements").get(0);
    }

    private static void assertRejected(String schema, JsonNode value, String expectedKeyword) {
        var errors = RenderingContractTestSupport.validate(schema, value);
        assertFalse(errors.isEmpty(), () -> "Expected rejection by " + schema);
        assertTrue(errors.stream().anyMatch(error -> expectedKeyword.equals(error.getKeyword())),
                () -> "Expected keyword " + expectedKeyword + " but got " + errors);
    }

    private static boolean containsNull(JsonNode node) {
        if (node.isNull()) {
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsNull(child)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                if (containsNull(fields.next().getValue())) {
                    return true;
                }
            }
        }
        return false;
    }
}
