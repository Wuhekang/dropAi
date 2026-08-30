package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicTextMetricsServiceTest {
    private final ResolvedFontProfile profile = MeasurementTestSupport.exactProfile();
    private final DeterministicTextMetricsService service = MeasurementTestSupport.textMetrics();

    @Test
    void insertsExplicitChineseLineBreaksWithoutLeadingClosingPunctuationOrTruncation() {
        String source = "健康数据，持续管理。";
        TextFitResult result = service.fit(request(
                source,
                1_800,
                1_800,
                MeasurementTestSupport.em(1_800, 4_500),
                MeasurementTestSupport.em(1_800, 10_000),
                5));

        assertTrue(result.fits());
        assertTrue(result.renderedText().contains("\n"));
        assertEquals(source, result.renderedText().replace("\n", ""));
        result.lines().stream().skip(1).forEach(line ->
                assertFalse("，。！？；：、）》】〕〉”".contains(line.substring(0, 1))));
        result.lines().stream().limit(result.lines().size() - 1L).forEach(line ->
                assertFalse("（《【〔〈".contains(line.substring(line.length() - 1))));
    }

    @Test
    void continuousEnglishAndNumbersNeverBreakInsideTheToken() {
        TextFitResult wrapped = service.fit(request(
                "Spring Boot 3",
                1_800,
                1_800,
                MeasurementTestSupport.em(1_800, 3_800),
                MeasurementTestSupport.em(1_800, 10_000),
                4));
        TextFitResult impossible = service.fit(request(
                "SpringBoot3",
                1_800,
                1_600,
                MeasurementTestSupport.em(1_800, 3_000),
                MeasurementTestSupport.em(1_800, 10_000),
                4));

        assertEquals("Spring \nBoot 3", wrapped.renderedText());
        assertEquals(TextFitStatus.UNFIT, impossible.status());
        assertEquals("", impossible.renderedText());
        assertFalse(impossible.failureReason().isBlank());
    }

    @Test
    void bulletMarkerIsNeverLeftOnAStandaloneLine() {
        TextFitResult result = service.fit(request(
                "• 健康管理",
                1_800,
                1_800,
                MeasurementTestSupport.em(1_800, 2_000),
                MeasurementTestSupport.em(1_800, 10_000),
                5));

        assertTrue(result.fits());
        assertTrue(result.lines().get(0).startsWith("• 健"));
        assertFalse("• ".equals(result.lines().get(0)));
    }

    @Test
    void reducesFontInFixedHalfPointStepsAndNeverBelowMinimum() {
        TextFitResult result = service.fit(request(
                "健康管理",
                1_800,
                1_600,
                MeasurementTestSupport.em(1_750, 4_000),
                MeasurementTestSupport.em(1_800, 5_000),
                1));

        assertEquals(TextFitStatus.FIT_WITH_FONT_SCALE, result.status());
        assertEquals(1_750, result.fontSizeHundredthPt());
        assertEquals("健康管理", result.renderedText());
    }

    @Test
    void defaultLocaleTimezoneAndRepeatedCallsDoNotAffectResults() {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            TextFitRequest request = request(
                    "Spring Boot与健康数据分析",
                    1_800,
                    1_600,
                    MeasurementTestSupport.em(1_800, 8_000),
                    MeasurementTestSupport.em(1_800, 10_000),
                    5);
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            TextFitResult first = service.fit(request);
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            for (int attempt = 0; attempt < 100; attempt++) {
                assertEquals(first, service.fit(request));
            }
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    void normalizesLineEndingsButPreservesEveryNonNewlineCharacter() {
        TextFitResult result = service.fit(request(
                "第一行\r\n第二行",
                1_800,
                1_800,
                MeasurementTestSupport.em(1_800, 10_000),
                MeasurementTestSupport.em(1_800, 10_000),
                3));

        assertEquals("第一行\n第二行", result.sourceText());
        assertEquals("第一行第二行", result.renderedText().replace("\n", ""));
    }

    private TextFitRequest request(
            String text,
            int defaultSize,
            int minimumSize,
            long width,
            long height,
            int maxLines
    ) {
        return new TextFitRequest(
                text,
                profile,
                "body",
                400,
                defaultSize,
                minimumSize,
                1_300,
                width,
                height,
                maxLines);
    }
}
