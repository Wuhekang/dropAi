package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptSocialScienceContentProfileTest {

    @Test
    void producesDenseSourceGroundedOpeningProposalWithFourNarrativeSections() {
        PptContentPlannerV2 planner = new PptContentPlannerV2(
                new PptPlannerRuleLibrary(new ObjectMapper()));
        PptContentPlannerV2.PlannerInput input = new PptContentPlannerV2.PlannerInput(
                Map.of("title", "华润新能源偿债能力问题及提升对策研究"),
                List.of(
                        chapter(1, "第一章", List.of(
                                "研究背景：双碳目标推动新能源行业扩大投资，项目建设周期长且资金回收较慢，企业债务管理压力随之增加。",
                                "国外研究现状：研究从单一比率转向流动性、杠杆水平、现金流和债务期限的综合评价。资产规模与电价变化影响财务绩效。利息保障倍数能够反映长期偿债压力。项目融资有助于优化债务期限。",
                                "国内研究现状：现有成果关注内部控制、融资结构、资金集中管理和多指标风险预警，形成了较完整的行业分析视角。",
                                "研究空白：现有研究缺乏针对华润新能源的案例分析，对应收账款、预付账款与债务结构之间的联系讨论不足。")),
                        chapter(2, "第二章", List.of(
                                "研究内容总体框架包括企业偿债现状、问题影响因素和提升对策，分别讨论短期流动性、长期资本结构与现金流保障。",
                                "研究方法采用文献研究法、案例分析法、比率分析法和趋势分析法，并建立短期偿债、长期偿债与现金流量指标体系。",
                                "技术路线以年度报告和财务报表为数据来源，依次完成资料整理、指标计算、问题识别、原因分析和对策设计。")),
                        chapter(3, "第三章", List.of(
                                "研究条件已经具备公开年报、文献数据库和电子表格工具。可能困难包括数据口径变化、业务明细有限、年度数据分散和引用核对工作量较大。解决措施是统一披露主体与会计口径，建立数据来源核对表，并通过趋势和结构指标开展审慎分析。")),
                        chapter(4, "第四章", List.of(
                                "研究进度安排分为资料收集、开题答辩、数据整理、指标分析、初稿撰写、中期修改和定稿归档等阶段。",
                                "预期成果是客观评价企业偿债能力，明确关键问题及其形成原因，并提出能够对应问题的改进建议，为企业经营决策提供参考。"))),
                List.of(),
                List.of(), "social_science");

        PptContentPlannerV2.PlannerResult planned = new PptContentSanitizerV1().sanitize(planner.plan(input));
        List<PptContentPlannerV2.CandidatePage> pages = planned.chapters().stream()
                .flatMap(chapter -> chapter.candidatePages().stream()).toList();
        List<String> requiredOpeningGrammar=List.of("国内外文献对比","四象限研究价值","研究逻辑时间轴","指标与进度路径");
        assertTrue(pages.stream().map(PptContentPlannerV2.CandidatePage::title).toList().containsAll(requiredOpeningGrammar),pages.toString());

        assertEquals("social_science", planned.majorType());
        assertTrue(pages.stream().map(PptContentPlannerV2.CandidatePage::title)
                .toList().containsAll(List.of(
                        "国内外文献对比", "四象限研究价值", "研究空白与核心问题",
                        "研究内容总体框架", "研究方法与指标体系", "研究逻辑时间轴",
                        "研究条件与困难对策", "指标与进度路径", "预期成果与应用价值")));
        PptContentPlannerV2.CandidatePage literature = pages.stream()
                .filter(page -> "国内外文献对比".equals(page.title())).findFirst().orElseThrow();
        assertEquals("COMPARISON", literature.contentType());
        assertEquals(2, literature.keyPoints().size(), literature+" filtered="+planned.filteredContents());
        assertTrue(literature.description().length() >= 30);
        assertTrue(literature.keyPoints().stream().mapToInt(String::length).sum()
                + literature.description().length() >= 80);
        assertEquals("ARCHITECTURE", pages.stream()
                .filter(page -> "研究内容总体框架".equals(page.title())).findFirst().orElseThrow().contentType());
        assertEquals("PROCESS", pages.stream()
                .filter(page -> "研究逻辑时间轴".equals(page.title())).findFirst().orElseThrow().contentType());
        assertEquals("METRICS", pages.stream()
                .filter(page -> "指标与进度路径".equals(page.title())).findFirst().orElseThrow().contentType());

        PptOutlinePlannerV1.OutlineResult outline = new PptOutlinePlannerV1().plan(
                new PptOutlinePlannerV1.OutlineRequest(pages, 12));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("title", "华润新能源偿债能力问题及提升对策研究");
        metadata.put("englishTitle", "Research on Solvency Problems and Improvement Countermeasures");
        metadata.put("major", "财务管理");
        metadata.put("presentationMode", "OPENING_PROPOSAL");
        metadata.put("majorType", "social_science");
        PptOutlineValidatorV1.ValidationResult validated = new PptOutlineValidatorV1().validate(
                new PptOutlineValidatorV1.ValidationRequest(metadata, outline));

        assertTrue(validated.valid(), validated.issues().toString());
        List<String> agenda = validated.slideTree().stream()
                .filter(page -> "AGENDA".equals(page.pageType()))
                .findFirst().orElseThrow().agendaItems().stream()
                .map(PptOutlineValidatorV1.AgendaItem::title).toList();
        assertEquals(List.of("研究背景与文献综述", "研究内容与研究方案", "研究条件与困难措施", "进度安排与预期目标"), agenda);
    }

    @Test
    void detectsBusinessAndHumanitiesWithoutChangingNursingOrComputerRouting() {
        assertEquals("social_science", PptPlanService.resolveMajorType("财务管理", "偿债能力研究", List.of()));
        assertEquals("social_science", PptPlanService.resolveMajorType("", "企业融资结构研究", List.of("年度报告", "财务指标")));
        assertEquals("nursing", PptPlanService.resolveMajorType("护理学", "患者营养管理", List.of()));
        assertEquals("computer", PptPlanService.resolveMajorType("软件工程", "管理系统设计", List.of("Spring Boot")));
    }

    private static PptContentPlannerV2.SourceChapter chapter(int number, String title, List<String> paragraphs) {
        return new PptContentPlannerV2.SourceChapter("chapter_" + number, title, new ArrayList<>(paragraphs));
    }

}
