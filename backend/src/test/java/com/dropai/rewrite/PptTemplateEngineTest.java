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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PptTemplateEngineTest {
    private static final Path SOURCE=Path.of("D:/废纸/ppt模板/ppt模板/教师说课PPT模板-15.pptx");
    private static final Path PAPER=Path.of("C:/Users/Administrator/Desktop/自己的/6022203537-高瑞康-基于Spring Boot的个人健康管理系统的设计与实现-ppt.pptx");

    @Test void analyzesProvidedTemplateMetadata()throws Exception{
        assumeTrue(Files.isRegularFile(SOURCE));
        PptTemplateService.TemplateMetadata meta=new PptTemplateService(null,new ObjectMapper()).analyze(SOURCE);
        assertFalse(meta.templateName().isBlank());assertFalse(meta.colors().isEmpty());assertFalse(meta.fonts().isEmpty());assertTrue(meta.slideCount()>0);assertTrue(meta.slideTypes().contains("cover"));assertTrue(meta.slideTypes().contains("thanks"));
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

    private String text(Path file)throws Exception{try(XMLSlideShow deck=new XMLSlideShow(Files.newInputStream(file))){StringBuilder out=new StringBuilder();for(var slide:deck.getSlides())for(XSLFShape shape:slide.getShapes())if(shape instanceof XSLFTextShape text&&!text.getText().isBlank())out.append(text.getText()).append('\n');return out.toString().replaceAll("\\s+"," ").trim();}}
}
