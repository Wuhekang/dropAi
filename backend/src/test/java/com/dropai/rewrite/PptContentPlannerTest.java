package com.dropai.rewrite;

import com.dropai.rewrite.service.ppt.PptContentPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PptContentPlannerTest {
    private final PptContentPlanner planner=new PptContentPlanner();

    @Test void isolatesMetadataFormsTitleAndKeywordPages(){
        String title="基于Spring Boot和Vue的家政服务管理平台设计与实现";
        List<String> blocks=List.of(title,"关键词：Spring Boot、Vue、MySQL","毕业设计任务书 课题来源：教师科研 学生姓名：张三", "系统采用分层架构，将用户端、管理端与数据访问职责分离，以支持家政服务预约、订单管理和服务评价。", "数据库围绕用户、服务项目、订单与评价建立关联，保证核心业务数据的一致性。");
        var pages=planner.planTextPages(title,"课题设计","说明系统设计方案",blocks,2);
        assertEquals(2,pages.size());
        assertTrue(pages.stream().noneMatch(p->p.title().contains("关键词")||p.title().equals(title)));
        assertTrue(pages.stream().flatMap(p->p.keyPoints().stream()).noneMatch(v->v.contains("课题来源")||v.contains("学生姓名")));
        assertTrue(pages.stream().allMatch(p->!p.pagePurpose().isBlank()&&!p.description().isBlank()&&!p.keyPoints().isEmpty()&&p.keyPoints().size()<=3));
    }

    @Test void rewritesAcademicHeadingIntoDefenseTitle(){
        var page=planner.planTextPages("论文题目","课题设计","",List.of("3.1 系统架构设计。系统采用前后端分离架构，各层承担清晰职责并通过接口协同。"),1).get(0);
        assertEquals("系统总体架构",page.title());
        assertFalse(page.keyPoints().isEmpty());
        assertDoesNotThrow(()->planner.requireValue(page));
    }

    @Test void createsDistinctDefenseTitlesForEachOutlineSection(){
        List<String> source=List.of("系统架构设计采用前后端分离方式，明确表现层、业务层与数据层职责。","系统实现覆盖预约、派单、履约和评价流程，形成完整业务闭环。","系统测试通过功能用例验证订单状态流转和权限控制是否符合预期。");
        assertEquals(List.of("项目背景与价值","建设目标与需求"),planner.planTextPages("论文题目","课题概述","",source,2).stream().map(PptContentPlanner.PagePlan::title).toList());
        assertEquals(List.of("系统总体架构","核心模块设计"),planner.planTextPages("论文题目","课题设计","",source,2).stream().map(PptContentPlanner.PagePlan::title).toList());
        assertEquals(List.of("核心功能实现","业务流程实现"),planner.planTextPages("论文题目","课题实现","",source,2).stream().map(PptContentPlanner.PagePlan::title).toList());
        assertEquals(List.of("测试方案与结果","系统验证结论"),planner.planTextPages("论文题目","系统测试","",source,2).stream().map(PptContentPlanner.PagePlan::title).toList());
    }

    @Test void rejectsStandaloneKeywordPage(){
        var invalid=new PptContentPlanner.PagePlan("总结技术","技术关键词页",List.of("Spring Boot"),"关键词汇总说明","关键词");
        assertThrows(IllegalStateException.class,()->planner.requireValue(invalid));
    }
}
