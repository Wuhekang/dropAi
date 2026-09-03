package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanCanonicalizer;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanHasher;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.DeterministicTextMetricsService;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.GlyphMetricsModel;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ImageFitCalculator;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontFace;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TableMetricsCalculator;
import com.dropai.rewrite.service.ppt.rendering.renderability.v1.PageRenderabilityValidator;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthManagementRenderPlanTest {
    @Test
    void compilesFortyPagesTwentyFiveAssetsAndTwoEditableTablesWithoutRenderer() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        JsonNode plan = fixture.frozen().document();

        assertEquals(40, plan.path("slides").size());
        assertEquals(25, plan.path("assets").size());
        assertEquals(40, uniqueText(plan.path("slides"), "sourcePageId").size());
        assertEquals(List.of("COVER", "THANKS"), List.of(
                plan.path("slides").get(0).path("pageType").asText(),
                plan.path("slides").get(39).path("pageType").asText()));
        assertEquals(2, countElements(plan, "TABLE"));
        assertEquals(25, countElements(plan, "IMAGE"));
        assertEquals(69, countElements(plan, "SHAPE"));
        assertEquals(4, countElements(plan, "CONNECTOR"));
        assertTrue(uniqueText(plan.path("slides"), "layoutId").stream().allMatch(LayoutIds.ALL::contains));
        assertEquals(LayoutIds.CONTENT_ARCHITECTURE_LAYERS,
                plan.path("slides").get(4).path("layoutId").asText());
        assertEquals(LayoutIds.CONTENT_ARCHITECTURE_LAYERS,
                plan.path("slides").get(9).path("layoutId").asText());
        assertEquals(LayoutIds.CONTENT_ARCHITECTURE_LAYERS,
                plan.path("slides").get(11).path("layoutId").asText());
        assertEquals(LayoutIds.IMAGE_RESULT_CHART_WITH_FINDING,
                plan.path("slides").get(26).path("layoutId").asText());
        assertEquals(6, countSlideElements(plan.path("slides").get(1), "SHAPE"));
        assertVisualGroup(plan.path("slides").get(2), "card-01");
        assertVisualGroup(plan.path("slides").get(4), "layer-01");
        assertVisualGroup(plan.path("slides").get(15), "step-01");
        JsonNode firstImage = firstElement(plan.path("slides").get(5), "IMAGE");
        assertTrue(firstImage.path("resolvedStyle").path("shadow").isObject());
        assertFalse(plan.path("slides").get(5).path("elements").get(0)
                .path("resolvedStyle").has("shadow"));
        JsonNode testTable = firstElement(plan.path("slides").get(36), "TABLE");
        assertEquals(5, testTable.path("statusCells").size());
        for (int rowIndex = 0; rowIndex < testTable.path("rows").size(); rowIndex++) {
            JsonNode status = testTable.path("statusCells").get(rowIndex);
            assertEquals(rowIndex, status.path("rowIndex").asInt());
            assertEquals(3, status.path("columnIndex").asInt());
            assertEquals(testTable.path("rows").get(rowIndex).path("cells").get(3).asText(),
                    status.path("text").asText());
            assertEquals(testTable.path("resolvedStyle").path("bodyFontFaceId").asText(),
                    status.path("fontFaceId").asText());
            assertEquals("CENTER", status.path("horizontalAlign").asText());
            assertEquals("MIDDLE", status.path("verticalAlign").asText());
        }
        JsonNode title = firstElement(plan.path("slides").get(2), "TEXT");
        assertTrue(title.path("lineCount").asInt() > 0);
        assertTrue(title.path("requiredWidthEmu").asLong() >= 0);
        assertTrue(title.path("requiredHeightEmu").asLong() > 0);
        assertTrue(title.path("resolvedStyle").path("lineHeightEmu").asLong() > 0);
        assertTrue(plan.path("engine").path("resolvedFontProfile").path("faces").size() >= 4);
        plan.path("slides").forEach(this::assertElementsHaveCanonicalPaintOrder);
        assertFalse(fixture.frozen().canonicalDocument().contains("pagePurpose"));
        assertFalse(fixture.frozen().canonicalDocument().contains("answerQuestion"));
        assertFalse(fixture.frozen().canonicalDocument().matches("(?s).*[A-Za-z]:[\\\\/].*"));
    }

    @Test
    void repeatedCompilationIsByteForByteAndHashIdentical() {
        HealthManagementRenderPlanSupport.CompiledFixture first =
                HealthManagementRenderPlanSupport.compile();
        for (int iteration = 0; iteration < 5; iteration++) {
            HealthManagementRenderPlanSupport.CompiledFixture repeated =
                    HealthManagementRenderPlanSupport.compile();
            assertArrayEquals(first.frozen().canonicalBytes(), repeated.frozen().canonicalBytes());
            assertEquals(first.hash(), repeated.hash());
        }
    }

    @Test
    void agendaTitleAndEveryAgendaEntryAreHorizontallyCentered() {
        JsonNode agendaSlide = HealthManagementRenderPlanSupport.compile()
                .frozen().document().path("slides").get(1);
        String slideId = agendaSlide.path("slideId").asText();

        assertEquals("AGENDA", agendaSlide.path("pageType").asText());
        assertEquals("CENTER", elementById(agendaSlide, slideId + "-title")
                .path("resolvedStyle").path("horizontalAlign").asText());
        for (int entry = 1; entry <= 5; entry++) {
            String elementId = String.format("%s-agenda-step-%02d-text", slideId, entry);
            assertEquals("CENTER", elementById(agendaSlide, elementId)
                    .path("resolvedStyle").path("horizontalAlign").asText(), elementId);
        }
    }

    @Test
    void committedSnapshotIsCompilerOutputAndUsesCanonicalHash() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        assertArrayEquals(
                HealthManagementRenderPlanSupport.readResource(
                        HealthManagementRenderPlanSupport.EXPECTED_JSON),
                fixture.frozen().canonicalBytes());
        String expectedHash = new String(
                HealthManagementRenderPlanSupport.readResource(
                        HealthManagementRenderPlanSupport.EXPECTED_HASH),
                StandardCharsets.UTF_8).strip();
        assertEquals(expectedHash, fixture.hash());
        assertEquals(fixture.hash(), new RenderPlanHasher().hash(fixture.frozen()));
    }

    @Test
    void frozenPlanDoesNotExposeMutableState() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        byte[] before = fixture.frozen().canonicalBytes();
        fixture.frozen().document().path("slides").get(0).deepCopy();
        byte[] escaped = fixture.frozen().canonicalBytes();
        escaped[0] = (byte) (escaped[0] + 1);
        assertArrayEquals(before, fixture.frozen().canonicalBytes());
    }

    @Test
    void schemaValidatorRejectsMutationInsteadOfRepairingIt() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        JsonNode mutated = fixture.draft().document();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mutated.path("slides").get(0))
                .put("layoutId", "unknown-layout.v1");
        var result = new RenderPlanValidator().validate(
                com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan.of(mutated),
                fixture.validationContext());
        assertFalse(result.valid());
        assertThrows(
                com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidationException.class,
                result::accept);
    }

    @Test
    void canonicalizerRejectsFloatingGeometry() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        JsonNode mutated = fixture.validated().document();
        ((com.fasterxml.jackson.databind.node.ObjectNode) mutated.path("slides").get(0)
                .path("elements").get(0)).put("xEmu", 0.5);
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderPlanCanonicalizer().canonicalBytes(mutated));
    }

    @Test
    void contentVisualSplitCompilesItsSingleBoundImage() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode tree = fixture.tree().document();
        ObjectNode contentPage = (ObjectNode) tree.path("pages").get(25);
        JsonNode imageBinding = tree.path("pages").get(26).path("assets").get(0).deepCopy();
        contentPage.put("contentType", "MIXED");
        contentPage.putArray("assets").add(imageBinding);

        JsonNode plan = compilerForTest().compile(
                new ValidatedPresentationTree(
                        tree,
                        fixture.tree().presentationId(),
                        fixture.tree().sourceTreeHash()),
                fixture.theme(), fixture.catalog(), fixture.bundle(), fixture.fonts()).document();
        JsonNode slide = plan.path("slides").get(25);
        assertEquals(LayoutIds.CONTENT_TEXT_VISUAL_SPLIT, slide.path("layoutId").asText());
        assertEquals(1, countSlideElements(slide, "IMAGE"));
        assertEquals("figure_4_10", firstElement(slide, "IMAGE").path("assetId").asText());
    }

    @Test
    void comparisonLayoutCompilesTwoIndependentEditableColumns() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode tree = fixture.tree().document();
        ((ObjectNode) tree.path("pages").get(25)).put("contentType", "COMPARISON");

        JsonNode plan = compilerForTest().compile(
                new ValidatedPresentationTree(
                        tree,
                        fixture.tree().presentationId(),
                        fixture.tree().sourceTreeHash()),
                fixture.theme(), fixture.catalog(), fixture.bundle(), fixture.fonts()).document();
        JsonNode slide = plan.path("slides").get(25);
        assertEquals(LayoutIds.CONTENT_COMPARISON_COLUMNS, slide.path("layoutId").asText());
        assertVisualGroup(slide, "column-01");
        assertVisualGroup(slide, "column-02");
    }

    @Test
    void contentRejectsMoreThanOneV1AssetBinding() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode tree = fixture.tree().document();
        ObjectNode contentPage = (ObjectNode) tree.path("pages").get(25);
        contentPage.put("contentType", "MIXED");
        ArrayNode bindings = contentPage.putArray("assets");
        bindings.add(tree.path("pages").get(26).path("assets").get(0).deepCopy());
        bindings.add(tree.path("pages").get(28).path("assets").get(0).deepCopy());

        assertThrows(RenderPlanCompilationException.class, () -> compilerForTest().compile(
                new ValidatedPresentationTree(
                        tree,
                        fixture.tree().presentationId(),
                        fixture.tree().sourceTreeHash()),
                fixture.theme(), fixture.catalog(), fixture.bundle(), fixture.fonts()));
    }

    @Test
    void extremelyNarrowImageFailsTheRecipeMinimumAreaRatio() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode manifest = fixture.bundle().manifestDocument();
        ArrayNode changedAssets = (ArrayNode) manifest.path("assets");
        for (JsonNode asset : changedAssets) {
            if ("figure_2_01".equals(asset.path("assetId").asText())) {
                ((ObjectNode) asset).put("widthPx", 1).put("heightPx", 10_000);
            }
        }
        RenderingAssetBundle narrowBundle = new RenderingAssetBundle(
                changedAssets,
                (ArrayNode) manifest.path("tables"),
                fixture.bundle().tableIndex());

        RenderPlanCompilationException failure = assertThrows(
                RenderPlanCompilationException.class,
                () -> compilerForTest().compile(
                        fixture.tree(), fixture.theme(), fixture.catalog(), narrowBundle, fixture.fonts()));
        assertEquals(com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode.IMAGE_ASPECT_DISTORTION,
                failure.qualityCode());
        assertTrue(failure.getMessage().contains("minImageAreaRatio"));
    }

    @Test
    void chartRecipeFallsBackDeterministicallyWhenSquareImageCannotMeetItsMinimumArea() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode manifest = fixture.bundle().manifestDocument();
        ArrayNode changedAssets = (ArrayNode) manifest.path("assets");
        for (JsonNode asset : changedAssets) {
            if ("figure_4_10".equals(asset.path("assetId").asText())) {
                ((ObjectNode) asset).put("widthPx", 1_000).put("heightPx", 1_000);
            }
        }
        RenderingAssetBundle squareBundle = new RenderingAssetBundle(
                changedAssets,
                (ArrayNode) manifest.path("tables"),
                fixture.bundle().tableIndex());

        JsonNode plan = compilerForTest().compile(
                fixture.tree(), fixture.theme(), fixture.catalog(), squareBundle, fixture.fonts()).document();

        assertEquals(LayoutIds.IMAGE_PROOF_SCREENSHOT_WIDE,
                plan.path("slides").get(26).path("layoutId").asText());
    }

    @Test
    void effectImageFallbackPreservesDescriptionAndEveryKeyPoint() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode tree = fixture.tree().document();
        ObjectNode page = (ObjectNode) tree.path("pages").get(26);
        page.put("contentType", "FIGURE");

        ObjectNode manifest = fixture.bundle().manifestDocument();
        ArrayNode changedAssets = (ArrayNode) manifest.path("assets");
        for (JsonNode asset : changedAssets) {
            if ("figure_4_10".equals(asset.path("assetId").asText())) {
                ((ObjectNode) asset).put("imageRole", "EFFECT").put("widthPx", 1_000).put("heightPx", 1_000);
            }
        }
        RenderingAssetBundle squareEffectBundle = new RenderingAssetBundle(
                changedAssets,
                (ArrayNode) manifest.path("tables"),
                fixture.bundle().tableIndex());

        JsonNode plan = compilerForTest().compile(
                new ValidatedPresentationTree(
                        tree,
                        fixture.tree().presentationId(),
                        fixture.tree().sourceTreeHash()),
                fixture.theme(), fixture.catalog(), squareEffectBundle, fixture.fonts()).document();
        JsonNode slide = plan.path("slides").get(26);

        assertEquals(LayoutIds.IMAGE_CENTERED_CAPTION_BOTTOM, slide.path("layoutId").asText());
        String rendered = normalizeVisibleText(slide);
        assertTrue(rendered.contains(normalizeText(page.path("description").asText())));
        page.path("keyPoints").forEach(point ->
                assertTrue(rendered.contains(normalizeText(point.asText())), point.asText()));
    }

    @Test
    void validatorRejectsMutatedTestStatusStyleInsteadOfRepairingIt() {
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile();
        ObjectNode plan = fixture.draft().document();
        ObjectNode table = (ObjectNode) firstElement(plan.path("slides").get(36), "TABLE");
        ((ObjectNode) table.path("statusCells").get(0)).put("fillColor", "#B63A3A");

        var result = new RenderPlanValidator().validate(
                com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan.of(plan),
                fixture.validationContext());

        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue ->
                issue.qualityCode() == com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode.SCHEMA_INVALID
                        && issue.message().contains("status style")));
    }

    private int countElements(JsonNode plan, String elementType) {
        int count = 0;
        for (JsonNode slide : plan.path("slides")) {
            for (JsonNode element : slide.path("elements")) {
                if (elementType.equals(element.path("elementType").asText())) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countSlideElements(JsonNode slide, String elementType) {
        int count = 0;
        for (JsonNode element : slide.path("elements")) {
            if (elementType.equals(element.path("elementType").asText())) {
                count++;
            }
        }
        return count;
    }

    private String normalizeVisibleText(JsonNode slide) {
        StringBuilder text = new StringBuilder();
        slide.path("elements").forEach(element -> {
            if ("TEXT".equals(element.path("elementType").asText())) {
                text.append(element.path("text").asText());
            }
        });
        return normalizeText(text.toString());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("[\\s•·▪●]+", "");
    }

    private JsonNode firstElement(JsonNode slide, String elementType) {
        for (JsonNode element : slide.path("elements")) {
            if (elementType.equals(element.path("elementType").asText())) {
                return element;
            }
        }
        throw new AssertionError("Missing element type " + elementType);
    }

    private void assertVisualGroup(JsonNode slide, String role) {
        String prefix = slide.path("slideId").asText() + "-" + role;
        JsonNode shape = elementById(slide, prefix);
        JsonNode text = elementById(slide, prefix + "-text");
        assertEquals("SHAPE", shape.path("elementType").asText());
        assertEquals("TEXT", text.path("elementType").asText());
        assertEquals("keyPointCard", shape.path("styleSource").path("component").asText());
        assertEquals("keyPointCard", text.path("styleSource").path("component").asText());
        assertTrue(shape.path("zIndex").asInt() < text.path("zIndex").asInt());
        assertTrue(shape.path("xEmu").asLong() <= text.path("xEmu").asLong());
        assertTrue(shape.path("yEmu").asLong() <= text.path("yEmu").asLong());
        assertTrue(shape.path("xEmu").asLong() + shape.path("widthEmu").asLong()
                >= text.path("xEmu").asLong() + text.path("widthEmu").asLong());
        assertTrue(shape.path("yEmu").asLong() + shape.path("heightEmu").asLong()
                >= text.path("yEmu").asLong() + text.path("heightEmu").asLong());
    }

    private JsonNode elementById(JsonNode slide, String elementId) {
        for (JsonNode element : slide.path("elements")) {
            if (elementId.equals(element.path("elementId").asText())) {
                return element;
            }
        }
        throw new AssertionError("Missing element " + elementId);
    }

    private void assertElementsHaveCanonicalPaintOrder(JsonNode slide) {
        int previousZ = -1;
        String previousId = "";
        for (JsonNode element : slide.path("elements")) {
            int currentZ = element.path("zIndex").asInt();
            String currentId = element.path("elementId").asText();
            assertTrue(currentZ > previousZ || currentZ == previousZ && currentId.compareTo(previousId) >= 0,
                    slide.path("slideId").asText() + " has non-canonical element order");
            previousZ = currentZ;
            previousId = currentId;
        }
    }

    private RenderPlanCompiler compilerForTest() {
        GlyphMetricsModel metricsModel = new GlyphMetricsModel() {
            @Override
            public long textWidthEmu(ResolvedFontFace face, int fontSizeHundredthPt, String text) {
                long fontEmu = BigInteger.valueOf(fontSizeHundredthPt)
                        .multiply(BigInteger.valueOf(12_700L))
                        .divide(BigInteger.valueOf(100L))
                        .longValueExact();
                long permille = text.codePoints().mapToLong(codePoint -> codePoint <= 0x7f ? 560L : 1000L).sum();
                return BigInteger.valueOf(fontEmu)
                        .multiply(BigInteger.valueOf(permille))
                        .divide(BigInteger.valueOf(1000L))
                        .longValueExact();
            }

            @Override
            public long naturalLineHeightEmu(ResolvedFontFace face, int fontSizeHundredthPt) {
                return BigInteger.valueOf(fontSizeHundredthPt)
                        .multiply(BigInteger.valueOf(12_700L))
                        .multiply(BigInteger.valueOf(108L))
                        .divide(BigInteger.valueOf(10_000L))
                        .longValueExact();
            }
        };
        DeterministicTextMetricsService textMetrics = new DeterministicTextMetricsService(metricsModel);
        return new RenderPlanCompiler(
                new PageRenderabilityValidator(),
                textMetrics,
                new ImageFitCalculator(),
                new TableMetricsCalculator(textMetrics));
    }

    private Set<String> uniqueText(JsonNode values, String field) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.path(field).asText()));
        return result;
    }
}
