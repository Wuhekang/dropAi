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
    void rejectsRewrittenTextWhenEvidencePlaceholdersChangeOrder() {
        PlatformDocumentTextProtector.ProtectedText protectedText = protector.protect(
                "项目投资100万元，偏差为5%，依据见[8]。",
                new AtomicInteger());
        var tokens = protectedText.segments().keySet().stream().toList();
        String reordered = protectedText.text()
                .replace(tokens.get(0), "[[TEMP_TOKEN]]")
                .replace(tokens.get(1), tokens.get(0))
                .replace("[[TEMP_TOKEN]]", tokens.get(1));

        assertThatIllegalStateException()
                .isThrownBy(() -> protectedText.validateAndRestore(reordered))
                .withMessage("平台 Skill 调换了结构占位符顺序");
    }

    @Test
    void protectsNumbersThatTouchChineseCharacters() {
        String source = "项目于2025年完成投资100万元，允许偏差为5%。";

        PlatformDocumentTextProtector.ProtectedText protectedText =
                protector.protect(source, new AtomicInteger());

        assertThat(protectedText.segments().values()).containsExactly("2025", "100", "5%");
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void treatsRunStylePlaceholderAsOneProtectedToken() {
        String source = "正文[[DROP_STYLE_PROTECTED_12]]继续";
        PlatformDocumentTextProtector.ProtectedText protectedText = protector.protect(
                source, new AtomicInteger());

        assertThat(protectedText.segments()).hasSize(1);
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void dayaEnglishProseLeavesOrdinaryWordsVisibleButProtectsEvidence() {
        String source = "This study examines whole-process cost control with LCC and BIM; "
                + "the error is 12.5% [12].";

        PlatformDocumentTextProtector.ProtectedText protectedText =
                protector.protectDayaEnglishProse(source, new AtomicInteger());

        assertThat(protectedText.text())
                .contains("This study examines whole-process cost control with ")
                .contains("the error is ");
        assertThat(protectedText.segments().values())
                .containsExactly("LCC", "BIM", "12.5%", "[12]")
                .doesNotContain("This", "study", "whole-process", "cost", "control");
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void dayaEnglishProseProtectsTechnicalIdentifiersAndCodesExactly() {
        String source = "The @RestController calls UserService.findById(1001) for GB/T, "
                + "ISO9001, BIM5D and v2.1 via https://example.com/api while CamelCase remains.";

        PlatformDocumentTextProtector.ProtectedText protectedText =
                protector.protectDayaEnglishProse(source, new AtomicInteger(20));

        assertThat(protectedText.segments().values()).containsExactly(
                "@RestController",
                "UserService.findById(1001)",
                "GB/T",
                "ISO9001",
                "BIM5D",
                "v2.1",
                "https://example.com/api",
                "CamelCase");
        assertThat(protectedText.text()).contains("The ", " calls ", " for ", " and ", " via ");
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
    }

    @Test
    void dayaEnglishProseProtectsNumberWordsAndStatisticalEvidence() {
        String source = "The work is divided into five practical phases; p<0.05, n=30, and R²=0.81.";

        PlatformDocumentTextProtector.ProtectedText protectedText =
                protector.protectDayaEnglishProse(source, new AtomicInteger());

        assertThat(protectedText.segments().values())
                .containsExactly("five", "p<0.05", "n=30", "R²=0.81");
        assertThat(protectedText.text()).contains("The work is divided into ", " practical phases");
        assertThat(protectedText.validateAndRestore(protectedText.text())).isEqualTo(source);
    }
}
