package com.dropai.rewrite.service.ppt.rendering.validation.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.MeasurementTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPlanValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXAMPLE =
            "ppt/rendering-contract/v1/valid/render-plan.valid.json";
    private final RenderPlanValidator validator = new RenderPlanValidator();

    @Test
    void acceptsSchemaValidSemanticPlanAndTheSoleFullBackgroundOverlap() {
        ObjectNode plan = validSemanticPlan();
        String before = plan.toString();

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));

        assertTrue(result.valid(), () -> result.issues().toString());
        assertTrue(result.issues().isEmpty(), () -> result.issues().toString());
        assertTrue(plan.toString().equals(before), "Validator must never modify its draft input");
        assertTrue(result.accept() != null);
    }

    @Test
    void acceptsVerifiedFullSlideDecorationWithoutChangingTheImagePageContract() {
        ObjectNode plan = validSemanticPlan();
        ObjectNode decoration = addTemplateDecoration(plan);

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));

        assertTrue(result.valid(), () -> result.issues().toString());
        assertTrue(result.issues().isEmpty(), () -> result.issues().toString());
        assertTrue(decoration.path("decorative").asBoolean());
    }

    @Test
    void decorativeFlagCannotBypassSurfaceGeometryOrTrustedAssetKindChecks() {
        ObjectNode plan = validSemanticPlan();
        ObjectNode decoration = addTemplateDecoration(plan);
        decoration.put("xEmu", 1);
        ((ObjectNode) plan.path("assets").get(1)).put("assetKind", "SCREENSHOT");

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));

        assertFalse(result.valid());
        assertHas(result, PptQualityCode.UNRENDERABLE_PAGE);
        assertHas(result, PptQualityCode.INVALID_REFERENCE);
    }

    @Test
    void aggregatesGeometryAspectTruncationOverlapAndMissingAssetFailures() {
        ObjectNode plan = validSemanticPlan();
        RenderPlanValidationContext context = context(plan);
        ObjectNode slide = (ObjectNode) plan.path("slides").get(0);
        ArrayNode elements = (ArrayNode) slide.path("elements");
        ObjectNode title = (ObjectNode) elements.get(1);
        ObjectNode image = (ObjectNode) elements.get(2);
        title.put("text", "机械截断内容…");
        image.put("xEmu", plan.path("slideSize").path("widthEmu").asLong() - 10);
        image.put("heightEmu", 2_000_000);
        elements.add(shape("slide-06-card", 600_000, 400_000, 1_000_000, 400_000, 10));
        ((ArrayNode) plan.path("assets")).removeAll();

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context);

        assertFalse(result.valid());
        assertHas(result, PptQualityCode.TEXT_TRUNCATED);
        assertHas(result, PptQualityCode.ELEMENT_OUT_OF_BOUNDS);
        assertHas(result, PptQualityCode.INVALID_REFERENCE);
        assertHas(result, PptQualityCode.MANDATORY_ASSET_MISSING);
        assertHas(result, PptQualityCode.ILLEGAL_OVERLAP);
        assertThrows(RenderPlanValidationException.class,
                result::accept);
    }

    @Test
    void reportsSchemaAndTableCapacityWithoutAttemptingAnAutoFix() {
        ObjectNode plan = validSemanticPlan();
        ObjectNode slide = (ObjectNode) plan.path("slides").get(0);
        slide.put("pageType", "TABLE");
        slide.put("layoutId", LayoutIds.TABLE_GENERIC_COMPACT);
        ArrayNode elements = (ArrayNode) slide.path("elements");
        ObjectNode image = (ObjectNode) elements.remove(2);
        ((ObjectNode) plan.path("assets").get(0)).put("mandatory", false);
        ObjectNode table = table("slide-06-table");
        ArrayNode columns = (ArrayNode) table.path("columns");
        while (columns.size() <= 5) {
            int index = columns.size() + 1;
            columns.addObject().put("key", "column-" + index)
                    .put("header", "列" + index).put("widthEmu", 500_000);
        }
        elements.add(table);
        ((ObjectNode) elements.get(1)).remove("text");

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));

        assertFalse(result.valid());
        assertHas(result, PptQualityCode.SCHEMA_INVALID);

        ObjectNode floatingPlan = validSemanticPlan();
        ((ObjectNode) floatingPlan.path("slides").get(0).path("elements").get(1)
                .path("resolvedStyle")).put("fontSizeHundredthPt", new BigDecimal("1E+308"));
        RenderPlanValidationResult floating = assertDoesNotThrow(() -> validator.validate(
                DraftSlideRenderPlan.of(floatingPlan), context(floatingPlan)));
        assertHas(floating, PptQualityCode.SCHEMA_INVALID);
        assertHas(result, PptQualityCode.TABLE_CAPACITY_EXCEEDED);
        assertTrue(columns.size() > 5, "Validator must not delete overflowing columns");
        assertTrue(image.path("assetId").asText().equals("figure_4_10"));
    }

    @Test
    void detectsSourceMappingAndAssetHashDriftFromFrozenInputs() {
        ObjectNode plan = validSemanticPlan();
        ((ObjectNode) plan.path("slides").get(0)).put("sourcePageId", "wrong-page");
        ((ObjectNode) plan.path("assets").get(0)).put("sha256", "sha256:" + "9".repeat(64));

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context(validSemanticPlan()));

        assertHas(result, PptQualityCode.SOURCE_PAGE_MAPPING_INVALID);
        assertHas(result, PptQualityCode.ASSET_HASH_MISMATCH);
    }

    @Test
    void rejectsOutOfRangeIntegersWithoutThrowingOrOverflowingGeometry() {
        ObjectNode plan = validSemanticPlan();
        ((ObjectNode) plan.path("slides").get(0).path("elements").get(1))
                .put("widthEmu", BigInteger.ONE.shiftLeft(100));

        RenderPlanValidationResult result = assertDoesNotThrow(() -> validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan)));

        assertFalse(result.valid());
        assertHas(result, PptQualityCode.SCHEMA_INVALID);
    }

    @Test
    void visibleAcademicTextMayContainPathsAndUuidExamplesButIdentityFieldsMayNot() {
        ObjectNode visiblePlan = validSemanticPlan();
        ((ObjectNode) visiblePlan.path("slides").get(0).path("elements").get(1)).put(
                "text",
                "示例路径 C:\\research\\dataset 与编号 550e8400-e29b-41d4-a716-446655440000");
        RenderPlanValidationResult visible = validator.validate(
                DraftSlideRenderPlan.of(visiblePlan), context(visiblePlan));
        assertTrue(visible.issues().stream().noneMatch(issue ->
                issue.qualityCode() == PptQualityCode.NON_DETERMINISTIC_RENDER_PLAN));
        assertTrue(visible.issues().stream().noneMatch(issue ->
                issue.message().contains("machine-specific absolute path")));

        ObjectNode identityPlan = validSemanticPlan();
        ((ObjectNode) identityPlan.path("slides").get(0)).put(
                "slideId", "slide-550e8400-e29b-41d4-a716-446655440000");
        RenderPlanValidationResult identity = validator.validate(
                DraftSlideRenderPlan.of(identityPlan), context(validSemanticPlan()));
        assertHas(identity, PptQualityCode.NON_DETERMINISTIC_RENDER_PLAN);
    }

    @Test
    void acceptsStableUuidDerivedPresentationIdentityButStillBindsItToContext() {
        ObjectNode plan = validSemanticPlan();
        plan.put("presentationId", "presentation-550e8400-e29b-41d4-a716-446655440000");

        RenderPlanValidationResult stable = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));
        assertTrue(stable.issues().stream().noneMatch(issue ->
                issue.qualityCode() == PptQualityCode.NON_DETERMINISTIC_RENDER_PLAN),
                () -> stable.issues().toString());

        ObjectNode differentExpected = validSemanticPlan();
        RenderPlanValidationResult mismatch = validator.validate(
                DraftSlideRenderPlan.of(plan), context(differentExpected));
        assertHas(mismatch, PptQualityCode.INVALID_REFERENCE);
    }

    @Test
    void rejectsBlankVisibleTextAndMissingExecutableStyleSource() {
        ObjectNode plan = validSemanticPlan();
        ObjectNode title = (ObjectNode) plan.path("slides").get(0).path("elements").get(1);
        title.put("text", "   ");
        title.remove("styleSource");

        RenderPlanValidationResult result = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));

        assertHas(result, PptQualityCode.UNRENDERABLE_PAGE);
        assertHas(result, PptQualityCode.SCHEMA_INVALID);
    }

    @Test
    void validatesFrozenNativeStatusCellCoordinatesTextFontAndThemeStyle() {
        ObjectNode plan = validSemanticPlan();
        ObjectNode slide = (ObjectNode) plan.path("slides").get(0);
        slide.put("pageType", "TABLE");
        slide.put("layoutId", LayoutIds.TABLE_TEST_RESULT_STATUS);
        ArrayNode elements = (ArrayNode) slide.path("elements");
        elements.remove(2);
        ((ObjectNode) plan.path("assets").get(0)).put("mandatory", false);
        ObjectNode table = table("slide-06-table");
        table.put("tableKind", "TEST_RESULT");
        ((ObjectNode) table.path("columns").get(1)).put("header", "状态");
        ((ArrayNode) table.path("rows").get(0).path("cells")).set(1, MAPPER.getNodeFactory().textNode("通过"));
        ArrayNode statuses = table.putArray("statusCells");
        statuses.addObject()
                .put("rowIndex", 0).put("columnIndex", 1).put("text", "通过")
                .put("fillColor", "#237A52").put("textColor", "#FFFFFF")
                .put("fontFaceId", "body-400").put("fontWeight", 400)
                .put("horizontalAlign", "CENTER").put("verticalAlign", "MIDDLE");
        elements.add(table);

        RenderPlanValidationResult valid = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));
        assertTrue(valid.valid(), () -> valid.issues().toString());

        ((ObjectNode) statuses.get(0)).put("rowIndex", 5).put("fillColor", "#B63A3A");
        RenderPlanValidationResult invalid = validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan));
        assertHas(invalid, PptQualityCode.INVALID_REFERENCE);
        assertHas(invalid, PptQualityCode.TABLE_CAPACITY_EXCEEDED);

        ((ObjectNode) statuses.get(0)).put("rowIndex", 0).put("fillColor", "#237A52").put("text", "");
        RenderPlanValidationResult blankStatus = assertDoesNotThrow(() -> validator.validate(
                DraftSlideRenderPlan.of(plan), context(plan)));
        assertHas(blankStatus, PptQualityCode.UNRENDERABLE_PAGE);
    }

    private RenderPlanValidationContext context(ObjectNode planWithExpectedHash) {
        ObjectNode engine = (ObjectNode) planWithExpectedHash.path("engine");
        ObjectNode slideSize = (ObjectNode) planWithExpectedHash.path("slideSize");
        Map<String, String> assetHashes = new java.util.LinkedHashMap<>();
        planWithExpectedHash.path("assets").forEach(asset -> assetHashes.put(
                asset.path("assetId").asText(), asset.path("sha256").asText()));
        return new RenderPlanValidationContext(
                planWithExpectedHash.path("presentationId").asText(),
                planWithExpectedHash.path("sourceTreeHash").asText(),
                engine.path("themeHash").asText(),
                engine.path("layoutCatalogHash").asText(),
                engine.path("fontProfileHash").asText(),
                expectedEngine(engine),
                expectedFontProfile(engine),
                MeasurementTestSupport.exactProfile(),
                MeasurementTestSupport.textMetrics(),
                slideSize.path("widthEmu").asLong(),
                slideSize.path("heightEmu").asLong(),
                List.of("page_06"),
                Map.of("page_06", new RenderPlanValidationContext.PageExpectation(
                        planWithExpectedHash.path("slides").get(0).path("pageType").asText(),
                        planWithExpectedHash.path("slides").get(0).path("layoutId").asText())),
                LayoutIds.ALL,
                Map.of(),
                Set.of("slideTitle", "tableBody", "imageFrame"),
                Set.of("typography.styles.slideTitle", "components.tableBody", "components.imageFrame"),
                assetHashes,
                RenderPlanValidationContext.SafeArea.none(),
                new RenderPlanValidationContext.StatusStyleExpectation(
                        "#237A52", "#9A6200", "#B63A3A", "#FFFFFF"),
                Map.of("slideTitle", 2_000, "tableHeader", 1_600, "tableBody", 1_600),
                1_000);
    }

    private RenderPlanValidationContext.EngineExpectation expectedEngine(ObjectNode engine) {
        return new RenderPlanValidationContext.EngineExpectation(
                engine.path("engineVersion").asText(),
                engine.path("themeId").asText(),
                engine.path("themeVersion").asText(),
                engine.path("layoutCatalogVersion").asText());
    }

    private RenderPlanValidationContext.FontProfileExpectation expectedFontProfile(ObjectNode engine) {
        ObjectNode profile = (ObjectNode) engine.path("resolvedFontProfile");
        Map<String, RenderPlanValidationContext.FontFaceExpectation> faces = new java.util.LinkedHashMap<>();
        profile.path("faces").forEach(face -> faces.put(
                face.path("fontFaceId").asText(),
                new RenderPlanValidationContext.FontFaceExpectation(
                        face.path("role").asText(),
                        face.path("weight").asInt(),
                        face.path("selectedFamily").asText(),
                        face.path("postScriptName").asText(),
                        face.path("fontSource").asText(),
                        face.path("fontFingerprint").asText(),
                        face.path("fallbackApplied").asBoolean())));
        return new RenderPlanValidationContext.FontProfileExpectation(
                profile.path("profileId").asText(),
                profile.path("measurementEngineVersion").asText(),
                faces);
    }

    private ObjectNode validSemanticPlan() {
        ObjectNode plan = readObject(EXAMPLE);
        ObjectNode slide = (ObjectNode) plan.path("slides").get(0);
        ArrayNode elements = (ArrayNode) slide.path("elements");
        ((ObjectNode) elements.get(0)).put("elementId", "slide-06-title");
        ObjectNode image = (ObjectNode) elements.get(1);
        image.put("elementId", "slide-06-image");
        image.put("heightEmu", 4_191_953);
        elements.insert(0, shape(
                "slide-06-background",
                0,
                0,
                plan.path("slideSize").path("widthEmu").asLong(),
                plan.path("slideSize").path("heightEmu").asLong(),
                0));
        return plan;
    }

    private ObjectNode addTemplateDecoration(ObjectNode plan) {
        String assetId = "tpl-safe-background";
        ObjectNode asset = ((ArrayNode) plan.path("assets")).addObject();
        asset.put("assetId", assetId);
        asset.put("bundlePath", "assets/templates/safe/background.png");
        asset.put("sha256", "sha256:" + "7".repeat(64));
        asset.put("mimeType", "image/png");
        asset.put("widthPx", 1600);
        asset.put("heightPx", 900);
        asset.put("assetKind", "TEMPLATE_DECORATION");
        asset.put("imageRole", "INFORMATION");
        asset.put("mandatory", true);

        ObjectNode slide = (ObjectNode) plan.path("slides").get(0);
        ObjectNode sourceImage = (ObjectNode) slide.path("elements").get(2);
        ObjectNode decoration = sourceImage.deepCopy();
        decoration.put("elementId", "slide-06-template-surface");
        decoration.put("xEmu", 0);
        decoration.put("yEmu", 0);
        decoration.put("widthEmu", plan.path("slideSize").path("widthEmu").asLong());
        decoration.put("heightEmu", plan.path("slideSize").path("heightEmu").asLong());
        decoration.put("zIndex", 5);
        decoration.put("decorative", true);
        decoration.put("assetId", assetId);
        decoration.put("fitMode", "CONTAIN");
        decoration.put("cropAllowed", false);
        decoration.remove("sourceCrop");
        ((ArrayNode) slide.path("elements")).insert(1, decoration);
        return decoration;
    }

    private ObjectNode shape(String id, long x, long y, long width, long height, int zIndex) {
        ObjectNode shape = MAPPER.createObjectNode();
        shape.put("elementId", id);
        shape.put("elementType", "SHAPE");
        shape.put("xEmu", x);
        shape.put("yEmu", y);
        shape.put("widthEmu", width);
        shape.put("heightEmu", height);
        shape.put("zIndex", zIndex);
        shape.put("shapeType", "RECTANGLE");
        ObjectNode style = shape.putObject("resolvedStyle");
        style.put("fillColor", "#F7F8FC");
        style.put("borderColor", "#F7F8FC");
        style.put("borderWidthEmu", 0);
        style.put("cornerRadiusEmu", 0);
        style.put("opacityPermille", 1000);
        return shape;
    }

    private ObjectNode table(String id) {
        ObjectNode table = MAPPER.createObjectNode();
        table.put("elementId", id);
        table.put("elementType", "TABLE");
        table.put("xEmu", 600_000);
        table.put("yEmu", 1_500_000);
        table.put("widthEmu", 5_000_000);
        table.put("heightEmu", 2_000_000);
        table.put("zIndex", 230);
        table.put("tableKind", "GENERIC");
        table.put("headerRowHeightEmu", 300_000);
        table.put("bodyRowHeightEmu", 300_000);
        ObjectNode style = table.putObject("resolvedStyle");
        style.put("headerFontFaceId", "body-600");
        style.put("headerFontWeight", 600);
        style.put("bodyFontFaceId", "body-400");
        style.put("bodyFontWeight", 400);
        style.put("fontFamily", "Microsoft YaHei");
        style.put("fontSizeHundredthPt", 1_600);
        style.put("fontWeight", 400);
        style.put("textColor", "#1D2340");
        style.put("headerFillColor", "#7257FF");
        style.put("bodyFillColor", "#FFFFFF");
        style.put("borderColor", "#E1E5EF");
        style.put("borderWidthEmu", 9_525);
        style.put("lineSpacingPermille", 1_300);
        style.put("paragraphSpaceBeforeEmu", 0);
        style.put("paragraphSpaceAfterEmu", 0);
        style.put("cellMarginLeftEmu", 0);
        style.put("cellMarginRightEmu", 0);
        style.put("cellMarginTopEmu", 0);
        style.put("cellMarginBottomEmu", 0);
        table.set("styleSource", MAPPER.createObjectNode()
                .put("component", "tableBody").put("themeToken", "components.tableBody"));
        ArrayNode columns = table.putArray("columns");
        columns.addObject().put("key", "column-1").put("header", "字段").put("widthEmu", 2_500_000);
        columns.addObject().put("key", "column-2").put("header", "结果").put("widthEmu", 2_500_000);
        table.putArray("rows").addObject().putArray("cells").add("登录").add("通过");
        return table;
    }

    private ObjectNode readObject(String resource) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing resource: " + resource);
            }
            return (ObjectNode) MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read resource: " + resource, exception);
        }
    }

    private void assertHas(RenderPlanValidationResult result, PptQualityCode code) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.qualityCode() == code),
                () -> "Expected " + code + " in " + result.issues());
    }
}
