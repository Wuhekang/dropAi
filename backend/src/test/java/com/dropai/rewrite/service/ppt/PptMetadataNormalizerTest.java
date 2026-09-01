package com.dropai.rewrite.service.ppt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PptMetadataNormalizerTest {
    private final PptMetadataNormalizer normalizer = new PptMetadataNormalizer();

    @Test
    void extractsRealFrontMatterWithoutChangingItsMeaning() {
        List<String> blocks = List.of(
                "本科毕业设计（论文）",
                "题目：基于Spring Boot的个人健康管理系统的",
                "设计与实现",
                "2171700297180学 院 智算工程学院",
                "2167255297180专 业 软件工程",
                "2168000297180学 号 6022203537",
                "2176145299085学生姓名 高 瑞 康",
                "2182495299085指导教师 蒋 辉",
                "2026 年 06 月 01 日",
                "摘要",
                "姓 名：不应进入元数据");

        Map<String, String> metadata = normalizer.extract(blocks);

        assertEquals("基于Spring Boot的个人健康管理系统的设计与实现", metadata.get("title"));
        assertEquals("智算工程学院", metadata.get("institution"));
        assertEquals("软件工程", metadata.get("major"));
        assertEquals("6022203537", metadata.get("studentNumber"));
        assertEquals("高瑞康", metadata.get("presenter"));
        assertEquals("蒋辉", metadata.get("advisor"));
        assertEquals("2026年6月1日", metadata.get("date"));
    }

    @Test
    void normalizesSupportedDateAndNameFormsDeterministically() {
        for (String value : List.of("2026 年 06 月 01 日", "2026-06-01", "2026/06/01")) {
            assertEquals("2026年6月1日", normalizer.normalizeValues(Map.of("date", value)).get("date"));
        }
        assertEquals("2026年6月", normalizer.normalizeValues(Map.of("date", "2026-06")).get("date"));
        assertEquals("张三", normalizer.normalizeValues(Map.of("presenter", "张  三")).get("presenter"));
        assertEquals("Ada Lovelace", normalizer.normalizeValues(Map.of("presenter", "Ada   Lovelace")).get("presenter"));

        Map<String, String> once = normalizer.normalizeValues(Map.of(
                "presenter", "高 瑞 康", "advisor", "蒋 辉", "date", "2026-06-01"));
        assertEquals(once, normalizer.normalizeValues(once));
    }

    @Test
    void rejectsInvalidDatesAndAmbiguousFrontMatter() {
        assertThrows(IllegalArgumentException.class,
                () -> normalizer.normalizeValues(Map.of("date", "2026-13-01")));
        assertThrows(IllegalStateException.class,
                () -> normalizer.extract(List.of("学生姓名 张三", "汇报人 李四", "摘要")));
        assertThrows(IllegalStateException.class,
                () -> normalizer.extract(List.of("题目：健康管理", "课题名称：健康管理系统", "摘要")));
    }

    @Test
    void requiresAnExplicitValueSeparatorAndStopsBeforeBodyText() {
        assertEquals(Map.of(), normalizer.extract(List.of(
                "指导教师评语",
                "专业设计说明书",
                "学生姓名填写说明",
                "[第2页] 摘要",
                "姓名：不应进入元数据")));
        assertEquals(Map.of(), normalizer.extract(List.of(
                "本科毕业设计（论文）",
                "[第3页] 第一章 绪论",
                "姓名 张三")));
    }

    @Test
    void productionAnalyzePrefersExtractedFullTitleOverParserFallback() {
        String fullTitle = "基于Spring Boot的个人健康管理系统的设计与实现";
        assertEquals(fullTitle, PptProjectService.resolveAnalyzedTopic(
                "", Map.of("title", fullTitle), "1.1 研究背景及意义"));
        assertEquals("用户确认的题目", PptProjectService.resolveAnalyzedTopic(
                "用户确认的题目", Map.of("title", fullTitle), "1.1 研究背景及意义"));
    }

    @Test
    void resolvesOnlyAnExplicitRepeatedFormTitleTruncation() {
        assertEquals("基于Spring Boot的个人健康管理系统的设计与实现", normalizer.extract(List.of(
                "题目：基于Spring Boot的个人健康管理系统的设计与实现",
                "题目：基于Spring Boot的个人健康管理系统的",
                "摘要")).get("title"));
    }

    @Test
    void adapterUsesNormalizedMetadataButPreservesChapterParagraphs() {
        List<String> blocks = List.of(
                "题目：基于Spring Boot的个人健康管理系统的",
                "设计与实现",
                "学 院 智算工程学院",
                "专 业 软件工程",
                "学 号 6022203537",
                "学生姓名 高 瑞 康",
                "指导教师 蒋 辉",
                "2026年6月1日",
                "第一章 绪论",
                "姓 名之间的空格属于正文，不应被元数据标准化器改写");
        PptDocumentParser.ParsedDocument document = new PptDocumentParser.ParsedDocument(
                "第一章 绪论", List.of("第一章 绪论"), blocks, List.of(), 0, 100, 0, 0);

        PptContentPlannerV2.PlannerInput input = new PptContentPlannerV2InputAdapter()
                .fromParsedDocument(document, "computer");

        assertEquals("基于Spring Boot的个人健康管理系统的设计与实现", input.metadata().get("title"));
        assertEquals("高瑞康", input.metadata().get("presenter"));
        assertEquals("蒋辉", input.metadata().get("advisor"));
        assertEquals("智算工程学院", input.metadata().get("institution"));
        assertEquals("2026年6月1日", input.metadata().get("date"));
        assertEquals(blocks.get(blocks.size() - 1),
                input.chapters().get(input.chapters().size() - 1).paragraphs().get(1));
    }
}
