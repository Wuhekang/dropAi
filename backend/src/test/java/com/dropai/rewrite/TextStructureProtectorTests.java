package com.dropai.rewrite;

import com.dropai.rewrite.service.TextStructureProtector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
