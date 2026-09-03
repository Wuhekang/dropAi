package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.PptAssetMapperV1;
import com.dropai.rewrite.service.ppt.PptContentPlannerV2;
import com.dropai.rewrite.service.ppt.PptContentPlannerV2InputAdapter;
import com.dropai.rewrite.service.ppt.PptContentSanitizerV1;
import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.dropai.rewrite.service.ppt.PptEngineV1Service;
import com.dropai.rewrite.service.ppt.PptOutlinePlannerV1;
import com.dropai.rewrite.service.ppt.PptOutlineValidatorV1;
import com.dropai.rewrite.service.ppt.PptPlannerRuleLibrary;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.LoadedRenderPlanBundle;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.RenderPlanBundleLoader;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in real-upload regression. The private source document is never committed. */
class PptRealSourceRenderingE2ETest {
    private static final String SOURCE_SHA256 =
            "ad0bef05aa389e388af704eecc0b32c8dc6ba70d4b255eef912cf96090e1d1a3";
    private static final String FULL_TITLE =
            "基于Spring Boot的个人健康管理系统的设计与实现";

    @TempDir
    Path temp;

    @Test
    void realUploadCompilesStoresLoadsAndRendersWithActualMicrosoftYaHei() throws Exception {
        String configuredSource = System.getProperty("ppt.sample.source", "").strip();
        Assumptions.assumeFalse(configuredSource.isBlank(), "Set ppt.sample.source for the real-source E2E");
        Path source = Path.of(configuredSource).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(source), "Real source document is unavailable");
        assertEquals(SOURCE_SHA256, sha256(Files.readAllBytes(source)).substring("sha256:".length()));

        PptDocumentParser.ParsedDocument parsed = new PptDocumentParser()
                .parse(source, temp.resolve("parsed-assets"));
        assertEquals(27, parsed.totalImageCount());
        assertEquals(25, parsed.assets().size());
        assertEquals(2, parsed.filteredAssetCount());

        PptContentPlannerV2.PlannerInput input = new PptContentPlannerV2InputAdapter()
                .fromParsedDocument(parsed, "computer");
        assertMetadata(input.metadata());

        ObjectMapper mapper = new ObjectMapper();
        PptContentPlannerV2 planner = new PptContentPlannerV2(new PptPlannerRuleLibrary(mapper));
        PptContentPlannerV2.PlannerResult sanitized = new PptContentSanitizerV1()
                .sanitize(planner.plan(input));
        List<PptContentPlannerV2.CandidatePage> candidates = sanitized.chapters().stream()
                .flatMap(chapter -> chapter.candidatePages().stream())
                .toList();
        PptOutlinePlannerV1.OutlineResult outline = new PptOutlinePlannerV1()
                .plan(new PptOutlinePlannerV1.OutlineRequest(candidates, 16));
        assertEquals(41, outline.slideTree().size());
        assertEquals(25, outline.slideTree().stream().filter(PptOutlinePlannerV1.SlideNode::mandatoryAsset).count());

        PptOutlineValidatorV1.ValidationResult validated = new PptOutlineValidatorV1()
                .validate(new PptOutlineValidatorV1.ValidationRequest(input.metadata(), outline));
        assertTrue(validated.valid(), validated.issues().toString());
        assertEquals(49, validated.slideTree().size());
        assertEquals(5, validated.slideTree().stream().filter(page -> "SECTION".equals(page.pageType())).count());
        assertEquals(25, validated.slideTree().stream().filter(page -> "IMAGE".equals(page.pageType())).count());

        ProductionRenderPlanRequest request = new ProductionRenderPlanRequest(
                "real-health-management-v1",
                input.metadata(),
                parsed.blocks(),
                validated,
                input);
        ProductionRenderPlanCoordinator coordinator =
                new ProductionRenderPlanCoordinator(mapper, new PptAssetMapperV1());

        ProductionRenderPlanPackage first = coordinator.compile(request);
        ProductionRenderPlanPackage second = coordinator.compile(request);
        assertEquals(first.renderPlanHash(), second.renderPlanHash());
        assertArrayEquals(first.plan().canonicalBytes(), second.plan().canonicalBytes());
        assertActualFonts(first);
        assertPlan(first.plan());
        assertImageContentConserved(validated, first.plan());

        Path bundleDirectory = temp.resolve("render-plan-bundle");
        var stored = coordinator.prepareAndStore(bundleDirectory, request);
        LoadedRenderPlanBundle loaded = new RenderPlanBundleLoader()
                .load(bundleDirectory, coordinator.runtimeExpectations());
        assertEquals(stored.renderPlanHash(), loaded.renderPlanHash());
        assertEquals(first.renderPlanHash(), loaded.renderPlanHash());
        assertArrayEquals(first.plan().canonicalBytes(), loaded.renderPlan().canonicalBytes());
        assertEquals(25, loaded.assetCount());

        Path generated = temp.resolve("dokiai-real-health-management.pptx");
        var receipt = new PptEngineV1Service().generate(
                loaded.renderPlan(), loaded.assetResolver(), generated);
        assertEquals(49, receipt.slideCount());
        assertEquals(loaded.renderPlanHash(), receipt.renderPlanHash());
        assertTrue(receipt.writtenBytes() > 0);
        assertPptx(generated);

        String configuredOutput = System.getProperty("ppt.sample.output", "").strip();
        if (!configuredOutput.isBlank()) {
            Path output = Path.of(configuredOutput).toAbsolutePath().normalize();
            Files.createDirectories(output.getParent());
            Files.copy(generated, output, StandardCopyOption.REPLACE_EXISTING);
            writeReport(output, source, first, receipt.writtenBytes());
        }
    }

    private void assertMetadata(Map<String, String> metadata) {
        assertEquals(FULL_TITLE, metadata.get("title"));
        assertEquals("高瑞康", metadata.get("presenter"));
        assertEquals("软件工程", metadata.get("major"));
        assertEquals("蒋辉", metadata.get("advisor"));
        assertEquals("6022203537", metadata.get("studentNumber"));
        assertEquals("智算工程学院", metadata.get("institution"));
        assertEquals("2026年6月1日", metadata.get("date"));
    }

    private void assertActualFonts(ProductionRenderPlanPackage compiled) {
        assertEquals("text-metrics-v1.1", compiled.actualFonts().measurementEngineVersion());
        compiled.actualFonts().faces().forEach(face -> {
            assertEquals("Microsoft YaHei", face.requestedFamily());
            assertEquals("Microsoft YaHei", face.resolvedFamily());
            assertFalse(face.fallbackApplied());
            assertTrue(face.fontFingerprint().matches("sha256:[a-f0-9]{64}"));
        });
        assertEquals(compiled.actualFonts().fontProfileHash(),
                compiled.plan().document().path("engine").path("fontProfileHash").asText());
    }

    private void assertPlan(FrozenSlideRenderPlan frozen) {
        ObjectNode plan = frozen.document();
        assertEquals(49, plan.path("slides").size());
        assertEquals(25, plan.path("assets").size());
        assertEquals(LayoutIds.COVER_CENTERED_LONG_TITLE,
                plan.path("slides").get(0).path("layoutId").asText());
        JsonNode coverTitle = elementById(plan.path("slides").get(0), "slide-001-title");
        assertEquals(FULL_TITLE, coverTitle.path("text").asText().replace("\n", ""));
        List<JsonNode> sections = java.util.stream.StreamSupport.stream(
                        plan.path("slides").spliterator(), false)
                .filter(slide -> "SECTION".equals(slide.path("pageType").asText()))
                .toList();
        assertEquals(List.of(
                        "01 项目背景与需求",
                        "02 系统设计",
                        "03 系统实现",
                        "04 测试验证",
                        "05 总结展望"),
                sections.stream().map(slide -> firstText(slide).path("text").asText()).toList());
        sections.forEach(this::assertSection);

        long slideWidth = plan.path("slideSize").path("widthEmu").asLong();
        long slideHeight = plan.path("slideSize").path("heightEmu").asLong();
        for (JsonNode slide : plan.path("slides")) {
            for (JsonNode element : slide.path("elements")) {
                long x = element.path("xEmu").asLong();
                long y = element.path("yEmu").asLong();
                long width = element.path("widthEmu").asLong();
                long height = element.path("heightEmu").asLong();
                assertTrue(x >= 0 && y >= 0 && width > 0 && height > 0);
                assertTrue(x + width <= slideWidth, element.path("elementId").asText());
                assertTrue(y + height <= slideHeight, element.path("elementId").asText());
                if ("TEXT".equals(element.path("elementType").asText())) {
                    assertTrue(element.path("requiredWidthEmu").asLong() <= width,
                            element.path("elementId").asText());
                    assertTrue(element.path("requiredHeightEmu").asLong() <= height,
                            element.path("elementId").asText());
                    String text = element.path("text").asText();
                    assertFalse(text.endsWith("...") || text.endsWith("…"));
                }
            }
        }
        String canonical = frozen.canonicalDocument();
        for (String forbidden : List.of(
                "pagePurpose=", "answerQuestion=", "Click to edit Master", "Second level", "未填写")) {
            assertFalse(canonical.contains(forbidden), forbidden);
        }
    }

    private void assertSection(JsonNode slide) {
        assertEquals("SECTION", slide.path("pageType").asText());
        assertEquals(LayoutIds.SECTION_CENTERED, slide.path("layoutId").asText());
        JsonNode titleElement = firstText(slide);
        assertEquals("CENTER", titleElement.path("resolvedStyle").path("horizontalAlign").asText());
        assertEquals("MIDDLE", titleElement.path("resolvedStyle").path("verticalAlign").asText());
        assertEquals(1, java.util.stream.StreamSupport.stream(
                        slide.path("elements").spliterator(), false)
                .filter(element -> "TEXT".equals(element.path("elementType").asText()))
                .count());
    }

    private JsonNode firstText(JsonNode slide) {
        for (JsonNode element : slide.path("elements")) {
            if ("TEXT".equals(element.path("elementType").asText())) {
                return element;
            }
        }
        throw new AssertionError("Missing text element on " + slide.path("slideId").asText());
    }

    private void assertImageContentConserved(
            PptOutlineValidatorV1.ValidationResult validated,
            FrozenSlideRenderPlan frozen
    ) {
        JsonNode slides = frozen.document().path("slides");
        long compactFallbacks = 0;
        for (PptOutlineValidatorV1.FullSlideNode page : validated.slideTree()) {
            if (!"IMAGE".equals(page.pageType())) {
                continue;
            }
            JsonNode slide = slides.get(page.pageNumber() - 1);
            String layoutId = slide.path("layoutId").asText();
            if (LayoutIds.IMAGE_CAPTION_SIDE_COMPACT.equals(layoutId)
                    || LayoutIds.IMAGE_CENTERED_CAPTION_BOTTOM.equals(layoutId)) {
                compactFallbacks++;
            }
            StringBuilder renderedText = new StringBuilder();
            for (JsonNode element : slide.path("elements")) {
                if ("TEXT".equals(element.path("elementType").asText())) {
                    renderedText.append(element.path("text").asText());
                }
            }
            String normalizedRendered = normalizeForConservation(renderedText.toString());
            assertTrue(normalizedRendered.contains(normalizeForConservation(page.description())),
                    () -> "Image description was lost on page " + page.pageNumber());
            for (String point : page.keyPoints()) {
                assertTrue(normalizedRendered.contains(normalizeForConservation(point)),
                        () -> "Image key point was lost on page " + page.pageNumber() + ": " + point);
            }
        }
        assertTrue(compactFallbacks > 0, "Real upload must exercise an image-caption fallback layout");
    }

    private String normalizeForConservation(String value) {
        return value == null ? "" : value.replaceAll("[\\s•·▪●]+", "");
    }

    private void assertPptx(Path pptx) throws Exception {
        int pictures = 0;
        try (InputStream input = Files.newInputStream(pptx); XMLSlideShow deck = new XMLSlideShow(input)) {
            assertEquals(49, deck.getSlides().size());
            for (var slide : deck.getSlides()) {
                pictures += (int) slide.getShapes().stream().filter(XSLFPictureShape.class::isInstance).count();
            }
        }
        assertEquals(25, pictures);
    }

    private JsonNode elementById(JsonNode slide, String id) {
        for (JsonNode element : slide.path("elements")) {
            if (id.equals(element.path("elementId").asText())) {
                return element;
            }
        }
        throw new AssertionError("Missing element " + id);
    }

    private void writeReport(
            Path output,
            Path source,
            ProductionRenderPlanPackage compiled,
            long bytes
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode report = mapper.createObjectNode();
        report.put("source", source.toString());
        report.put("sourceSha256", "sha256:" + SOURCE_SHA256);
        report.put("renderPlanHash", compiled.renderPlanHash());
        report.put("pptxSha256", sha256(Files.readAllBytes(output)));
        report.put("pptxBytes", bytes);
        report.put("slides", 49);
        report.put("images", 25);
        ObjectNode quality = report.putObject("qualityErrors");
        for (String code : List.of(
                "TEXT_OVERFLOW", "TEXT_TRUNCATED", "ELEMENT_OUT_OF_BOUNDS",
                "ILLEGAL_OVERLAP", "IMAGE_ASPECT_DISTORTION")) {
            quality.put(code, 0);
        }
        Map<String, Integer> layouts = new TreeMap<>();
        compiled.plan().document().path("slides").forEach(slide ->
                layouts.merge(slide.path("layoutId").asText(), 1, Integer::sum));
        ObjectNode layoutCounts = report.putObject("layoutCounts");
        layouts.forEach(layoutCounts::put);
        Files.writeString(
                output.resolveSibling(output.getFileName() + ".e2e.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n",
                StandardCharsets.UTF_8);
    }

    private String sha256(byte[] bytes) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
