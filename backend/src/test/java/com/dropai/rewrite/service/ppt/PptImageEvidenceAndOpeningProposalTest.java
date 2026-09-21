package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PptImageEvidenceAndOpeningProposalTest {

    @Test
    void everyValidSourceImageBecomesOneMandatoryDedicatedSlideEvenOverPageLimit() {
        PptContentPlannerV2 planner = new PptContentPlannerV2(
                new PptPlannerRuleLibrary(new ObjectMapper()));
        List<Map<String, Object>> assets = List.of(
                asset("figure_1_1", "chapter_1", "图1-1 研究技术路线图\n该图说明资料收集、问题分析与方案形成的先后关系。"),
                asset("figure_2_1", "chapter_2", "图2-1 核心指标体系图\n该图展示三个指标维度及其相互关系。"),
                asset("figure_2_2", "chapter_2", "研究框架围绕现状、问题、原因和对策依次展开。"));
        var input = new PptContentPlannerV2.PlannerInput(
                Map.of("title", "企业经营问题研究"),
                List.of(
                        new PptContentPlannerV2.SourceChapter("chapter_1", "第一章 选题依据",
                                List.of("研究背景说明了选题的现实价值和需要解决的核心问题。")),
                        new PptContentPlannerV2.SourceChapter("chapter_2", "第二章 研究方案",
                                List.of("研究方法包括文献研究、案例分析和指标比较。"))),
                assets, List.of(), "general");

        var planned = planner.plan(input);
        var imagePages = planned.chapters().stream().flatMap(chapter -> chapter.candidatePages().stream())
                .filter(page -> "IMAGE_EVIDENCE".equals(page.candidateType())).toList();
        assertEquals(assets.size(), imagePages.size());
        assertTrue(imagePages.stream().allMatch(PptContentPlannerV2.CandidatePage::mandatoryAsset));
        assertEquals(assets.stream().map(asset -> String.valueOf(asset.get("id"))).sorted().toList(),
                imagePages.stream().map(page -> page.sourceRefs().figureId()).sorted().toList());
        assertTrue(imagePages.stream().noneMatch(page -> page.title().startsWith("系统证据图")));
        assertTrue(imagePages.stream().allMatch(page -> page.description().length() >= 40
                && page.description().length() <= 70), imagePages.toString());

        List<PptContentPlannerV2.CandidatePage> candidates = planned.chapters().stream()
                .flatMap(chapter -> chapter.candidatePages().stream()).toList();
        var outline = new PptOutlinePlannerV1().plan(
                new PptOutlinePlannerV1.OutlineRequest(candidates, 1));
        assertEquals(assets.size(), outline.slideTree().stream()
                .filter(page -> "IMAGE_EVIDENCE".equals(page.candidateType())).count());
    }

    @Test
    void recognizesOpeningProposalByStructureAndAddsEditableSemanticPages() {
        List<String> blocks = List.of(
                "选题目的及意义：说明课题价值。",
                "国内外研究现状：梳理已有成果。",
                "研究内容：明确研究对象与问题。",
                "研究方法：采用文献研究和案例分析。",
                "技术路线：资料收集、分析和总结。",
                "进度安排：分阶段完成开题、研究与答辩。",
                "预期成果：形成研究报告。"
        );
        assertTrue(PptOpeningProposalDetector.isOpeningProposal(blocks));
        var planner=new PptContentPlannerV2(new PptPlannerRuleLibrary(new ObjectMapper()));
        var input=new PptContentPlannerV2.PlannerInput(
                Map.of("title","课题研究","presentationMode","OPENING_PROPOSAL"),
                List.of(
                        new PptContentPlannerV2.SourceChapter("chapter_1","第一章 研究现状",blocks.subList(0,2)),
                        new PptContentPlannerV2.SourceChapter("chapter_2","第二章 研究方案",blocks.subList(2,5)),
                        new PptContentPlannerV2.SourceChapter("chapter_4","第四章 进度安排",blocks.subList(5,7))),
                List.of(),List.of(),"social_science");
        var generated=planner.plan(input).chapters().stream()
                .flatMap(chapter->chapter.candidatePages().stream())
                .filter(page->Set.of(PptContentPlannerV2.LITERATURE_COMPARISON_TITLE,
                        PptContentPlannerV2.VALUE_QUADRANT_TITLE,
                        PptContentPlannerV2.RESEARCH_TIMELINE_TITLE,
                        PptContentPlannerV2.METRICS_PROGRESS_TITLE).contains(page.title()))
                .toList();
        assertEquals(4,generated.size());
        assertTrue(generated.stream().allMatch(page->"TEXT".equals(page.candidateType())));
        assertTrue(generated.stream().noneMatch(PptContentPlannerV2.CandidatePage::mandatoryAsset));
        assertEquals(Set.of(
                        PptContentPlannerV2.LITERATURE_COMPARISON_TITLE,
                        PptContentPlannerV2.VALUE_QUADRANT_TITLE,
                        PptContentPlannerV2.RESEARCH_TIMELINE_TITLE,
                        PptContentPlannerV2.METRICS_PROGRESS_TITLE),
                generated.stream().map(PptContentPlannerV2.CandidatePage::title)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("COMPARISON","PROCESS","METRICS"),generated.stream()
                .map(PptContentPlannerV2.CandidatePage::contentType)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void computerOpeningProposalGetsGroundedVisualGrammarsAndSummaryWithoutFinanceCopy() {
        var planner = new PptContentPlannerV2(new PptPlannerRuleLibrary(new ObjectMapper()));
        var input = new PptContentPlannerV2.PlannerInput(
                Map.of("title", "安全类图书智能问答与推荐系统的设计与实现",
                        "presentationMode", "OPENING_PROPOSAL"),
                List.of(
                        new PptContentPlannerV2.SourceChapter("chapter_1", "一、论文选题的背景、意义",
                                List.of("安全类图书资料较多，用户难以快速找到适合岗位与问题的学习资源。",
                                        "在应用层面，问答来源展示和审核机制有助于提高安全知识获取的可靠性。")),
                        new PptContentPlannerV2.SourceChapter("chapter_2", "二、相关研究的最新成果及动态",
                                List.of("国内推荐研究关注协同过滤和冷启动问题。",
                                        "国外研究将外部检索与生成模型结合，形成检索增强生成范式。")),
                        new PptContentPlannerV2.SourceChapter("chapter_3", "三、研究的基本内容与拟解决的问题",
                                List.of("系统采用双角色结构，管理员维护知识库，用户开展图书检索和智能问答。",
                                        "推荐模块使用内容特征与协同过滤形成个性化书目。")),
                        new PptContentPlannerV2.SourceChapter("chapter_4", "四、研究的方法与技术路线",
                                List.of("技术路线为：文献调研与需求分析→数据库设计→问答检索实现→推荐算法实现→系统测试→论文撰写。",
                                        "系统测试与评价关注问答来源正确性和推荐Precision@K、Recall@K。")),
                        new PptContentPlannerV2.SourceChapter("chapter_5", "五、研究的总体安排与进度",
                                List.of("先完成需求分析，再实现前后端与核心算法，最终完成测试和答辩准备。"))),
                List.of(), List.of(), "computer");

        var pages = planner.plan(input).chapters().stream()
                .flatMap(chapter -> chapter.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(page -> "SUMMARY".equals(page.pagePurpose())));
        assertTrue(pages.stream().anyMatch(page -> PptContentPlannerV2.LITERATURE_COMPARISON_TITLE.equals(page.title())));
        assertTrue(pages.stream().anyMatch(page -> PptContentPlannerV2.RESEARCH_TIMELINE_TITLE.equals(page.title())));
        assertTrue(pages.stream().noneMatch(page -> (page.description() + String.join(" ", page.keyPoints()))
                .contains("华润新能源")));
        assertTrue(pages.stream().noneMatch(page -> (page.description() + String.join(" ", page.keyPoints()))
                .contains("偿债")));
        assertTrue(pages.stream().noneMatch(page -> (page.description() + String.join(" ", page.keyPoints()))
                .contains("健康数据")));
        var outline = new PptOutlinePlannerV1().plan(new PptOutlinePlannerV1.OutlineRequest(pages, 8));
        assertTrue(outline.slideTree().stream().anyMatch(page -> "SUMMARY".equals(page.pagePurpose())));
    }

    @Test
    void genericComputerNeedsDoNotInheritHealthManagementRulePoints() {
        var planner = new PptContentPlannerV2(new PptPlannerRuleLibrary(new ObjectMapper()));
        var input = new PptContentPlannerV2.PlannerInput(
                Map.of("title", "安全类图书智能问答与推荐系统"),
                List.of(new PptContentPlannerV2.SourceChapter("chapter_2", "第二章 需求分析",
                        List.of("需求分析围绕图书检索、知识来源展示和推荐结果解释展开。"))),
                List.of(), List.of(), "computer");
        var pages = planner.plan(input).chapters().stream()
                .flatMap(chapter -> chapter.candidatePages().stream()).toList();
        assertTrue(pages.stream().anyMatch(page -> "系统需求分析".equals(page.title())));
        assertTrue(pages.stream().noneMatch(page -> (page.description() + String.join(" ", page.keyPoints()))
                .contains("健康数据")));
    }

    private static Map<String, Object> asset(String id, String chapterId, String caption) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("chapterId", chapterId);
        value.put("caption", caption);
        value.put("path", id + ".png");
        value.put("width", 1600);
        value.put("height", 900);
        return value;
    }
}
