package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PlatformDocumentTextProtectorTest {
    private final PlatformDocumentTextProtector protector = new PlatformDocumentTextProtector();

    @Test
    void restoresNumbersCitationsUrlsAndTechnicalNamesExactly() {
        String source = "@RestController 调用 UserService.findById(1001)，响应耗时 25ms，"
                + "误差为 3.14%，依据见[12]，接口为 https://example.com/api/v1。";

        PlatformDocumentTextProtector.ProtectedText protectedText =
                protector.protect(source, new AtomicInteger());

        assertThat(protectedText.segments()).isNotEmpty();
        assertThat(protectedText.text()).contains("[[DROP_AI_PROTECTED_");
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
        assertThat(protectedText.validateAndRestore(protectedText.text()))
                .contains("@RestController")
                .contains("UserService.findById(1001)")
                .contains("25ms")
                .contains("3.14%")
                .contains("[12]")
                .contains("https://example.com/api/v1");
    }

    @Test
    void rejectsRewrittenTextWhenAnyPlaceholderIsMissing() {
        PlatformDocumentTextProtector.ProtectedText protectedText = protector.protect(
                "系统通过 HealthService.findById(42) 获取记录，依据见[8]。",
                new AtomicInteger());
        String firstToken = protectedText.segments().keySet().iterator().next();
        String missingOne = protectedText.text().replace(firstToken, "");

        assertThatIllegalStateException()
                .isThrownBy(() -> protectedText.validateAndRestore(missingOne))
                .withMessage("平台 Skill 未完整保留结构占位符");
    }

    @Test
    void treatsRunStylePlaceholderAsOneProtectedToken() {
        String source = "正文[[DROP_STYLE_PROTECTED_12]]继续";
        PlatformDocumentTextProtector.ProtectedText protectedText = protector.protect(
                source, new AtomicInteger());

        assertThat(protectedText.segments()).hasSize(1);
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
    }
}
