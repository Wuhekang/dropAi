package com.dropai.rewrite.service.ppt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in regression for user-owned opening reports; no private documents enter Git. */
class PptLocalOpeningPersonalizationTest {
    @TempDir Path temp;

    @Test void realOpeningReportKeepsGroundedGraphicsAndNoForeignExampleCopy() throws Exception {
        String configured = System.getProperty("ppt.opening.personalized.source", "").strip();
        Assumptions.assumeFalse(configured.isBlank(), "Set ppt.opening.personalized.source locally");
        Path source = Path.of(configured);
        Assumptions.assumeTrue(Files.isRegularFile(source));
        var parsed = new PptDocumentParser().parse(source, temp.resolve("assets"));
        var base = new PptContentPlannerV2InputAdapter().fromParsedDocument(parsed, "computer");
        if(source.getFileName().toString().contains("Mate80"))
            assertTrue(base.metadata().getOrDefault("title","").contains("用户精准营销策略研究"));
        var metadata = new java.util.LinkedHashMap<>(base.metadata());
        metadata.put("presentationMode", "OPENING_PROPOSAL");
        var input = new PptContentPlannerV2.PlannerInput(metadata, base.chapters(), base.assets(),
                base.tables(), base.majorType());
        var result = new PptContentSanitizerV1().sanitize(
                new PptContentPlannerV2(new PptPlannerRuleLibrary(new ObjectMapper())).plan(input));
        List<PptContentPlannerV2.CandidatePage> pages = result.chapters().stream()
                .flatMap(chapter -> chapter.candidatePages().stream()).toList();
        String all = pages.stream().map(page -> page.title() + page.description()
                + String.join("", page.keyPoints())).reduce("", String::concat);
        for (String forbidden : List.of("华润新能源", "偿债能力", "健康数据", "个人健康管理系统",
                "毕业设计（论文）选题背景", "论文技术路线清晰")) assertFalse(all.contains(forbidden), forbidden);
        assertTrue(pages.stream().anyMatch(page -> "四象限研究价值".equals(page.title())
                && page.keyPoints().size() == 4));
        assertTrue(pages.stream().anyMatch(page -> "国内外文献对比".equals(page.title())
                && page.keyPoints().size() == 2));
        assertTrue(pages.stream().flatMap(page -> page.keyPoints().stream())
                .noneMatch(point -> point.matches(".*\\[[0-9]+$")));
        var progress=pages.stream().filter(page->"指标与进度路径".equals(page.title())).findFirst().orElseThrow();
        assertEquals(5,progress.keyPoints().size());
        assertTrue(progress.keyPoints().stream().allMatch(point->point.length()<=25));
        assertTrue(progress.keyPoints().stream().noneMatch(point->point.contains("等、")||point.contains("分别梳理")));
        assertTrue(pages.stream().flatMap(page->page.keyPoints().stream())
                .noneMatch(point->point.equals("概括本节核心内容")));
        assertEquals(parsed.assets().size(), pages.stream()
                .filter(page -> "IMAGE_EVIDENCE".equals(page.candidateType())).count());
    }
}
