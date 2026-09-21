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
import com.dropai.rewrite.service.ppt.SourceDocumentPrecheckService;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.LayoutIds;
import com.dropai.rewrite.service.ppt.rendering.template.v1.RenderingTemplatePackRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in source-first regression for the commercial opening-proposal page system. */
class PptCommercialOpeningProposalE2ETest {
    @TempDir Path temp;

    @Test
    void realOpeningProposalBuildsEditableDeterministicCommercialDeck() throws Exception {
        String configured=System.getProperty("ppt.opening.source","").strip();
        Assumptions.assumeFalse(configured.isBlank(),"Set ppt.opening.source for the commercial opening-proposal E2E");
        Path source=Path.of(configured).toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(source),"Opening-proposal source is unavailable");

        PptDocumentParser.ParsedDocument parsed=new PptDocumentParser().parse(source,temp.resolve("assets"));
        assertTrue(new SourceDocumentPrecheckService().check(parsed).passed());
        var inputAdapter=new PptContentPlannerV2InputAdapter();
        var raw=inputAdapter.fromParsedDocument(parsed,"social_science");
        Map<String,String> metadata=new LinkedHashMap<>(raw.metadata());
        metadata.put("presentationMode","OPENING_PROPOSAL");
        metadata.put("majorType","social_science");
        var input=new PptContentPlannerV2.PlannerInput(metadata,raw.chapters(),raw.assets(),raw.tables(),raw.majorType());

        ObjectMapper mapper=new ObjectMapper();
        var planner=new PptContentPlannerV2(new PptPlannerRuleLibrary(mapper));
        var planned=new PptContentSanitizerV1().sanitize(planner.plan(input));
        List<PptContentPlannerV2.CandidatePage> candidates=planned.chapters().stream()
                .flatMap(chapter->chapter.candidatePages().stream()).toList();
        var outline=new PptOutlinePlannerV1().plan(new PptOutlinePlannerV1.OutlineRequest(candidates,8));
        var validated=new PptOutlineValidatorV1().validate(
                new PptOutlineValidatorV1.ValidationRequest(metadata,outline));
        assertTrue(validated.valid(),validated.issues()+" chapters="+raw.chapters()+" candidates="+candidates+" decisions="+outline.decisions());

        var request=new ProductionRenderPlanRequest("huarun-opening-commercial",metadata,parsed.blocks(),
                validated,input,RenderingTemplatePackRegistry.BEAR_EDITORIAL_OLIVE_V1);
        var coordinator=new ProductionRenderPlanCoordinator(mapper,new PptAssetMapperV1());
        var first=coordinator.compile(request);
        var second=coordinator.compile(request);
        assertEquals(first.renderPlanHash(),second.renderPlanHash());
        assertArrayEquals(first.plan().canonicalBytes(),second.plan().canonicalBytes());
        assertCommercialPlan(first.plan().document());

        Path generated=temp.resolve("huarun-opening-commercial.pptx");
        var receipt=new PptEngineV1Service().generate(first.plan(),first.sourceAssets(),generated);
        assertEquals(first.plan().document().path("slides").size(),receipt.slideCount());
        try(InputStream in=Files.newInputStream(generated);XMLSlideShow deck=new XMLSlideShow(in)){
            assertEquals(receipt.slideCount(),deck.getSlides().size());
            long comparisonEllipses=deck.getSlides().get(3).getShapes().stream()
                    .filter(XSLFAutoShape.class::isInstance)
                    .map(XSLFAutoShape.class::cast)
                    .filter(shape->shape.getShapeType()==ShapeType.ELLIPSE)
                    .count();
            assertTrue(comparisonEllipses>=5,"Comparison rings must remain native ellipses after rendering");
        }

        String outputProperty=System.getProperty("ppt.opening.output","").strip();
        if(!outputProperty.isBlank()){
            Path output=Path.of(outputProperty).toAbsolutePath().normalize();
            Files.createDirectories(output.getParent());
            Files.copy(generated,output,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(output.resolveSibling(output.getFileName()+".render-plan.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(first.plan().document())+"\n",
                    StandardCharsets.UTF_8);
            writeReport(mapper,output,source,first.plan().document(),first.renderPlanHash());
        }
    }

    private void assertCommercialPlan(ObjectNode plan){
        assertTrue(plan.path("slides").size()>=14 && plan.path("slides").size()<=17);
        Set<String> layouts=new LinkedHashSet<>();
        List<String> sequence=new ArrayList<>();
        Map<String,JsonNode> byTitle=new LinkedHashMap<>();
        for(JsonNode slide:plan.path("slides")){
            String layout=slide.path("layoutId").asText();layouts.add(layout);sequence.add(layout);
            slide.path("elements").forEach(element->{if("TEXT".equals(element.path("elementType").asText()))byTitle.putIfAbsent(element.path("text").asText().replace("\n",""),slide);});
            if("CONTENT".equals(slide.path("pageType").asText()))assertTrue(hasAnchor(slide),slide.toPrettyString());
        }
        assertTrue(layouts.size()>=8,"Expected at least eight deterministic compositions: "+layouts);
        for(int index=2;index<sequence.size();index++)assertFalse(sequence.get(index).equals(sequence.get(index-1))&&sequence.get(index).equals(sequence.get(index-2)),"Layout repeated three times: "+sequence.get(index)+" sequence="+sequence+" slides="+slideLabels(plan));
        assertEquals(LayoutIds.CONTENT_COMPARISON_FOCUS,findByTitle(plan,PptContentPlannerV2.LITERATURE_COMPARISON_TITLE).path("layoutId").asText());
        assertEquals(LayoutIds.CONTENT_QUADRANT_MATRIX,findByTitle(plan,PptContentPlannerV2.VALUE_QUADRANT_TITLE).path("layoutId").asText());
        assertEquals(LayoutIds.CONTENT_RESEARCH_TIMELINE,findByTitle(plan,PptContentPlannerV2.RESEARCH_TIMELINE_TITLE).path("layoutId").asText());
        assertEquals(LayoutIds.CONTENT_PROGRESS_ROADMAP,findByTitle(plan,PptContentPlannerV2.METRICS_PROGRESS_TITLE).path("layoutId").asText());
        assertElementGroup(findByTitle(plan,PptContentPlannerV2.LITERATURE_COMPARISON_TITLE),"compare-","SHAPE",5);
        assertElementGroup(findByTitle(plan,PptContentPlannerV2.VALUE_QUADRANT_TITLE),"quadrant-0","SHAPE",4);
        assertElementGroup(findByTitle(plan,PptContentPlannerV2.RESEARCH_TIMELINE_TITLE),"timeline-node-","SHAPE",5);
        assertElementGroup(findByTitle(plan,PptContentPlannerV2.RESEARCH_TIMELINE_TITLE),"timeline-rail-","CONNECTOR",4);
        assertElementGroup(findByTitle(plan,"研究方法与指标体系"),"method-","SHAPE",5);
        assertElementGroup(findByTitle(plan,"研究内容总体框架"),"framework-card-","SHAPE",4);
        assertElementGroup(findByTitle(plan,PptContentPlannerV2.METRICS_PROGRESS_TITLE),"roadmap-stage-","SHAPE",5);
        assertElementGroup(findByTitle(plan,"预期成果与应用价值"),"outcome-stage-","SHAPE",4);
        for(String title:List.of(PptContentPlannerV2.LITERATURE_COMPARISON_TITLE,PptContentPlannerV2.VALUE_QUADRANT_TITLE,PptContentPlannerV2.RESEARCH_TIMELINE_TITLE,PptContentPlannerV2.METRICS_PROGRESS_TITLE)){
            JsonNode slide=findByTitle(plan,title);
            assertFalse(java.util.stream.StreamSupport.stream(slide.path("elements").spliterator(),false).anyMatch(element->"IMAGE".equals(element.path("elementType").asText())),title+" must be native editable content");
        }
        JsonNode summary=findByTitle(plan,"预期成果与应用价值");
        String summaryText=summary.path("elements").toString();
        assertFalse(summaryText.contains("（二）预期达到的目标")||summaryText.contains("四、研究方案及预期达到的目标"),summaryText);
    }

    private boolean hasAnchor(JsonNode slide){return java.util.stream.StreamSupport.stream(slide.path("elements").spliterator(),false).anyMatch(element->Set.of("SHAPE","CONNECTOR","IMAGE","TABLE").contains(element.path("elementType").asText()));}
    private void assertElementGroup(JsonNode slide,String idFragment,String elementType,int minimumCount){long count=java.util.stream.StreamSupport.stream(slide.path("elements").spliterator(),false).filter(element->element.path("elementId").asText().contains(idFragment)&&elementType.equals(element.path("elementType").asText())).count();assertTrue(count>=minimumCount,slide.path("layoutId").asText()+" must keep its native "+idFragment+" grammar; found "+count);}
    private List<String> slideLabels(ObjectNode plan){List<String> labels=new ArrayList<>();for(JsonNode slide:plan.path("slides")){String title="";for(JsonNode element:slide.path("elements"))if("TEXT".equals(element.path("elementType").asText())&&element.path("elementId").asText().endsWith("-title")){title=element.path("text").asText();break;}labels.add(slide.path("layoutId").asText()+":"+title);}return labels;}
    private JsonNode findByTitle(ObjectNode plan,String title){for(JsonNode slide:plan.path("slides"))for(JsonNode element:slide.path("elements"))if("TEXT".equals(element.path("elementType").asText())&&title.equals(element.path("text").asText().replace("\n","")))return slide;throw new AssertionError("Missing slide: "+title);}
    private void writeReport(ObjectMapper mapper,Path output,Path source,ObjectNode plan,String hash)throws Exception{ObjectNode report=mapper.createObjectNode();report.put("source",source.toString());report.put("sourceSha256",sha(Files.readAllBytes(source)));report.put("pptxSha256",sha(Files.readAllBytes(output)));report.put("renderPlanHash",hash);report.put("slideCount",plan.path("slides").size());Map<String,Integer> counts=new TreeMap<>();plan.path("slides").forEach(slide->counts.merge(slide.path("layoutId").asText(),1,Integer::sum));ObjectNode layouts=report.putObject("layoutCounts");counts.forEach(layouts::put);report.put("nativeOpeningGrammar",true);report.put("placeholderCount",0);Files.writeString(output.resolveSibling(output.getFileName()+".qa.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n",StandardCharsets.UTF_8);}
    private String sha(byte[] bytes)throws Exception{return"sha256:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
