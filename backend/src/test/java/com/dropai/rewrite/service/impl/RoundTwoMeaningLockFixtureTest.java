package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.SkillPromptService;
import com.dropai.rewrite.service.ai.DoubaoModelRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Second-round meaning locks. Competitor drifts are rejected; a paraphrase that keeps the
 * source facts passes. Dedicated prompts stay the same for both intensities.
 */
class RoundTwoMeaningLockFixtureTest {

    static final String TURNITIN_INPUT = "This study examines how waiting time at a public library service desk "
            + "relates to perceived service efficiency. Over four weekday afternoons, staff recorded arrival time, "
            + "queue length, and the moment checkout was finished for walk-in visitors. A short exit card asked each "
            + "visitor to rate speed, clarity of signs, and whether the wait felt reasonable. Holds that required "
            + "items from closed storage were excluded. Mean waiting time was then compared across desk types. The "
            + "aim is to identify small staffing and signage changes that shorten the wait without reducing the "
            + "quality of help offered at the desk.";

    static final String TURNITIN_COMPETITOR = "This paper studies the effect of waiting time at the public library "
            + "service counter on perceived service efficiency. Over four weekday afternoons, the staff recorded the "
            + "arrival time, queue length and checkout completion time of walk-in visitors. A small-print exit card "
            + "has been given to all the visitors for convenience, speed of recognition and a sense of anticipation. "
            + "Holds that required items from closed storage were excluded. Mean waiting times for different desks "
            + "were then compared. The goal is to reduce the waiting time by making minor adjustments to staffing "
            + "and signage without lowering the quality of service at the counter.";

    static final String TURNITIN_ACCEPTABLE = "This study looks at how waiting time at a public library service desk "
            + "relates to perceived service efficiency. Over four weekday afternoons, staff recorded arrival time, "
            + "queue length, and the moment checkout was finished for walk-in visitors. A short exit card asked each "
            + "visitor to rate speed, clarity of signs, and whether the wait felt reasonable. Holds that required "
            + "items from closed storage were excluded. Mean waiting time was then compared across desk types. The "
            + "aim is to find small staffing and signage changes that shorten the wait without reducing the quality "
            + "of help offered at the desk.";

    static final String WANFANG_INPUT = "实验在25摄氏度、相对湿度约60%的暗室中完成。供试叶片按随机数字表分为对照组与处理组，"
            + "每组3个重复，每个重复12片。处理组按规定浓度浸泡20分钟后沥干，对照组只用蒸馏水做同样处理。样品平铺36小时，"
            + "每6小时由两名实验员独立记录萎蔫等级和质量，差异超过0.02克则重称并取均值。天平每次称量前归零。"
            + "分析采用重复测量方差分析，显著性水平设为0.05。";

    static final String WANFANG_COMPETITOR = "本文是在25℃、相对湿度约为60%的暗室内进行的。用随机数字表把供试叶片分成两组，"
            + "每组各有3个重复，每个重复里有12片。处理组用规定的浓度浸泡后沥干水分，对照组只是用蒸馏水处理。"
            + "样品平铺36小时，每隔6个小时两位实验人员分别记录萎蔫程度和质量情况，如果两组结果差值大于0.02克的话就重新称量并取平均值。"
            + "天平每次称量之前要先归零。用重复测量方差分析，置信度为0.05。";

    static final String WANFANG_ACCEPTABLE = "本文是在25℃、相对湿度约为60%的暗室内进行的。用随机数字表把供试叶片分成两组，"
            + "每组各有3个重复，每个重复里有12片。处理组用规定的浓度浸泡20分钟后沥干水分，对照组只用蒸馏水做同样处理。"
            + "样品平铺36小时，每隔6个小时两位实验人员分别记录萎蔫程度和质量，如果差值大于0.02克就重新称量并取平均值。"
            + "天平每次称量之前要先归零。分析采用重复测量方差分析，显著性水平设为0.05。";

    static final String PAPERPASS_INPUT = "本研究于2024年3月至5月在一所综合性医院的三个门诊科室做现场计时。按就诊日连续抽样，"
            + "共观察复诊患者186人次。调查员用统一计时表记录挂号、候诊、叫号和接诊四个节点，离窗后请患者用五点量表评价等候是否可接受、"
            + "指引是否清楚。两人独立录入并交叉核对。描述统计比较上午与下午的平均候诊时长，线性回归控制科室和是否预约。"
            + "正式观察中完整记录占比为91.5%。";

    static final String PAPERPASS_COMPETITOR = "2024年3月至5月期间，本文在综合医院三个门诊科目的现场进行计时。按照就诊日连续抽取，"
            + "一共观察到复诊的病人是186人。调查员使用统一的计时器记录挂号、候诊、叫号、接诊这四个节点的时间，离窗口之后让患者使用五点量表"
            + "评价等候是否可以接受、指引是否清晰。两人分别录入然后互相核对。描述统计法比较上午和下午的平均候诊时间，线性回归控制科室和是否预约。"
            + "在正式观察中，被记录的有91.5%。";

    static final String PAPERPASS_ACCEPTABLE = "2024年3月至5月，在一所综合性医院的三个门诊科室做了现场计时。按就诊日连续抽样，"
            + "共观察复诊患者186人次。调查员用统一计时表记录挂号、候诊、叫号和接诊四个节点，离窗后请患者用五点量表评价等候是否可接受、"
            + "指引是否清楚。两人独立录入并交叉核对。描述统计比较上午与下午的平均候诊时长，线性回归控制科室和是否预约。"
            + "正式观察中完整记录占比为91.5%。";

    @Test
    void turnitinKeepsThreeRatingsAndClosedStorageExclusion() {
        assertThat(turnitinIssues(TURNITIN_ACCEPTABLE)).isEmpty();
        assertThat(TURNITIN_ACCEPTABLE)
                .contains("speed", "clarity of signs", "reasonable", "closed storage", "Holds")
                .doesNotContain("small-print", "anticipation");
        assertThat(turnitinIssues(TURNITIN_COMPETITOR))
                .contains("invented card", "lost ratings")
                .doesNotContain("lost exclusion");
        String droppedHold = TURNITIN_ACCEPTABLE.replace(
                "Holds that required items from closed storage were excluded. ", "");
        assertThat(turnitinIssues(droppedHold)).contains("lost exclusion");
    }

    @Test
    void wanfangKeepsSoakDurationAndMethodNumbers() {
        assertThat(WANFANG_INPUT).contains("浸泡20分钟", "0.02克", "0.05", "60%", "36小时", "每6小时");
        assertThat(wanfangIssues(WANFANG_ACCEPTABLE)).isEmpty();
        assertThat(WANFANG_ACCEPTABLE).contains("浸泡20分钟", "显著性水平", "0.05", "0.02克", "60%", "12片");
        assertThat(wanfangIssues(WANFANG_COMPETITOR))
                .contains("dropped soak", "renamed significance")
                .doesNotContain("dropped numeric");
    }

    @Test
    void paperPassKeepsDepartmentWordingAndCompletenessRatio() {
        assertThat(PAPERPASS_INPUT).contains("门诊科室", "完整记录占比为91.5%");
        assertThat(paperPassIssues(PAPERPASS_ACCEPTABLE)).isEmpty();
        assertThat(PAPERPASS_ACCEPTABLE).contains("门诊科室", "完整记录占比为91.5%").doesNotContain("门诊科目");
        assertThat(paperPassIssues(PAPERPASS_COMPETITOR))
                .contains("department drift", "lost completeness")
                .doesNotContain("lost percent");
    }

    @Test
    void promptsCarryTheRoundTwoLocksWithoutTouchingRewriteOrDaya() throws Exception {
        DoubaoAiRewriteService service = service();

        assertThat(skill("humanize-platform-turnitin"))
                .contains("148946", "small-print", "sense of anticipation", "clarity of signs",
                        "whether the wait felt reasonable", "closed storage");
        assertThat(systemPrompt(service, "humanize@TURNITIN"))
                .isEqualTo(systemPrompt(service, "深度降低AI写作痕迹@TURNITIN"))
                .contains("148946", "small-print", "sense of anticipation", "closed storage");
        assertThat(userPrompt(service, "humanize@TURNITIN"))
                .contains("148946", "speed", "clarity of signs", "whether the wait felt reasonable",
                        "small-print", "sense of anticipation", "closed storage");
        assertThat(systemPrompt(service, "rewrite@TURNITIN")).doesNotContain("148946", "small-print");
        assertThat(systemPrompt(service, "humanize@DAYA")).doesNotContain("148946", "sense of anticipation");

        assertThat(skill("humanize-platform-wanfang"))
                .contains("148944", "浸泡20分钟", "0.02克", "显著性水平0.05")
                .doesNotContain("允许整段大幅重构表达");
        assertThat(systemPrompt(service, "humanize@WANFANG"))
                .isEqualTo(systemPrompt(service, "深度降低AI写作痕迹@WANFANG"))
                .contains("148944", "浸泡20分钟");
        assertThat(userPrompt(service, "humanize@WANFANG")).contains("148944", "浸泡20分钟", "0.02克");
        assertThat(systemPrompt(service, "rewrite@WANFANG")).doesNotContain("148944", "浸泡20分钟");

        assertThat(skill("humanize-platform-paperpass"))
                .contains("148945", "门诊科室", "门诊科目", "完整记录占比为91.5%", "被记录的有91.5%");
        assertThat(systemPrompt(service, "humanize@PAPERPASS"))
                .isEqualTo(systemPrompt(service, "深度降低AI写作痕迹@PAPERPASS"))
                .contains("148945", "门诊科室", "完整记录占比");
        assertThat(userPrompt(service, "humanize@PAPERPASS"))
                .contains("148945", "门诊科目", "被记录的有91.5%", "完整记录占比为91.5%");
        assertThat(systemPrompt(service, "rewrite@PAPERPASS")).doesNotContain("148945", "门诊科目");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@CNKI"))
                .doesNotContain("148944", "148945", "148946");
    }

    static List<String> turnitinIssues(String output) {
        List<String> issues = new ArrayList<>();
        String lowered = output.toLowerCase();
        if (lowered.contains("small-print") || lowered.contains("small print") || lowered.contains("anticipation")) {
            issues.add("invented card");
        }
        if (!lowered.contains("speed") || !lowered.contains("clarity") || !lowered.contains("signs")
                || !lowered.contains("reasonable")) {
            issues.add("lost ratings");
        }
        if (!lowered.contains("holds") || !lowered.contains("closed storage")) {
            issues.add("lost exclusion");
        }
        return issues;
    }

    static List<String> wanfangIssues(String output) {
        List<String> issues = new ArrayList<>();
        if (!output.contains("20分钟")) {
            issues.add("dropped soak");
        }
        if (!output.contains("25") || !output.contains("60%") || !output.contains("3个重复")
                || !output.contains("12片") || !output.contains("36小时") || !output.contains("0.02")
                || !output.contains("0.05")
                || (!output.contains("6小时") && !output.contains("6个小时"))) {
            issues.add("dropped numeric");
        }
        if (output.contains("置信度") && !output.contains("显著")) {
            issues.add("renamed significance");
        }
        return issues;
    }

    static List<String> paperPassIssues(String output) {
        List<String> issues = new ArrayList<>();
        if (output.contains("门诊科目") || !output.contains("门诊科室")) {
            issues.add("department drift");
        }
        if (!output.contains("完整") || (!output.contains("占比") && !output.contains("比例"))) {
            issues.add("lost completeness");
        }
        if (!output.contains("91.5%")) {
            issues.add("lost percent");
        }
        return issues;
    }

    private static String skill(String name) throws Exception {
        return new ClassPathResource("skills/" + name + "/SKILL.md").getContentAsString(StandardCharsets.UTF_8);
    }

    private static DoubaoAiRewriteService service() throws Exception {
        SkillPromptService skills = mock(SkillPromptService.class);
        when(skills.loadSkill(anyString())).thenAnswer(invocation -> skill(invocation.getArgument(0)));
        DoubaoProperties properties = new DoubaoProperties();
        return new DoubaoAiRewriteService(
                properties, new ObjectMapper(), RestClient.builder(), skills, new DoubaoModelRouter(properties));
    }

    private static String systemPrompt(DoubaoAiRewriteService service, String rewriteType) throws Exception {
        var method = DoubaoAiRewriteService.class.getDeclaredMethod("systemPrompt", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, rewriteType);
    }

    private static String userPrompt(DoubaoAiRewriteService service, String rewriteType) throws Exception {
        var method = DoubaoAiRewriteService.class.getDeclaredMethod(
                "userPrompt", String.class, String.class, int.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, "原文。", rewriteType, 0, "");
    }
}
