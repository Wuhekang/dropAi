package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.TableKind;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.DeterministicLayoutSelector;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.LayoutCatalog;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.LayoutRecipe;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.PageLayoutFeatures;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.SelectedLayout;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.DeterministicTextMetricsService;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ImageFitCalculator;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ImageFitRequest;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ImageFitResult;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.MeasurementException;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontFace;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.ResolvedFontProfile;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TableMetricsCalculator;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TableMetricsRequest;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TableMetricsResult;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TextFitRequest;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.TextFitResult;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderability.v1.PageRenderabilityIssue;
import com.dropai.rewrite.service.ppt.rendering.renderability.v1.PageRenderabilityResult;
import com.dropai.rewrite.service.ppt.rendering.renderability.v1.PageRenderabilityValidator;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ResolvedTheme;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministically compiles validated content into fully resolved editable
 * slide elements.  It does not call a planner, mutate content or render PPTX.
 */
public final class RenderPlanCompiler {
    public static final String ENGINE_VERSION = "1.0.0";

    private final PageRenderabilityValidator renderabilityValidator;
    private final DeterministicTextMetricsService textMetrics;
    private final ImageFitCalculator imageFitCalculator;
    private final TableMetricsCalculator tableMetricsCalculator;
    private final PageLayoutFeatureExtractor featureExtractor;

    public RenderPlanCompiler(
            PageRenderabilityValidator renderabilityValidator,
            DeterministicTextMetricsService textMetrics,
            ImageFitCalculator imageFitCalculator,
            TableMetricsCalculator tableMetricsCalculator
    ) {
        this.renderabilityValidator = Objects.requireNonNull(renderabilityValidator, "renderabilityValidator");
        this.textMetrics = Objects.requireNonNull(textMetrics, "textMetrics");
        this.imageFitCalculator = Objects.requireNonNull(imageFitCalculator, "imageFitCalculator");
        this.tableMetricsCalculator = Objects.requireNonNull(tableMetricsCalculator, "tableMetricsCalculator");
        this.featureExtractor = new PageLayoutFeatureExtractor();
    }

    public DraftSlideRenderPlan compile(
            ValidatedPresentationTree tree,
            ResolvedTheme theme,
            LayoutCatalog catalog,
            RenderingAssetBundle assets,
            ResolvedFontProfile fontProfile
    ) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(fontProfile, "fontProfile");
        requireFontAgreement(theme, fontProfile);

        ObjectNode treeDocument = tree.document();
        ArrayNode pages = (ArrayNode) treeDocument.path("pages");
        for (JsonNode page : pages) {
            requireV1BindingLimits(page, PageType.valueOf(page.path("pageType").asText()));
        }
        PageRenderabilityResult renderability = renderabilityValidator.validate(
                treeDocument,
                assets.manifestDocument(),
                assets.tableIndex());
        if (!renderability.renderable()) {
            PageRenderabilityIssue first = renderability.issues().get(0);
            throw new RenderPlanCompilationException(
                    first.qualityCode(),
                    "Page renderability failed: " + renderability.issues());
        }

        ThemePlanStyleResolver styles = new ThemePlanStyleResolver(theme);
        ObjectNode plan = JsonNodeFactory.instance.objectNode();
        plan.put("schemaVersion", "render-plan.v1");
        plan.put("presentationId", tree.presentationId());
        plan.put("sourceTreeHash", tree.sourceTreeHash());
        plan.set("engine", engine(theme, catalog, fontProfile));
        ObjectNode slideSize = plan.putObject("slideSize");
        slideSize.put("widthEmu", styles.slideWidthEmu());
        slideSize.put("heightEmu", styles.slideHeightEmu());
        plan.set("assets", assets.assets());

        int summaryCount = countPageType(pages, PageType.SUMMARY);
        int summaryOrdinal = 0;
        DeterministicLayoutSelector selector = new DeterministicLayoutSelector(catalog);
        ArrayNode slides = plan.putArray("slides");
        for (JsonNode page : pages) {
            PageType pageType = PageType.valueOf(page.path("pageType").asText());
            if (pageType == PageType.SUMMARY) {
                summaryOrdinal++;
            }
            PageLayoutFeatures features = featureExtractor.extract(
                    page,
                    assets,
                    pageType == PageType.SUMMARY ? summaryOrdinal : 0,
                    pageType == PageType.SUMMARY ? summaryCount : 0);
            SelectedLayout selected = selector.select(features);
            slides.add(compileWithFallbacks(
                    page,
                    treeDocument,
                    assets,
                    fontProfile,
                    styles,
                    catalog,
                    selected.recipe(),
                    pages.size()));
        }
        return DraftSlideRenderPlan.of(plan);
    }

    private ObjectNode compileWithFallbacks(
            JsonNode page,
            ObjectNode tree,
            RenderingAssetBundle assets,
            ResolvedFontProfile fontProfile,
            ThemePlanStyleResolver styles,
            LayoutCatalog catalog,
            LayoutRecipe selected,
            int slideCount
    ) {
        List<LayoutRecipe> attempts = new ArrayList<>();
        attempts.add(selected);
        selected.fallbacks().forEach(id -> attempts.add(catalog.require(id)));
        RenderPlanCompilationException lastFailure = null;
        for (LayoutRecipe recipe : attempts) {
            try {
                return compileSlide(page, tree, assets, fontProfile, styles, recipe, slideCount);
            } catch (RenderPlanCompilationException exception) {
                if (exception.qualityCode() != PptQualityCode.TEXT_OVERFLOW
                        && exception.qualityCode() != PptQualityCode.TABLE_CAPACITY_EXCEEDED
                        && exception.qualityCode() != PptQualityCode.IMAGE_ASPECT_DISTORTION) {
                    throw exception;
                }
                lastFailure = exception;
            }
        }
        throw Objects.requireNonNull(lastFailure, "layout attempt failure");
    }

    private ObjectNode compileSlide(
            JsonNode page,
            ObjectNode tree,
            RenderingAssetBundle assets,
            ResolvedFontProfile fontProfile,
            ThemePlanStyleResolver styles,
            LayoutRecipe recipe,
            int slideCount
    ) {
        int index = page.path("index").asInt();
        String slideId = String.format(Locale.ROOT, "slide-%03d", index);
        PageType pageType = PageType.valueOf(page.path("pageType").asText());
        ObjectNode slide = JsonNodeFactory.instance.objectNode();
        slide.put("slideId", slideId);
        slide.put("sourcePageId", requiredText(page, "sourcePageId"));
        slide.put("index", index);
        slide.put("pageType", pageType.name());
        slide.put("layoutId", recipe.layoutId());
        ArrayNode elements = slide.putArray("elements");
        elements.add(background(slideId, styles));

        switch (pageType) {
            case COVER -> compileCover(elements, slideId, page, tree.path("metadata"), recipe, fontProfile, styles);
            case AGENDA -> compileAgenda(elements, slideId, page, tree.path("agendaSections"), recipe,
                    fontProfile, styles, index, slideCount);
            case CONTENT -> compileContent(elements, slideId, page, recipe, assets, fontProfile, styles,
                    index, slideCount);
            case IMAGE -> compileImage(elements, slideId, page, recipe, assets, fontProfile, styles,
                    index, slideCount);
            case TABLE -> compileTable(elements, slideId, page, recipe, assets, fontProfile, styles,
                    index, slideCount);
            case SUMMARY -> compileSummary(elements, slideId, page, recipe, fontProfile, styles,
                    index, slideCount);
            case THANKS -> compileThanks(elements, slideId, page, tree.path("metadata"), recipe,
                    fontProfile, styles);
        }
        sortElements(elements);
        return slide;
    }

    private void sortElements(ArrayNode elements) {
        List<JsonNode> ordered = new ArrayList<>();
        elements.forEach(ordered::add);
        ordered.sort(Comparator
                .comparingInt((JsonNode element) -> element.path("zIndex").asInt())
                .thenComparing(element -> element.path("elementId").asText()));
        elements.removeAll();
        ordered.forEach(elements::add);
    }

    private void compileCover(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            JsonNode metadata,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        addText(elements, slideId, "title", requiredText(page, "title"), requiredSlot(recipe, "title"),
                "deckTitle", "coverTitle", "display", recipe.constraints().titleMaxLines(),
                "CENTER", "MIDDLE", 220, fonts, styles);
        String subtitle = hasText(metadata, "englishTitle")
                ? requiredText(metadata, "englishTitle")
                : requiredText(page, "description");
        addText(elements, slideId, "subtitle", subtitle, requiredSlot(recipe, "subtitle"),
                "subtitle", "bodyText", "body", 2, "CENTER", "MIDDLE", 230, fonts, styles);
        String metadataText = String.format(Locale.ROOT,
                "汇报人：%s    专业：%s\n指导教师：%s    学号：%s\n%s    %s",
                requiredText(metadata, "presenter"),
                requiredText(metadata, "major"),
                requiredText(metadata, "advisor"),
                requiredText(metadata, "studentNumber"),
                requiredText(metadata, "institution"),
                requiredText(metadata, "date"));
        addText(elements, slideId, "metadata", metadataText, requiredSlot(recipe, "metadata"),
                "body", "bodyText", "body", 3, "CENTER", "MIDDLE", 230, fonts, styles);
    }

    private void compileAgenda(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            JsonNode agendaSections,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            int index,
            int slideCount
    ) {
        addStandardTitle(elements, slideId, page, recipe, fonts, styles);
        if (!agendaSections.isArray() || agendaSections.isEmpty()) {
            throw new RenderPlanCompilationException(PptQualityCode.UNRENDERABLE_PAGE,
                    "Agenda sections are missing");
        }
        SlotGeometry agenda = SlotGeometry.from(requiredSlot(recipe, "agenda"), styles);
        List<SlotGeometry> steps = splitVertical(agenda, agendaSections.size(), styles.spacingEmu("smPt"));
        for (int entry = 0; entry < agendaSections.size(); entry++) {
            String role = String.format(Locale.ROOT, "agenda-step-%02d", entry + 1);
            addCard(elements, slideId, role, steps.get(entry), styles);
            addText(elements, slideId, role + "-text",
                    String.format(Locale.ROOT, "%02d  %s", entry + 1,
                            requiredText(agendaSections.get(entry), "title")),
                    inset(steps.get(entry), styles.spacingEmu("mdPt"), styles.spacingEmu("xsPt")),
                    "bodyStrong", "keyPointCard", "body", 2,
                    "LEFT", "MIDDLE", 230, fonts, styles);
        }
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    private void compileContent(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            RenderingAssetBundle assets,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            int index,
            int slideCount
    ) {
        addStandardTitle(elements, slideId, page, recipe, fonts, styles);
        String keyPoints = bullets(page.path("keyPoints"));
        switch (recipe.layoutId()) {
            case LayoutIds.CONTENT_THREE_CARDS -> compileHorizontalCards(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"),
                    "card", fonts, styles, false);
            case LayoutIds.CONTENT_ARCHITECTURE_LAYERS -> compileArchitectureLayers(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"),
                    fonts, styles);
            case LayoutIds.CONTENT_PROCESS_STEPS -> compileProcessSteps(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"),
                    fonts, styles);
            case LayoutIds.CONTENT_COMPARISON_COLUMNS -> compileComparisonColumns(
                    elements, slideId, page, recipe, fonts, styles);
            case LayoutIds.CONTENT_TEXT_VISUAL_SPLIT -> compileTextVisualSplit(
                    elements, slideId, page, recipe, assets, fonts, styles);
            default -> {
                if (recipe.slots().containsKey("keyPoints") && !keyPoints.isBlank()) {
                    addText(elements, slideId, "key-points", keyPoints, recipe.slots().get("keyPoints"),
                            "body", "keyPointCard", "body", Math.max(3, page.path("keyPoints").size() * 2),
                            "LEFT", "MIDDLE", 230, fonts, styles);
                } else if (recipe.slots().containsKey("body")) {
                    String body = keyPoints.isBlank() ? requiredText(page, "description") : keyPoints;
                    addText(elements, slideId, "body", body, recipe.slots().get("body"),
                            "body", "bodyText", "body", 8, "LEFT", "TOP", 230, fonts, styles);
                }
            }
        }
        if (!LayoutIds.CONTENT_COMPARISON_COLUMNS.equals(recipe.layoutId())
                && !LayoutIds.CONTENT_TEXT_VISUAL_SPLIT.equals(recipe.layoutId())
                && recipe.slots().containsKey("conclusion") && hasText(page, "description")) {
            addText(elements, slideId, "conclusion", requiredText(page, "description"),
                    recipe.slots().get("conclusion"), "bodyStrong", "summaryCard", "body", 4,
                    "LEFT", "MIDDLE", 240, fonts, styles);
        }
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    private void compileHorizontalCards(
            ArrayNode elements,
            String slideId,
            JsonNode values,
            LayoutRecipe.Slot slot,
            String rolePrefix,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            boolean numbered
    ) {
        List<String> items = requiredTextItems(values, rolePrefix);
        List<SlotGeometry> cards = splitHorizontal(
                SlotGeometry.from(slot, styles), items.size(), styles.spacingEmu("mdPt"));
        for (int item = 0; item < items.size(); item++) {
            String role = String.format(Locale.ROOT, "%s-%02d", rolePrefix, item + 1);
            addCard(elements, slideId, role, cards.get(item), styles);
            String text = numbered
                    ? String.format(Locale.ROOT, "%02d\n%s", item + 1, items.get(item))
                    : items.get(item);
            addText(elements, slideId, role + "-text", text,
                    inset(cards.get(item), styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                    "bodyStrong", "keyPointCard", "body", 4,
                    "CENTER", "MIDDLE", 230, fonts, styles);
        }
    }

    private void compileArchitectureLayers(
            ArrayNode elements,
            String slideId,
            JsonNode values,
            LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "architecture layer");
        List<SlotGeometry> layers = splitVertical(
                SlotGeometry.from(slot, styles), items.size(), styles.spacingEmu("smPt"));
        for (int item = 0; item < items.size(); item++) {
            String role = String.format(Locale.ROOT, "layer-%02d", item + 1);
            addCard(elements, slideId, role, layers.get(item), styles);
            addText(elements, slideId, role + "-text", items.get(item),
                    inset(layers.get(item), styles.spacingEmu("mdPt"), styles.spacingEmu("xsPt")),
                    "bodyStrong", "keyPointCard", "body", 3,
                    "CENTER", "MIDDLE", 230, fonts, styles);
        }
    }

    private void compileProcessSteps(
            ArrayNode elements,
            String slideId,
            JsonNode values,
            LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "process step");
        List<SlotGeometry> steps = splitHorizontal(
                SlotGeometry.from(slot, styles), items.size(), styles.spacingEmu("lgPt"));
        for (int item = 0; item < items.size(); item++) {
            String role = String.format(Locale.ROOT, "step-%02d", item + 1);
            addCard(elements, slideId, role, steps.get(item), styles);
            addText(elements, slideId, role + "-text",
                    String.format(Locale.ROOT, "%02d\n%s", item + 1, items.get(item)),
                    inset(steps.get(item), styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                    "bodyStrong", "keyPointCard", "body", 4,
                    "CENTER", "MIDDLE", 230, fonts, styles);
            if (item > 0) {
                addConnector(elements, slideId, item, steps.get(item - 1), steps.get(item), styles);
            }
        }
    }

    private void compileComparisonColumns(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        SlotGeometry left = SlotGeometry.from(requiredSlot(recipe, "body"), styles);
        SlotGeometry right = SlotGeometry.from(requiredSlot(recipe, "keyPoints"), styles);
        addCard(elements, slideId, "column-01", left, styles);
        addText(elements, slideId, "column-01-text", requiredText(page, "description"),
                inset(left, styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                "body", "keyPointCard", "body", 8,
                "LEFT", "MIDDLE", 230, fonts, styles);
        addCard(elements, slideId, "column-02", right, styles);
        addText(elements, slideId, "column-02-text", bullets(page.path("keyPoints")),
                inset(right, styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                "body", "keyPointCard", "body", 8,
                "LEFT", "MIDDLE", 230, fonts, styles);
    }

    private void compileTextVisualSplit(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            RenderingAssetBundle assets,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        String keyPoints = bullets(page.path("keyPoints"));
        addText(elements, slideId, "body",
                keyPoints.isBlank() ? requiredText(page, "description") : keyPoints,
                requiredSlot(recipe, "body"), "body", "bodyText", "body", 8,
                "LEFT", "TOP", 230, fonts, styles);
        addBoundImage(elements, slideId, page, recipe, assets, styles);
        if (recipe.slots().containsKey("caption") && hasText(page, "description")) {
            addText(elements, slideId, "caption", requiredText(page, "description"),
                    recipe.slots().get("caption"), "caption", "caption", "body", 3,
                    "LEFT", "TOP", 240, fonts, styles);
        }
    }

    private void compileImage(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            RenderingAssetBundle assets,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            int index,
            int slideCount
    ) {
        addStandardTitle(elements, slideId, page, recipe, fonts, styles);
        addBoundImage(elements, slideId, page, recipe, assets, styles);

        String keyPoints = bullets(page.path("keyPoints"));
        if (recipe.slots().containsKey("body") && !keyPoints.isBlank()) {
            addText(elements, slideId, "notes", keyPoints, recipe.slots().get("body"),
                    "body", "bodyText", "body", Math.max(3, page.path("keyPoints").size() * 2),
                    "LEFT", "TOP", 240, fonts, styles);
        }
        if (recipe.slots().containsKey("caption") && hasText(page, "description")) {
            addText(elements, slideId, "caption", requiredText(page, "description"),
                    recipe.slots().get("caption"), "caption", "caption", "body", 3,
                    "LEFT", "TOP", 240, fonts, styles);
        }
        if (recipe.slots().containsKey("conclusion") && !keyPoints.isBlank()) {
            addText(elements, slideId, "conclusion", keyPoints, recipe.slots().get("conclusion"),
                    "bodyStrong", "summaryCard", "body", 4,
                    "LEFT", "MIDDLE", 245, fonts, styles);
        }
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    private void addBoundImage(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            RenderingAssetBundle assets,
            ThemePlanStyleResolver styles
    ) {
        JsonNode binding = page.path("assets").get(0);
        ObjectNode asset = assets.requireAsset(requiredText(binding, "assetId"));
        SlotGeometry target = SlotGeometry.from(requiredSlot(recipe, "image"), styles);
        ImageFitResult image;
        try {
            image = imageFitCalculator.calculate(new ImageFitRequest(
                    asset.path("widthPx").asInt(),
                    asset.path("heightPx").asInt(),
                    target.xEmu(), target.yEmu(), target.widthEmu(), target.heightEmu(),
                    recipe.constraints().imageFit(),
                    recipe.constraints().cropAllowed()));
        } catch (MeasurementException exception) {
            throw new RenderPlanCompilationException(exception.qualityCode(), exception.getMessage(), exception);
        }
        requireMinimumImageArea(slideId, image, target, recipe.constraints().minImageAreaRatio());
        elements.add(imageElement(slideId, asset.path("assetId").asText(), image, styles));
    }

    private void requireMinimumImageArea(
            String slideId,
            ImageFitResult image,
            SlotGeometry target,
            BigDecimal minimumRatio
    ) {
        BigDecimal normalized = Objects.requireNonNull(minimumRatio, "minimumRatio").stripTrailingZeros();
        BigInteger numerator = normalized.unscaledValue();
        BigInteger denominator = BigInteger.ONE;
        if (normalized.scale() > 0) {
            denominator = BigInteger.TEN.pow(normalized.scale());
        } else if (normalized.scale() < 0) {
            numerator = numerator.multiply(BigInteger.TEN.pow(-normalized.scale()));
        }
        if (numerator.signum() < 0) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    "Layout minimum image area ratio must not be negative");
        }
        BigInteger renderedArea = BigInteger.valueOf(image.widthEmu())
                .multiply(BigInteger.valueOf(image.heightEmu()));
        BigInteger targetArea = BigInteger.valueOf(target.widthEmu())
                .multiply(BigInteger.valueOf(target.heightEmu()));
        if (renderedArea.multiply(denominator).compareTo(targetArea.multiply(numerator)) < 0) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.IMAGE_ASPECT_DISTORTION,
                    slideId + " image cannot satisfy layout minImageAreaRatio=" + minimumRatio);
        }
    }

    private void compileTable(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            RenderingAssetBundle assets,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            int index,
            int slideCount
    ) {
        addStandardTitle(elements, slideId, page, recipe, fonts, styles);
        JsonNode binding = page.path("tables").get(0);
        String tableId = requiredText(binding, "tableId");
        ObjectNode table = assets.requireTable(tableId);
        TableKind kind = TableKind.valueOf(requiredText(table, "tableKind"));
        List<String> headers = textValues(table.path("columns"));
        List<List<String>> rows = rowValues(table.path("rows"));
        SlotGeometry slot = SlotGeometry.from(requiredSlot(recipe, "table"), styles);
        String headerTypography = styles.componentTypographyStyle("tableHeader");
        String bodyTypography = styles.componentTypographyStyle("tableBody");
        int headerWeight = styles.fontWeight(headerTypography);
        int bodyWeight = styles.fontWeight(bodyTypography);
        int lineSpacingPermille = styles.lineSpacingPermille(bodyTypography);
        long horizontalCellPaddingEmu = Emu.points(BigDecimal.valueOf(8));
        long verticalCellPaddingEmu = Emu.points(BigDecimal.valueOf(5));
        TableMetricsResult measured;
        try {
            measured = tableMetricsCalculator.calculate(new TableMetricsRequest(
                    kind,
                    headers,
                    rows,
                    fonts,
                    "body",
                    headerWeight,
                    bodyWeight,
                    styles.defaultFontSize(bodyTypography),
                    styles.minimumFontSize(bodyTypography),
                    lineSpacingPermille,
                    slot.widthEmu(),
                    slot.heightEmu(),
                    horizontalCellPaddingEmu,
                    verticalCellPaddingEmu,
                    Emu.inches(BigDecimal.valueOf(0.72)),
                    2,
                    3));
        } catch (MeasurementException exception) {
            throw new RenderPlanCompilationException(exception.qualityCode(), exception.getMessage(), exception);
        }
        elements.add(tableElement(slideId, tableId, kind, slot, measured, fonts, styles,
                headerWeight, bodyWeight, lineSpacingPermille,
                horizontalCellPaddingEmu, verticalCellPaddingEmu));
        if (recipe.slots().containsKey("conclusion") && hasText(page, "description")) {
            addText(elements, slideId, "conclusion", requiredText(page, "description"),
                    recipe.slots().get("conclusion"), "caption", "caption", "body", 3,
                    "LEFT", "MIDDLE", 240, fonts, styles);
        }
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    private void compileSummary(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            int index,
            int slideCount
    ) {
        addStandardTitle(elements, slideId, page, recipe, fonts, styles);
        addText(elements, slideId, "key-points", bullets(page.path("keyPoints")),
                requiredSlot(recipe, "keyPoints"), "body", "keyPointCard", "body", 6,
                "LEFT", "MIDDLE", 230, fonts, styles);
        addText(elements, slideId, "conclusion", requiredText(page, "description"),
                requiredSlot(recipe, "conclusion"), "bodyStrong", "summaryCard", "body", 4,
                "LEFT", "MIDDLE", 240, fonts, styles);
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    private void compileThanks(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            JsonNode metadata,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        addText(elements, slideId, "title", requiredText(page, "title"), requiredSlot(recipe, "title"),
                "sectionTitle", "sectionTitle", "display", recipe.constraints().titleMaxLines(),
                "CENTER", "MIDDLE", 220, fonts, styles);
        addText(elements, slideId, "body", requiredText(page, "description"), requiredSlot(recipe, "body"),
                "subtitle", "bodyText", "body", 2, "CENTER", "MIDDLE", 230, fonts, styles);
        if (recipe.slots().containsKey("footer")) {
            addText(elements, slideId, "footer", requiredText(metadata, "institution"),
                    recipe.slots().get("footer"), "footnote", "pageNumber", "body", 2,
                    "CENTER", "MIDDLE", 240, fonts, styles);
        }
    }

    private void addStandardTitle(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        addText(elements, slideId, "title", requiredText(page, "title"), requiredSlot(recipe, "title"),
                "slideTitle", "slideTitle", "display", recipe.constraints().titleMaxLines(),
                "LEFT", "MIDDLE", 220, fonts, styles);
    }

    private void addFooter(
            ArrayNode elements,
            String slideId,
            LayoutRecipe recipe,
            int index,
            int slideCount,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        if (!recipe.slots().containsKey("footer")) {
            return;
        }
        addText(elements, slideId, "footer", String.format(Locale.ROOT, "%02d / %02d", index, slideCount),
                recipe.slots().get("footer"), "footnote", "pageNumber", "body", 1,
                "RIGHT", "MIDDLE", 260, fonts, styles);
    }

    private void addText(
            ArrayNode elements,
            String slideId,
            String role,
            String text,
            LayoutRecipe.Slot slot,
            String typographyStyle,
            String component,
            String fontRole,
            int maxLines,
            String horizontalAlign,
            String verticalAlign,
            int zIndex,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        addText(elements, slideId, role, text, SlotGeometry.from(slot, styles), typographyStyle,
                component, fontRole, maxLines, horizontalAlign, verticalAlign, zIndex, fonts, styles);
    }

    private void addText(
            ArrayNode elements,
            String slideId,
            String role,
            String text,
            SlotGeometry geometry,
            String typographyStyle,
            String component,
            String fontRole,
            int maxLines,
            String horizontalAlign,
            String verticalAlign,
            int zIndex,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        if (text == null || text.isBlank()) {
            throw new RenderPlanCompilationException(PptQualityCode.UNRENDERABLE_PAGE,
                    slideId + " " + role + " text is blank");
        }
        int weight = styles.fontWeight(typographyStyle);
        TextFitResult fitted = textMetrics.fit(new TextFitRequest(
                text,
                fonts,
                fontRole,
                weight,
                styles.defaultFontSize(typographyStyle),
                styles.minimumFontSize(typographyStyle),
                styles.lineSpacingPermille(typographyStyle),
                geometry.widthEmu(),
                geometry.heightEmu(),
                maxLines));
        if (!fitted.fits()) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.TEXT_OVERFLOW,
                    slideId + " " + role + " cannot fit without truncation: " + fitted.failureReason());
        }
        ResolvedFontFace face = fonts.requireFace(fontRole, weight);
        if (!face.fontFingerprint().equals(fitted.fontFingerprint())) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.FONT_UNAVAILABLE,
                    slideId + " " + role + " measurement font fingerprint drifted during compilation");
        }
        long lineHeight = fitted.lineHeightEmu();
        ObjectNode element = baseElement(
                slideId + "-" + role,
                "TEXT",
                geometry.xEmu(), geometry.yEmu(), geometry.widthEmu(), geometry.heightEmu(),
                zIndex,
                styles.textStyle(typographyStyle, component, fontFaceId(fontRole, weight),
                        fitted.fontFamily(), fitted.fontSizeHundredthPt(), lineHeight,
                        horizontalAlign, verticalAlign));
        element.set("styleSource", styles.styleSource(component, "typography.styles." + typographyStyle));
        element.put("text", fitted.renderedText());
        element.put("lineCount", fitted.lines().size());
        element.put("requiredWidthEmu", fitted.requiredWidthEmu());
        element.put("requiredHeightEmu", fitted.requiredHeightEmu());
        elements.add(element);
    }

    private void addCard(
            ArrayNode elements,
            String slideId,
            String role,
            SlotGeometry geometry,
            ThemePlanStyleResolver styles
    ) {
        ObjectNode shape = baseElement(
                slideId + "-" + role,
                "SHAPE",
                geometry.xEmu(), geometry.yEmu(), geometry.widthEmu(), geometry.heightEmu(),
                110,
                styles.shapeStyle("keyPointCard"));
        shape.set("styleSource", styles.styleSource("keyPointCard", "components.keyPointCard"));
        shape.put("shapeType", "ROUNDED_RECTANGLE");
        elements.add(shape);
    }

    private void addConnector(
            ArrayNode elements,
            String slideId,
            int ordinal,
            SlotGeometry from,
            SlotGeometry to,
            ThemePlanStyleResolver styles
    ) {
        long startX = from.rightEmu();
        long endX = to.xEmu();
        long centerY = Math.addExact(from.yEmu(), from.heightEmu() / 2L);
        ObjectNode connector = baseElement(
                String.format(Locale.ROOT, "%s-connector-%02d", slideId, ordinal),
                "CONNECTOR",
                startX,
                centerY,
                Math.max(1L, endX - startX),
                1L,
                105,
                styles.connectorStyle());
        connector.set("styleSource", styles.styleSource("keyPointCard", "colors.accent.primary"));
        connector.put("startXEmu", startX);
        connector.put("startYEmu", centerY);
        connector.put("endXEmu", endX);
        connector.put("endYEmu", centerY);
        connector.put("lineType", "STRAIGHT");
        connector.put("startArrow", "NONE");
        connector.put("endArrow", "TRIANGLE");
        elements.add(connector);
    }

    private ObjectNode imageElement(
            String slideId,
            String assetId,
            ImageFitResult image,
            ThemePlanStyleResolver styles
    ) {
        ObjectNode element = baseElement(
                slideId + "-image-" + assetId,
                "IMAGE",
                image.xEmu(), image.yEmu(), image.widthEmu(), image.heightEmu(),
                120,
                styles.imageStyle());
        element.set("styleSource", styles.styleSource("imageFrame", "components.imageFrame"));
        element.put("assetId", assetId);
        element.put("fitMode", image.fitMode().name());
        element.put("cropAllowed", image.cropAllowed());
        image.sourceCrop().ifPresent(crop -> {
            ObjectNode sourceCrop = element.putObject("sourceCrop");
            sourceCrop.put("leftPermille", crop.leftPermille());
            sourceCrop.put("topPermille", crop.topPermille());
            sourceCrop.put("rightPermille", crop.rightPermille());
            sourceCrop.put("bottomPermille", crop.bottomPermille());
        });
        return element;
    }

    private ObjectNode tableElement(
            String slideId,
            String tableId,
            TableKind tableKind,
            SlotGeometry slot,
            TableMetricsResult measured,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles,
            int headerWeight,
            int bodyWeight,
            int lineSpacingPermille,
            long horizontalCellPaddingEmu,
            long verticalCellPaddingEmu
    ) {
        long y = Math.addExact(slot.yEmu(), Math.max(0L, slot.heightEmu() - measured.totalHeightEmu()) / 2L);
        ObjectNode element = baseElement(
                slideId + "-table-" + tableId,
                "TABLE",
                slot.xEmu(), y, slot.widthEmu(), measured.totalHeightEmu(),
                120,
                styles.tableStyle(
                        measured.fontFamily(),
                        measured.fontSizeHundredthPt(),
                        fontFaceId("body", headerWeight),
                        headerWeight,
                        fontFaceId("body", bodyWeight),
                        bodyWeight,
                        lineSpacingPermille,
                        horizontalCellPaddingEmu,
                        verticalCellPaddingEmu));
        fonts.requireFace("body", headerWeight);
        if (!fonts.requireFace("body", bodyWeight).fontFingerprint().equals(measured.fontFingerprint())) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.FONT_UNAVAILABLE,
                    slideId + " table measurement font fingerprint does not match its executable faces");
        }
        element.set("styleSource", styles.styleSource("tableBody", "components.tableBody"));
        element.put("tableKind", tableKind.name());
        element.put("headerRowHeightEmu", measured.headerRowHeightEmu());
        element.put("bodyRowHeightEmu", measured.bodyRowHeightEmu());
        ArrayNode columns = element.putArray("columns");
        for (int index = 0; index < measured.renderedHeaders().size(); index++) {
            ObjectNode column = columns.addObject();
            column.put("key", String.format(Locale.ROOT, "column-%02d", index + 1));
            column.put("header", measured.renderedHeaders().get(index));
            column.put("widthEmu", measured.columnWidthsEmu().get(index));
        }
        ArrayNode rows = element.putArray("rows");
        for (List<String> values : measured.renderedRows()) {
            ArrayNode cells = rows.addObject().putArray("cells");
            values.forEach(cells::add);
        }
        if (tableKind == TableKind.TEST_RESULT) {
            addStatusCells(element, measured, styles, bodyWeight);
        }
        return element;
    }

    private void addStatusCells(
            ObjectNode table,
            TableMetricsResult measured,
            ThemePlanStyleResolver styles,
            int bodyWeight
    ) {
        int statusColumn = -1;
        for (int index = 0; index < measured.renderedHeaders().size(); index++) {
            String normalized = measured.renderedHeaders().get(index).strip();
            if ("状态".equals(normalized) || "status".equalsIgnoreCase(normalized)) {
                statusColumn = index;
                break;
            }
        }
        if (statusColumn < 0) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    "TEST_RESULT table requires an explicit status column");
        }
        ArrayNode statusCells = table.putArray("statusCells");
        for (int rowIndex = 0; rowIndex < measured.renderedRows().size(); rowIndex++) {
            List<String> row = measured.renderedRows().get(rowIndex);
            if (statusColumn >= row.size() || row.get(statusColumn).isBlank()) {
                throw new RenderPlanCompilationException(
                        PptQualityCode.UNRENDERABLE_PAGE,
                        "TEST_RESULT table contains a blank status cell at row " + rowIndex);
            }
            String value = row.get(statusColumn);
            ObjectNode status = statusCells.addObject();
            status.put("rowIndex", rowIndex);
            status.put("columnIndex", statusColumn);
            status.put("text", value);
            status.put("fillColor", statusFillColor(value, styles));
            status.put("textColor", styles.colorToken("colors.text.inverse"));
            status.put("fontFaceId", fontFaceId("body", bodyWeight));
            status.put("fontWeight", bodyWeight);
            status.put("horizontalAlign", "CENTER");
            status.put("verticalAlign", "MIDDLE");
        }
    }

    private String statusFillColor(String value, ThemePlanStyleResolver styles) {
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.equals("通过") || normalized.equals("正常") || normalized.equals("成功")
                || normalized.equals("PASS") || normalized.equals("PASSED") || normalized.equals("OK")) {
            return styles.colorToken("colors.state.success");
        }
        if (normalized.equals("失败") || normalized.equals("异常") || normalized.equals("未通过")
                || normalized.equals("FAIL") || normalized.equals("FAILED") || normalized.equals("ERROR")) {
            return styles.colorToken("colors.state.danger");
        }
        return styles.colorToken("colors.state.warning");
    }

    private ObjectNode background(String slideId, ThemePlanStyleResolver styles) {
        ObjectNode background = baseElement(
                slideId + "-background",
                "SHAPE",
                0L,
                0L,
                styles.slideWidthEmu(),
                styles.slideHeightEmu(),
                0,
                styles.canvasStyle());
        background.put("shapeType", "RECTANGLE");
        return background;
    }

    private ObjectNode baseElement(
            String id,
            String type,
            long x,
            long y,
            long width,
            long height,
            int zIndex,
            ObjectNode resolvedStyle
    ) {
        ObjectNode element = JsonNodeFactory.instance.objectNode();
        element.put("elementId", id);
        element.put("elementType", type);
        element.put("xEmu", x);
        element.put("yEmu", y);
        element.put("widthEmu", width);
        element.put("heightEmu", height);
        element.put("zIndex", zIndex);
        element.set("resolvedStyle", resolvedStyle);
        return element;
    }

    private ObjectNode engine(
            ResolvedTheme theme,
            LayoutCatalog catalog,
            ResolvedFontProfile fonts
    ) {
        ObjectNode engine = JsonNodeFactory.instance.objectNode();
        engine.put("engineVersion", ENGINE_VERSION);
        engine.put("themeId", theme.themeId());
        engine.put("themeVersion", theme.themeVersion());
        engine.put("themeHash", theme.resolvedThemeHash());
        engine.put("layoutCatalogVersion", catalog.catalogVersion());
        engine.put("layoutCatalogHash", catalog.catalogHash());
        engine.put("fontProfileHash", fonts.fontProfileHash());
        ObjectNode profile = engine.putObject("resolvedFontProfile");
        profile.put("profileId", fonts.profileId());
        profile.put("measurementEngineVersion", fonts.measurementEngineVersion());
        ArrayNode faces = profile.putArray("faces");
        fonts.faces().forEach((role, byWeight) -> byWeight.forEach((weight, face) -> {
            ObjectNode value = faces.addObject();
            value.put("fontFaceId", fontFaceId(role, weight));
            value.put("role", face.role());
            value.put("weight", face.weight());
            value.put("selectedFamily", face.selectedFamily());
            value.put("postScriptName", face.postScriptName());
            value.put("fontSource", face.fontSource().name());
            value.put("fontFingerprint", face.fontFingerprint());
            value.put("fallbackApplied", face.fallbackApplied());
        }));
        return engine;
    }

    private String fontFaceId(String role, int weight) {
        return requireStableId(role, "font role") + "-" + weight;
    }

    private String requireStableId(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.INVALID_REFERENCE,
                    name + " cannot form a stable RenderPlan id: " + value);
        }
        return value;
    }

    private void requireFontAgreement(ResolvedTheme theme, ResolvedFontProfile fonts) {
        Map<String, String> configured = theme.fontProfile().resolvedFamilies();
        for (String role : List.of("body", "display")) {
            String configuredFamily = configured.get(role);
            String actualFamily = fonts.selectedFamilies().get(role);
            if (configuredFamily == null || !configuredFamily.equals(actualFamily)) {
                throw new RenderPlanCompilationException(
                        PptQualityCode.FONT_UNAVAILABLE,
                        "Resolved theme and measured font disagree for " + role
                                + ": configured=" + configuredFamily + ", actual=" + actualFamily);
            }
        }
    }

    private void requireV1BindingLimits(JsonNode page, PageType pageType) {
        int assetCount = bindingCount(page, "assets");
        int tableCount = bindingCount(page, "tables");
        boolean valid = switch (pageType) {
            case IMAGE -> assetCount == 1 && tableCount == 0;
            case TABLE -> assetCount == 0 && tableCount == 1;
            case CONTENT -> assetCount <= 1 && tableCount == 0;
            default -> true;
        };
        if (!valid) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    pageType + " page violates the V1 single-binding contract: assets="
                            + assetCount + ", tables=" + tableCount);
        }
    }

    private int bindingCount(JsonNode page, String field) {
        JsonNode bindings = page.path(field);
        if (!bindings.isArray()) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    field + " must be an array");
        }
        return bindings.size();
    }

    private List<String> requiredTextItems(JsonNode values, String role) {
        List<String> items = textValues(values);
        if (items.isEmpty() || items.stream().anyMatch(String::isBlank)) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    role + " requires at least one non-blank item");
        }
        return items;
    }

    private List<SlotGeometry> splitHorizontal(SlotGeometry slot, int count, long gapEmu) {
        if (count < 1 || gapEmu < 0) {
            throw new IllegalArgumentException("Horizontal split requires a positive count and non-negative gap");
        }
        long totalGap = Math.multiplyExact(gapEmu, count - 1L);
        long usable = Math.subtractExact(slot.widthEmu(), totalGap);
        if (usable < count) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    "Layout slot is too narrow for " + count + " editable visual groups");
        }
        long base = usable / count;
        long remainder = usable % count;
        long cursor = slot.xEmu();
        List<SlotGeometry> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long width = base + (index < remainder ? 1L : 0L);
            result.add(new SlotGeometry(cursor, slot.yEmu(), width, slot.heightEmu()));
            cursor = Math.addExact(Math.addExact(cursor, width), gapEmu);
        }
        return List.copyOf(result);
    }

    private List<SlotGeometry> splitVertical(SlotGeometry slot, int count, long gapEmu) {
        if (count < 1 || gapEmu < 0) {
            throw new IllegalArgumentException("Vertical split requires a positive count and non-negative gap");
        }
        long totalGap = Math.multiplyExact(gapEmu, count - 1L);
        long usable = Math.subtractExact(slot.heightEmu(), totalGap);
        if (usable < count) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    "Layout slot is too short for " + count + " editable visual groups");
        }
        long base = usable / count;
        long remainder = usable % count;
        long cursor = slot.yEmu();
        List<SlotGeometry> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long height = base + (index < remainder ? 1L : 0L);
            result.add(new SlotGeometry(slot.xEmu(), cursor, slot.widthEmu(), height));
            cursor = Math.addExact(Math.addExact(cursor, height), gapEmu);
        }
        return List.copyOf(result);
    }

    private SlotGeometry inset(SlotGeometry slot, long horizontal, long vertical) {
        long width = Math.subtractExact(slot.widthEmu(), Math.multiplyExact(horizontal, 2L));
        long height = Math.subtractExact(slot.heightEmu(), Math.multiplyExact(vertical, 2L));
        if (width < 1 || height < 1) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    "Visual group is too small for themed text insets");
        }
        return new SlotGeometry(
                Math.addExact(slot.xEmu(), horizontal),
                Math.addExact(slot.yEmu(), vertical),
                width,
                height);
    }

    private int countPageType(ArrayNode pages, PageType type) {
        int count = 0;
        for (JsonNode page : pages) {
            if (type.name().equals(page.path("pageType").asText())) {
                count++;
            }
        }
        return count;
    }

    private LayoutRecipe.Slot requiredSlot(LayoutRecipe recipe, String name) {
        LayoutRecipe.Slot slot = recipe.slots().get(name);
        if (slot == null) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNKNOWN_LAYOUT,
                    recipe.layoutId() + " is missing required slot " + name);
        }
        return slot;
    }

    private String bullets(JsonNode values) {
        if (!values.isArray()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isTextual() && !value.textValue().isBlank()) {
                lines.add("• " + value.textValue());
            }
        }
        return String.join("\n", lines);
    }

    private List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (!values.isArray()) {
            return List.of();
        }
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private List<List<String>> rowValues(JsonNode values) {
        List<List<String>> rows = new ArrayList<>();
        if (!values.isArray()) {
            return List.of();
        }
        values.forEach(row -> rows.add(textValues(row)));
        return List.copyOf(rows);
    }

    private boolean hasText(JsonNode owner, String field) {
        JsonNode value = owner.path(field);
        return value.isTextual() && !value.textValue().isBlank();
    }

    private String requiredText(JsonNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    field + " must be a non-blank string");
        }
        return value.textValue();
    }
}
