package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.PptAssetMapperV1;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.RenderPlanBundleStore;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.StoredRenderPlanBundle;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.StagedRenderPlanBundle;
import com.dropai.rewrite.service.ppt.rendering.bundle.v1.BundleRuntimeExpectations;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.*;
import com.dropai.rewrite.service.ppt.rendering.compiler.v1.*;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.layout.v1.*;
import com.dropai.rewrite.service.ppt.rendering.measurement.v1.*;
import com.dropai.rewrite.service.ppt.rendering.plan.v1.DraftSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderability.v1.PageRenderabilityValidator;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.*;
import com.dropai.rewrite.service.ppt.rendering.validation.v1.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.*;

/** Production-only orchestration. Content is accepted as final and never reinterpreted. */
@Service
public final class ProductionRenderPlanCoordinator {
    private final ObjectMapper mapper;
    private final PptAssetMapperV1 assetMapper;
    private final RenderPlanBundleStore bundleStore;

    @Autowired
    public ProductionRenderPlanCoordinator(ObjectMapper mapper, PptAssetMapperV1 assetMapper) {
        this(mapper, assetMapper, new RenderPlanBundleStore());
    }

    ProductionRenderPlanCoordinator(ObjectMapper mapper, PptAssetMapperV1 assetMapper,
                                    RenderPlanBundleStore bundleStore) {
        this.mapper=mapper; this.assetMapper=assetMapper; this.bundleStore=bundleStore;
    }

    public StoredRenderPlanBundle prepareAndStore(Path target, ProductionRenderPlanRequest request) {
        StagedRenderPlanBundle staged = prepareAndStage(target, request);
        try {
            return bundleStore.publish(staged);
        } catch (RuntimeException exception) {
            bundleStore.discard(staged);
            throw exception;
        }
    }

    public StagedRenderPlanBundle prepareAndStage(Path target, ProductionRenderPlanRequest request) {
        ProductionRenderPlanPackage compiled = compile(request);
        return bundleStore.stage(target, compiled.plan(), compiled.sourceAssets(), compiled.actualFonts(),
                new ProductionBuildIdentityLoader().load());
    }

    public StoredRenderPlanBundle publish(StagedRenderPlanBundle staged) {
        return bundleStore.publish(staged);
    }

    public void discard(StagedRenderPlanBundle staged) {
        bundleStore.discard(staged);
    }

    public ProductionRenderPlanPackage compile(ProductionRenderPlanRequest request) {
        var mapping = assetMapper.map(new PptAssetMapperV1.MappingRequest(
                request.validatedTree(), request.plannerInput().assets(), request.plannerInput().tables(), null));
        var adapted = new ProductionPresentationAdapter(mapper).adapt(request, mapping);

        RuntimeComponents runtime=runtimeComponents();
        ProductionFontInventoryLoader.LoadedFonts loaded = runtime.loaded();
        ResolvedTheme theme=runtime.theme();
        ResolvedFontProfile fonts=runtime.fonts();
        var actualFonts=runtime.actualFonts();
        LayoutCatalog catalog=runtime.catalog();
        DeterministicTextMetricsService metrics=new DeterministicTextMetricsService(new AwtGlyphMetricsModel());
        RenderPlanCompiler compiler=new RenderPlanCompiler(new PageRenderabilityValidator(),metrics,
                new ImageFitCalculator(),new TableMetricsCalculator(metrics));
        DraftSlideRenderPlan draft=compiler.compile(adapted.tree(),theme,catalog,adapted.assets(),fonts);
        RenderPlanValidationContext context=context(adapted.tree(),adapted.assets(),theme,catalog,fonts,metrics,draft);
        RenderPlanValidationResult result=new RenderPlanValidator().validate(draft,context);
        if(!result.valid())throw new IllegalStateException("Production RenderPlan validation failed: "+result.issues());
        FrozenSlideRenderPlan frozen=new RenderPlanFreezer().freeze(result.accept());
        String hash=new RenderPlanHasher().hash(frozen);
        return new ProductionRenderPlanPackage(frozen,hash,adapted.resolver(),actualFonts);
    }

    public BundleRuntimeExpectations runtimeExpectations() {
        RuntimeComponents runtime=runtimeComponents();
        var buildIdentity = new ProductionBuildIdentityLoader().load();
        return new BundleRuntimeExpectations(RenderPlanCompiler.ENGINE_VERSION,
                buildIdentity.rendererVersion(), buildIdentity.gitCommit(),
                runtime.theme().themeId(),runtime.theme().themeVersion(),
                runtime.theme().resolvedThemeHash(),runtime.catalog().catalogVersion(),
                runtime.catalog().catalogHash(),runtime.actualFonts());
    }

    private RuntimeComponents runtimeComponents(){
        ProductionFontInventoryLoader.LoadedFonts loaded = new ProductionFontInventoryLoader().load();
        Set<String> families = new LinkedHashSet<>();
        loaded.resources().forEach(face -> families.add(face.family()));
        ResolvedTheme theme = ThemeEngine.academicV1(families)
                .resolve(ThemeResolutionRequest.academicPurpleV1());
        Map<String,List<String>> requests=new LinkedHashMap<>();
        for(String role:List.of("body","display")){List<String> values=new ArrayList<>(theme.fontProfile().declaredFamilies().get(role));values.addAll(theme.fontProfile().allowedFallbackFamilies().get(role));requests.put(role,List.copyOf(values));}
        Map<String,Set<Integer>> weights=Map.of("body",new LinkedHashSet<>(List.of(400,500,600)),"display",Set.of(700));
        ResolvedFontProfile fonts = new ResolvedFontProfileResolver(loaded.measurementInventory())
                .resolve(theme.fontProfile().profileId(), requests, weights);
        var actualFonts=loaded.production(fonts);
        LayoutCatalog catalog=new LayoutCatalogLoader().loadAcademicV1();
        return new RuntimeComponents(loaded,theme,fonts,actualFonts,catalog);
    }

    private static RenderPlanValidationContext context(ValidatedPresentationTree tree,RenderingAssetBundle bundle,
            ResolvedTheme theme,LayoutCatalog catalog,ResolvedFontProfile fonts,
            DeterministicTextMetricsService metrics,DraftSlideRenderPlan draft){
        List<String> pageIds=new ArrayList<>();tree.pages().forEach(p->pageIds.add(p.path("sourcePageId").asText()));
        Map<String,RenderPlanValidationContext.PageExpectation> pages=new LinkedHashMap<>();
        draft.document().path("slides").forEach(p->pages.put(p.path("sourcePageId").asText(),new RenderPlanValidationContext.PageExpectation(p.path("pageType").asText(),p.path("layoutId").asText())));
        Map<String,String> hashes=new LinkedHashMap<>();bundle.assets().forEach(a->hashes.put(a.path("assetId").asText(),a.path("sha256").asText()));
        JsonNode slide=theme.document().path("slide");
        Map<String,Integer> minimums=Map.of("coverTitle",5000,"sectionTitle",4000,"slideTitle",3500,"bodyText",1600,"keyPointCard",1600,"summaryCard",1600,"caption",1600,"pageNumber",1200,"tableHeader",1600,"tableBody",1600);
        Set<String> components=new LinkedHashSet<>();theme.document().path("components").fieldNames().forEachRemaining(components::add);
        Map<String,Set<String>> overlaps=new LinkedHashMap<>();catalog.recipes().forEach(r->overlaps.put(r.layoutId(),new LinkedHashSet<>(r.constraints().allowedContainedTextComponents())));
        Set<String> tokens=new LinkedHashSet<>();collect(theme.document(),"",tokens);
        return new RenderPlanValidationContext(tree.presentationId(),tree.sourceTreeHash(),theme.resolvedThemeHash(),catalog.catalogHash(),fonts.fontProfileHash(),
                new RenderPlanValidationContext.EngineExpectation(RenderPlanCompiler.ENGINE_VERSION,theme.themeId(),theme.themeVersion(),catalog.catalogVersion()),
                fontExpectation(fonts),fonts,metrics,inches(slide.path("widthIn").decimalValue()),inches(slide.path("heightIn").decimalValue()),pageIds,pages, LayoutIds.ALL,overlaps,components,tokens,hashes,
                new RenderPlanValidationContext.SafeArea(inches(slide.path("safeArea").path("leftIn").decimalValue()),inches(slide.path("safeArea").path("topIn").decimalValue()),inches(slide.path("safeArea").path("rightIn").decimalValue()),inches(slide.path("safeArea").path("bottomIn").decimalValue())),
                new RenderPlanValidationContext.StatusStyleExpectation(theme.document().path("colors").path("state").path("success").asText(),theme.document().path("colors").path("state").path("warning").asText(),theme.document().path("colors").path("state").path("danger").asText(),theme.document().path("colors").path("text").path("inverse").asText()),minimums,1200);
    }
    private static RenderPlanValidationContext.FontProfileExpectation fontExpectation(ResolvedFontProfile profile) {
        Map<String, RenderPlanValidationContext.FontFaceExpectation> faces = new LinkedHashMap<>();
        profile.faces().forEach((role, weights) -> weights.forEach((weight, face) ->
                faces.put(role + "-" + weight, new RenderPlanValidationContext.FontFaceExpectation(
                        face.role(), face.weight(), face.selectedFamily(), face.postScriptName(),
                        face.fontSource().name(), face.fontFingerprint(), face.fallbackApplied()))));
        return new RenderPlanValidationContext.FontProfileExpectation(
                profile.profileId(), profile.measurementEngineVersion(), faces);
    }
    private static void collect(JsonNode n,String prefix,Set<String> out){if(!n.isObject())return;n.fields().forEachRemaining(e->{String p=prefix.isEmpty()?e.getKey():prefix+"."+e.getKey();out.add(p);collect(e.getValue(),p,out);});}
    private static long inches(BigDecimal v){return v.multiply(BigDecimal.valueOf(914400L)).setScale(0,RoundingMode.HALF_UP).longValueExact();}
    private record RuntimeComponents(ProductionFontInventoryLoader.LoadedFonts loaded,ResolvedTheme theme,
            ResolvedFontProfile fonts,com.dropai.rewrite.service.ppt.rendering.bundle.v1.ProductionFontInventory actualFonts,
            LayoutCatalog catalog){}
}
