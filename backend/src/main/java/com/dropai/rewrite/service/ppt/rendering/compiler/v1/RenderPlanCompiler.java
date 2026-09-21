package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.AssetKind;
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
import com.dropai.rewrite.service.ppt.rendering.template.v1.RenderingTemplatePack;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ResolvedTheme;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministically compiles validated content into fully resolved editable
 * slide elements.  It does not call a planner, mutate content or render PPTX.
 */
public final class RenderPlanCompiler {
    public static final String ENGINE_VERSION = "1.0.1";

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
        return compileInternal(tree, theme, catalog, assets, fontProfile, null);
    }

    /**
     * Compiles the same frozen content/layout contract with an optional trusted
     * visual template surface. The surface contribution is limited to one
     * full-slide decorative image and cannot alter page content, layout
     * selection or existing element geometry.
     */
    public DraftSlideRenderPlan compile(
            ValidatedPresentationTree tree,
            ResolvedTheme theme,
            LayoutCatalog catalog,
            RenderingAssetBundle assets,
            ResolvedFontProfile fontProfile,
            RenderingTemplatePack templatePack
    ) {
        return compileInternal(
                tree,
                theme,
                catalog,
                assets,
                fontProfile,
                Objects.requireNonNull(templatePack, "templatePack"));
    }

    private DraftSlideRenderPlan compileInternal(
            ValidatedPresentationTree tree,
            ResolvedTheme theme,
            LayoutCatalog catalog,
            RenderingAssetBundle assets,
            ResolvedFontProfile fontProfile,
            RenderingTemplatePack templatePack
    ) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(fontProfile, "fontProfile");
        requireFontAgreement(theme, fontProfile);
        if (templatePack != null
                && (!templatePack.themeRequest().themeId().equals(theme.themeId())
                || !templatePack.themeRequest().expectedVersion().equals(theme.themeVersion()))) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Resolved theme does not match template pack " + templatePack.templatePackId());
        }

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
        List<String> recentLayouts = new ArrayList<>();
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
            SelectedLayout selected = selector.select(features, recentLayouts);
            ObjectNode compiled = compileWithFallbacks(
                    page,
                    treeDocument,
                    assets,
                    fontProfile,
                    styles,
                    catalog,
                    selected.recipe(),
                    pages.size(),
                    templatePack);
            slides.add(compiled);
            recentLayouts.add(compiled.path("layoutId").asText());
            if (recentLayouts.size() > 2) recentLayouts.remove(0);
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
            int slideCount,
            RenderingTemplatePack templatePack
    ) {
        List<LayoutRecipe> attempts = new ArrayList<>();
        collectFallbackAttempts(selected, catalog, new LinkedHashSet<>(), attempts);
        RenderPlanCompilationException lastFailure = null;
        for (LayoutRecipe recipe : attempts) {
            try {
                return compileSlide(
                        page, tree, assets, fontProfile, styles, recipe, slideCount, templatePack);
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

    private void collectFallbackAttempts(
            LayoutRecipe recipe,
            LayoutCatalog catalog,
            Set<String> visited,
            List<LayoutRecipe> attempts
    ) {
        if (!visited.add(recipe.layoutId())) {
            return;
        }
        attempts.add(recipe);
        for (String fallback : recipe.fallbacks()) {
            collectFallbackAttempts(catalog.require(fallback), catalog, visited, attempts);
        }
    }

    private ObjectNode compileSlide(
            JsonNode page,
            ObjectNode tree,
            RenderingAssetBundle assets,
            ResolvedFontProfile fontProfile,
            ThemePlanStyleResolver styles,
            LayoutRecipe recipe,
            int slideCount,
            RenderingTemplatePack templatePack
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
        boolean openingProposal="OPENING_PROPOSAL".equalsIgnoreCase(tree.path("metadata").path("presentationMode").asText());
        if (templatePack != null && !(openingProposal && (pageType == PageType.CONTENT || pageType == PageType.SUMMARY))) {
            templatePack.surfaceAssetId(pageType).ifPresent(assetId ->
                    elements.add(templateSurfaceElement(slideId, assetId, assets, styles)));
        }

        switch (pageType) {
            case COVER -> compileCover(elements, slideId, page, tree.path("metadata"), recipe, fontProfile, styles);
            case AGENDA -> compileAgenda(elements, slideId, page, tree.path("agendaSections"), recipe,
                    fontProfile, styles, index, slideCount);
            case SECTION -> compileSection(elements, slideId, page, tree.path("agendaSections"), recipe, fontProfile, styles);
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
        String titleStyle=LayoutIds.COVER_CENTERED_LONG_TITLE.equals(recipe.layoutId())?"sectionTitle":"deckTitle";
        String titleComponent=LayoutIds.COVER_CENTERED_LONG_TITLE.equals(recipe.layoutId())?"sectionTitle":"coverTitle";
        addText(elements, slideId, "title", requiredText(page, "title"), requiredSlot(recipe, "title"),
                titleStyle, titleComponent, "display", recipe.constraints().titleMaxLines(),
                "CENTER", "MIDDLE", 220, fonts, styles);
        String subtitle = hasText(metadata, "englishTitle")
                ? requiredText(metadata, "englishTitle")
                : requiredText(page, "description");
        addText(elements, slideId, "subtitle", subtitle, requiredSlot(recipe, "subtitle"),
                "subtitle", styles.hasComponent("surfaceSubtitle")?"surfaceSubtitle":"bodyText", "body", 2, "CENTER", "MIDDLE", 230, fonts, styles);
        List<String> metadataLines = new ArrayList<>();
        String presenter=metadata.path("presenter").asText("").strip(),major=requiredText(metadata,"major");
        metadataLines.add(presenter.isBlank()?"专业："+major:String.format(Locale.ROOT, "汇报人：%s    专业：%s",presenter,major));
        String advisor=metadata.path("advisor").asText("").strip();
        String advisorLine = advisor.isBlank()?"":"指导教师：" + advisor;
        String studentNumber = metadata.path("studentNumber").asText("").strip();
        if (!studentNumber.isBlank()) advisorLine += (advisorLine.isBlank()?"":"    ")+"学号：" + studentNumber;
        if(!advisorLine.isBlank())metadataLines.add(advisorLine);
        String institutionLine = requiredText(metadata, "institution");
        String date = metadata.path("date").asText("").strip();
        if (!date.isBlank()) institutionLine += "    " + date;
        metadataLines.add(institutionLine);
        String metadataText = String.join("\n", metadataLines);
        addText(elements, slideId, "metadata", metadataText, requiredSlot(recipe, "metadata"),
                "body", styles.hasComponent("surfaceBody")?"surfaceBody":"bodyText", "body", 3, "CENTER", "MIDDLE", 230, fonts, styles);
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
        addText(elements, slideId, "title", requiredText(page, "title"), requiredSlot(recipe, "title"),
                "slideTitle", "slideTitle", "display", recipe.constraints().titleMaxLines(),
                "CENTER", "MIDDLE", 220, fonts, styles);
        if (!agendaSections.isArray() || agendaSections.isEmpty()) {
            throw new RenderPlanCompilationException(PptQualityCode.UNRENDERABLE_PAGE,
                    "Agenda sections are missing");
        }
        SlotGeometry agenda = SlotGeometry.from(requiredSlot(recipe, "agenda"), styles);
        List<SlotGeometry> steps = splitVertical(agenda, agendaSections.size(), styles.spacingEmu("smPt"));
        for (int entry = 0; entry < agendaSections.size(); entry++) {
            String role = String.format(Locale.ROOT, "agenda-step-%02d", entry + 1);
            addCard(elements, slideId, role, steps.get(entry), styles);
            JsonNode agendaEntry=agendaSections.get(entry);
            String range=agendaRange(agendaEntry.path("sourcePageIds"));
            String description=agendaEntry.path("description").asText("").strip();
            String agendaText=String.format(Locale.ROOT,"%02d  %s%s%s",entry+1,
                    requiredText(agendaEntry,"title"),range.isBlank()?"":"    "+range,
                    description.isBlank()?"":"\n"+description);
            addText(elements,slideId,role+"-text",agendaText,
                    inset(steps.get(entry),styles.spacingEmu("mdPt"),styles.spacingEmu("xsPt")),
                    "bodyStrong","keyPointCard","body",3,
                    "CENTER","MIDDLE",230,fonts,styles);
        }
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    private String agendaRange(JsonNode sourcePageIds){
        if(!sourcePageIds.isArray()||sourcePageIds.isEmpty())return"";
        String first=sourcePageIds.get(0).asText("").replaceFirst("^page-0*","");
        String last=sourcePageIds.get(sourcePageIds.size()-1).asText("").replaceFirst("^page-0*","");
        if(first.isBlank()||last.isBlank())return"";
        return first.equals(last)?"P"+first:"P"+first+" — P"+last;
    }

    private void compileSection(
            ArrayNode elements,
            String slideId,
            JsonNode page,
            JsonNode agendaSections,
            LayoutRecipe recipe,
            ResolvedFontProfile fonts,
            ThemePlanStyleResolver styles
    ) {
        String raw=requiredText(page,"title");
        var matcher=java.util.regex.Pattern.compile("^(\\d{2})\\s+(.+)$").matcher(raw);
        String number=matcher.matches()?matcher.group(1):String.format(Locale.ROOT,"%02d",page.path("index").asInt());
        String title=matcher.matches()?matcher.group(2):raw;
        addText(elements,slideId,"number",number,requiredSlot(recipe,"title"),
                "deckTitle","sectionTitle","display",1,"CENTER","MIDDLE",220,fonts,styles);
        addText(elements,slideId,"title",title,requiredSlot(recipe,"body"),
                "sectionTitle","sectionTitle","display",recipe.constraints().titleMaxLines(),
                "CENTER","MIDDLE",221,fonts,styles);
    }

    private JsonNode agendaEntry(JsonNode agendaSections,String sectionId){if(agendaSections!=null&&agendaSections.isArray())for(JsonNode entry:agendaSections)if(sectionId.equals(entry.path("sectionId").asText()))return entry;return null;}

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
                    "card", fonts, styles, true);
            case LayoutIds.CONTENT_ARCHITECTURE_LAYERS -> compileArchitectureLayers(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"),
                    fonts, styles);
            case LayoutIds.CONTENT_PROCESS_STEPS -> compileProcessSteps(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"),
                    fonts, styles);
            case LayoutIds.CONTENT_COMPARISON_COLUMNS -> compileComparisonColumns(
                    elements, slideId, page, recipe, fonts, styles);
            case LayoutIds.CONTENT_COMPARISON_FOCUS -> compileComparisonFocus(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_QUADRANT_MATRIX -> compileQuadrantMatrix(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_RESEARCH_TIMELINE -> compileResearchTimeline(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_PROGRESS_ROADMAP -> compileProgressRoadmap(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_METHOD_ORBIT -> compileMethodOrbit(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_FRAMEWORK_FLOW -> compileFrameworkFlow(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_PROBLEM_SOLUTION_BRIDGE -> compileProblemSolutionBridge(
                    elements, slideId, page.path("keyPoints"), requiredSlot(recipe, "keyPoints"), fonts, styles);
            case LayoutIds.CONTENT_TEXT_VISUAL_SPLIT -> compileTextVisualSplit(
                    elements, slideId, page, recipe, assets, fonts, styles);
            default -> {
                if (recipe.slots().containsKey("keyPoints") && !keyPoints.isBlank()) {
                    SlotGeometry keyPointSlot=SlotGeometry.from(recipe.slots().get("keyPoints"),styles);
                    addCard(elements,slideId,"key-points",keyPointSlot,styles);
                    addText(elements, slideId, "key-points-text", keyPoints,
                            inset(keyPointSlot,styles.spacingEmu("mdPt"),styles.spacingEmu("smPt")),
                            "body", "keyPointCard", "body", Math.max(3, page.path("keyPoints").size() * 2),
                            "LEFT", "MIDDLE", 230, fonts, styles);
                } else if (recipe.slots().containsKey("body")) {
                    String body = keyPoints.isBlank() ? requiredText(page, "description") : keyPoints;
                    SlotGeometry bodySlot=SlotGeometry.from(recipe.slots().get("body"),styles);
                    addCard(elements,slideId,"body",bodySlot,styles);
                    addText(elements, slideId, "body-text", body,
                            inset(bodySlot,styles.spacingEmu("mdPt"),styles.spacingEmu("smPt")),
                            "body", "keyPointCard", "body", 8, "LEFT", "MIDDLE", 230, fonts, styles);
                }
            }
        }
        if (!LayoutIds.CONTENT_COMPARISON_COLUMNS.equals(recipe.layoutId())
                && !LayoutIds.CONTENT_TEXT_VISUAL_SPLIT.equals(recipe.layoutId())
                && recipe.slots().containsKey("conclusion") && hasText(page, "description")) {
            addText(elements, slideId, "conclusion", requiredText(page, "description"),
                    recipe.slots().get("conclusion"), "caption", "summaryCard", "body", 3,
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
        SlotGeometry content = SlotGeometry.from(slot, styles);
        List<SlotGeometry> cards;
        if (items.size() == 4) {
            List<SlotGeometry> rows = splitVertical(content, 2, styles.spacingEmu("mdPt"));
            cards = new ArrayList<>(4);
            cards.addAll(splitHorizontal(rows.get(0), 2, styles.spacingEmu("mdPt")));
            cards.addAll(splitHorizontal(rows.get(1), 2, styles.spacingEmu("mdPt")));
        } else {
            cards = splitHorizontal(content, items.size(), styles.spacingEmu("mdPt"));
        }
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
            addText(elements, slideId, role + "-text", String.format(Locale.ROOT,"%02d  %s",item+1,items.get(item)),
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
        List<String> items=requiredTextItems(page.path("keyPoints"),"comparison point");
        boolean paired=items.size()==2;
        addCard(elements, slideId, "column-01", left, styles);
        addText(elements, slideId, "column-01-text", paired?items.get(0):requiredText(page, "description"),
                inset(left, styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                "body", "keyPointCard", "body", 8,
                "LEFT", "MIDDLE", 230, fonts, styles);
        addCard(elements, slideId, "column-02", right, styles);
        addText(elements, slideId, "column-02-text", paired?items.get(1):bullets(page.path("keyPoints")),
                inset(right, styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                "body", "keyPointCard", "body", 8,
                "LEFT", "MIDDLE", 230, fonts, styles);
        if(paired&&recipe.slots().containsKey("conclusion"))addText(elements,slideId,"conclusion",requiredText(page,"description"),recipe.slots().get("conclusion"),"caption","summaryCard","body",3,"LEFT","MIDDLE",240,fonts,styles);
    }

    /** Two-sided comparison with explicit focal contrast instead of two empty card columns. */
    private void compileComparisonFocus(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "comparison focus");
        if (items.size() != 2) {
            throw new RenderPlanCompilationException(PptQualityCode.UNRENDERABLE_PAGE,
                    "Comparison focus requires exactly two points");
        }
        SlotGeometry content = SlotGeometry.from(slot, styles);
        long centerGap = Emu.inches(new BigDecimal("1.15"));
        long panelWidth = Math.subtractExact(content.widthEmu(), centerGap) / 2L;
        long panelY = Math.addExact(content.yEmu(), Emu.inches(new BigDecimal("1.02")));
        long panelHeight = Math.subtractExact(content.heightEmu(), Emu.inches(new BigDecimal("1.08")));
        SlotGeometry left = new SlotGeometry(content.xEmu(), panelY, panelWidth, panelHeight);
        SlotGeometry right = new SlotGeometry(content.rightEmu() - panelWidth, panelY, panelWidth, panelHeight);
        ObjectNode leftRing = addDecorShape(elements, slideId, "compare-left", left, "ELLIPSE", "keyPointCard",
                "colors.surface.canvas", "colors.accent.primary", 110, styles);
        ObjectNode rightRing = addDecorShape(elements, slideId, "compare-right", right, "ELLIPSE", "keyPointCard",
                "colors.surface.canvas", "colors.accent.secondary", 110, styles);
        leftRing.withObject("/resolvedStyle").put("borderWidthEmu", Emu.points(new BigDecimal("7")));
        rightRing.withObject("/resolvedStyle").put("borderWidthEmu", Emu.points(new BigDecimal("7")));
        leftRing.withObject("/resolvedStyle").remove("shadow");
        rightRing.withObject("/resolvedStyle").remove("shadow");

        String[] first = splitLabel(items.get(0));
        String[] second = splitLabel(items.get(1));
        long badgeSize = Emu.inches(new BigDecimal("0.86"));
        SlotGeometry leftBadge = new SlotGeometry(
                left.xEmu() + (left.widthEmu() - badgeSize) / 2L,
                content.yEmu(), badgeSize, badgeSize);
        SlotGeometry rightBadge = new SlotGeometry(
                right.xEmu() + (right.widthEmu() - badgeSize) / 2L,
                content.yEmu(), badgeSize, badgeSize);
        addDecorShape(elements, slideId, "compare-left-badge", leftBadge, "ELLIPSE", "summaryCard",
                "colors.accent.primary", "colors.accent.primary", 120, styles);
        addDecorShape(elements, slideId, "compare-right-badge", rightBadge, "ELLIPSE", "summaryCard",
                "colors.accent.secondary", "colors.accent.secondary", 120, styles);
        addTextWithColor(elements, slideId, "compare-left-badge-text", first[0], inset(leftBadge,
                        styles.spacingEmu("xsPt"), styles.spacingEmu("xsPt")),
                "bodyStrong", "summaryCard", "body", 2, "CENTER", "MIDDLE", 230,
                "colors.text.inverse", fonts, styles);
        addTextWithColor(elements, slideId, "compare-right-badge-text", second[0], inset(rightBadge,
                        styles.spacingEmu("xsPt"), styles.spacingEmu("xsPt")),
                "bodyStrong", "summaryCard", "body", 2, "CENTER", "MIDDLE", 230,
                "colors.text.inverse", fonts, styles);
        addText(elements, slideId, "compare-left-text", first[1], inset(left,
                        styles.spacingEmu("mdPt"), styles.spacingEmu("mdPt")),
                "bodyStrong", "keyPointCard", "body", 6, "CENTER", "MIDDLE", 230, fonts, styles);
        addText(elements, slideId, "compare-right-text", second[1], inset(right,
                        styles.spacingEmu("mdPt"), styles.spacingEmu("mdPt")),
                "bodyStrong", "keyPointCard", "body", 6, "CENTER", "MIDDLE", 230, fonts, styles);

        long vsSize = Emu.inches(new BigDecimal("0.68"));
        SlotGeometry versus = new SlotGeometry(
                content.xEmu() + (content.widthEmu() - vsSize) / 2L,
                panelY + (panelHeight - vsSize) / 2L, vsSize, vsSize);
        addDecorShape(elements, slideId, "compare-vs", versus, "ELLIPSE", "summaryCard",
                "colors.surface.dark", "colors.surface.dark", 140, styles);
        addTextWithColor(elements, slideId, "compare-vs-text", "VS", versus,
                "bodyStrong", "summaryCard", "body", 1, "CENTER", "MIDDLE", 150,
                "colors.text.inverse", fonts, styles);
    }

    /** Genuine 2x2 matrix with visible axes and four independent native quadrants. */
    private void compileQuadrantMatrix(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "quadrant");
        if (items.size() != 4) {
            throw new RenderPlanCompilationException(PptQualityCode.UNRENDERABLE_PAGE,
                    "Quadrant matrix requires exactly four points");
        }
        SlotGeometry content = SlotGeometry.from(slot, styles);
        // Keep an explicit visual gutter between the four value fields so the
        // matrix reads as four independent quadrants instead of one card grid.
        long axisGap = Emu.inches(new BigDecimal("0.30"));
        List<SlotGeometry> rows = splitVertical(content, 2, axisGap);
        List<SlotGeometry> quadrants = new ArrayList<>(4);
        quadrants.addAll(splitHorizontal(rows.get(0), 2, axisGap));
        quadrants.addAll(splitHorizontal(rows.get(1), 2, axisGap));
        String[] fills = {"colors.accent.soft", "colors.surface.elevated", "colors.surface.elevated", "colors.accent.soft"};
        String[] borders = {"colors.accent.primary", "colors.accent.secondary", "colors.accent.secondary", "colors.accent.primary"};
        for (int i = 0; i < 4; i++) {
            String role = String.format(Locale.ROOT, "quadrant-%02d", i + 1);
            addDecorShape(elements, slideId, role, quadrants.get(i), "ROUNDED_RECTANGLE", "keyPointCard",
                    fills[i], borders[i], 110, styles);
            addText(elements, slideId, role + "-text", String.format(Locale.ROOT, "%02d  %s", i + 1, items.get(i)),
                    inset(quadrants.get(i), styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                    "bodyStrong", "keyPointCard", "body", 5, "LEFT", "MIDDLE", 230, fonts, styles);
        }
        long axisThickness = Math.max(1L, axisGap / 3L);
        long centerX = content.xEmu() + content.widthEmu() / 2L;
        long centerY = content.yEmu() + content.heightEmu() / 2L;
        long centerClear = axisThickness * 2L;
        addDecorShape(elements, slideId, "quadrant-axis-v-top",
                new SlotGeometry(centerX - axisThickness / 2L, content.yEmu(), axisThickness,
                        Math.max(1L, centerY - centerClear - content.yEmu())),
                "RECTANGLE", "summaryCard", "colors.accent.primary", "colors.accent.primary", 105, styles);
        addDecorShape(elements, slideId, "quadrant-axis-v-bottom",
                new SlotGeometry(centerX - axisThickness / 2L, centerY + centerClear, axisThickness,
                        Math.max(1L, content.bottomEmu() - centerY - centerClear)),
                "RECTANGLE", "summaryCard", "colors.accent.primary", "colors.accent.primary", 105, styles);
        addDecorShape(elements, slideId, "quadrant-axis-h-left",
                new SlotGeometry(content.xEmu(), centerY - axisThickness / 2L,
                        Math.max(1L, centerX - centerClear - content.xEmu()), axisThickness),
                "RECTANGLE", "summaryCard", "colors.accent.secondary", "colors.accent.secondary", 105, styles);
        addDecorShape(elements, slideId, "quadrant-axis-h-right",
                new SlotGeometry(centerX + centerClear, centerY - axisThickness / 2L,
                        Math.max(1L, content.rightEmu() - centerX - centerClear), axisThickness),
                "RECTANGLE", "summaryCard", "colors.accent.secondary", "colors.accent.secondary", 105, styles);
    }

    /** Alternating milestone labels around a central rail; no repeated card shell. */
    private void compileResearchTimeline(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "research timeline");
        SlotGeometry content = SlotGeometry.from(slot, styles);
        long margin = Emu.inches(new BigDecimal("0.42"));
        long railY = content.yEmu() + content.heightEmu() / 2L;
        long startX = content.xEmu() + margin;
        long endX = content.rightEmu() - margin;
        long node = Emu.inches(new BigDecimal("0.52"));
        long labelHeight = Emu.inches(new BigDecimal("1.25"));
        long labelWidth = Math.min(Emu.inches(new BigDecimal("2.15")),
                Math.max(Emu.inches(new BigDecimal("1.65")), content.widthEmu() / items.size() - Emu.inches(new BigDecimal("0.08"))));
        for (int i = 0; i < items.size(); i++) {
            long x = items.size() == 1 ? (startX + endX) / 2L : startX + (endX - startX) * i / (items.size() - 1L);
            if (i > 0) {
                long previousX = items.size() == 1 ? x : startX + (endX - startX) * (i - 1L) / (items.size() - 1L);
                addConnectorBetweenPoints(elements, slideId, String.format(Locale.ROOT, "timeline-rail-%02d", i),
                        previousX + node / 2L, railY, x - node / 2L, railY,
                        "NONE", i == items.size() - 1 ? "TRIANGLE" : "NONE", 104, styles);
            }
            SlotGeometry dot = new SlotGeometry(x - node / 2L, railY - node / 2L, node, node);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "timeline-node-%02d", i + 1), dot,
                    "ELLIPSE", "summaryCard", i % 2 == 0 ? "colors.accent.primary" : "colors.accent.secondary",
                    i % 2 == 0 ? "colors.accent.primary" : "colors.accent.secondary", 120, styles);
            addTextWithColor(elements, slideId, String.format(Locale.ROOT, "timeline-node-%02d-text", i + 1),
                    String.format(Locale.ROOT, "%02d", i + 1), dot, "bodyStrong", "summaryCard", "body", 1,
                    "CENTER", "MIDDLE", 130, "colors.text.inverse", fonts, styles);
            long labelY = i % 2 == 0
                    ? railY - node / 2L - labelHeight - Emu.inches(new BigDecimal("0.16"))
                    : railY + node / 2L + Emu.inches(new BigDecimal("0.16"));
            long labelX = Math.max(content.xEmu(), Math.min(x - labelWidth / 2L, content.rightEmu() - labelWidth));
            SlotGeometry label = new SlotGeometry(labelX, labelY, labelWidth, labelHeight);
            addText(elements, slideId, String.format(Locale.ROOT, "timeline-label-%02d", i + 1), items.get(i),
                    label, "bodyStrong", "keyPointCard", "body", 5,
                    "CENTER", i % 2 == 0 ? "BOTTOM" : "TOP", 230, fonts, styles);
        }
    }

    /** Staggered roadmap whose geometry is intentionally different from the research timeline. */
    private void compileProgressRoadmap(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "progress roadmap");
        SlotGeometry content = SlotGeometry.from(slot, styles);
        long gap = Emu.inches(new BigDecimal("0.18"));
        long blockWidth = Math.subtractExact(content.widthEmu(), gap * (items.size() - 1L)) / items.size();
        long blockHeight = Emu.inches(new BigDecimal("1.55"));
        long rise = items.size() <= 1 ? 0L : Math.max(1L, (content.heightEmu() - blockHeight) / (items.size() - 1L));
        List<SlotGeometry> blocks = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            long x = content.xEmu() + i * (blockWidth + gap);
            long y = content.bottomEmu() - blockHeight - i * rise;
            blocks.add(new SlotGeometry(x, y, blockWidth, blockHeight));
        }
        for (int i = 1; i < blocks.size(); i++) {
            SlotGeometry a = blocks.get(i - 1), b = blocks.get(i);
            addConnectorBetweenPoints(elements, slideId, String.format(Locale.ROOT, "roadmap-link-%02d", i),
                    a.rightEmu(), a.yEmu() + a.heightEmu() / 2L,
                    b.xEmu(), b.yEmu() + b.heightEmu() / 2L,
                    "NONE", "TRIANGLE", 104, styles);
        }
        for (int i = 0; i < blocks.size(); i++) {
            SlotGeometry block = blocks.get(i);
            String fill = i == blocks.size() - 1 ? "colors.accent.primary" : i % 2 == 0
                    ? "colors.accent.soft" : "colors.surface.elevated";
            String border = i == blocks.size() - 1 ? "colors.accent.primary" : "colors.border.strong";
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "roadmap-stage-%02d", i + 1), block,
                    "ROUNDED_RECTANGLE", "keyPointCard", fill, border, 110, styles);
            if (i == blocks.size() - 1) {
                addTextWithColor(elements, slideId, String.format(Locale.ROOT, "roadmap-stage-%02d-text", i + 1),
                        String.format(Locale.ROOT, "%02d\n%s", i + 1, items.get(i)),
                        inset(block, styles.spacingEmu("smPt"), styles.spacingEmu("xsPt")),
                        "bodyStrong", "keyPointCard", "body", 5, "CENTER", "MIDDLE", 230,
                        "colors.text.inverse", fonts, styles);
            } else {
                addText(elements, slideId, String.format(Locale.ROOT, "roadmap-stage-%02d-text", i + 1),
                        String.format(Locale.ROOT, "%02d\n%s", i + 1, items.get(i)),
                        inset(block, styles.spacingEmu("smPt"), styles.spacingEmu("xsPt")),
                        "bodyStrong", "keyPointCard", "body", 5, "CENTER", "MIDDLE", 230, fonts, styles);
            }
        }
    }

    /** Hub-and-spoke method map. */
    private void compileMethodOrbit(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "method orbit");
        SlotGeometry content = SlotGeometry.from(slot, styles);
        if (items.size() < 3 || items.size() > 4) {
            compileArchitectureLayers(elements, slideId, values, slot, fonts, styles);
            return;
        }
        long boxW = Emu.inches(new BigDecimal("3.65"));
        long boxH = Emu.inches(new BigDecimal("1.22"));
        long centerW = Emu.inches(new BigDecimal("1.55"));
        long centerH = Emu.inches(new BigDecimal("1.05"));
        SlotGeometry hub = new SlotGeometry(content.xEmu() + (content.widthEmu() - centerW) / 2L,
                content.yEmu() + (content.heightEmu() - centerH) / 2L, centerW, centerH);
        List<SlotGeometry> boxes = List.of(
                new SlotGeometry(content.xEmu(), content.yEmu(), boxW, boxH),
                new SlotGeometry(content.rightEmu() - boxW, content.yEmu(), boxW, boxH),
                new SlotGeometry(content.xEmu(), content.bottomEmu() - boxH, boxW, boxH),
                new SlotGeometry(content.rightEmu() - boxW, content.bottomEmu() - boxH, boxW, boxH));
        for (int i = 0; i < items.size(); i++) {
            SlotGeometry box = boxes.get(i);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "method-%02d", i + 1), box,
                    "ROUNDED_RECTANGLE", "keyPointCard", i % 2 == 0 ? "colors.accent.soft" : "colors.surface.elevated",
                    i % 2 == 0 ? "colors.accent.primary" : "colors.accent.secondary", 110, styles);
            addText(elements, slideId, String.format(Locale.ROOT, "method-%02d-text", i + 1), items.get(i),
                    inset(box, styles.spacingEmu("smPt"), styles.spacingEmu("xsPt")),
                    "bodyStrong", "keyPointCard", "body", 4, "CENTER", "MIDDLE", 230, fonts, styles);
        }
        addDecorShape(elements, slideId, "method-hub", hub, "ELLIPSE", "summaryCard",
                "colors.accent.primary", "colors.accent.primary", 130, styles);
        addTextWithColor(elements, slideId, "method-hub-text", "研究方法", hub,
                "bodyStrong", "summaryCard", "body", 2, "CENTER", "MIDDLE", 140,
                "colors.text.inverse", fonts, styles);
    }

    /** Alternating vertical logic spine for conceptual frameworks. */
    private void compileFrameworkFlow(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "framework flow");
        SlotGeometry content = SlotGeometry.from(slot, styles);
        long spineX = content.xEmu() + content.widthEmu() / 2L;
        long node = Emu.inches(new BigDecimal("0.42"));
        long cardW = Emu.inches(new BigDecimal("4.15"));
        long gap = Emu.inches(new BigDecimal("0.12"));
        List<SlotGeometry> rows = splitVertical(content, items.size(), gap);
        for (int i = 0; i < items.size(); i++) {
            SlotGeometry row = rows.get(i);
            if (i > 0) {
                SlotGeometry previous = rows.get(i - 1);
                addConnectorBetweenPoints(elements, slideId, String.format(Locale.ROOT, "framework-spine-%02d", i),
                        spineX, previous.yEmu() + previous.heightEmu() / 2L + node / 2L,
                        spineX, row.yEmu() + row.heightEmu() / 2L - node / 2L,
                        "NONE", i == items.size() - 1 ? "TRIANGLE" : "NONE", 103, styles);
            }
            long cardX = i % 2 == 0 ? content.xEmu() : content.rightEmu() - cardW;
            SlotGeometry card = new SlotGeometry(cardX, row.yEmu(), cardW, row.heightEmu());
            SlotGeometry dot = new SlotGeometry(spineX - node / 2L,
                    row.yEmu() + (row.heightEmu() - node) / 2L, node, node);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "framework-card-%02d", i + 1), card,
                    "ROUNDED_RECTANGLE", "keyPointCard", i % 2 == 0 ? "colors.accent.soft" : "colors.surface.elevated",
                    i % 2 == 0 ? "colors.accent.primary" : "colors.accent.secondary", 110, styles);
            addText(elements, slideId, String.format(Locale.ROOT, "framework-card-%02d-text", i + 1), items.get(i),
                    inset(card, styles.spacingEmu("mdPt"), styles.spacingEmu("xsPt")),
                    "bodyStrong", "keyPointCard", "body", 4, i % 2 == 0 ? "LEFT" : "RIGHT", "MIDDLE", 230,
                    fonts, styles);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "framework-node-%02d", i + 1), dot,
                    "ELLIPSE", "summaryCard", "colors.accent.primary", "colors.accent.primary", 125, styles);
        }
    }

    /** Two-row problem-to-response bridge. */
    private void compileProblemSolutionBridge(
            ArrayNode elements, String slideId, JsonNode values, LayoutRecipe.Slot slot,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(values, "problem solution bridge");
        if (items.size() != 4) {
            compileHorizontalCards(elements, slideId, values, slot, "bridge", fonts, styles, true);
            return;
        }
        SlotGeometry content = SlotGeometry.from(slot, styles);
        List<SlotGeometry> rows = splitVertical(content, 2, styles.spacingEmu("mdPt"));
        long centerGap = Emu.inches(new BigDecimal("1.15"));
        long boxW = (content.widthEmu() - centerGap) / 2L;
        for (int i = 0; i < 2; i++) {
            SlotGeometry row = rows.get(i);
            SlotGeometry problem = new SlotGeometry(content.xEmu(), row.yEmu(), boxW, row.heightEmu());
            SlotGeometry response = new SlotGeometry(content.rightEmu() - boxW, row.yEmu(), boxW, row.heightEmu());
            addConnectorBetweenPoints(elements, slideId, String.format(Locale.ROOT, "bridge-link-%02d", i + 1),
                    problem.rightEmu(), problem.yEmu() + problem.heightEmu() / 2L,
                    response.xEmu(), response.yEmu() + response.heightEmu() / 2L,
                    "NONE", "TRIANGLE", 104, styles);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "bridge-problem-%02d", i + 1), problem,
                    "ROUNDED_RECTANGLE", "keyPointCard", "colors.surface.elevated", "colors.accent.secondary", 110, styles);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "bridge-response-%02d", i + 1), response,
                    "ROUNDED_RECTANGLE", "keyPointCard", "colors.accent.soft", "colors.accent.primary", 110, styles);
            addText(elements, slideId, String.format(Locale.ROOT, "bridge-problem-%02d-text", i + 1), items.get(i),
                    inset(problem, styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                    "bodyStrong", "keyPointCard", "body", 4, "LEFT", "MIDDLE", 230, fonts, styles);
            addText(elements, slideId, String.format(Locale.ROOT, "bridge-response-%02d-text", i + 1), items.get(i + 2),
                    inset(response, styles.spacingEmu("mdPt"), styles.spacingEmu("smPt")),
                    "bodyStrong", "keyPointCard", "body", 4, "LEFT", "MIDDLE", 230, fonts, styles);
        }
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
        boolean keyPointsRendered = keyPoints.isBlank();
        if (recipe.slots().containsKey("body") && !keyPoints.isBlank()) {
            addText(elements, slideId, "notes", keyPoints, recipe.slots().get("body"),
                    "body", "bodyText", "body", Math.max(3, page.path("keyPoints").size() * 2),
                    "LEFT", "TOP", 240, fonts, styles);
            keyPointsRendered = true;
        }
        if (recipe.slots().containsKey("caption") && hasText(page, "description")) {
            addText(elements, slideId, "caption", requiredText(page, "description"),
                    recipe.slots().get("caption"), "caption", "caption", "body",
                    recipe.constraints().captionMaxLines(),
                    "LEFT", "TOP", 240, fonts, styles);
        }
        if (!keyPointsRendered && recipe.slots().containsKey("conclusion")) {
            addText(elements, slideId, "conclusion", keyPoints, recipe.slots().get("conclusion"),
                    "bodyStrong", "summaryCard", "body", 4,
                    "LEFT", "MIDDLE", 245, fonts, styles);
            keyPointsRendered = true;
        }
        if (!keyPointsRendered) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.UNRENDERABLE_PAGE,
                    recipe.layoutId() + " has no slot for non-empty image-page keyPoints");
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
        if (LayoutIds.SUMMARY_OUTCOME_CHAIN.equals(recipe.layoutId())) {
            compileOutcomeChain(elements, slideId, page, recipe, fonts, styles);
            addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
            return;
        }
        SlotGeometry keyPoints=SlotGeometry.from(requiredSlot(recipe,"keyPoints"),styles);
        addCard(elements,slideId,"key-points",keyPoints,styles);
        addText(elements, slideId, "key-points-text", bullets(page.path("keyPoints")),
                inset(keyPoints,styles.spacingEmu("mdPt"),styles.spacingEmu("smPt")), "body", "keyPointCard", "body", 6,
                "LEFT", "MIDDLE", 230, fonts, styles);
        SlotGeometry conclusion=SlotGeometry.from(requiredSlot(recipe,"conclusion"),styles);
        addCard(elements,slideId,"conclusion",conclusion,styles,"summaryCard");
        addText(elements, slideId, "conclusion-text", requiredText(page, "description"),
                inset(conclusion,styles.spacingEmu("mdPt"),styles.spacingEmu("xsPt")), "bodyStrong", "summaryCard", "body", 4,
                "LEFT", "MIDDLE", 240, fonts, styles);
        addFooter(elements, slideId, recipe, index, slideCount, fonts, styles);
    }

    /** Deliverables become a left-to-right value chain instead of another bullet card. */
    private void compileOutcomeChain(
            ArrayNode elements, String slideId, JsonNode page, LayoutRecipe recipe,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        List<String> items = requiredTextItems(page.path("keyPoints"), "outcome chain");
        SlotGeometry content = SlotGeometry.from(requiredSlot(recipe, "keyPoints"), styles);
        List<SlotGeometry> stages = splitHorizontal(content, items.size(), styles.spacingEmu("lgPt"));
        for (int i = 1; i < stages.size(); i++) {
            SlotGeometry a = stages.get(i - 1), b = stages.get(i);
            addConnectorBetweenPoints(elements, slideId, String.format(Locale.ROOT, "outcome-link-%02d", i),
                    a.rightEmu(), a.yEmu() + a.heightEmu() / 2L,
                    b.xEmu(), b.yEmu() + b.heightEmu() / 2L,
                    "NONE", "TRIANGLE", 104, styles);
        }
        for (int i = 0; i < stages.size(); i++) {
            SlotGeometry stage = stages.get(i);
            long markerSize = Emu.inches(new BigDecimal("0.66"));
            SlotGeometry marker = new SlotGeometry(stage.xEmu() + (stage.widthEmu() - markerSize) / 2L,
                    stage.yEmu(), markerSize, markerSize);
            SlotGeometry body = new SlotGeometry(stage.xEmu(),
                    stage.yEmu() + markerSize + Emu.inches(new BigDecimal("0.14")),
                    stage.widthEmu(), stage.heightEmu() - markerSize - Emu.inches(new BigDecimal("0.14")));
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "outcome-marker-%02d", i + 1), marker,
                    "ELLIPSE", "summaryCard", i == stages.size() - 1 ? "colors.accent.secondary" : "colors.accent.primary",
                    i == stages.size() - 1 ? "colors.accent.secondary" : "colors.accent.primary", 120, styles);
            addTextWithColor(elements, slideId, String.format(Locale.ROOT, "outcome-marker-%02d-text", i + 1),
                    String.format(Locale.ROOT, "%02d", i + 1), marker,
                    "bodyStrong", "summaryCard", "body", 1, "CENTER", "MIDDLE", 130,
                    "colors.text.inverse", fonts, styles);
            addDecorShape(elements, slideId, String.format(Locale.ROOT, "outcome-stage-%02d", i + 1), body,
                    "ROUNDED_RECTANGLE", "keyPointCard", i % 2 == 0 ? "colors.accent.soft" : "colors.surface.elevated",
                    i % 2 == 0 ? "colors.accent.primary" : "colors.accent.secondary", 110, styles);
            addText(elements, slideId, String.format(Locale.ROOT, "outcome-stage-%02d-text", i + 1), items.get(i),
                    inset(body, styles.spacingEmu("smPt"), styles.spacingEmu("smPt")),
                    "bodyStrong", "keyPointCard", "body", 5, "CENTER", "MIDDLE", 230, fonts, styles);
        }
        SlotGeometry conclusion = SlotGeometry.from(requiredSlot(recipe, "conclusion"), styles);
        addDecorShape(elements, slideId, "outcome-conclusion", conclusion,
                "ROUNDED_RECTANGLE", "summaryCard", "colors.accent.soft", "colors.accent.primary", 110, styles);
        addText(elements, slideId, "outcome-conclusion-text", requiredText(page, "description"),
                inset(conclusion, styles.spacingEmu("mdPt"), styles.spacingEmu("xsPt")),
                "bodyStrong", "summaryCard", "body", 4, "CENTER", "MIDDLE", 240, fonts, styles);
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
                "subtitle", styles.hasComponent("surfaceSubtitle")?"surfaceSubtitle":"bodyText", "body", 2, "CENTER", "MIDDLE", 230, fonts, styles);
        if (recipe.slots().containsKey("footer")) {
            addText(elements, slideId, "footer", requiredText(metadata, "institution"),
                    recipe.slots().get("footer"), "footnote", styles.hasComponent("surfaceFootnote")?"surfaceFootnote":"pageNumber", "body", 2,
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

    private ObjectNode addText(
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
        return addText(elements, slideId, role, text, SlotGeometry.from(slot, styles), typographyStyle,
                component, fontRole, maxLines, horizontalAlign, verticalAlign, zIndex, fonts, styles);
    }

    private ObjectNode addText(
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
        return element;
    }

    private ObjectNode addTextWithColor(
            ArrayNode elements, String slideId, String role, String text, SlotGeometry geometry,
            String typographyStyle, String component, String fontRole, int maxLines,
            String horizontalAlign, String verticalAlign, int zIndex, String textColorToken,
            ResolvedFontProfile fonts, ThemePlanStyleResolver styles
    ) {
        ObjectNode element = addText(elements, slideId, role, text, geometry, typographyStyle, component,
                fontRole, maxLines, horizontalAlign, verticalAlign, zIndex, fonts, styles);
        ((ObjectNode) element.path("resolvedStyle")).put("textColor", styles.colorToken(textColorToken));
        element.set("styleSource", styles.styleSource(component, textColorToken));
        return element;
    }

    private void addCard(
            ArrayNode elements,
            String slideId,
            String role,
            SlotGeometry geometry,
            ThemePlanStyleResolver styles
    ) {
        addCard(elements,slideId,role,geometry,styles,"keyPointCard");
    }

    private void addCard(
            ArrayNode elements,
            String slideId,
            String role,
            SlotGeometry geometry,
            ThemePlanStyleResolver styles,
            String component
    ) {
        ObjectNode shape = baseElement(
                slideId + "-" + role,
                "SHAPE",
                geometry.xEmu(), geometry.yEmu(), geometry.widthEmu(), geometry.heightEmu(),
                110,
                styles.shapeStyle(component));
        shape.set("styleSource", styles.styleSource(component, "components."+component));
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

    private void addConnectorBetweenPoints(
            ArrayNode elements, String slideId, String role,
            long startX, long startY, long endX, long endY,
            String startArrow, String endArrow, int zIndex,
            ThemePlanStyleResolver styles
    ) {
        long x = Math.min(startX, endX);
        long y = Math.min(startY, endY);
        ObjectNode connector = baseElement(
                slideId + "-" + role,
                "CONNECTOR",
                x, y,
                Math.max(1L, Math.abs(endX - startX)),
                Math.max(1L, Math.abs(endY - startY)),
                zIndex,
                styles.connectorStyle());
        connector.set("styleSource", styles.styleSource("keyPointCard", "colors.accent.primary"));
        connector.put("startXEmu", startX);
        connector.put("startYEmu", startY);
        connector.put("endXEmu", endX);
        connector.put("endYEmu", endY);
        connector.put("lineType", "STRAIGHT");
        connector.put("startArrow", startArrow);
        connector.put("endArrow", endArrow);
        elements.add(connector);
    }

    private ObjectNode addDecorShape(
            ArrayNode elements, String slideId, String role, SlotGeometry geometry,
            String shapeType, String component, String fillToken, String borderToken,
            int zIndex, ThemePlanStyleResolver styles
    ) {
        ObjectNode style = styles.shapeStyle(component);
        style.put("fillColor", styles.colorToken(fillToken));
        style.put("borderColor", styles.colorToken(borderToken));
        ObjectNode shape = baseElement(slideId + "-" + role, "SHAPE",
                geometry.xEmu(), geometry.yEmu(), geometry.widthEmu(), geometry.heightEmu(), zIndex, style);
        shape.set("styleSource", styles.styleSource(component, fillToken));
        shape.put("shapeType", shapeType);
        elements.add(shape);
        return shape;
    }

    private String[] splitLabel(String value) {
        int index = Math.max(value.indexOf('：'), value.indexOf(':'));
        if (index > 0 && index < value.length() - 1) {
            return new String[]{value.substring(0, index).strip(), value.substring(index + 1).strip()};
        }
        return new String[]{"对比", value.strip()};
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

    private ObjectNode templateSurfaceElement(
            String slideId,
            String assetId,
            RenderingAssetBundle assets,
            ThemePlanStyleResolver styles
    ) {
        ObjectNode asset = assets.requireAsset(assetId);
        if (!AssetKind.TEMPLATE_DECORATION.name().equals(asset.path("assetKind").asText())) {
            throw new RenderPlanCompilationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Template surface must reference a TEMPLATE_DECORATION asset: " + assetId);
        }
        ObjectNode element = baseElement(
                slideId + "-template-surface",
                "IMAGE",
                0L,
                0L,
                styles.slideWidthEmu(),
                styles.slideHeightEmu(),
                5,
                styles.templateDecorationStyle());
        element.set("styleSource", styles.styleSource("imageFrame", "components.imageFrame"));
        element.put("decorative", true);
        element.put("assetId", assetId);
        element.put("fitMode", ImageFitMode.CONTAIN.name());
        element.put("cropAllowed", false);
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
            case SECTION -> assetCount == 0 && tableCount == 0;
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
