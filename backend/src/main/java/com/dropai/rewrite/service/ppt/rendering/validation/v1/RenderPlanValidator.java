package com.dropai.rewrite.service.ppt.rendering.validation.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.RenderPlanIssue;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.DeterministicTextMetricsService;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TextFitRequest;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TextFitResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aggregates frozen-schema checks with deterministic semantic checks. It never
 * moves, resizes, deletes or otherwise repairs an element.
 */
public final class RenderPlanValidator {
    private static final String SCHEMA_RESOURCE =
            "ppt/rendering-contract/v1/render-plan.v1.schema.json";
    private static final long EMU_PER_PIXEL_AT_96_DPI = 9_525L;
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final int MAX_TEXT_CODE_UNITS = 8_000;
    private static final int MAX_TABLE_CELL_CODE_UNITS = 2_000;
    private static final int MAX_SLIDES = 200;
    private static final int MAX_ASSETS = 500;
    private static final int MAX_ELEMENTS_PER_SLIDE = 256;
    private static final Set<String> INT_FIELDS = Set.of(
            "index", "zIndex", "weight", "fontSizeHundredthPt", "fontWeight",
            "headerFontWeight", "bodyFontWeight", "lineSpacingPermille", "lineCount",
            "widthPx", "heightPx", "leftPermille", "topPermille", "rightPermille",
            "bottomPermille", "opacityPermille", "angleThousandthDegree", "rowIndex",
            "columnIndex");
    private static final Set<String> IDENTITY_FIELDS = Set.of(
            "presentationId", "slideId", "sourcePageId", "elementId", "assetId",
            "profileId", "fontFaceId");
    private static final Set<String> TIMESTAMP_FIELDS = Set.of(
            "timestamp", "generatedAt", "createdAt", "updatedAt");

    private final Schema schema;

    public RenderPlanValidator() {
        this.schema = loadSchema();
    }

    public RenderPlanValidationResult validate(
            DraftSlideRenderPlan draft,
            RenderPlanValidationContext context
    ) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(context, "context");
        JsonNode document = draft.document();
        List<RenderPlanIssue> issues = new ArrayList<>();

        schema.validate(document).stream()
                .map(Object::toString)
                .sorted()
                .forEach(message -> issues.add(issue(
                        PptQualityCode.SCHEMA_INVALID, null, null, message)));

        boolean safeIntegers = validateIntegerRanges(document, "$", null, issues);
        boolean safeCollections = validateCollectionLimits(document, issues);
        validatePortableValues(document, "$", null, issues);
        if (safeIntegers && safeCollections) {
            validatePlan(document, context, issues);
        }
        return new RenderPlanValidationResult(issues, document);
    }

    public ValidatedSlideRenderPlan validateAndAccept(
            DraftSlideRenderPlan draft,
            RenderPlanValidationContext context
    ) {
        return validate(draft, context).accept();
    }

    private void validatePlan(
            JsonNode plan,
            RenderPlanValidationContext context,
            List<RenderPlanIssue> issues
    ) {
        validateFrozenIdentity(plan, context, issues);
        long slideWidth = positiveLong(plan.path("slideSize"), "widthEmu");
        long slideHeight = positiveLong(plan.path("slideSize"), "heightEmu");
        Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces =
                validateResolvedFontProfile(plan.path("engine").path("resolvedFontProfile"), context, issues);
        Map<String, JsonNode> assets = validateAssetRegistry(plan.path("assets"), context, issues);
        JsonNode slides = plan.path("slides");
        if (!slides.isArray()) {
            return;
        }

        validateExpectedPageMapping(slides, context, issues);
        Set<String> slideIds = new HashSet<>();
        Set<String> globalElementIds = new HashSet<>();
        Set<String> referencedAssets = new HashSet<>();

        int expectedIndex = 1;
        for (JsonNode slide : slides) {
            String slideId = text(slide, "slideId");
            if (slideId != null && !slideIds.add(slideId)) {
                issues.add(issue(PptQualityCode.DUPLICATE_ID, slideId, null,
                        "Duplicate slideId", Map.of("slideId", slideId)));
            }
            int actualIndex = slide.path("index").asInt(-1);
            if (actualIndex != expectedIndex) {
                issues.add(issue(PptQualityCode.SLIDE_ORDER_MISMATCH, slideId, null,
                        "Slide indices must be continuous",
                        Map.of("actualIndex", actualIndex, "expectedIndex", expectedIndex)));
            }
            String layoutId = text(slide, "layoutId");
            if (layoutId != null && !context.knownLayoutIds().contains(layoutId)) {
                issues.add(issue(PptQualityCode.UNKNOWN_LAYOUT, slideId, null,
                        "Layout is not registered", Map.of("layoutId", layoutId)));
            }
            String sourcePageId = text(slide, "sourcePageId");
            RenderPlanValidationContext.PageExpectation expectedPage =
                    context.expectedPage(sourcePageId);
            if (expectedPage == null) {
                issues.add(issue(PptQualityCode.SOURCE_PAGE_MAPPING_INVALID, slideId, null,
                        "Slide has no frozen page contract",
                        Map.of("sourcePageId", sourcePageId == null ? "" : sourcePageId)));
            } else {
                String pageType = text(slide, "pageType");
                if (!expectedPage.pageType().equals(pageType)
                        || !expectedPage.layoutId().equals(layoutId)) {
                    issues.add(issue(PptQualityCode.SOURCE_PAGE_MAPPING_INVALID, slideId, null,
                            "Slide page type or deterministic layout differs from the frozen page contract",
                            Map.of("actualLayoutId", layoutId == null ? "" : layoutId,
                                    "actualPageType", pageType == null ? "" : pageType,
                                    "expectedLayoutId", expectedPage.layoutId(),
                                    "expectedPageType", expectedPage.pageType())));
                }
            }
            validateSlide(slide, slideWidth, slideHeight, context, fontFaces, assets,
                    globalElementIds, referencedAssets, issues);
            expectedIndex++;
        }

        for (Map.Entry<String, JsonNode> entry : assets.entrySet()) {
            if (entry.getValue().path("mandatory").asBoolean(false)
                    && !referencedAssets.contains(entry.getKey())) {
                issues.add(issue(PptQualityCode.MANDATORY_ASSET_MISSING, null, null,
                        "Mandatory asset is not referenced by any image element",
                        Map.of("assetId", entry.getKey())));
            }
        }
    }

    private void validateFrozenIdentity(
            JsonNode plan,
            RenderPlanValidationContext context,
            List<RenderPlanIssue> issues
    ) {
        compareText(plan, "presentationId", context.expectedPresentationId(),
                PptQualityCode.INVALID_REFERENCE, "Presentation id differs from the frozen input", issues);
        compareText(plan, "sourceTreeHash", context.expectedSourceTreeHash(),
                PptQualityCode.INVALID_HASH, "Source tree hash differs from the frozen input", issues);
        JsonNode engine = plan.path("engine");
        RenderPlanValidationContext.EngineExpectation expectedEngine = context.expectedEngine();
        compareText(engine, "engineVersion", expectedEngine.engineVersion(),
                PptQualityCode.INVALID_REFERENCE, "Render engine version differs from the compiler contract", issues);
        compareText(engine, "themeId", expectedEngine.themeId(),
                PptQualityCode.INVALID_REFERENCE, "Theme id differs from the resolved theme", issues);
        compareText(engine, "themeVersion", expectedEngine.themeVersion(),
                PptQualityCode.INVALID_REFERENCE, "Theme version differs from the resolved theme", issues);
        compareText(engine, "layoutCatalogVersion", expectedEngine.layoutCatalogVersion(),
                PptQualityCode.INVALID_REFERENCE, "Layout catalog version differs from the loaded catalog", issues);
        compareText(engine, "themeHash", context.expectedThemeHash(),
                PptQualityCode.INVALID_HASH, "Resolved theme hash differs from the frozen input", issues);
        compareText(engine, "layoutCatalogHash", context.expectedLayoutCatalogHash(),
                PptQualityCode.INVALID_HASH, "Layout catalog hash differs from the frozen input", issues);
        compareText(engine, "fontProfileHash", context.expectedFontProfileHash(),
                PptQualityCode.INVALID_HASH, "Resolved font profile hash differs from the frozen input", issues);
        long width = plan.path("slideSize").path("widthEmu").asLong(0);
        long height = plan.path("slideSize").path("heightEmu").asLong(0);
        if (width != context.expectedSlideWidthEmu() || height != context.expectedSlideHeightEmu()) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                    "Slide size differs from the frozen theme",
                    Map.of("actualHeightEmu", height, "actualWidthEmu", width,
                            "expectedHeightEmu", context.expectedSlideHeightEmu(),
                            "expectedWidthEmu", context.expectedSlideWidthEmu())));
        }
    }

    private void compareText(
            JsonNode owner,
            String field,
            String expected,
            PptQualityCode code,
            String message,
            List<RenderPlanIssue> issues
    ) {
        String actual = text(owner, field);
        if (!Objects.equals(expected, actual)) {
            issues.add(issue(code, null, null, message,
                    Map.of("actual", actual == null ? "" : actual, "expected", expected)));
        }
    }

    private Map<String, JsonNode> validateAssetRegistry(
            JsonNode assetArray,
            RenderPlanValidationContext context,
            List<RenderPlanIssue> issues
    ) {
        Map<String, JsonNode> assets = new LinkedHashMap<>();
        if (!assetArray.isArray()) {
            return assets;
        }
        for (JsonNode asset : assetArray) {
            String assetId = text(asset, "assetId");
            if (assetId == null) {
                continue;
            }
            if (assets.putIfAbsent(assetId, asset) != null) {
                issues.add(issue(PptQualityCode.DUPLICATE_ID, null, null,
                        "Duplicate assetId", Map.of("assetId", assetId)));
            }
            String expectedHash = context.expectedAssetHashes().get(assetId);
            String actualHash = text(asset, "sha256");
            if (expectedHash == null) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, null, null,
                        "RenderPlan contains an asset outside the frozen bundle",
                        Map.of("assetId", assetId)));
            } else if (!expectedHash.equals(actualHash)) {
                issues.add(issue(PptQualityCode.ASSET_HASH_MISMATCH, null, null,
                        "Asset hash differs from the frozen bundle",
                        Map.of("actualHash", actualHash == null ? "" : actualHash,
                                "assetId", assetId, "expectedHash", expectedHash)));
            }
        }
        for (String expectedAssetId : context.expectedAssetHashes().keySet()) {
            if (!assets.containsKey(expectedAssetId)) {
                issues.add(issue(PptQualityCode.MANDATORY_ASSET_MISSING, null, null,
                        "Frozen bundle asset is missing from the RenderPlan",
                        Map.of("assetId", expectedAssetId)));
            }
        }
        return assets;
    }

    private Map<String, RenderPlanValidationContext.FontFaceExpectation> validateResolvedFontProfile(
            JsonNode profile,
            RenderPlanValidationContext context,
            List<RenderPlanIssue> issues
    ) {
        RenderPlanValidationContext.FontProfileExpectation expected = context.expectedFontProfile();
        compareText(profile, "profileId", expected.profileId(), PptQualityCode.INVALID_REFERENCE,
                "Resolved font profile id differs from the measured profile", issues);
        compareText(profile, "measurementEngineVersion", expected.measurementEngineVersion(),
                PptQualityCode.INVALID_REFERENCE,
                "Text measurement engine version differs from the measured profile", issues);
        Map<String, RenderPlanValidationContext.FontFaceExpectation> actual = new LinkedHashMap<>();
        JsonNode faces = profile.path("faces");
        if (faces.isArray()) {
            for (JsonNode face : faces) {
                String faceId = text(face, "fontFaceId");
                if (faceId == null) {
                    continue;
                }
                RenderPlanValidationContext.FontFaceExpectation value;
                try {
                    value = new RenderPlanValidationContext.FontFaceExpectation(
                            text(face, "role"),
                            face.path("weight").asInt(0),
                            text(face, "selectedFamily"),
                            text(face, "postScriptName"),
                            text(face, "fontSource"),
                            text(face, "fontFingerprint"),
                            face.path("fallbackApplied").asBoolean(false));
                } catch (RuntimeException exception) {
                    issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                            "Resolved font face is incomplete or invalid", Map.of("fontFaceId", faceId)));
                    continue;
                }
                if (actual.putIfAbsent(faceId, value) != null) {
                    issues.add(issue(PptQualityCode.DUPLICATE_ID, null, null,
                            "Duplicate fontFaceId", Map.of("fontFaceId", faceId)));
                }
                RenderPlanValidationContext.FontFaceExpectation expectedFace = expected.faces().get(faceId);
                if (!value.equals(expectedFace)) {
                    issues.add(issue(PptQualityCode.INVALID_HASH, null, null,
                            "Resolved font face differs from the fingerprinted measurement input",
                            Map.of("fontFaceId", faceId)));
                }
            }
        }
        for (String expectedFaceId : expected.faces().keySet()) {
            if (!actual.containsKey(expectedFaceId)) {
                issues.add(issue(PptQualityCode.FONT_UNAVAILABLE, null, null,
                        "Measured font face is missing from the executable RenderPlan",
                        Map.of("fontFaceId", expectedFaceId)));
            }
        }
        for (String actualFaceId : actual.keySet()) {
            if (!expected.faces().containsKey(actualFaceId)) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, null, null,
                        "RenderPlan contains an unmeasured font face",
                        Map.of("fontFaceId", actualFaceId)));
            }
        }
        return actual;
    }

    private void validateExpectedPageMapping(
            JsonNode slides,
            RenderPlanValidationContext context,
            List<RenderPlanIssue> issues
    ) {
        List<String> expected = context.expectedSourcePageIds();
        if (expected.isEmpty()) {
            return;
        }
        if (slides.size() != expected.size()) {
            issues.add(issue(PptQualityCode.SLIDE_COUNT_MISMATCH, null, null,
                    "RenderPlan slide count differs from the validated presentation tree",
                    Map.of("actualSlides", slides.size(), "expectedSlides", expected.size())));
        }
        int common = Math.min(slides.size(), expected.size());
        Set<String> actualIds = new HashSet<>();
        for (int index = 0; index < common; index++) {
            String actual = text(slides.get(index), "sourcePageId");
            if (actual != null && !actualIds.add(actual)) {
                issues.add(issue(PptQualityCode.SOURCE_PAGE_MAPPING_INVALID,
                        text(slides.get(index), "slideId"), null,
                        "A source page is mapped more than once", Map.of("sourcePageId", actual)));
            }
            if (!Objects.equals(expected.get(index), actual)) {
                issues.add(issue(PptQualityCode.SOURCE_PAGE_MAPPING_INVALID,
                        text(slides.get(index), "slideId"), null,
                        "Slide does not preserve the frozen source-page mapping",
                        Map.of("actualSourcePageId", actual == null ? "" : actual,
                                "expectedSourcePageId", expected.get(index), "index", index + 1)));
            }
        }
    }

    private void validateSlide(
            JsonNode slide,
            long slideWidth,
            long slideHeight,
            RenderPlanValidationContext context,
            Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces,
            Map<String, JsonNode> assets,
            Set<String> globalElementIds,
            Set<String> referencedAssets,
            List<RenderPlanIssue> issues
    ) {
        String slideId = text(slide, "slideId");
        JsonNode elements = slide.path("elements");
        if (!elements.isArray()) {
            return;
        }
        List<ElementBox> boxes = new ArrayList<>();
        int fullBackgrounds = 0;
        int textElements = 0;
        int imageElements = 0;
        int tableElements = 0;
        int previousZIndex = -1;
        String previousElementId = "";
        for (JsonNode element : elements) {
            String elementId = text(element, "elementId");
            boolean decorative = element.path("decorative").asBoolean(false);
            if (elementId != null && !globalElementIds.add(elementId)) {
                issues.add(issue(PptQualityCode.DUPLICATE_ID, slideId, elementId,
                        "Element IDs must be globally stable and unique"));
            }
            ElementBox box = ElementBox.from(element);
            if (box != null) {
                validateGeometry(box, slideWidth, slideHeight, context, slideId, element, issues);
                if (!decorative) {
                    boxes.add(box);
                }
                if (box.isFullBackground(element, slideWidth, slideHeight)) {
                    fullBackgrounds++;
                }
            }
            String type = text(element, "elementType");
            int zIndex = element.path("zIndex").asInt(-1);
            if (zIndex < previousZIndex
                    || zIndex == previousZIndex && elementId != null
                    && elementId.compareTo(previousElementId) < 0) {
                issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                        "Elements are not in canonical paint order (zIndex, elementId)"));
            }
            previousZIndex = zIndex;
            previousElementId = elementId == null ? "" : elementId;
            validateStyleSource(element, slideId, elementId, context, issues);
            if (decorative) {
                validateDecorativeElement(
                        element, box, slideWidth, slideHeight, slideId, elementId, assets, issues);
            }
            if ("TEXT".equals(type)) {
                textElements++;
                validateText(element, slideId, elementId, context, fontFaces, issues);
            } else if ("IMAGE".equals(type)) {
                if (!decorative) {
                    imageElements++;
                }
                validateImage(element, slideId, elementId, assets, referencedAssets, issues);
            } else if ("TABLE".equals(type)) {
                tableElements++;
                validateTable(element, slideId, elementId, context, fontFaces, issues);
            } else if ("CONNECTOR".equals(type)) {
                validateConnector(element, slideId, elementId, slideWidth, slideHeight, issues);
            }
        }
        if (fullBackgrounds > 1) {
            issues.add(issue(PptQualityCode.ILLEGAL_OVERLAP, slideId, null,
                    "A slide may contain at most one full-page background shape",
                    Map.of("backgroundCount", fullBackgrounds)));
        }
        validatePageElementContract(
                text(slide, "pageType"), slideId, textElements, imageElements, tableElements, issues);
        validateOverlaps(
                slideId,
                elements,
                boxes,
                slideWidth,
                slideHeight,
                context.allowedContainedTextComponents(text(slide, "layoutId")),
                issues);
    }

    private void validateDecorativeElement(
            JsonNode element,
            ElementBox box,
            long slideWidth,
            long slideHeight,
            String slideId,
            String elementId,
            Map<String, JsonNode> assets,
            List<RenderPlanIssue> issues
    ) {
        if (!"IMAGE".equals(element.path("elementType").asText())) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                    "Only IMAGE elements may be marked decorative"));
            return;
        }
        if (box == null || box.x != 0 || box.y != 0
                || box.width != slideWidth || box.height != slideHeight
                || box.zIndex != 5) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, slideId, elementId,
                    "Template decoration must be a deterministic full-slide surface at zIndex 5"));
        }
        String assetId = text(element, "assetId");
        JsonNode asset = assetId == null ? null : assets.get(assetId);
        if (asset == null || !"TEMPLATE_DECORATION".equals(asset.path("assetKind").asText())) {
            issues.add(issue(PptQualityCode.INVALID_REFERENCE, slideId, elementId,
                    "Decorative image must reference a TEMPLATE_DECORATION asset",
                    assetId == null ? Map.of() : Map.of("assetId", assetId)));
        }
    }

    private void validatePageElementContract(
            String pageType,
            String slideId,
            int textElements,
            int imageElements,
            int tableElements,
            List<RenderPlanIssue> issues
    ) {
        if (pageType == null) {
            return;
        }
        boolean valid = textElements >= 1;
        if ("IMAGE".equals(pageType)) {
            valid &= imageElements == 1 && tableElements == 0;
        } else if ("TABLE".equals(pageType)) {
            valid &= tableElements == 1 && imageElements == 0;
        } else if ("CONTENT".equals(pageType)) {
            valid &= tableElements == 0 && imageElements <= 1;
        } else {
            valid &= tableElements == 0 && imageElements == 0;
        }
        if (!valid) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, slideId, null,
                    "Slide element roles do not satisfy the frozen page-type contract",
                    Map.of("imageElements", imageElements, "pageType", pageType,
                            "tableElements", tableElements, "textElements", textElements)));
        }
    }

    private void validateStyleSource(
            JsonNode element,
            String slideId,
            String elementId,
            RenderPlanValidationContext context,
            List<RenderPlanIssue> issues
    ) {
        JsonNode source = element.path("styleSource");
        if (source.isMissingNode() || source.isNull()) {
            boolean canvasBackground = "SHAPE".equals(element.path("elementType").asText())
                    && element.path("elementId").asText("").endsWith("background")
                    && element.path("zIndex").asInt(-1) == 0;
            if (!canvasBackground) {
                issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                        "Every executable non-background element must declare its resolved style source"));
            }
            return;
        }
        String component = text(source, "component");
        String token = text(source, "themeToken");
        if (component == null || !context.knownStyleComponents().contains(component)) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                    "Element references an unknown theme component",
                    Map.of("component", component == null ? "" : component)));
        }
        if (token == null || !context.knownThemeTokens().contains(token)) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                    "Element references an unknown resolved theme token",
                    Map.of("themeToken", token == null ? "" : token)));
        }
    }

    private void validateGeometry(
            ElementBox box,
            long slideWidth,
            long slideHeight,
            RenderPlanValidationContext context,
            String slideId,
            JsonNode element,
            List<RenderPlanIssue> issues
    ) {
        if (box.x < 0 || box.y < 0 || box.width <= 0 || box.height <= 0
                || box.x > slideWidth || box.y > slideHeight
                || box.width > slideWidth - box.x || box.height > slideHeight - box.y) {
            issues.add(issue(PptQualityCode.ELEMENT_OUT_OF_BOUNDS, slideId, box.elementId,
                    "Element geometry is outside the slide",
                    Map.of("heightEmu", box.height, "widthEmu", box.width,
                            "xEmu", box.x, "yEmu", box.y)));
            return;
        }
        if (box.isFullBackground(element, slideWidth, slideHeight)
                || element.path("decorative").asBoolean(false)
                || "pageNumber".equals(element.path("styleSource").path("component").asText())) {
            return;
        }
        RenderPlanValidationContext.SafeArea safe = context.safeArea();
        if (box.x < safe.leftEmu() || box.y < safe.topEmu()
                || box.right() > slideWidth - safe.rightEmu()
                || box.bottom() > slideHeight - safe.bottomEmu()) {
            issues.add(issue(PptQualityCode.SAFE_AREA_VIOLATION, slideId, box.elementId,
                    "Element is outside the configured safe area"));
        }
    }

    private void validateText(
            JsonNode element,
            String slideId,
            String elementId,
            RenderPlanValidationContext context,
            Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces,
            List<RenderPlanIssue> issues
    ) {
        JsonNode style = element.path("resolvedStyle");
        int fontSize = style.path("fontSizeHundredthPt").asInt(0);
        String component = text(element.path("styleSource"), "component");
        int minimum = context.minimumFontSize(component);
        if (fontSize > 0 && fontSize < minimum) {
            issues.add(issue(PptQualityCode.FONT_BELOW_MINIMUM, slideId, elementId,
                    "Resolved font size is below the configured minimum",
                    Map.of("actualHundredthPt", fontSize, "minimumHundredthPt", minimum)));
        }
        validateFontReference(style, "fontFaceId", "fontWeight", fontFaces,
                slideId, elementId, issues);
        JsonNode valueNode = element.get("text");
        if (valueNode == null || !valueNode.isTextual() || valueNode.textValue().isBlank()) {
            issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, slideId, elementId,
                    "Visible text must be a non-blank executable string"));
            return;
        }
        String value = valueNode.textValue();
        if (value.length() > MAX_TEXT_CODE_UNITS) {
            issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                    "Visible text exceeds the V1 deterministic measurement limit",
                    Map.of("length", value.length(), "maximum", MAX_TEXT_CODE_UNITS)));
            return;
        }
        String stripped = value.stripTrailing();
        if (endsWithTruncationMarker(stripped)) {
            issues.add(issue(PptQualityCode.TEXT_TRUNCATED, slideId, elementId,
                    "Visible text ends with a truncation marker"));
        }
        int measuredLineCount = element.path("lineCount").asInt(0);
        int visibleLineCount = value.split("\n", -1).length;
        long requiredWidth = element.path("requiredWidthEmu").asLong(-1);
        long requiredHeight = element.path("requiredHeightEmu").asLong(-1);
        long lineHeight = style.path("lineHeightEmu").asLong(0);
        long availableWidth = remainingSpace(
                element.path("widthEmu").asLong(0),
                style.path("marginLeftEmu").asLong(0),
                style.path("marginRightEmu").asLong(0));
        long availableHeight = remainingSpace(
                element.path("heightEmu").asLong(0),
                style.path("marginTopEmu").asLong(0),
                style.path("marginBottomEmu").asLong(0),
                style.path("paragraphSpaceBeforeEmu").asLong(0),
                style.path("paragraphSpaceAfterEmu").asLong(0));
        if (measuredLineCount != visibleLineCount) {
            issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                    "Text lineCount does not match the frozen rendered text",
                    Map.of("actualLineCount", visibleLineCount, "measuredLineCount", measuredLineCount)));
        }
        if (requiredWidth < 0 || requiredHeight < 1
                || requiredWidth > availableWidth || requiredHeight > availableHeight) {
            issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                    "Measured text dimensions exceed the executable text box",
                    Map.of("availableHeightEmu", availableHeight,
                            "availableWidthEmu", availableWidth,
                            "requiredHeightEmu", requiredHeight,
                            "requiredWidthEmu", requiredWidth)));
        }
        if (lineHeight < 1 || measuredLineCount < 1) {
            issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                    "Text line metrics are missing or invalid"));
        } else {
            BigInteger safetyHeight = BigInteger.valueOf(
                    DeterministicTextMetricsService.TEXT_BOX_VERTICAL_SAFETY_EMU);
            BigInteger minimumMeasuredHeight = BigInteger.valueOf(Math.max(0L, measuredLineCount - 1L))
                    .multiply(BigInteger.valueOf(lineHeight)).add(BigInteger.ONE).add(safetyHeight);
            BigInteger maximumMeasuredHeight = BigInteger.valueOf(measuredLineCount)
                    .multiply(BigInteger.valueOf(lineHeight)).add(safetyHeight);
            BigInteger frozenHeight = BigInteger.valueOf(requiredHeight);
            if (frozenHeight.compareTo(minimumMeasuredHeight) < 0
                    || frozenHeight.compareTo(maximumMeasuredHeight) > 0) {
                issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                        "Text requiredHeight is inconsistent with lineCount and lineHeight",
                        Map.of("lineCount", measuredLineCount, "lineHeightEmu", lineHeight,
                                "requiredHeightEmu", requiredHeight)));
            }
        }
        String fontFaceId = text(style, "fontFaceId");
        RenderPlanValidationContext.FontFaceExpectation face = fontFaces.get(fontFaceId);
        int lineSpacingPermille = style.path("lineSpacingPermille").asInt(0);
        if (face != null && fontSize > 0 && measuredLineCount > 0
                && availableWidth > 0 && availableHeight > 0 && lineSpacingPermille >= 1_000) {
            try {
                TextFitResult independentlyMeasured = context.textMetrics().fit(new TextFitRequest(
                        value,
                        context.resolvedFontProfile(),
                        face.role(),
                        face.weight(),
                        fontSize,
                        fontSize,
                        lineSpacingPermille,
                        availableWidth,
                        availableHeight,
                        measuredLineCount));
                if (!independentlyMeasured.fits()
                        || !value.equals(independentlyMeasured.renderedText())
                        || measuredLineCount != independentlyMeasured.lines().size()
                        || requiredWidth != independentlyMeasured.requiredWidthEmu()
                        || requiredHeight != independentlyMeasured.requiredHeightEmu()
                        || lineHeight != independentlyMeasured.lineHeightEmu()) {
                    issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                            "Frozen text metrics do not match independent measurement",
                            Map.of("fontFaceId", fontFaceId)));
                }
            } catch (RuntimeException exception) {
                issues.add(issue(PptQualityCode.TEXT_OVERFLOW, slideId, elementId,
                        "Independent text measurement failed for the frozen font face",
                        Map.of("fontFaceId", fontFaceId)));
            }
        }
    }

    private void validateFontReference(
            JsonNode style,
            String faceIdField,
            String weightField,
            Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces,
            String slideId,
            String elementId,
            List<RenderPlanIssue> issues
    ) {
        String faceId = text(style, faceIdField);
        RenderPlanValidationContext.FontFaceExpectation face = faceId == null ? null : fontFaces.get(faceId);
        if (face == null) {
            issues.add(issue(PptQualityCode.FONT_UNAVAILABLE, slideId, elementId,
                    "Element references an unresolved measured font face",
                    faceId == null ? Map.of() : Map.of("fontFaceId", faceId)));
            return;
        }
        int weight = style.path(weightField).asInt(0);
        String family = text(style, "fontFamily");
        if (weight != face.weight() || !Objects.equals(family, face.selectedFamily())) {
            issues.add(issue(PptQualityCode.FONT_UNAVAILABLE, slideId, elementId,
                    "Element font style differs from its measured font face",
                    Map.of("fontFaceId", faceId)));
        }
    }

    private boolean endsWithTruncationMarker(String value) {
        return value.endsWith("…") || value.endsWith("...");
    }

    private void validateImage(
            JsonNode element,
            String slideId,
            String elementId,
            Map<String, JsonNode> assets,
            Set<String> referencedAssets,
            List<RenderPlanIssue> issues
    ) {
        String assetId = text(element, "assetId");
        JsonNode asset = assetId == null ? null : assets.get(assetId);
        if (asset == null) {
            issues.add(issue(PptQualityCode.INVALID_REFERENCE, slideId, elementId,
                    "Image element references an unknown asset",
                    assetId == null ? Map.of() : Map.of("assetId", assetId)));
            return;
        }
        referencedAssets.add(assetId);
        long targetWidth = element.path("widthEmu").asLong(0);
        long targetHeight = element.path("heightEmu").asLong(0);
        long sourceWidth = asset.path("widthPx").asLong(0);
        long sourceHeight = asset.path("heightPx").asLong(0);
        String fitMode = text(element, "fitMode");
        if ("CONTAIN".equals(fitMode)) {
            if (!sameRatio(targetWidth, targetHeight, sourceWidth, sourceHeight)) {
                issues.add(issue(PptQualityCode.IMAGE_ASPECT_DISTORTION, slideId, elementId,
                        "Contained image geometry does not preserve source aspect ratio",
                        Map.of("assetId", assetId)));
            }
        } else if ("COVER".equals(fitMode)) {
            JsonNode crop = element.path("sourceCrop");
            long horizontal = 1000L - crop.path("leftPermille").asLong(0)
                    - crop.path("rightPermille").asLong(0);
            long vertical = 1000L - crop.path("topPermille").asLong(0)
                    - crop.path("bottomPermille").asLong(0);
            if (!element.path("cropAllowed").asBoolean(false) || horizontal <= 0 || vertical <= 0) {
                issues.add(issue(PptQualityCode.CROP_NOT_ALLOWED, slideId, elementId,
                        "Cover image lacks a valid, explicitly allowed crop"));
            } else if (!sameRatioWithCrop(targetWidth, targetHeight,
                    sourceWidth, sourceHeight, horizontal, vertical)) {
                issues.add(issue(PptQualityCode.IMAGE_ASPECT_DISTORTION, slideId, elementId,
                        "Cover crop does not resolve to the target aspect ratio",
                        Map.of("assetId", assetId)));
            }
        }
        long requiredWidthPx = divideCeiling(targetWidth, EMU_PER_PIXEL_AT_96_DPI);
        long requiredHeightPx = divideCeiling(targetHeight, EMU_PER_PIXEL_AT_96_DPI);
        if (sourceWidth > 0 && sourceHeight > 0
                && (sourceWidth < requiredWidthPx || sourceHeight < requiredHeightPx)) {
            issues.add(issue(PptQualityCode.IMAGE_RESOLUTION_LOW, slideId, elementId,
                    "Source image resolution is below 96-DPI target geometry",
                    Map.of("assetId", assetId, "requiredHeightPx", requiredHeightPx,
                            "requiredWidthPx", requiredWidthPx, "sourceHeightPx", sourceHeight,
                            "sourceWidthPx", sourceWidth)));
        }
    }

    private boolean sameRatio(long targetWidth, long targetHeight, long sourceWidth, long sourceHeight) {
        if (targetWidth <= 0 || targetHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return false;
        }
        BigInteger left = BigInteger.valueOf(targetWidth).multiply(BigInteger.valueOf(sourceHeight));
        BigInteger right = BigInteger.valueOf(targetHeight).multiply(BigInteger.valueOf(sourceWidth));
        BigInteger differencePermille = left.subtract(right).abs().multiply(BigInteger.valueOf(1000));
        BigInteger scale = left.max(right);
        return differencePermille.compareTo(scale.multiply(BigInteger.valueOf(2))) <= 0;
    }

    private boolean sameRatioWithCrop(
            long targetWidth,
            long targetHeight,
            long sourceWidth,
            long sourceHeight,
            long horizontalPermille,
            long verticalPermille
    ) {
        if (targetWidth <= 0 || targetHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0
                || horizontalPermille <= 0 || verticalPermille <= 0) {
            return false;
        }
        BigInteger croppedWidth = BigInteger.valueOf(sourceWidth)
                .multiply(BigInteger.valueOf(horizontalPermille));
        BigInteger croppedHeight = BigInteger.valueOf(sourceHeight)
                .multiply(BigInteger.valueOf(verticalPermille));
        BigInteger left = BigInteger.valueOf(targetWidth).multiply(croppedHeight);
        BigInteger right = BigInteger.valueOf(targetHeight).multiply(croppedWidth);
        BigInteger differencePermille = left.subtract(right).abs().multiply(BigInteger.valueOf(1000));
        BigInteger scale = left.max(right);
        return differencePermille.compareTo(scale.multiply(BigInteger.valueOf(2))) <= 0;
    }

    private void validateTable(
            JsonNode element,
            String slideId,
            String elementId,
            RenderPlanValidationContext context,
            Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces,
            List<RenderPlanIssue> issues
    ) {
        JsonNode style = element.path("resolvedStyle");
        int fontSize = style.path("fontSizeHundredthPt").asInt(0);
        int minimum = Math.max(context.minimumFontSize("tableHeader"),
                context.minimumFontSize("tableBody"));
        if (fontSize > 0 && fontSize < minimum) {
            issues.add(issue(PptQualityCode.FONT_BELOW_MINIMUM, slideId, elementId,
                    "Resolved table font size is below the configured minimum",
                    Map.of("actualHundredthPt", fontSize, "minimumHundredthPt", minimum)));
        }
        validateFontReference(style, "headerFontFaceId", "headerFontWeight", fontFaces,
                slideId, elementId, issues);
        validateFontReference(style, "bodyFontFaceId", "bodyFontWeight", fontFaces,
                slideId, elementId, issues);
        if (style.path("fontWeight").asInt(0) != style.path("bodyFontWeight").asInt(-1)) {
            issues.add(issue(PptQualityCode.FONT_UNAVAILABLE, slideId, elementId,
                    "Table fontWeight must equal the resolved bodyFontWeight"));
        }
        JsonNode columns = element.path("columns");
        JsonNode rows = element.path("rows");
        int columnCount = columns.isArray() ? columns.size() : 0;
        int rowCount = rows.isArray() ? rows.size() : 0;
        boolean exceeds = columnCount < 1 || columnCount > 5 || rowCount < 1 || rowCount > 7;
        BigInteger columnWidth = BigInteger.ZERO;
        if (columns.isArray()) {
            for (JsonNode column : columns) {
                columnWidth = columnWidth.add(BigInteger.valueOf(column.path("widthEmu").asLong(0)));
                String header = column.path("header").asText("").stripTrailing();
                if (header.length() > MAX_TABLE_CELL_CODE_UNITS) {
                    exceeds = true;
                    issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                            "Table header exceeds the V1 deterministic measurement limit"));
                    continue;
                }
                if (endsWithTruncationMarker(header)) {
                    issues.add(issue(PptQualityCode.TEXT_TRUNCATED, slideId, elementId,
                            "Table header ends with a truncation marker"));
                }
                validateTableCellText(
                        header,
                        column.path("widthEmu").asLong(0),
                        element.path("headerRowHeightEmu").asLong(0),
                        style,
                        "headerFontFaceId",
                        "headerFontWeight",
                        context,
                        fontFaces,
                        slideId,
                        elementId,
                        issues);
            }
        }
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                if (!row.path("cells").isArray() || row.path("cells").size() != columnCount) {
                    exceeds = true;
                } else {
                    for (int columnIndex = 0; columnIndex < row.path("cells").size(); columnIndex++) {
                        JsonNode cell = row.path("cells").get(columnIndex);
                        if (cell.asText("").length() > MAX_TABLE_CELL_CODE_UNITS) {
                            exceeds = true;
                            issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                                    "Table cell exceeds the V1 deterministic measurement limit"));
                            continue;
                        }
                        if (endsWithTruncationMarker(cell.asText("").stripTrailing())) {
                            issues.add(issue(PptQualityCode.TEXT_TRUNCATED, slideId, elementId,
                                    "Table cell ends with a truncation marker"));
                        }
                        long cellWidth = columns.isArray() && columnIndex < columns.size()
                                ? columns.get(columnIndex).path("widthEmu").asLong(0)
                                : 0L;
                        validateTableCellText(
                                cell.asText(""),
                                cellWidth,
                                element.path("bodyRowHeightEmu").asLong(0),
                                style,
                                "bodyFontFaceId",
                                "bodyFontWeight",
                                context,
                                fontFaces,
                                slideId,
                                elementId,
                                issues);
                    }
                }
            }
        }
        validateTableStatusCells(element, columns, rows, context, fontFaces,
                slideId, elementId, issues);
        BigInteger totalHeight = BigInteger.valueOf(element.path("headerRowHeightEmu").asLong(0))
                .add(BigInteger.valueOf(element.path("bodyRowHeightEmu").asLong(0))
                        .multiply(BigInteger.valueOf(rowCount)));
        if (columnWidth.compareTo(BigInteger.valueOf(element.path("widthEmu").asLong(0))) > 0
                || totalHeight.compareTo(BigInteger.valueOf(element.path("heightEmu").asLong(0))) > 0) {
            exceeds = true;
        }
        if (exceeds) {
            issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                    "Structured table exceeds its resolved row, column or geometry capacity",
                    Map.of("columnCount", columnCount, "rowCount", rowCount)));
        }
    }

    private void validateTableStatusCells(
            JsonNode element,
            JsonNode columns,
            JsonNode rows,
            RenderPlanValidationContext context,
            Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces,
            String slideId,
            String elementId,
            List<RenderPlanIssue> issues
    ) {
        if (!"TEST_RESULT".equals(text(element, "tableKind"))) {
            return;
        }
        JsonNode statusCells = element.path("statusCells");
        if (!statusCells.isArray()) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                    "TEST_RESULT table must freeze native status-cell positions and styles"));
            return;
        }
        Set<String> coordinates = new HashSet<>();
        Set<Integer> representedRows = new HashSet<>();
        for (JsonNode status : statusCells) {
            int rowIndex = status.path("rowIndex").asInt(-1);
            int columnIndex = status.path("columnIndex").asInt(-1);
            String coordinate = rowIndex + ":" + columnIndex;
            if (!coordinates.add(coordinate)) {
                issues.add(issue(PptQualityCode.DUPLICATE_ID, slideId, elementId,
                        "Duplicate table status-cell coordinate", Map.of("coordinate", coordinate)));
                continue;
            }
            if (!rows.isArray() || rowIndex < 0 || rowIndex >= rows.size()
                    || !columns.isArray() || columnIndex < 0 || columnIndex >= columns.size()) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, slideId, elementId,
                        "Table status cell points outside the native table grid",
                        Map.of("columnIndex", columnIndex, "rowIndex", rowIndex)));
                continue;
            }
            representedRows.add(rowIndex);
            JsonNode cells = rows.get(rowIndex).path("cells");
            String cellText = cells.isArray() && columnIndex < cells.size()
                    ? cells.get(columnIndex).asText("") : "";
            String statusText = status.path("text").asText("");
            if (statusText.isBlank()) {
                issues.add(issue(PptQualityCode.UNRENDERABLE_PAGE, slideId, elementId,
                        "Table status text must be non-blank",
                        Map.of("columnIndex", columnIndex, "rowIndex", rowIndex)));
                continue;
            }
            if (!statusText.equals(cellText)) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, slideId, elementId,
                        "Table status style text must equal its native cell text",
                        Map.of("columnIndex", columnIndex, "rowIndex", rowIndex)));
            }
            String header = columns.get(columnIndex).path("header").asText("").strip();
            if (!("状态".equals(header) || "status".equalsIgnoreCase(header))) {
                issues.add(issue(PptQualityCode.INVALID_REFERENCE, slideId, elementId,
                        "Table status style must target the explicit status column",
                        Map.of("columnIndex", columnIndex, "rowIndex", rowIndex)));
            }
            String faceId = text(status, "fontFaceId");
            RenderPlanValidationContext.FontFaceExpectation face = fontFaces.get(faceId);
            int weight = status.path("fontWeight").asInt(0);
            JsonNode tableStyle = element.path("resolvedStyle");
            if (face == null || weight != face.weight()
                    || !Objects.equals(faceId, text(tableStyle, "bodyFontFaceId"))
                    || weight != tableStyle.path("bodyFontWeight").asInt(0)) {
                issues.add(issue(PptQualityCode.FONT_UNAVAILABLE, slideId, elementId,
                        "Table status style must use the table body measured font face",
                        faceId == null ? Map.of() : Map.of("fontFaceId", faceId)));
            }
            RenderPlanValidationContext.StatusStyleExpectation expectedStyle =
                    context.statusStyleExpectation();
            String expectedFill = expectedStyle.expectedFillColor(statusText);
            if (!expectedFill.equals(status.path("fillColor").asText())
                    || !expectedStyle.textColor().equals(status.path("textColor").asText())
                    || !"CENTER".equals(status.path("horizontalAlign").asText())
                    || !"MIDDLE".equals(status.path("verticalAlign").asText())) {
                issues.add(issue(PptQualityCode.SCHEMA_INVALID, slideId, elementId,
                        "Table status style differs from the frozen theme semantics",
                        Map.of("columnIndex", columnIndex, "rowIndex", rowIndex)));
            }
        }
        int rowCount = rows.isArray() ? rows.size() : 0;
        if (representedRows.size() != rowCount || statusCells.size() != rowCount) {
            issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                    "TEST_RESULT table must freeze exactly one status style per body row",
                    Map.of("rowCount", rowCount, "statusCellCount", statusCells.size())));
        }
    }

    private void validateTableCellText(
            String value,
            long cellWidthEmu,
            long rowHeightEmu,
            JsonNode style,
            String faceIdField,
            String weightField,
            RenderPlanValidationContext context,
            Map<String, RenderPlanValidationContext.FontFaceExpectation> fontFaces,
            String slideId,
            String elementId,
            List<RenderPlanIssue> issues
    ) {
        String faceId = text(style, faceIdField);
        RenderPlanValidationContext.FontFaceExpectation face = fontFaces.get(faceId);
        int fontSize = style.path("fontSizeHundredthPt").asInt(0);
        int spacing = style.path("lineSpacingPermille").asInt(0);
        long contentWidth = remainingSpace(
                cellWidthEmu,
                style.path("cellMarginLeftEmu").asLong(0),
                style.path("cellMarginRightEmu").asLong(0));
        long contentHeight = remainingSpace(
                rowHeightEmu,
                style.path("cellMarginTopEmu").asLong(0),
                style.path("cellMarginBottomEmu").asLong(0),
                style.path("paragraphSpaceBeforeEmu").asLong(0),
                style.path("paragraphSpaceAfterEmu").asLong(0));
        if (face == null || fontSize < 1 || spacing < 1_000
                || contentWidth < 1 || contentHeight < 1) {
            return;
        }
        if (value.isEmpty()) {
            try {
                long natural = context.textMetrics().naturalLineHeightEmu(
                        context.resolvedFontProfile(), face.role(), face.weight(), fontSize);
                if (natural > contentHeight) {
                    issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                            "Empty editable table cell does not retain one line of font height"));
                }
            } catch (RuntimeException exception) {
                issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                        "Independent empty table cell measurement failed"));
            }
            return;
        }
        int lines = value.split("\n", -1).length;
        try {
            TextFitResult measured = context.textMetrics().fit(new TextFitRequest(
                    value,
                    context.resolvedFontProfile(),
                    face.role(),
                    style.path(weightField).asInt(0),
                    fontSize,
                    fontSize,
                    spacing,
                    contentWidth,
                    contentHeight,
                    lines));
            if (!measured.fits() || !value.equals(measured.renderedText())) {
                issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                        "Table cell text does not fit its frozen native cell geometry"));
            }
        } catch (RuntimeException exception) {
            issues.add(issue(PptQualityCode.TABLE_CAPACITY_EXCEEDED, slideId, elementId,
                    "Independent table cell measurement failed"));
        }
    }

    private void validateConnector(
            JsonNode element,
            String slideId,
            String elementId,
            long slideWidth,
            long slideHeight,
            List<RenderPlanIssue> issues
    ) {
        for (String prefix : List.of("start", "end")) {
            long x = element.path(prefix + "XEmu").asLong(-1);
            long y = element.path(prefix + "YEmu").asLong(-1);
            if (x < 0 || y < 0 || x > slideWidth || y > slideHeight) {
                issues.add(issue(PptQualityCode.ELEMENT_OUT_OF_BOUNDS, slideId, elementId,
                        "Connector endpoint is outside the slide", Map.of("endpoint", prefix)));
            }
        }
    }

    private void validateOverlaps(
            String slideId,
            JsonNode elements,
            List<ElementBox> boxes,
            long slideWidth,
            long slideHeight,
            Set<String> allowedContainedTextComponents,
            List<RenderPlanIssue> issues
    ) {
        Map<String, JsonNode> elementsById = new HashMap<>();
        elements.forEach(element -> elementsById.put(text(element, "elementId"), element));
        for (int leftIndex = 0; leftIndex < boxes.size(); leftIndex++) {
            ElementBox left = boxes.get(leftIndex);
            JsonNode leftNode = elementsById.get(left.elementId);
            for (int rightIndex = leftIndex + 1; rightIndex < boxes.size(); rightIndex++) {
                ElementBox right = boxes.get(rightIndex);
                JsonNode rightNode = elementsById.get(right.elementId);
                if (!left.intersects(right)) {
                    continue;
                }
                if (left.isFullBackground(leftNode, slideWidth, slideHeight)
                        || right.isFullBackground(rightNode, slideWidth, slideHeight)) {
                    continue;
                }
                if (isExplicitContainedTextOverlap(
                        left, leftNode, right, rightNode, allowedContainedTextComponents)
                        || isExplicitContainedTextOverlap(
                        right, rightNode, left, leftNode, allowedContainedTextComponents)) {
                    continue;
                }
                issues.add(issue(PptQualityCode.ILLEGAL_OVERLAP, slideId, right.elementId,
                        "Elements overlap without an explicit V1 overlap contract",
                        Map.of("overlapsElementId", left.elementId)));
            }
        }
    }

    private boolean isExplicitContainedTextOverlap(
            ElementBox shape,
            JsonNode shapeNode,
            ElementBox text,
            JsonNode textNode,
            Set<String> allowedContainedTextComponents
    ) {
        if (shapeNode == null || textNode == null
                || !"SHAPE".equals(shapeNode.path("elementType").asText())
                || !"TEXT".equals(textNode.path("elementType").asText())
                || shape.zIndex >= text.zIndex
                || !shape.contains(text)
                || !text.elementId.equals(shape.elementId + "-text")) {
            return false;
        }
        String shapeComponent = text(shapeNode.path("styleSource"), "component");
        String textComponent = text(textNode.path("styleSource"), "component");
        return Objects.equals(shapeComponent, textComponent)
                && allowedContainedTextComponents.contains(shapeComponent);
    }

    private boolean validateIntegerRanges(
            JsonNode node,
            String path,
            String field,
            List<RenderPlanIssue> issues
    ) {
        boolean safe = true;
        if (node.isNumber() && !node.isIntegralNumber()) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                    "RenderPlan executable numbers must be integers",
                    Map.of("jsonPath", path)));
            return false;
        }
        if (node.isIntegralNumber()) {
            if (!node.canConvertToLong() || field != null && INT_FIELDS.contains(field)
                    && !node.canConvertToInt()) {
                issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                        "RenderPlan integer is outside the executable numeric range",
                        Map.of("jsonPath", path)));
                return false;
            }
            return true;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                safe &= validateIntegerRanges(
                        entry.getValue(), path + "." + entry.getKey(), entry.getKey(), issues);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                safe &= validateIntegerRanges(node.get(index), path + "[" + index + "]", field, issues);
            }
        }
        return safe;
    }

    private boolean validateCollectionLimits(JsonNode document, List<RenderPlanIssue> issues) {
        boolean safe = true;
        JsonNode assets = document.path("assets");
        if (assets.isArray() && assets.size() > MAX_ASSETS) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                    "RenderPlan exceeds the V1 executable asset limit",
                    Map.of("actual", assets.size(), "maximum", MAX_ASSETS)));
            safe = false;
        }
        JsonNode slides = document.path("slides");
        if (slides.isArray() && slides.size() > MAX_SLIDES) {
            issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                    "RenderPlan exceeds the V1 executable slide limit",
                    Map.of("actual", slides.size(), "maximum", MAX_SLIDES)));
            return false;
        }
        if (slides.isArray()) {
            for (JsonNode slide : slides) {
                JsonNode elements = slide.path("elements");
                if (elements.isArray() && elements.size() > MAX_ELEMENTS_PER_SLIDE) {
                    issues.add(issue(PptQualityCode.SCHEMA_INVALID, text(slide, "slideId"), null,
                            "Slide exceeds the V1 executable element limit",
                            Map.of("actual", elements.size(), "maximum", MAX_ELEMENTS_PER_SLIDE)));
                    safe = false;
                }
            }
        }
        return safe;
    }

    private void validatePortableValues(
            JsonNode node,
            String path,
            String field,
            List<RenderPlanIssue> issues
    ) {
        if (node.isTextual()) {
            String value = node.textValue();
            boolean pathField = field != null
                    && (field.equals("bundlePath") || field.endsWith("Path") || field.endsWith("Directory"));
            if (pathField && (WINDOWS_ABSOLUTE.matcher(value).matches()
                    || value.startsWith("/") || value.startsWith("\\\\"))) {
                issues.add(issue(PptQualityCode.SCHEMA_INVALID, null, null,
                        "RenderPlan contains a machine-specific absolute path",
                        Map.of("jsonPath", path)));
            }
            boolean stableRootPresentationId = "$.presentationId".equals(path);
            if (field != null && IDENTITY_FIELDS.contains(field)
                    && !stableRootPresentationId && UUID.matcher(value).find()) {
                issues.add(issue(PptQualityCode.NON_DETERMINISTIC_RENDER_PLAN, null, null,
                        "RenderPlan contains a UUID-like random value",
                        Map.of("jsonPath", path)));
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String childField = entry.getKey();
                if (TIMESTAMP_FIELDS.contains(childField)) {
                    issues.add(issue(PptQualityCode.NON_DETERMINISTIC_RENDER_PLAN, null, null,
                            "RenderPlan contains a runtime timestamp field",
                            Map.of("jsonPath", path + "." + childField)));
                }
                validatePortableValues(entry.getValue(), path + "." + childField, childField, issues);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validatePortableValues(node.get(index), path + "[" + index + "]", field, issues);
            }
        }
    }

    private long remainingSpace(long total, long... deductions) {
        BigInteger remaining = BigInteger.valueOf(total);
        for (long deduction : deductions) {
            remaining = remaining.subtract(BigInteger.valueOf(deduction));
        }
        if (remaining.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) {
            return Long.MIN_VALUE;
        }
        if (remaining.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return Long.MAX_VALUE;
        }
        return remaining.longValue();
    }

    private long positiveLong(JsonNode owner, String field) {
        long value = owner.path(field).asLong(0);
        return Math.max(0, value);
    }

    private long divideCeiling(long numerator, long denominator) {
        if (numerator <= 0) {
            return 0;
        }
        return (numerator - 1) / denominator + 1;
    }

    private String text(JsonNode owner, String field) {
        if (owner == null) {
            return null;
        }
        JsonNode value = owner.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue()
                : null;
    }

    private RenderPlanIssue issue(
            PptQualityCode code,
            String slideId,
            String elementId,
            String message
    ) {
        return issue(code, slideId, elementId, message, Map.of());
    }

    private RenderPlanIssue issue(
            PptQualityCode code,
            String slideId,
            String elementId,
            String message,
            Map<String, Object> metrics
    ) {
        return new RenderPlanIssue(code, slideId, elementId, message, metrics);
    }

    private Schema loadSchema() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = RenderPlanValidator.class.getClassLoader()
                .getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing RenderPlan schema: " + SCHEMA_RESOURCE);
            }
            JsonNode schemaNode = mapper.readTree(input);
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(schemaNode);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load RenderPlan schema", exception);
        }
    }

    private record ElementBox(
            String elementId,
            long x,
            long y,
            long width,
            long height,
            int zIndex
    ) {
        private static ElementBox from(JsonNode element) {
            JsonNode id = element.get("elementId");
            if (id == null || !id.isTextual()) {
                return null;
            }
            return new ElementBox(
                    id.textValue(),
                    element.path("xEmu").asLong(-1),
                    element.path("yEmu").asLong(-1),
                    element.path("widthEmu").asLong(0),
                    element.path("heightEmu").asLong(0),
                    element.path("zIndex").asInt(-1));
        }

        private long right() {
            return saturatedAdd(x, width);
        }

        private long bottom() {
            return saturatedAdd(y, height);
        }

        private static long saturatedAdd(long left, long right) {
            if (right > 0 && left > Long.MAX_VALUE - right) {
                return Long.MAX_VALUE;
            }
            if (right < 0 && left < Long.MIN_VALUE - right) {
                return Long.MIN_VALUE;
            }
            return left + right;
        }

        private boolean intersects(ElementBox other) {
            return x < other.right() && right() > other.x
                    && y < other.bottom() && bottom() > other.y;
        }

        private boolean contains(ElementBox other) {
            return x <= other.x && y <= other.y
                    && right() >= other.right() && bottom() >= other.bottom();
        }

        private boolean isFullBackground(JsonNode element, long slideWidth, long slideHeight) {
            return element != null
                    && "SHAPE".equals(element.path("elementType").asText())
                    && elementId.endsWith("background")
                    && zIndex == 0
                    && x == 0
                    && y == 0
                    && width == slideWidth
                    && height == slideHeight;
        }
    }
}
