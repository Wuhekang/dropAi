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
        assertThat(systems.getAllValues().get(0)).doesNotContain("PHASE=DAYA_ENUMERATION_RECHECK");
        assertThat(systems.getAllValues().get(1))
                .contains("PHASE=DAYA_ENUMERATION_RECHECK", "每句 20 个汉字以内");
        assertThat(users.getAllValues().get(1))
                .contains("\"context\":\"4.2 风险控制\"")
                .contains("\"original\":\"第一，核对台账。第二，复查现场。第三，记录结果。\"")
                .contains("\"draft\":\"首先核对台账，其次复查现场，最后记录结果。\"");
    }

    @Test
    void dayaFallsBackToTheCompleteFirstPassWhenEnumerationReviewFails() {
        when(doubao.complete(anyString(), anyString(), anyInt()))
                .thenReturn(
                        "{\"segments\":["
                                + "{\"id\":\"p8\",\"text\":\"台账已经核对，现场已经复查，结果已经记录。\"},"
                                + "{\"id\":\"p9\",\"text\":\"普通段落完成首轮改写。\"}]}",
                        "{\"segments\":[]}");
        List<PlatformDoubaoRewriteGateway.Segment> segments = List.of(
                new PlatformDoubaoRewriteGateway.Segment(
                        "p8", "第一，核对台账。第二，复查现场。第三，记录结果。", "4.2 风险控制"),
                new PlatformDoubaoRewriteGateway.Segment(
                        "p9", "普通段落保持既有事实并完成自然表达调整。", "4.2 风险控制"));

        assertThat(gateway.rewriteBatch(segments, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE))
                .containsExactly(
                        Map.entry("p8", "台账已经核对，现场已经复查，结果已经记录。"),
                        Map.entry("p9", "普通段落完成首轮改写。"));
        verify(doubao, times(2)).complete(anyString(), anyString(), anyInt());
    }

    @Test
    void ordinaryDayaParagraphUsesOnePassAndKeepsTheSimplePayload() {
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
                .contains("profile-id: daya-report-segment-rebuild-v2")
                .doesNotContain("PHASE=DAYA_ENUMERATION_RECHECK");
        assertThat(user.getValue())
                .contains("\"id\":\"p1\"", "\"text\":\"大雅原始正文保持既有事实。\"")
                .doesNotContain("\"context\"", "\"original\"", "\"draft\"");
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
