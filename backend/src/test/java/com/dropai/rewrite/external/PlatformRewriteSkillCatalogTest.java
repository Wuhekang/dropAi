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
                .contains("profile-id: daya-full-narrative-rebuild-v9")
                .contains("每个输入 `id` 都必须实质改写")
                .contains("风险识别只用于选择基础重构")
                .contains("未命中标签", "允许不改")
                .contains("正文表格长说明")
                .contains("不得在一个正文段内输出回车、软换行或制表符")
                .contains("同一表中相邻说明格会被平台连起来判断")
                .contains("资料—指标—评分—结论")
                .contains("不得为了稀释比例或补齐结构添加与原句无关的内容")
                .contains("公式、变量、编号、输入数字和计算结果保持原样")
                .contains("工程概况与参数清单")
                .contains("高相似微改")
                .contains("重度疑似占比", "轻度疑似单列参考")
                .contains("第一/第二/第三", "每句不超过 20 个汉字")
                .contains("中文摘要、英文摘要、正文自然语言段落")
                .contains("正文表格中经应用筛出的长说明文字")
                .contains("执行模型为豆包")
                .contains("不是大雅官方规则")
                .contains("不得新增原文没有的数据、案例、引用、作者、年份、系统功能、测试结果或性能结论");
        assertThat(catalog.load(XuejiePlatform.DAYA))
                .doesNotContain("允许扩写", "扩写预算", "6000 个字符",
                        "没有分条和完整报告链的段落，不要为了统一风格而过度改写");
    }
}
