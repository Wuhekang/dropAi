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
 * Golden style checks for 降重狗 ordinary / dedicated 降AI samples.
 * CNKI 148900, Gezida 148898 and Turnitin stay in their own fixtures.
 * Competitor meaning drift is rejected. A fact-preserving paraphrase still passes the
 * fact lock; prompts now require a structural rewrite instead of that rhythm.
 */
class OrdinaryPlatformSampleFixtureTest {

    static final String INPUT = "近年来，随着信息技术的快速发展，数字化转型已经成为各行各业提升核心竞争力的重要路径。"
            + "本文旨在通过构建一套系统化的分析框架，深入研究相关技术在实际场景中的应用效果，从而为后续优化提供有益参考。"
            + "研究表明，通过合理配置资源并完善协同机制，可以有效提升整体运行效率，并在一定程度上降低运营成本。"
            + "与此同时，还需要进一步关注数据安全与隐私保护问题，以确保技术方案具备可持续的实践价值。";

    static final String WEIPU_COMPETITOR = "近几年来，由于信息技术的发展迅速，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要通过对有关技术在实际应用中所产生的效果进行系统的分析来达到对之加以改进的目的。"
            + "从实际研究中得知，在科学分配资源的基础上建立起良好的合作机制，可以使整个系统的运转更加高效，同时也可以减少运营成本。"
            + "另外也要注意数据安全和隐私保护的问题，保证技术方案有持续使用的必要性。";

    static final String WEIPU_ACCEPTABLE = WEIPU_COMPETITOR.replace("持续使用的必要性", "可持续的实践价值");

    static final String ZHUQUE_COMPETITOR = "近些年来，由于信息技术的迅速发展，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要是为了建立一个完整的分析体系，对相关的技术在实际的应用中产生的效果进行探究，为之后的改进提供一定的参考依据。"
            + "从已有研究可知，在合理安排资源、改善协作关系之后，可以提高企业的综合运作水平，并且也能够一定程度地削减企业的开支。"
            + "另外还要继续注意数据安全和隐私保护的问题，保证技术方案有持久的应用价值。";

    static final String ZHUQUE_ACCEPTABLE = "近些年来，由于信息技术的迅速发展，数字化转型成了各个行业提高自身核心竞争力的一种途径。"
            + "本文主要是为了建立一个分析体系，对相关技术在实际应用中的效果进行探究，为之后的改进提供参考。"
            + "从已有研究可知，在合理安排资源、改善协同机制之后，可以提高整体运行效率，并且也能够在一定程度上降低运营成本。"
            + "另外还要注意数据安全和隐私保护的问题，保证技术方案有持久的应用价值。";

    static final String PAPERYY_COMPETITOR = "近几年来，由于信息技术的发展迅速，数字化转型已经成为了各个行业提高自身核心竞争力的一种方式。"
            + "本文主要从建立一个系统的分析框架入手，对有关技术在具体的应用中所产生的效果进行详细的分析，进而给之后的改进工作提供一定的借鉴意义。"
            + "经过研究发现，对资源进行合理的分配和建立完善的协同机制，可以使整个系统的运行效率得到提高，在某种程度上也能降低企业的运营成本。"
            + "另外还要对数据安全和隐私保护进行重视，保证技术方案有持续的可行性。";

    static final String WANFANG_COMPETITOR = "近些年来，因为信息技术飞速发展，数字化转型成了各个领域提高核心竞争力的主要途径。"
            + "本文试图建立一个系统的分析框架来探究相关技术在具体的环境中所起到的作用，并且给后面改进提供一些启示。"
            + "经过研究可知，在科学合理地安排资源的基础上以及构建起有效的协同机制后，可以大大提高系统的运行效率，并且还可以减少运营成本。"
            + "另外还要特别重视数据安全和隐私保护的问题，防止该技术方案失效。";

    static final String WANFANG_ACCEPTABLE = "近些年来，因为信息技术飞速发展，数字化转型成了各个领域提高核心竞争力的主要途径。"
            + "本文试图建立一个系统的分析框架来探究相关技术在具体环境中的作用，并且给后面的改进提供参考。"
            + "经过研究可知，在合理安排资源并且构建起协同机制后，可以提高系统的运行效率，并且还可以降低运营成本。"
            + "另外还要重视数据安全和隐私保护的问题，以保证技术方案具备可持续的实践价值。";

    static final String PAPERPASS_COMPETITOR = "近几年来，信息技术迅猛发展，数字化转型成为各个行业企业提高自身核心竞争力的行之有效的办法。"
            + "本文试图创建起一套系统的分析架构，对技术在真实运用里产生的效果展开探究，以此来给之后的改良给予一些参照。"
            + "经过合理调配资源、健全协同机制之后，可有效地提高整个运行的效率，在某种程度上也能削减运行的成本。"
            + "数据安全、隐私保护这些技术手段的运用到本项目的安全防护技术分析当中还存在着诸多不足之处，有待于探究。";

    static final String PAPERPASS_ACCEPTABLE = "近几年来，信息技术迅猛发展，数字化转型成为各个行业提高自身核心竞争力的有效办法。"
            + "本文试图建立一套系统的分析框架，对相关技术在真实运用中产生的效果展开探究，并给之后的改进提供参照。"
            + "经过合理调配资源、健全协同机制之后，可以提高整体运行效率，在一定程度上也能降低运营成本。"
            + "数据安全与隐私保护仍需要进一步关注，以确保技术方案具备可持续的实践价值。";

    @Test
    void sampleLengthsMatchTheAttachedOrders() {
        assertThat(INPUT).hasSize(186);
        assertThat(WEIPU_COMPETITOR).hasSize(179);
        assertThat(ZHUQUE_COMPETITOR).hasSize(194);
        assertThat(PAPERYY_COMPETITOR).hasSize(207);
        assertThat(WANFANG_COMPETITOR).hasSize(182);
        assertThat(PAPERPASS_COMPETITOR).hasSize(195);
    }

    @Test
    void weipuKeepsPracticalValueAndRejectsNecessityDrift() {
        assertThat(ordinaryIssues(INPUT, WEIPU_ACCEPTABLE, "weipu")).isEmpty();
        assertThat(WEIPU_ACCEPTABLE)
                .contains("近几年来", "本文主要", "从实际研究中得知", "实践价值")
                .doesNotContain("本文旨在", "研究表明", "持续使用的必要性");
        assertThat(ordinaryIssues(INPUT, WEIPU_COMPETITOR, "weipu"))
                .contains("meaning drift", "lost practical value")
                .doesNotContain("expanded", "sentence count", "formal opening");
        assertThat(ordinaryIssues(INPUT, INPUT, "weipu")).contains("formal opening");
        assertThat(ordinaryIssues(INPUT, INPUT + LONG_PAD, "weipu")).contains("template closing");
    }

    @Test
    void zhuqueKeepsEfficiencyAndCost() {
        assertThat(ordinaryIssues(INPUT, ZHUQUE_ACCEPTABLE, "zhuque")).isEmpty();
        assertThat(ZHUQUE_ACCEPTABLE)
                .contains("近些年来", "本文主要是为了", "从已有研究可知", "运行效率", "运营成本")
                .doesNotContain("综合运作水平", "开支");
        assertThat(ordinaryIssues(INPUT, ZHUQUE_COMPETITOR, "zhuque"))
                .contains("meaning drift", "lost efficiency", "lost cost")
                .doesNotContain("expanded", "formal opening", "sentence count");
    }

    @Test
    void paperYyAcceptsFeasibilityParaphraseAndRejectsANewGap() {
        assertThat(ordinaryIssues(INPUT, PAPERYY_COMPETITOR, "paperyy")).isEmpty();
        assertThat(PAPERYY_COMPETITOR)
                .contains("本文主要从", "经过研究发现", "持续的可行性", "运行效率", "运营成本")
                .doesNotContain("本文旨在", "研究表明");
        String drifted = PAPERYY_COMPETITOR.replace("保证技术方案有持续的可行性", "仍有不足之处，有待探究");
        assertThat(ordinaryIssues(INPUT, drifted, "paperyy")).contains("meaning drift", "lost practical value");
    }

    @Test
    void wanfangKeepsPracticalValueAndRejectsFailureDrift() {
        assertThat(ordinaryIssues(INPUT, WANFANG_ACCEPTABLE, "wanfang")).isEmpty();
        assertThat(WANFANG_ACCEPTABLE)
                .contains("因为", "本文试图", "并且", "运行效率", "运营成本", "实践价值")
                .doesNotContain("防止该技术方案失效", "失效");
        assertThat(ordinaryIssues(INPUT, WANFANG_COMPETITOR, "wanfang"))
                .contains("meaning drift", "lost practical value")
                .doesNotContain("expanded", "sentence count", "formal opening");
    }

    @Test
    void paperPassShakesStructureWithoutInventingAGap() {
        assertThat(ordinaryIssues(INPUT, PAPERPASS_ACCEPTABLE, "paperpass")).isEmpty();
        assertThat(PAPERPASS_ACCEPTABLE)
                .contains("本文试图", "运行效率", "运营成本", "数据安全", "隐私保护", "实践价值")
                .doesNotContain("本文旨在", "研究表明", "不足之处", "有待于探究");
        assertThat(ordinaryIssues(INPUT, PAPERPASS_COMPETITOR, "paperpass"))
                .contains("meaning drift", "lost practical value")
                .doesNotContain("expanded", "sentence count", "lost security");
    }

    @Test
    void ordinaryPromptsCarrySampleRulesAndDeepStaysOnTheExistingOverlay() throws Exception {
        DoubaoAiRewriteService service = service();

        assertThat(systemPrompt(service, "humanize@WEIPU"))
                .contains("name: humanize-zh-academic", "profile:weipu-ordinary-v2", "148908", "持续使用的必要性",
                        "重排信息入口", "拆句")
                .doesNotContain("profile:weipu-deep-v1", "不要整段换序", "四句都保留");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@WEIPU"))
                .contains("profile:weipu-deep-v1", "维普深度改写")
                .doesNotContain("148908", "profile:weipu-ordinary-v2");
        assertThat(userPrompt(service, "humanize@WEIPU"))
                .contains("重排信息入口", "拆句", "148908", "持续使用的必要性")
                .doesNotContain("不限定微量改写", "近义节奏", "不要整段换序");
        assertThat(userPrompt(service, "深度降低AI写作痕迹@WEIPU"))
                .contains("不限定微量改写", "维普深度改写")
                .doesNotContain("148908", "这是普通模式");

        assertThat(systemPrompt(service, "humanize@ZHUQUE"))
                .contains("profile:zhuque-ordinary-v2", "148907", "运行效率", "运营成本", "综合运作水平", "重排信息入口")
                .doesNotContain("profile:zhuque-deep-v1", "不要增删句子");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@ZHUQUE"))
                .contains("profile:zhuque-deep-v1", "朱雀深度改写")
                .doesNotContain("148907");
        assertThat(systemPrompt(service, "humanize@PAPERYY"))
                .contains("profile:paperyy-ordinary-v2", "148904", "本文主要从", "经过研究发现", "持续的可行性", "拆句")
                .doesNotContain("profile:paperyy-deep-v1");
        assertThat(systemPrompt(service, "深度降低AI写作痕迹@PAPERYY"))
                .contains("profile:paperyy-deep-v1", "PaperYY 深度改写")
                .doesNotContain("148904");

        assertThat(userPrompt(service, "humanize@GENERAL"))
                .contains("重排信息入口", "拆句", "70%", "150%")
                .doesNotContain("近义节奏", "不要整段换序");
        assertThat(userPrompt(service, "humanize-shared@WEIPU"))
                .contains("不限定微量改写")
                .doesNotContain("148908", "profile:");
        assertThat(userPrompt(service, "rewrite@WEIPU"))
                .contains("维普")
                .doesNotContain("148908", "近义节奏", "profile:");
    }

    @Test
    void wanfangAndPaperPassSkillsCarryTheSampleWithoutChangingRewriteOrDaya() throws Exception {
        String wanfang = skill("humanize-platform-wanfang");
        assertThat(wanfang)
                .contains("148902", "186", "182", "可以略短", "重排信息入口", "因为", "并且", "另外",
                        "防止该技术方案失效")
                .doesNotContain("允许整段大幅重构表达", "不要求原句结构可还原");
        String paperPass = skill("humanize-platform-paperpass");
        assertThat(paperPass)
                .contains("148903", "186", "195", "允许整段大幅重构表达", "不要求原句结构可还原",
                        "85%—130%", "软参考", "不足之处", "有待于探究", "可持续的实践价值")
                .doesNotContain("name: humanize-zh-academic");

        DoubaoAiRewriteService service = service();
        assertThat(systemPrompt(service, "humanize@WANFANG"))
                .isEqualTo(systemPrompt(service, "深度降低AI写作痕迹@WANFANG"))
                .contains("name: humanize-platform-wanfang", "profile:wanfang-ai-dedicated-v2", "148902");
        assertThat(userPrompt(service, "humanize@WANFANG"))
                .isEqualTo(userPrompt(service, "深度降低AI写作痕迹@WANFANG"))
                .contains("专用 Skill", "148902", "防止该技术方案失效")
                .doesNotContain("不限定微量改写");
        assertThat(systemPrompt(service, "humanize@PAPERPASS"))
                .isEqualTo(systemPrompt(service, "深度降低AI写作痕迹@PAPERPASS"))
                .contains("name: humanize-platform-paperpass", "148903", "不足之处");
        assertThat(userPrompt(service, "humanize@PAPERPASS"))
                .isEqualTo(userPrompt(service, "深度降低AI写作痕迹@PAPERPASS"))
                .contains("专用 Skill", "148903", "有待于探究");
        assertThat(systemPrompt(service, "rewrite@WANFANG")).doesNotContain("148902", "防止该技术方案失效");
        assertThat(systemPrompt(service, "rewrite@PAPERPASS")).doesNotContain("148903", "有待于探究");
        assertThat(systemPrompt(service, "humanize@DAYA"))
                .doesNotContain("148902", "148903", "name: humanize-platform-wanfang", "name: humanize-platform-paperpass");
    }

    private static final String LONG_PAD = "综上所述，这一路径具有重要意义，还需要继续补充背景、案例、数据和实施过程。";

    static List<String> ordinaryIssues(String input, String output, String platform) {
        List<String> issues = new ArrayList<>();
        if (output.length() > Math.ceil(input.length() * 1.55d)) {
            issues.add("expanded");
        }
        if (output.length() < Math.floor(input.length() * 0.70d)) {
            issues.add("too short");
        }
        if (output.contains("本文旨在") || output.contains("研究表明")) {
            issues.add("formal opening");
        }
        if (output.contains("综上所述") || output.contains("具有重要意义")) {
            issues.add("template closing");
        }
        switch (platform) {
            case "weipu" -> {
                if (output.contains("持续使用的必要性")) {
                    issues.add("meaning drift");
                }
                if (!output.contains("实践价值") && !output.contains("应用价值")) {
                    issues.add("lost practical value");
                }
            }
            case "zhuque" -> {
                if (output.contains("综合运作水平") || output.contains("开支")) {
                    issues.add("meaning drift");
                }
                if (!output.contains("运行效率")) {
                    issues.add("lost efficiency");
                }
                if (!output.contains("运营成本")) {
                    issues.add("lost cost");
                }
            }
            case "paperyy" -> {
                if (output.contains("不足之处") || output.contains("有待探究")) {
                    issues.add("meaning drift");
                }
                if (!output.contains("可行性") && !output.contains("实践价值") && !output.contains("应用价值")) {
                    issues.add("lost practical value");
                }
            }
            case "wanfang" -> {
                if (output.contains("失效")) {
                    issues.add("meaning drift");
                }
                if (!output.contains("实践价值") && !output.contains("可持续")) {
                    issues.add("lost practical value");
                }
            }
            case "paperpass" -> {
                if (output.contains("不足之处") || output.contains("有待于") || output.contains("有待探究")) {
                    issues.add("meaning drift");
                }
                if (!output.contains("数据安全") || !output.contains("隐私保护")) {
                    issues.add("lost security");
                }
                if (!output.contains("实践价值") && !output.contains("可持续")) {
                    issues.add("lost practical value");
                }
            }
            default -> throw new IllegalArgumentException(platform);
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
        return (String) method.invoke(service, INPUT, rewriteType, 0, "");
    }
}
