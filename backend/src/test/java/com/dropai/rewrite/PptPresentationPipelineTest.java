package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.dropai.rewrite.service.ppt.PptTextValidator;
import com.dropai.rewrite.service.ppt.PptxGenerator;
import com.dropai.rewrite.service.ppt.PptAiService;
import com.dropai.rewrite.service.ppt.PptGenerationSkillService;
import com.dropai.rewrite.config.PptProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PptPresentationPipelineTest {
    @Test void callsConfiguredDoubaoForSourceGroundedOutline() throws Exception {
        Assumptions.assumeTrue(System.getenv("DOKIAI_PPT_ARK_API_KEY")!=null||System.getenv("ARK_API_KEY")!=null||System.getenv("DOUBAO_API_KEY")!=null,"本地未配置豆包密钥");
        Path source=Path.of(System.getProperty("ppt.test.source","C:/Users/Administrator/Desktop/自己的/6022203537-高瑞康-基于Spring Boot的个人健康管理系统的设计与实现-ppt.pptx"));
        var parsed=new PptDocumentParser().parse(source,Path.of("target","ppt-ai-assets").toAbsolutePath());
        ObjectMapper mapper=new ObjectMapper();var result=new PptAiService(new PptProperties(new StandardEnvironment()),RestClient.builder(),mapper,new PptGenerationSkillService(mapper)).createOutline("基于Spring Boot的个人健康管理系统的设计与实现",parsed);
        assertTrue(result.providerInvoked(),"豆包 Responses API 未成功调用，状态="+result.providerStatus());
        assertEquals("ppt-generation",result.audit().skillName());assertEquals("2.0.0",result.audit().skillVersion());assertTrue(result.audit().skillHash().matches("[0-9a-f]{64}"));
        assertTrue(result.items().size()>=4);assertEquals(result.items().size(),result.items().stream().map(PptAiService.OutlineItem::title).distinct().count());
    }
    @Test void validatesTwentyVisibleCharactersAndFourBoxes(){PptTextValidator v=new PptTextValidator();var result=v.validateSlideTextLimits("这是一个超过二十四个可见字符需要自动缩短的幻灯片标题",List.of("这是一个明显超过二十个可见字符的正文文本框内容需要压缩","第二项","第三项","第四项","第五项"));assertTrue(v.visible(result.title())<=24);assertEquals(4,result.bodyBoxes().size());assertTrue(result.bodyBoxes().stream().allMatch(x->v.visible(x)<=20));assertEquals("AUTO_FIXED",result.status());}
    @Test void toleratesLegacyTemplateProfileWithoutLayoutVariant()throws Exception{PptxGenerator generator=new PptxGenerator(new PptTextValidator());Path output=Path.of("target","ppt-qa","legacy-profile.pptx").toAbsolutePath();var section=new PptxGenerator.SectionSpec("s1","系统设计",List.of(new PptxGenerator.SlideSpec("总体架构",List.of("前后端分离","分层处理"),"源文档内容",null,"KEYWORDS")));var deck=new PptxGenerator.DeckSpec("健康管理系统","Health Management System","高瑞康","计算机科学与技术","","6022203537",List.of(section),List.of("持续优化"));var profile=new PptxGenerator.TemplateProfile(null,null,null,null,null,null,"Microsoft YaHei",null,null,null);assertTrue(Files.size(generator.generate(deck,output,profile).path())>0);}

    @Test void parsesSourceAndGeneratesEditableAcademicDeck() throws Exception {
        Path source=Path.of(System.getProperty("ppt.test.source","C:/Users/Administrator/Desktop/自己的/6022203537-高瑞康-基于Spring Boot的个人健康管理系统的设计与实现-ppt.pptx"));
        Assumptions.assumeTrue(Files.isRegularFile(source),"测试源PPTX不存在："+source);
        Path qa=Path.of("target","ppt-qa").toAbsolutePath();Files.createDirectories(qa);PptDocumentParser parser=new PptDocumentParser();var parsed=parser.parse(source,qa.resolve("assets"));assertTrue(parsed.blocks().size()>=4);assertTrue(parsed.assets().size()>=1);assertTrue(parsed.characterCount()>100);
        PptTextValidator validator=new PptTextValidator();PptxGenerator generator=new PptxGenerator(validator);List<String> names=List.of("课题概述","课题设计","课题实现","系统测试");List<PptxGenerator.SectionSpec> sections=new ArrayList<>();int cursor=0;
        for(String name:names){List<PptxGenerator.SlideSpec> slides=new ArrayList<>();for(int i=0;i<2;i++){String sourceText=parsed.blocks().get(cursor++%parsed.blocks().size());String visibleSource=sourceText.replaceFirst("^\\[第\\d+页]\\s*","");List<String> boxes=new ArrayList<>();for(String part:visibleSource.split("[。！？；\\n]+")){if(part.isBlank())continue;boxes.add(validator.compact(part,20));if(boxes.size()==4)break;}Path asset=parsed.assets().isEmpty()?null:parsed.assets().get((cursor-1)%parsed.assets().size()).path();slides.add(new PptxGenerator.SlideSpec(i==0?name:validator.compact(boxes.get(0),24),boxes,sourceText,asset,"IMAGE_TEXT"));}sections.add(new PptxGenerator.SectionSpec(name,name,slides));}
        Path output=qa.resolve("基于Spring Boot的个人健康管理系统的设计与实现.pptx");var result=generator.generate(new PptxGenerator.DeckSpec("基于Spring Boot的个人健康管理系统的设计与实现","Design and Implementation of a Personal Health Management System","高瑞康","计算机科学与技术","指导老师","6022203537",sections,List.of("完善移动端适配","扩展智能分析能力","优化数据安全机制")),output);
        assertTrue(Files.size(output)>100_000);assertEquals("pptx",extension(output));
        try(XMLSlideShow deck=new XMLSlideShow(Files.newInputStream(output))){assertEquals(16,deck.getSlides().size());assertTrue(slideText(deck.getSlides().get(deck.getSlides().size()-2)).contains("未来展望"));assertTrue(slideText(deck.getSlides().get(deck.getSlides().size()-1)).contains("谢谢大家"));assertTrue(deck.getSlides().stream().allMatch(s->!slideText(s).isBlank()));}
    }
    private static String slideText(XSLFSlide slide){StringBuilder out=new StringBuilder();for(XSLFShape shape:slide.getShapes())if(shape instanceof XSLFTextShape t)out.append(t.getText()).append('\n');return out.toString();}
    private static String extension(Path p){String n=p.getFileName().toString();return n.substring(n.lastIndexOf('.')+1);}
}
