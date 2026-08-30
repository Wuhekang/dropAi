package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanFreezer;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanHasher;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.LayoutCatalog;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.LayoutCatalogLoader;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.DeterministicTextMetricsService;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.FontFaceInventory;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.FontFaceResource;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.FontSource;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.GlyphMetricsModel;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ImageFitCalculator;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontFace;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontProfile;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontProfileResolver;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TableMetricsCalculator;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderability.v1.PageRenderabilityValidator;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ResolvedTheme;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ThemeEngine;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ThemeResolutionRequest;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidationContext;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.RenderPlanValidator;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.ValidatedSlideRenderPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class HealthManagementRenderPlanSupport {
    static final String ROOT = "ppt/rendering-fixtures/health-management/v1/";
    static final String EXPECTED_JSON = ROOT + "expected-render-plan.v1.json";
    static final String EXPECTED_HASH = ROOT + "expected-render-plan.v1.sha256";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FAMILY = "Microsoft YaHei";

    private HealthManagementRenderPlanSupport() {
    }

    static CompiledFixture compile() {
        ObjectNode treeDocument = object(readJson("validated-presentation-tree.json"));
        ObjectNode manifest = object(readJson("fixture-manifest.json"));
        Map<String, ObjectNode> tables = new LinkedHashMap<>();
        manifest.path("tables").forEach(registration -> {
            String tableId = registration.path("tableId").asText();
            tables.put(tableId, object(readJson(registration.path("bundlePath").asText())));
        });
        RenderingAssetBundle bundle = new RenderingAssetBundle(
                (ArrayNode) manifest.path("assets"),
                (ArrayNode) manifest.path("tables"),
                tables);
        ValidatedPresentationTree tree = new ValidatedPresentationTree(
                treeDocument,
                treeDocument.path("fixtureId").asText(),
                manifest.path("presentationTree").path("sha256").asText());
        ResolvedTheme theme = ThemeEngine.academicV1(Set.of(FAMILY))
                .resolve(ThemeResolutionRequest.academicPurpleV1());
        ResolvedFontProfile fonts = fontProfile(theme);
        LayoutCatalog catalog = new LayoutCatalogLoader().loadAcademicV1();
        DeterministicTextMetricsService textMetrics =
                new DeterministicTextMetricsService(new FixtureGlyphMetricsModel());
        RenderPlanCompiler compiler = new RenderPlanCompiler(
                new PageRenderabilityValidator(),
                textMetrics,
                new ImageFitCalculator(),
                new TableMetricsCalculator(textMetrics));
        DraftSlideRenderPlan draft = compiler.compile(tree, theme, catalog, bundle, fonts);

        RenderPlanValidationContext context = validationContext(
                treeDocument, manifest, theme, catalog, fonts, textMetrics, draft.document());
        var validation = new RenderPlanValidator().validate(draft, context);
        if (!validation.valid()) {
            throw new AssertionError("Fixture RenderPlan validation failed: " + validation.issues());
        }
        ValidatedSlideRenderPlan validated = validation.accept();
        FrozenSlideRenderPlan frozen = new RenderPlanFreezer().freeze(validated);
        String hash = new RenderPlanHasher().hash(frozen);
        return new CompiledFixture(
                tree, bundle, theme, fonts, catalog, draft, context, validated, frozen, hash);
    }

    static JsonNode readJson(String relativePath) {
        try (InputStream input = resource(ROOT + relativePath)) {
            return MAPPER.readTree(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fixture JSON " + relativePath, exception);
        }
    }

    static byte[] readResource(String resource) {
        try (InputStream input = resource(resource)) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read resource " + resource, exception);
        }
    }

    private static InputStream resource(String resource) {
        InputStream input = HealthManagementRenderPlanSupport.class.getClassLoader()
                .getResourceAsStream(resource);
        if (input == null) {
            throw new IllegalArgumentException("Missing classpath resource: " + resource);
        }
        return input;
    }

    private static ResolvedFontProfile fontProfile(ResolvedTheme theme) {
        Map<String, List<String>> requests = new LinkedHashMap<>();
        for (String role : List.of("body", "display")) {
            List<String> ordered = new ArrayList<>(theme.fontProfile().declaredFamilies().get(role));
            ordered.addAll(theme.fontProfile().allowedFallbackFamilies().get(role));
            requests.put(role, List.copyOf(ordered));
        }
        Map<String, Set<Integer>> weights = Map.of(
                "body", new LinkedHashSet<>(List.of(400, 500, 600)),
                "display", Set.of(700));
        FontFaceInventory inventory = (family, weight) -> {
            if (!FAMILY.equals(family) || !weights.values().stream().anyMatch(values -> values.contains(weight))) {
                return List.of();
            }
            byte[] bytes = ("dokiai-fixture-font-material-v1|" + family + "|" + weight)
                    .getBytes(StandardCharsets.UTF_8);
            return List.of(new FontFaceResource(
                    family,
                    "DokiAIFixtureCJK-" + weight,
                    weight,
                    FontSource.PROVIDED,
                    bytes));
        };
        return new ResolvedFontProfileResolver(inventory).resolve(
                theme.fontProfile().profileId(),
                requests,
                weights);
    }

    private static RenderPlanValidationContext validationContext(
            ObjectNode tree,
            ObjectNode manifest,
            ResolvedTheme theme,
            LayoutCatalog catalog,
            ResolvedFontProfile fonts,
            DeterministicTextMetricsService textMetrics,
            ObjectNode compiledPlan
    ) {
        List<String> pageIds = new ArrayList<>();
        tree.path("pages").forEach(page -> pageIds.add(page.path("sourcePageId").asText()));
        Map<String, RenderPlanValidationContext.PageExpectation> pageExpectations =
                new LinkedHashMap<>();
        compiledPlan.path("slides").forEach(renderedSlide -> pageExpectations.put(
                renderedSlide.path("sourcePageId").asText(),
                new RenderPlanValidationContext.PageExpectation(
                        renderedSlide.path("pageType").asText(),
                        renderedSlide.path("layoutId").asText())));
        Map<String, String> hashes = new LinkedHashMap<>();
        manifest.path("assets").forEach(asset -> hashes.put(
                asset.path("assetId").asText(),
                asset.path("sha256").asText()));
        JsonNode slide = theme.document().path("slide");
        long left = inches(slide.path("safeArea").path("leftIn").decimalValue());
        long top = inches(slide.path("safeArea").path("topIn").decimalValue());
        long right = inches(slide.path("safeArea").path("rightIn").decimalValue());
        long bottom = inches(slide.path("safeArea").path("bottomIn").decimalValue());
        Map<String, Integer> minimums = Map.of(
                "coverTitle", 5000,
                "sectionTitle", 4000,
                "slideTitle", 3500,
                "bodyText", 1600,
                "keyPointCard", 1600,
                "summaryCard", 1600,
                "caption", 1600,
                "pageNumber", 1200,
                "tableHeader", 1600,
                "tableBody", 1600);
        Set<String> components = new LinkedHashSet<>();
        theme.document().path("components").fieldNames().forEachRemaining(components::add);
        Map<String, Set<String>> allowedOverlaps = new LinkedHashMap<>();
        catalog.recipes().forEach(recipe -> allowedOverlaps.put(
                recipe.layoutId(),
                new LinkedHashSet<>(recipe.constraints().allowedContainedTextComponents())));
        Set<String> themeTokens = new LinkedHashSet<>();
        collectThemePaths(theme.document(), "", themeTokens);
        return new RenderPlanValidationContext(
                tree.path("fixtureId").asText(),
                manifest.path("presentationTree").path("sha256").asText(),
                theme.resolvedThemeHash(),
                catalog.catalogHash(),
                fonts.fontProfileHash(),
                new RenderPlanValidationContext.EngineExpectation(
                        RenderPlanCompiler.ENGINE_VERSION,
                        theme.themeId(),
                        theme.themeVersion(),
                        catalog.catalogVersion()),
                fontProfileExpectation(fonts),
                fonts,
                textMetrics,
                inches(slide.path("widthIn").decimalValue()),
                inches(slide.path("heightIn").decimalValue()),
                pageIds,
                pageExpectations,
                LayoutIds.ALL,
                allowedOverlaps,
                components,
                themeTokens,
                hashes,
                new RenderPlanValidationContext.SafeArea(left, top, right, bottom),
                new RenderPlanValidationContext.StatusStyleExpectation(
                        theme.document().path("colors").path("state").path("success").asText(),
                        theme.document().path("colors").path("state").path("warning").asText(),
                        theme.document().path("colors").path("state").path("danger").asText(),
                        theme.document().path("colors").path("text").path("inverse").asText()),
                minimums,
                1200);
    }

    private static RenderPlanValidationContext.FontProfileExpectation fontProfileExpectation(
            ResolvedFontProfile fonts
    ) {
        Map<String, RenderPlanValidationContext.FontFaceExpectation> faces = new LinkedHashMap<>();
        fonts.faces().forEach((role, byWeight) -> byWeight.forEach((weight, face) -> {
            String faceId = role + "-" + weight;
            faces.put(faceId, new RenderPlanValidationContext.FontFaceExpectation(
                    face.role(),
                    face.weight(),
                    face.selectedFamily(),
                    face.postScriptName(),
                    face.fontSource().name(),
                    face.fontFingerprint(),
                    face.fallbackApplied()));
        }));
        return new RenderPlanValidationContext.FontProfileExpectation(
                fonts.profileId(),
                fonts.measurementEngineVersion(),
                faces);
    }

    private static void collectThemePaths(JsonNode node, String prefix, Set<String> result) {
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            result.add(path);
            collectThemePaths(entry.getValue(), path, result);
        });
    }

    private static long inches(java.math.BigDecimal value) {
        return value.multiply(java.math.BigDecimal.valueOf(914_400L))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static ObjectNode object(JsonNode node) {
        if (!(node instanceof ObjectNode object)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return object;
    }

    record CompiledFixture(
            ValidatedPresentationTree tree,
            RenderingAssetBundle bundle,
            ResolvedTheme theme,
            ResolvedFontProfile fonts,
            LayoutCatalog catalog,
            DraftSlideRenderPlan draft,
            RenderPlanValidationContext validationContext,
            ValidatedSlideRenderPlan validated,
            FrozenSlideRenderPlan frozen,
            String hash
    ) {
    }

    /** Deliberately deterministic fixture metric model; production uses fingerprinted font bytes. */
    private static final class FixtureGlyphMetricsModel implements GlyphMetricsModel {
        @Override
        public long textWidthEmu(ResolvedFontFace face, int fontSizeHundredthPt, String text) {
            long fontEmu = BigInteger.valueOf(fontSizeHundredthPt)
                    .multiply(BigInteger.valueOf(12_700L))
                    .divide(BigInteger.valueOf(100L))
                    .longValueExact();
            long permille = text.codePoints().mapToLong(codePoint -> {
                if (Character.isWhitespace(codePoint)) {
                    return 350L;
                }
                if (codePoint <= 0x7f) {
                    return Character.isLetterOrDigit(codePoint) ? 560L : 500L;
                }
                return 1000L;
            }).sum();
            return BigInteger.valueOf(fontEmu)
                    .multiply(BigInteger.valueOf(permille))
                    .add(BigInteger.valueOf(500L))
                    .divide(BigInteger.valueOf(1000L))
                    .longValueExact();
        }

        @Override
        public long naturalLineHeightEmu(
                ResolvedFontFace face,
                int fontSizeHundredthPt
        ) {
            return BigInteger.valueOf(fontSizeHundredthPt)
                    .multiply(BigInteger.valueOf(12_700L))
                    .multiply(BigInteger.valueOf(108L))
                    .add(BigInteger.valueOf(5_000L))
                    .divide(BigInteger.valueOf(10_000L))
                    .longValueExact();
        }
    }
}
