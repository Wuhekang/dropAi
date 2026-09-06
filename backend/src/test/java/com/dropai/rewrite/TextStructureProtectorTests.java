package com.dropai.rewrite;

import com.dropai.rewrite.service.TextStructureProtector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextStructureProtectorTests {

    private final TextStructureProtector protector = new TextStructureProtector();

    @Test
    void protectsAndRestoresStructuredContent() {
        String source = """
                正文需要自然化润色。

                | 接口 | 方法 |
                |---|---|
                | /api/rewrite | POST |

                配置位于 `application.yml`，文档见 https://example.com/docs。
                [1] 张三. 学术写作研究[J]. 写作研究, 2025(2): 10-15.
                """;

        TextStructureProtector.ProtectedText protectedText = protector.protect(source);

        assertThat(protectedText.protectedCount()).isEqualTo(4);
        assertThat(protectedText.text()).contains("[[DROP_AI_PROTECTED_0]]");
        assertThat(protectedText.restore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void protectsInlineAbstractLabelsWhileLeavingTheirBodiesEditable() {
        String source = "摘要：本文围绕平台资料核对流程展开研究。\n"
                + "Abstract: This study examines the document review workflow.";

        TextStructureProtector.ProtectedText protectedText = protector.protect(source, true);

        assertThat(protectedText.protectedCount()).isEqualTo(2);
        assertThat(protectedText.text())
                .doesNotContain("摘要：", "Abstract:")
                .contains("本文围绕平台资料核对流程展开研究。")
                .contains("This study examines the document review workflow.");
        assertThat(protectedText.restore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void protectsNumericFactsAndInlineCitations() {
        String source = "2025年抽取了36份记录，合格率为92.5%，结果见文献[12]。";

        TextStructureProtector.ProtectedText protectedText = protector.protect(source, true);

        assertThat(protectedText.protectedCount()).isEqualTo(4);
        assertThat(protectedText.text())
                .doesNotContain("2025", "36", "92.5", "[12]");
        assertThat(protectedText.restore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void rejectsMissingDuplicatedOrReorderedProtectedContent() {
        TextStructureProtector.ProtectedText protectedText = protector.protect("2025年共36份记录", true);

        assertThatThrownBy(() -> protectedText.restore("[[DROP_AI_PROTECTED_1]]份记录"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少受保护内容");
        assertThatThrownBy(() -> protectedText.restore(
                "[[DROP_AI_PROTECTED_0]][[DROP_AI_PROTECTED_0]][[DROP_AI_PROTECTED_1]]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复了受保护内容");
        assertThatThrownBy(() -> protectedText.restore(
                "[[DROP_AI_PROTECTED_1]][[DROP_AI_PROTECTED_0]]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("改变了受保护内容占位符顺序");
    }

    @Test
    void defaultProtectionKeepsLegacyRewriteScope() {
        String source = "2025年抽取了36份记录，结果见文献[12]。";

        TextStructureProtector.ProtectedText protectedText = protector.protect(source);

        assertThat(protectedText.protectedCount()).isZero();
        assertThat(protectedText.text()).isEqualTo(source);
        assertThat(protectedText.restore("改写时可调整这些数字")).isEqualTo("改写时可调整这些数字");
    }
}
