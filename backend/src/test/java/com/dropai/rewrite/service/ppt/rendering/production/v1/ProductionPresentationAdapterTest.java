package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionPresentationAdapterTest {
    @Test
    void adaptationIsPortableAndDeterministicWithoutReinterpretingCopy() {
        PptOutlineValidatorV1.ValidationResult tree = tree();
        PptAssetMapperV1.MappingResult mapped = new PptAssetMapperV1.MappingResult(
                true, false, "ASSET_MAPPED_TREE", tree.slideTree().size(),
                tree.slideTree().stream().map(p -> new PptAssetMapperV1.MappedSlideNode(p, List.of())).toList(),
                List.of());
        PptContentPlannerV2.PlannerInput input = new PptContentPlannerV2.PlannerInput(
                metadata(), List.of(), List.of(), List.of(), "computer");
        ProductionRenderPlanRequest request = new ProductionRenderPlanRequest(
                "4f06bf20-1848-4fb7-8b32-1097e8f0fddb", metadata(), List.of("source"), tree, input);
        ProductionPresentationAdapter adapter = new ProductionPresentationAdapter(new ObjectMapper());

        var first = adapter.adapt(request, mapped);
        var second = adapter.adapt(request, mapped);

        assertEquals(first.tree().presentationId(), second.tree().presentationId());
        assertEquals(first.tree().sourceTreeHash(), second.tree().sourceTreeHash());
        assertEquals("项目背景与需求",
                first.tree().document().path("agendaSections").get(0).path("title").asText());
        assertEquals("原始答辩标题", first.tree().document().path("pages").get(2).path("title").asText());
        assertFalse(first.tree().document().toString().contains("未填写"));
    }

    @Test
    void missingProductionMetadataFailsExplicitly() {
        Map<String,String> incomplete = Map.of("title", "题目");
        var request = new ProductionRenderPlanRequest("project-1", incomplete, List.of(), tree(),
                new PptContentPlannerV2.PlannerInput(incomplete,List.of(),List.of(),List.of(),"computer"));
        var mapped = new PptAssetMapperV1.MappingResult(true,false,"ASSET_MAPPED_TREE",tree().slideTree().size(),
                tree().slideTree().stream().map(p->new PptAssetMapperV1.MappedSlideNode(p,List.of())).toList(),List.of());
        IllegalStateException error=assertThrows(IllegalStateException.class,
                ()->new ProductionPresentationAdapter(new ObjectMapper()).adapt(request,mapped));
        assertTrue(error.getMessage().contains("metadata missing"));
    }

    @Test
    void invalidValidatedTreeAndCoverMetadataDriftFailClosed() {
        PptOutlineValidatorV1.ValidationResult source=tree();
        var mapped=new PptAssetMapperV1.MappingResult(true,false,"ASSET_MAPPED_TREE",source.slideTree().size(),
                source.slideTree().stream().map(page->new PptAssetMapperV1.MappedSlideNode(page,List.of())).toList(),List.of());
        var input=new PptContentPlannerV2.PlannerInput(metadata(),List.of(),List.of(),List.of(),"computer");
        var invalid=new PptOutlineValidatorV1.ValidationResult(false,"FULL_PRESENTATION_TREE",source.slideTree(),
                List.of(new PptOutlineValidatorV1.ValidationIssue("BROKEN",1,"broken")));
        var adapter=new ProductionPresentationAdapter(new ObjectMapper());

        assertThrows(IllegalStateException.class,()->adapter.adapt(
                new ProductionRenderPlanRequest("project-1",metadata(),List.of(),invalid,input),mapped));

        Map<String,String> drifted=new java.util.LinkedHashMap<>(metadata());
        drifted.put("presenter","另一个人");
        IllegalStateException mismatch=assertThrows(IllegalStateException.class,()->adapter.adapt(
                new ProductionRenderPlanRequest("project-1",drifted,List.of(),source,input),mapped));
        assertTrue(mismatch.getMessage().contains("COVER metadata mismatch"));
    }

    @Test
    void productionFontsNeverFallBackToAnImplicitSystemFont() {
        String previous=System.getProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY);
        try {
            System.setProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY, "");
            IllegalStateException error=assertThrows(IllegalStateException.class,
                    ()->new ProductionFontInventoryLoader().load());
            assertTrue(error.getMessage().contains("explicit font files"));
        } finally {
            if(previous==null)System.clearProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY);
            else System.setProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY,previous);
        }
    }

    @Test
    void productionCompilerRecordsTheExplicitlyResolvedFontFiles() {
        Path regular=Path.of("C:/Windows/Fonts/msyh.ttc");
        Path bold=Path.of("C:/Windows/Fonts/msyhbd.ttc");
        Assumptions.assumeTrue(Files.isRegularFile(regular)&&Files.isRegularFile(bold));
        String previous=System.getProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY);
        try {
            System.setProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY,
                    "400="+regular+",500="+regular+",600="+bold+",700="+bold);
            var input=new PptContentPlannerV2.PlannerInput(metadata(),List.of(),List.of(),List.of(),"computer");
            var request=new ProductionRenderPlanRequest("project-compile-v1",metadata(),List.of("source"),tree(),input);
            ProductionRenderPlanPackage compiled=new ProductionRenderPlanCoordinator(
                    new ObjectMapper(),new PptAssetMapperV1()).compile(request);
            assertEquals(ProductionPresentationAdapter.presentationIdForProject("project-compile-v1"),
                    compiled.plan().document().path("presentationId").asText());
            assertEquals(6,compiled.plan().document().path("slides").size());
            assertEquals(compiled.renderPlanHash(),
                    new com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanHasher().hash(compiled.plan()));
            assertEquals(4,compiled.actualFonts().faces().size());
            assertTrue(compiled.actualFonts().faces().stream().allMatch(face ->
                    face.fontFingerprint().matches("^sha256:[a-f0-9]{64}$")));
        } finally {
            if(previous==null)System.clearProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY);
            else System.setProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY,previous);
        }
    }

    @Test
    void productionFontInventoryRejectsARegularFileDeclaredAsBold() {
        Path regular=Path.of("C:/Windows/Fonts/msyh.ttc");
        Assumptions.assumeTrue(Files.isRegularFile(regular));
        String previous=System.getProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY);
        try {
            System.setProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY,
                    "400="+regular+",500="+regular+",600="+regular+",700="+regular);
            IllegalStateException error=assertThrows(IllegalStateException.class,
                    ()->new ProductionFontInventoryLoader().load());
            assertTrue(error.getMessage().contains("style does not match weight"));
        } finally {
            if(previous==null)System.clearProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY);
            else System.setProperty(ProductionFontInventoryLoader.FONT_FILES_PROPERTY,previous);
        }
    }

    @Test
    void productionBuildIdentityRequiresAndRecordsAFullGitCommit() {
        String previous=System.getProperty(ProductionBuildIdentityLoader.GIT_COMMIT_PROPERTY);
        try {
            System.setProperty(ProductionBuildIdentityLoader.GIT_COMMIT_PROPERTY,"");
            assertThrows(IllegalStateException.class,
                    ()->new ProductionBuildIdentityLoader().load());
            System.setProperty(ProductionBuildIdentityLoader.GIT_COMMIT_PROPERTY,"not-a-commit");
            assertThrows(IllegalStateException.class,
                    ()->new ProductionBuildIdentityLoader().load());
            String expected="3".repeat(40);
            System.setProperty(ProductionBuildIdentityLoader.GIT_COMMIT_PROPERTY,expected);
            assertEquals(expected,new ProductionBuildIdentityLoader().load().gitCommit());
        } finally {
            if(previous==null)System.clearProperty(ProductionBuildIdentityLoader.GIT_COMMIT_PROPERTY);
            else System.setProperty(ProductionBuildIdentityLoader.GIT_COMMIT_PROPERTY,previous);
        }
    }


    private static Map<String,String> metadata(){return Map.of("title","论文题目","englishTitle","Academic Thesis","presenter","学生","major","专业","advisor","教师","studentNumber","20260001","institution","学院","date","2026年6月1日");}
    private static PptOutlineValidatorV1.ValidationResult tree(){
        var agenda=List.of(
                new PptOutlineValidatorV1.AgendaItem(1,"项目背景与需求","PROJECT_OVERVIEW",List.of(3)),
                new PptOutlineValidatorV1.AgendaItem(2,"系统设计","SYSTEM_DESIGN",List.of(4)),
                new PptOutlineValidatorV1.AgendaItem(3,"总结展望","SUMMARY_OUTLOOK",List.of(5)));
        List<PptOutlineValidatorV1.FullSlideNode> pages=List.of(
                new PptOutlineValidatorV1.FullSlideNode(1,"COVER","OPENING","论文题目","","",List.of(),"","",null,
                        new PptOutlineValidatorV1.FixedPayload("论文题目","Academic Thesis","学生","专业","教师","20260001"),
                        List.of(),"",null,false,List.of(),1),
                new PptOutlineValidatorV1.FullSlideNode(2,"AGENDA","OPENING","目录","","",List.of(),"","",null,null,agenda,"",null,false,List.of(),1),
                page(3,"CONTENT","PROJECT_OVERVIEW","原始答辩标题","BACKGROUND",List.of("观点一")),
                page(4,"CONTENT","SYSTEM_DESIGN","系统总体架构","DESIGN",List.of("观点二")),
                page(5,"SUMMARY","SUMMARY_OUTLOOK","项目总结","SUMMARY",List.of("观点三")),
                page(6,"THANKS","CLOSING","谢谢大家","",List.of()));
        return new PptOutlineValidatorV1.ValidationResult(true,"FULL_PRESENTATION_TREE",pages,List.of());
    }
    private static PptOutlineValidatorV1.FullSlideNode page(int n,String type,String section,String title,String purpose,List<String> points){return new PptOutlineValidatorV1.FullSlideNode(n,type,section,title,purpose,purpose.isBlank()?"":"这一页回答什么？",points,purpose.isBlank()?"":"这是完整说明。","章节",null,null,List.of(),"",null,false,List.of(),1);}
}
