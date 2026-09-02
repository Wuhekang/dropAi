package com.dropai.rewrite.external;

import com.dropai.rewrite.service.writing.DoubaoWritingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformDoubaoRewriteGatewayTest {
    private DoubaoWritingService doubao;
    private PlatformDoubaoRewriteGateway gateway;

    @BeforeEach
    void setUp() {
        doubao = mock(DoubaoWritingService.class);
        when(doubao.configured()).thenReturn(true);
        gateway = new PlatformDoubaoRewriteGateway(
                doubao,
                new PlatformRewriteSkillCatalog(),
                new ObjectMapper());
    }

    @Test
    void dayaRunsAnExtraDoubaoReviewOnlyForEnumeratedSegments() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p8\",\"text\":\"首先核对台账，其次复查现场，最后记录结果。\"}]}",
                        "{\"segments\":[{\"id\":\"p8\",\"text\":\"台账逐项核对。现场随后复查。结果当天记录。\"}]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment(
                        "p8", "第一，核对台账。第二，复查现场。第三，记录结果。", "4.2 风险控制"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p8", "台账逐项核对。现场随后复查。结果当天记录。"));

        ArgumentCaptor<String> systems = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(2)).complete(systems.capture(), users.capture(), anyInt());
        assertThat(systems.getAllValues().get(0)).doesNotContain("PHASE=DAYA_TARGETED_RECHECK");
        assertThat(systems.getAllValues().get(1))
                .contains("PHASE=DAYA_TARGETED_RECHECK", "从原稿事实重新组织");
        assertThat(users.getAllValues().get(0))
                .contains("\"context\":\"4.2 风险控制\"")
                .contains("\"allowExpansion\":true")
                .contains("\"text\":\"第一，核对台账。第二，复查现场。第三，记录结果。\"");
        assertThat(users.getAllValues().get(1))
                .contains("\"context\":\"4.2 风险控制\"")
                .contains("\"rule\":\"enumeration\"")
                .contains("\"allowExpansion\":true")
                .contains("\"original\":\"第一，核对台账。第二，复查现场。第三，记录结果。\"")
                .contains("\"draft\":\"首先核对台账，其次复查现场，最后记录结果。\"")
                .contains("\"reasons\":\"RESIDUAL_SEQUENCE\"");
    }

    @Test
    void dayaRechecksOrderingWordsIntroducedIntoAnOrdinaryParagraphWithoutShortSentenceRule() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p3\",\"text\":\"先由监理核对，再经建设单位复查，最后报主管部门。\"}]}",
                        "{\"segments\":[{\"id\":\"p3\",\"text\":\"监理核对资料，建设单位复查费用，主管部门按权限审批。\"}]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment(
                        "p3", "监理核对资料，建设单位复查费用，主管部门按权限审批。", "第三章 管理措施"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p3", "监理核对资料，建设单位复查费用，主管部门按权限审批。"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(2)).complete(system.capture(), user.capture(), anyInt());
        assertThat(system.getAllValues().get(1))
                .contains("PHASE=DAYA_TARGETED_RECHECK");
        assertThat(user.getAllValues().get(1))
                .contains("\"rule\":\"targeted_rebuild\"", "RESIDUAL_SEQUENCE", "保持自然长短变化");
    }

    @Test
    void dayaDoesNotKeepAnIntroducedOrderingChainWhenReviewFails() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p3\",\"text\":\"首先核对资料，然后复查费用，最后办理审批。\"}]}",
                        "{\"segments\":[]}");
        String original = "监理核对资料，建设单位复查费用，主管部门按权限审批。";

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment("p3", original)),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p3", original));
    }

    @Test
    void dayaFallsBackToAValidDraftWhenReviewDropsAProtectedToken() {
        String token = "[[DROP_AI_PROTECTED_7]]";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p6\",\"text\":\"台账已经核对。费用" + token
                                + "已经复查。结果当天记录。\"}]}",
                        "{\"segments\":[{\"id\":\"p6\",\"text\":\"台账核对完成。费用已经复查。结果当天记录。\"}]}");
        String original = "第一，核对台账。第二，复查费用" + token + "。第三，记录结果。";
        String draft = "台账已经核对。费用" + token + "已经复查。结果当天记录。";

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment("p6", original)),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p6", draft));
    }

    @Test
    void dayaFallsBackToTheCompleteFirstPassWhenEnumerationReviewFails() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":["
                                + "{\"id\":\"p8\",\"text\":\"台账已经核对。现场已经复查。结果已经记录。\"},"
                                + "{\"id\":\"p9\",\"text\":\"普通段落完成首轮改写。\"}]}",
                        "{\"segments\":[]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment(
                        "p8", "第一，核对台账。第二，复查现场。第三，记录结果。", "4.2 风险控制"),
                new PlatformDoubaoRewriteGateway.Segment(
                        "p9", "普通段落保持既有事实并完成自然表达调整。", "4.2 风险控制"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(
                        Map.entry("p8", "台账已经核对。现场已经复查。结果已经记录。"),
                        Map.entry("p9", "普通段落完成首轮改写。"));
        verify(doubao, times(2)).complete(anyString(), anyString(), anyInt());
    }

    @Test
    void ordinaryDayaParagraphUsesOnePassAndCarriesItsSectionContext() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"segments\":[{\"id\":\"p1\",\"text\":\"大雅单阶段结果。\"}]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment("p1", "大雅原始正文保持既有事实。", "第二章 需求分析"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p1", "大雅单阶段结果。"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(1)).complete(system.capture(), user.capture(), anyInt());
        assertThat(system.getValue())
                .contains("profile-id: daya-report-segment-rebuild-v7")
                .contains("不得套用普通降 AI 的轻改逻辑")
                .contains("只有 true 才能围绕原段已有事实适当补写")
                .doesNotContain("PHASE=DAYA_TARGETED_RECHECK");
        assertThat(user.getValue())
                .contains("\"id\":\"p1\"", "\"context\":\"第二章 需求分析\"",
                        "\"text\":\"大雅原始正文保持既有事实。\"", "\"allowExpansion\":false")
                .doesNotContain("\"original\"", "\"draft\"");
    }

    @Test
    void dayaDoubleModeAlsoUsesTheIndependentCompressionProfile() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"segments\":[{\"id\":\"p2\",\"text\":\"现场记录可直接说明项目情况。\"}]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment(
                        "p2", "该段说明项目现场已经核实的数据和限制条件。", "第二章 项目概况"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.DOUBLE))
                .containsExactly(Map.entry("p2", "现场记录可直接说明项目情况。"));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(1)).complete(system.capture(), anyString(), anyInt());
        assertThat(system.getValue())
                .contains("profile-id: daya-report-segment-rebuild-v7")
                .contains("当前为大雅独立双降模式")
                .contains("不得套用普通降重或普通降 AI 的轻改逻辑")
                .contains("允许明显压缩、整段重组并删除重复解释");
    }

    @Test
    void dayaRechecksOnlyTheHighSimilarityOrdinarySegmentAndUsesTheSaferDraft() {
        String original = "县域水利部门依托现场台账建立了造价审核流程，工作人员结合施工图纸、验收记录和签证资料核对工程量，确认结果后保存全部复核依据。";
        String punctuationOnly = "县域水利部门依托现场台账建立了造价审核流程。工作人员结合施工图纸、验收记录和签证资料核对工程量。确认结果后保存全部复核依据。";
        String rebuilt = "复核依据与台账一并归档。项目负责人回到施工图和验收记录核实工程量，现场签证只用于确认施工事实。县域水利部门据此完成造价审核。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":["
                                + "{\"id\":\"p10\",\"text\":\"" + punctuationOnly + "\"},"
                                + "{\"id\":\"p11\",\"text\":\"现场资料由项目负责人核验，相关记录统一归档。\"}]}",
                        "{\"segments\":[{\"id\":\"p10\",\"text\":\"" + rebuilt + "\"}]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment("p10", original, "3.2 施工管理"),
                new PlatformDoubaoRewriteGateway.Segment(
                        "p11", "该段说明项目现场资料的核验过程以及已经形成的归档结果。", "3.2 施工管理"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(
                        Map.entry("p10", rebuilt),
                        Map.entry("p11", "现场资料由项目负责人核验，相关记录统一归档。"));

        ArgumentCaptor<String> systems = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(2)).complete(systems.capture(), users.capture(), anyInt());
        assertThat(systems.getAllValues().get(1)).contains("PHASE=DAYA_TARGETED_RECHECK");
        assertThat(users.getAllValues().get(0))
                .contains("\"context\":\"3.2 施工管理\"")
                .contains("\"id\":\"p10\"", "\"id\":\"p11\"");
        assertThat(users.getAllValues().get(1))
                .contains("\"id\":\"p10\"", "HIGH_SIMILARITY", "FORMULAIC_RESEARCH_CHAIN")
                .doesNotContain("\"id\":\"p11\"");
    }

    @Test
    void dayaUsesAReviewThatPassesFinalGateWhenTheFirstDraftDoesNot() {
        String original = "县域水利部门依托现场台账建立了造价审核流程，工作人员结合施工图纸、验收记录和签证资料核对工程量，确认结果后保存全部复核依据。";
        String firstDraft = "县域水利部门依托现场台账建立了造价审核流程。工作人员结合施工图纸、验收记录和签证资料核对工程量。确认结果后保存全部复核依据。";
        String secondDraft = "县域水利部门借助现场台账建立起造价审核流程。工作人员结合施工图纸、验收记录和签证资料复核工程量。确认结论后保存全部复核依据。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p12\",\"text\":\"" + firstDraft + "\"}]}",
                        "{\"segments\":[{\"id\":\"p12\",\"text\":\"" + secondDraft + "\"}]}");

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment(
                        "p12", original, "4.1 造价审核")),
                XuejiePlatform.DAYA,
                XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p12", secondDraft));
        verify(doubao, times(2)).complete(anyString(), anyString(), anyInt());
    }

    @Test
    void dayaUsesContextualAbstractRiskForTheTargetedReview() {
        String original = "学校改扩建工程留下了施工记录、现场签证和进度资料。"
                + "项目使用AHP核对风险轻重，管理漏洞主要出现在签证和进度环节，原稿也给出了相应整改意见。";
        String firstDraft = "本文以学校改扩建工程为对象，采用AHP和模糊综合评价分析施工风险。"
                + "结果显示现场签证和进度管理仍有不足，据此提出整改建议，为同类项目提供参考。";
        String reviewed = "现场签证和进度记录暴露出施工管理漏洞，AHP只用于核对风险轻重。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p15\",\"text\":\"" + firstDraft + "\"}]}",
                        "{\"segments\":[{\"id\":\"p15\",\"text\":\"" + reviewed + "\"}]}");

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment(
                        "p15", original, "中文摘要")),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p15", reviewed));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(2)).complete(anyString(), user.capture(), anyInt());
        assertThat(user.getAllValues().get(1))
                .contains("ABSTRACT_MODULE_CHAIN")
                .contains("背景—对象—方法—结果—建议—价值");
    }

    @Test
    void dayaRejectsLineBrokenDraftAndKeepsTheReviewedParagraphOnOneLine() {
        String original = "第一，核对台账。第二，复查现场。第三，记录结果。";
        String lineBroken = "台账已经核对。\n现场已经复查。\n结果已经记录。";
        String reviewed = "现场记录汇总了台账核对结果。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p16\",\"text\":\"台账已经核对。\\n现场已经复查。\\n结果已经记录。\"}]}",
                        "{\"segments\":[{\"id\":\"p16\",\"text\":\"" + reviewed + "\"}]}");

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment("p16", original)),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p16", reviewed))
                .doesNotContainValue(lineBroken);
    }

    @Test
    void dayaAcceptsAReviewThatCompressesAnEnumerationBelowSeventyPercent() {
        String original = "第一，项目责任写入台账并长期核验。第二，现场资料由专人逐项复查。第三，处理结果当天记录并保存。";
        String firstDraft = "首先把项目责任写入台账。其次由专人复查现场资料。最后记录并保存处理结果。";
        String compressed = "责任、资料和结果合在现场记录中。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p14\",\"text\":\"" + firstDraft + "\"}]}",
                        "{\"segments\":[{\"id\":\"p14\",\"text\":\"" + compressed + "\"}]}");

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment("p14", original)),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p14", compressed));
        assertThat(compressed.length()).isLessThan((int) Math.floor(original.length() * 0.70));
    }

    @Test
    void dayaAllowsOnlyRiskEligibleParagraphsToGrowWithoutAPerParagraphCap() {
        String original = "第一，核对台账。第二，复查现场。第三，记录结果。";
        String firstDraft = "首先核对台账，其次复查现场，最后记录结果。";
        String expanded = "项目负责人当天核对台账。原有登记内容继续沿用。现场资料交给监理复查。"
                + "复查只确认已有记录。处理结果写回项目台账。相关内容仍以原件为准。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p18\",\"text\":\"" + firstDraft + "\"}]}",
                        "{\"segments\":[{\"id\":\"p18\",\"text\":\"" + expanded + "\"}]}");

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment(
                        "p18", original, "4.2 风险控制")),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p18", expanded));
        assertThat(DayaRewriteQualityRules.comparableLength(expanded))
                .isGreaterThan(DayaRewriteQualityRules.comparableLength(original) * 2);

        ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(2)).complete(anyString(), users.capture(), anyInt());
        assertThat(users.getAllValues().get(0)).contains("\"allowExpansion\":true");
        assertThat(users.getAllValues().get(1))
                .contains("\"allowExpansion\":true")
                .contains("不得新增事实、数据、案例或结论");
    }

    @Test
    void dayaRejectsGrowthForAnOrdinaryParagraphAndRequestsANonGrowingReview() {
        String original = "现场资料由负责人核对，结果记入台账。";
        String firstDraft = "现场资料由负责人核对，所有结果均记入项目台账并交给相关人员保存。";
        String reviewed = "负责人核对现场资料后，将全部结果写入项目台账，并把现有记录交给相关人员统一保存。";
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":[{\"id\":\"p19\",\"text\":\"" + firstDraft + "\"}]}",
                        "{\"segments\":[{\"id\":\"p19\",\"text\":\"" + reviewed + "\"}]}");

        assertThat(gateway.rewriteBatch(
                List.of(new PlatformDoubaoRewriteGateway.Segment(
                        "p19", original, "第二章 项目概况")),
                XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(Map.entry("p19", original));

        ArgumentCaptor<String> users = ArgumentCaptor.forClass(String.class);
        verify(doubao, times(2)).complete(anyString(), users.capture(), anyInt());
        assertThat(users.getAllValues().get(0)).contains("\"allowExpansion\":false");
        assertThat(users.getAllValues().get(1))
                .contains("\"allowExpansion\":false")
                .contains("DISALLOWED_EXPANSION", "不得增长");
    }

    @Test
    void mapsStrictJsonResponseById() {
        when(doubao.complete(anyString(), anyString(), anyInt())).thenReturn("""
                {"segments":[
                  {"id":"p4","text":"第四段已经改写。"},
                  {"id":"p9","text":"第九段已经改写。"}
                ]}
                """);
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment("p4", "第四段原始正文。"),
                new PlatformDoubaoRewriteGateway.Segment("p9", "第九段原始正文。"));

        Map<String, String> rewritten = gateway.rewriteBatch(
                segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE);

        assertThat(rewritten).containsExactly(
                Map.entry("p4", "第四段已经改写。"),
                Map.entry("p9", "第九段已经改写。"));
    }

    @Test
    void rejectsResponseWithMissingId() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn("{\"segments\":[{\"id\":\"p4\",\"text\":\"第四段已经改写。\"}]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = twoSegments();

        assertThatIllegalStateException()
                .isThrownBy(() -> gateway.rewriteBatch(
                        segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .withMessage("豆包批处理结果的段落 id 或顺序不完整");
    }

    @Test
    void rejectsResponseWithDuplicateId() {
        when(doubao.complete(anyString(), anyString(), anyInt())).thenReturn("""
                {"segments":[
                  {"id":"p4","text":"第四段改写一。"},
                  {"id":"p4","text":"第四段改写二。"}
                ]}
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> gateway.rewriteBatch(
                        twoSegments(), XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .withMessage("豆包批处理结果包含空值或重复 id");
    }

    private List<PlatformDoubaoRewriteGateway.Segment> twoSegments() {
        List<PlatformDoubaoRewriteGateway.Segment> segments = new ArrayList<>();
        segments.add(new PlatformDoubaoRewriteGateway.Segment("p4", "第四段原始正文。"));
        segments.add(new PlatformDoubaoRewriteGateway.Segment("p9", "第九段原始正文。"));
        return segments;
    }
}
