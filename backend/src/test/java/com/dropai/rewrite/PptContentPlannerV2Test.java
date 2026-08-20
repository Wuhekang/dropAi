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
                        new PptContentPlannerV2.SourceChapter("c1","第一章 项目概述",List.of("题目：基于Spring Boot的个人健康管理系统的设计与实现","学生姓名：高瑞康","毕业设计任务书 审批意见 签字栏","Java简介","随着健康管理需求增长，传统记录方式难以持续跟踪数据，系统需要提供统一的信息管理能力。")),
                        new PptContentPlannerV2.SourceChapter("c3","第三章 系统设计",List.of("3.1 系统架构设计","系统采用前后端分离和分层架构，前端负责交互，后端处理业务逻辑，数据库统一保存核心数据。","数据库围绕用户数据、健康记录和业务信息建立实体关系。","项目采用Spring Boot、Vue、MySQL和MyBatis完成系统开发。"))
                ),List.of(),List.of(),"computer");
        var result=planner.plan(input);List<PptContentPlannerV2.CandidatePage> pages=result.chapters().stream().flatMap(c->c.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统总体架构")&&p.answerQuestion().equals("系统如何组织核心功能？")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("数据库结构设计")));
        assertEquals(1,pages.stream().filter(p->p.title().equals("系统开发技术路线")).count());
        assertTrue(pages.stream().allMatch(p->{assertDoesNotThrow(()->planner.requireComplete(p));return true;}));
        assertTrue(pages.stream().noneMatch(p->p.title().contains("Java简介")||p.title().contains("MySQL数据库")||p.title().contains("基于Spring Boot")));
        assertEquals(List.of("THESIS_TITLE","METADATA","FORM_CONTENT","LOW_VALUE_STANDALONE"),result.filteredContents().stream().map(PptContentPlannerV2.FilteredContent::reason).toList());
    }

    @Test void doesNotDecideFinalPageCountOrRenderAnything(){
        assertTrue(PptContentPlannerV2.class.getDeclaredMethods().length>0);
        assertTrue(List.of(PptContentPlannerV2.class.getDeclaredMethods()).stream().noneMatch(m->m.getName().matches("generate|render|selectFinalPages")));
    }

    @Test void realHealthThesisProducesDefenseCandidatesWithoutTitleOrStandaloneTechnologyPages()throws Exception{
        Path source=Path.of(System.getProperty("ppt.health.docx","D:/废纸/自己的/6022203537_高瑞康/6022203537_高瑞康_基于Spring Boot的个人健康管理系统的设计与实现最终稿.docx"));
        Assumptions.assumeTrue(Files.isRegularFile(source));
        var parsed=new PptDocumentParser().parse(source,Path.of("target","ppt-content-v2-assets"));
        var input=new PptContentPlannerV2InputAdapter().fromParsedDocument(parsed,"computer");var result=planner.plan(input);
        var pages=result.chapters().stream().flatMap(c->c.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统总体架构")));
        assertTrue(pages.stream().anyMatch(p->p.title().equals("系统开发技术路线")));
        assertTrue(pages.stream().noneMatch(p->p.title().equals(parsed.title())||p.title().matches("(?i).*(Java简介|MySQL介绍|Spring Boot介绍|Vue介绍).*")));
        assertTrue(pages.stream().allMatch(p->{assertDoesNotThrow(()->planner.requireComplete(p));return true;}));
    }
}
