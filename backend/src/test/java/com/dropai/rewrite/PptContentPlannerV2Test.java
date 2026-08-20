package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptContentPlannerV2;
import com.dropai.rewrite.service.ppt.PptPlannerRuleLibrary;
import com.dropai.rewrite.service.ppt.PptContentPlannerV2InputAdapter;
import com.dropai.rewrite.service.ppt.PptDocumentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

class PptContentPlannerV2Test {
    private final PptContentPlannerV2 planner=new PptContentPlannerV2(new PptPlannerRuleLibrary(new ObjectMapper()));

    @Test void readsGeneralAndComputerRuleLibraries(){
        var library=new PptPlannerRuleLibrary(new ObjectMapper());
        assertEquals("general",library.load("general").majorType());
        assertEquals("computer",library.load("computer").majorType());
        assertTrue(library.load("computer").technologyTerms().contains("Spring Boot"));
    }

    @Test void createsCompleteCandidatePagesAndFiltersPollution(){
        var input=new PptContentPlannerV2.PlannerInput(
                Map.of("title","基于Spring Boot的个人健康管理系统的设计与实现","student","高瑞康"),
                List.of(
                        new PptContentPlannerV2.SourceChapter("c1","第一章 项目概述",List.of("题目：基于Spring Boot的个人健康管理系统的设计与实现","学生姓名：高瑞康","毕业设计任务书 审批意见 签字栏","Java简介","随着健康管理需求增长，传统记录方式难以持续跟踪数据，系统需要提供统一的信息管理能力。","系统采用Spring Boot、Vue和MySQL构建开发技术路线。")),
                        new PptContentPlannerV2.SourceChapter("c3","第三章 系统设计",List.of("3.1 系统架构设计","系统采用前后端分离和分层架构，前端负责交互，后端处理业务逻辑，数据库统一保存核心数据。","数据库围绕用户数据、健康记录和业务信息建立实体关系。","项目采用Spring Boot、Vue、MySQL和MyBatis完成系统开发。"))
                ),List.of(),List.of(),"computer");
        var result=planner.plan(input);List<PptContentPlannerV2.CandidatePage> pages=result.chapters().stream().flatMap(c->c.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统总体架构")&&p.answerQuestion().equals("系统如何组织核心功能？")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("数据库结构设计")));
        assertEquals(1,pages.stream().filter(p->p.title().equals("系统开发技术路线")).count());
        assertTrue(pages.stream().allMatch(p->{assertDoesNotThrow(()->planner.requireComplete(p));return true;}));
        var imagePages=pages.stream().filter(p->p.candidateType().equals("IMAGE_EVIDENCE")).toList();
        assertEquals(input.assets().size(),imagePages.size(),"每张有效图片必须生成一个证据候选页");
        assertEquals(imagePages.size(),imagePages.stream().map(p->p.sourceRefs().figureId()).distinct().count());
        assertTrue(pages.stream().filter(p->p.candidateType().equals("TABLE_SUMMARY")).collect(java.util.stream.Collectors.groupingBy(PptContentPlannerV2.CandidatePage::sourceChapter,java.util.stream.Collectors.counting())).values().stream().allMatch(count->count<=1));
        assertTrue(pages.stream().noneMatch(p->p.title().contains("Java简介")||p.title().contains("MySQL数据库")||p.title().contains("基于Spring Boot")));
        var reasons=result.filteredContents().stream().map(PptContentPlannerV2.FilteredContent::reason).toList();
        assertTrue(reasons.containsAll(List.of("THESIS_TITLE","METADATA","FORM_CONTENT","LOW_VALUE_STANDALONE")));
    }

    @Test void doesNotDecideFinalPageCountOrRenderAnything(){
        assertTrue(PptContentPlannerV2.class.getDeclaredMethods().length>0);
        assertTrue(List.of(PptContentPlannerV2.class.getDeclaredMethods()).stream().noneMatch(m->m.getName().matches("generate|render|selectFinalPages")));
    }

    @Test void splitsImplementationAndSummaryIntoDefenseReadyCandidates(){
        var input=new PptContentPlannerV2.PlannerInput(Map.of(),List.of(
                new PptContentPlannerV2.SourceChapter("c4","第四章 系统实现",List.of(
                        "本章完成管理端功能、用户端功能和AI智能服务等系统功能实现。",
                        "用户通过登录注册、健康数据管理和数据录入完成日常健康记录。",
                        "数据可视化页面基于ECharts展示趋势分析、健康指标和交互图表。",
                        "AI健康评估调用大语言模型分析健康数据并生成结构化评估报告。")),
                new PptContentPlannerV2.SourceChapter("c6","第六章 总结与展望",List.of(
                        "总结：系统完成了健康管理、智能分析和功能验证等具体工作。",
                        "展望未来，系统将进一步提升模型能力并优化交互体验。"))
        ),List.of(),List.of(),"computer");
        var pages=planner.plan(input).chapters().stream().flatMap(c->c.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统功能整体实现")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("AI健康评估功能")&&p.keyPoints().contains("AI模型分析")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("健康数据可视化")&&p.pagePurpose().equals("RESULT")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("用户端交互设计")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("项目总结")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("未来优化方向")));
        assertEquals(pages.size(),pages.stream().map(p->p.sourceChapter()+"|"+p.title()).distinct().count());
    }

    @Test void realHealthThesisProducesDefenseCandidatesWithoutTitleOrStandaloneTechnologyPages()throws Exception{
        Path source=Path.of(System.getProperty("ppt.health.docx","D:/废纸/自己的/6022203537_高瑞康/6022203537_高瑞康_基于Spring Boot的个人健康管理系统的设计与实现最终稿.docx"));
        Assumptions.assumeTrue(Files.isRegularFile(source));
        var parsed=new PptDocumentParser().parse(source,Path.of("target","ppt-content-v2-assets"));
        var input=new PptContentPlannerV2InputAdapter().fromParsedDocument(parsed,"computer");var result=planner.plan(input);
        assertTrue(input.metadata().get("title").contains("个人健康管理系统"));
        assertTrue(input.chapters().size()>=4&&input.chapters().size()<=8,"适配器应按一级章节聚合，而不是把每个二级标题当成章节");
        var pages=result.chapters().stream().flatMap(c->c.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统总体架构")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统功能架构")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统开发技术路线")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统功能整体实现")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("AI健康评估功能")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("健康数据可视化")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("用户端交互设计")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("项目总结")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("未来优化方向")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统需求分析")&&!p.keyPoints().contains("第二章 需求分析")));
        assertTrue(pages.stream().filter(p->p.title().equals("AI健康评估功能")).allMatch(p->p.description().contains("个性化建议")));
        assertEquals(1,pages.stream().filter(p->p.title().equals("系统开发技术路线")).count());
        assertTrue(pages.stream().noneMatch(p->p.title().equals(parsed.title())||p.title().matches("(?i).*(Java简介|MySQL介绍|Spring Boot介绍|Vue介绍).*")));
        assertTrue(pages.stream().allMatch(p->{assertDoesNotThrow(()->planner.requireComplete(p));return true;}));
        var evidence=pages.stream().filter(p->p.candidateType().equals("IMAGE_EVIDENCE")).toList();
        assertEquals(input.assets().size(),evidence.size());
        assertEquals(evidence.size(),evidence.stream().map(p->p.sourceRefs().figureId()).distinct().count());
        var outline=new com.dropai.rewrite.service.ppt.PptOutlinePlannerV1().plan(new com.dropai.rewrite.service.ppt.PptOutlinePlannerV1.OutlineRequest(pages,12));
        assertEquals(input.assets().size(),outline.slideTree().stream().filter(s->s.candidateType().equals("IMAGE_EVIDENCE")).count());
        var validated=new com.dropai.rewrite.service.ppt.PptOutlineValidatorV1().validate(new com.dropai.rewrite.service.ppt.PptOutlineValidatorV1.ValidationRequest(input.metadata(),outline));
        assertTrue(validated.valid(),validated.issues().toString());
    }
}
