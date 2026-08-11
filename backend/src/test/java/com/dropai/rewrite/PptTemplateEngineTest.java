package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.dropai.rewrite.service.ppt.PptTemplateService;
import com.dropai.rewrite.service.ppt.PptTextValidator;
import com.dropai.rewrite.service.ppt.PptxGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PptTemplateEngineTest {
    private static final Path SOURCE=Path.of("D:/废纸/ppt模板/ppt模板/教师说课PPT模板-15.pptx");
    private static final Path PAPER=Path.of("C:/Users/Administrator/Desktop/自己的/6022203537-高瑞康-基于Spring Boot的个人健康管理系统的设计与实现-ppt.pptx");

    @Test void analyzesProvidedTemplateMetadata()throws Exception{
        assumeTrue(Files.isRegularFile(SOURCE));
        PptTemplateService.TemplateMetadata meta=new PptTemplateService(null,new ObjectMapper()).analyze(SOURCE);
        assertFalse(meta.templateName().isBlank());assertFalse(meta.colors().isEmpty());assertFalse(meta.fonts().isEmpty());assertTrue(meta.slideCount()>0);assertTrue(meta.slideTypes().contains("cover"));assertTrue(meta.slideTypes().contains("thanks"));assertEquals(meta.slideCount(),meta.slides().size());assertEquals("cover",meta.slides().get(0).pageType());assertEquals("catalog",meta.slides().get(1).pageType());assertFalse(meta.slides().get(2).usableRegions().isEmpty());
    }

    @Test void samePaperProducesDifferentStylesWithoutChangingContent()throws Exception{
        assumeTrue(Files.isRegularFile(PAPER));Path dir=Path.of("target","ppt-template-qa");Files.createDirectories(dir);
        PptDocumentParser parser=new PptDocumentParser();var parsed=parser.parse(PAPER,dir.resolve("assets"));Path image=parsed.assets().isEmpty()?null:parsed.assets().get(0).path();
        PptxGenerator generator=new PptxGenerator(new PptTextValidator());
        var slides=List.of(new PptxGenerator.SlideSpec("系统总体设计",List.of("前后端职责清晰","健康数据统一管理","AI分析辅助决策"),"来源文档完整内容",image,"IMAGE_TEXT"));
        var deck=new PptxGenerator.DeckSpec("基于Spring Boot的个人健康管理系统的设计与实现","Design and Implementation of a Personal Health Management System","高瑞康","计算机科学与技术","指导教师","6022203537",List.of(new PptxGenerator.SectionSpec("1","系统设计",slides)),List.of("持续优化健康分析","扩展智能服务能力"));
        Path tech=dir.resolve("健康管理系统-科技答辩.pptx"),design=dir.resolve("健康管理系统-高级设计.pptx"),business=dir.resolve("健康管理系统-商务汇报.pptx");
        generator.generate(deck,tech,new PptxGenerator.TemplateProfile("TECH_DEFENSE","#2563EB","#22D3EE","#0F172A","#64748B","#EFF6FF","Microsoft YaHei","tech","科技答辩",null));
        generator.generate(deck,design,new PptxGenerator.TemplateProfile("PREMIUM_DESIGN","#A06A42","#D8B48A","#27231F","#756B62","#F7F1EA","Microsoft YaHei","design","高级设计",null));
        generator.generate(deck,business,new PptxGenerator.TemplateProfile("BUSINESS","#1E3A5F","#D9A441","#172033","#657087","#F3F6FA","Microsoft YaHei","business","商务汇报",null));
        assertEquals(text(tech),text(design));assertEquals(text(tech),text(business));assertFalse(Arrays.equals(Files.readAllBytes(tech),Files.readAllBytes(design)));assertTrue(Files.size(tech)>10_000);assertTrue(Files.size(design)>10_000);assertTrue(Files.size(business)>10_000);
    }

    @Test void customTemplateUsesSourceSlidesAndKeepsRequiredContent()throws Exception{
        Path source=Path.of("../template-import/all-provided-templates/templates/教师说课PPT模板-17/template.pptx");assumeTrue(Files.isRegularFile(source));Path dir=Path.of("target","ppt-template-qa");Files.createDirectories(dir);
        PptxGenerator generator=new PptxGenerator(new PptTextValidator());
        var slides=List.of(new PptxGenerator.SlideSpec("系统总体设计",List.of("前后端职责清晰","健康数据统一管理","AI分析辅助决策"),"来源文档完整内容",null,"KEYWORDS"));
        var deck=new PptxGenerator.DeckSpec("基于Spring Boot的个人健康管理系统的设计与实现","Design and Implementation of a Personal Health Management System","高瑞康","计算机科学与技术","指导教师","6022203537",List.of(new PptxGenerator.SectionSpec("1","系统设计",slides)),List.of("持续优化健康分析","扩展智能服务能力"));
        Path output=dir.resolve("健康管理系统-教师说课模板17.pptx");generator.generate(deck,output,new PptxGenerator.TemplateProfile("CUSTOM","#2563EB","#22D3EE","#0F172A","#64748B","#EFF6FF","Microsoft YaHei","academic","教师说课PPT模板-17",source.toAbsolutePath().toString()));
        String visible=text(output);assertTrue(visible.contains("基于Spring Boot"));assertTrue(visible.contains("未来展望"));assertTrue(visible.contains("谢谢大家"));assertTrue(Files.size(output)>100_000);
    }

    @Test void websiteTemplateMapsFixedPagesAndMixesBodySlides()throws Exception{
        Path source=Path.of("../qa/website-custom-template-17-final.pptx");assumeTrue(Files.isRegularFile(source));Path dir=Path.of("target","ppt-template-mapping-qa");Files.createDirectories(dir);
        ObjectMapper mapper=new ObjectMapper();PptTemplateService.TemplateMetadata metadata=new PptTemplateService(null,mapper).analyze(source);mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("template_metadata.json").toFile(),metadata);assertEquals(metadata.slideCount(),metadata.slides().size());PptTemplateService.TemplateSlideMetadata titlePage=metadata.slides().get(2);assertTrue(titlePage.hasLeftDecoration());assertTrue(titlePage.hasRightDecoration());assertFalse(titlePage.hasImageSlot());assertEquals("center",titlePage.titleAlign());assertTrue(titlePage.safeArea().left()>=.18);assertTrue(titlePage.safeArea().right()<=.82);
        PptxGenerator generator=new PptxGenerator(new PptTextValidator());
        var body=List.of(
                new PptxGenerator.SlideSpec("系统架构",List.of("前后端分层设计","数据服务统一管理"),"架构说明",null,"TIMELINE"),
                new PptxGenerator.SlideSpec("系统界面展示",List.of("核心功能入口清晰","交互路径简洁"),"界面说明",null,"IMAGE_TEXT"),
                new PptxGenerator.SlideSpec("测试结果",List.of("核心用例全部通过","响应性能满足要求"),"测试说明",null,"TABLE"),
                new PptxGenerator.SlideSpec("功能总结",List.of("实现健康数据管理","支持智能分析"),"总结说明",null,"KEYWORDS"));
        var deck=new PptxGenerator.DeckSpec("基于Spring Boot的个人健康管理系统的设计与实现","Personal Health Management System","测试用户","计算机科学与技术","指导教师","6022203537",List.of(new PptxGenerator.SectionSpec("1","系统设计",body)),List.of("持续优化使用体验","扩展智能分析能力"));
        Path output=dir.resolve("website-custom-template-17-remapped.pptx");generator.generate(deck,output,new PptxGenerator.TemplateProfile("CUSTOM","#2563EB","#22D3EE","#0F172A","#64748B","#EFF6FF","Microsoft YaHei","academic","website-custom-template-17-final",source.toAbsolutePath().toString()));
        Path log=dir.resolve("website-custom-template-17-remapped-template-mapping.json");assertTrue(Files.isRegularFile(log));Map<?,?> payload=mapper.readValue(log.toFile(),Map.class);assertEquals(Boolean.TRUE,payload.get("validated"));List<?> mappings=(List<?>)payload.get("mappings");assertTrue(mappings.size()>=8);assertEquals(1,((Number)((Map<?,?>)mappings.get(0)).get("sourcePage")).intValue());assertEquals(2,((Number)((Map<?,?>)mappings.get(1)).get("sourcePage")).intValue());assertEquals("thanks",((Map<?,?>)mappings.get(mappings.size()-1)).get("contentType"));
        int thanks=((Number)((Map<?,?>)mappings.get(mappings.size()-1)).get("sourcePage")).intValue();LinkedHashSet<Integer> middle=new LinkedHashSet<>();for(int i=2;i<mappings.size()-1;i++){int sourcePage=((Number)((Map<?,?>)mappings.get(i)).get("sourcePage")).intValue();assertNotEquals(1,sourcePage);assertNotEquals(2,sourcePage);assertNotEquals(thanks,sourcePage);middle.add(sourcePage);}assertTrue(middle.size()>=3,"正文至少应混合使用3个模板页面");assertTrue(Files.size(output)>100_000);
    }

    private String text(Path file)throws Exception{try(XMLSlideShow deck=new XMLSlideShow(Files.newInputStream(file))){StringBuilder out=new StringBuilder();for(var slide:deck.getSlides())for(XSLFShape shape:slide.getShapes())if(shape instanceof XSLFTextShape text&&!text.getText().isBlank())out.append(text.getText()).append('\n');return out.toString().replaceAll("\\s+"," ").trim();}}
}
