package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlatformRewriteSkillCatalogTest {
    private final PlatformRewriteSkillCatalog catalog = new PlatformRewriteSkillCatalog();

    @Test
    void loadsAndCachesTheCompleteDayaSkill() {
        String first = catalog.load(XuejiePlatform.DAYA);
        String second = catalog.load(XuejiePlatform.DAYA);

        assertThat(first)
                .startsWith("---\nname: platform-ai-")
                .contains("profile-id:")
                .contains("DropAI 自定义")
                .contains("## 共用硬约束")
                .contains("保留原意、事实、观点、条件、否定关系和因果关系")
                .contains("不得新增原文没有的数据、案例、引用、作者、年份、系统功能、测试结果或性能结论")
                .contains("[[DROP_AI_PROTECTED_数字]]")
                .contains("不承诺检测结果、通过率或具体百分比")
                .contains("只输出改写后的正文");
        assertThat(first).contains("大雅");
        assertThat(second).isSameAs(first);
    }

    @Test
    void rejectsNullInsteadOfFallingBackToTheDefaultDropAiSkill() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> catalog.load(null))
                .withMessage("仅支持大雅平台");
    }

    @Test
    void dayaProfileContainsTheSevenReportAndEnumerationRules() {
        assertThat(catalog.load(XuejiePlatform.DAYA))
                .contains("profile-id: daya-report-segment-rebuild-v2")
                .contains("重度疑似占比", "轻度疑似单列参考")
                .contains("第一/第二/第三", "每句不超过 20 个汉字")
                .contains("中文摘要、英文摘要和正文自然语言段落")
                .contains("执行模型为豆包")
                .contains("不是大雅官方规则")
                .contains("不得新增原文没有的数据、案例、引用、作者、年份、系统功能、测试结果或性能结论");
    }
}
