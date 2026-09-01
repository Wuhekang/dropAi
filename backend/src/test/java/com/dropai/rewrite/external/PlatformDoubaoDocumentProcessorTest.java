package com.dropai.rewrite.external;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformDoubaoDocumentProcessorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rewritesOnlyBodyNaturalLanguageAndPreservesDocumentStructure() throws Exception {
        Path source = temporaryDirectory.resolve("source.docx");
        Path output = temporaryDirectory.resolve("output.docx");
        writeFixture(source);

        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    java.util.List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    segments.forEach(segment -> rewritten.put(segment.id(),
                            segment.text().replace("日常记录场景", "日常采集场景")));
                    return rewritten;
                });

        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                gateway, new PlatformDocumentTextProtector());
        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(1);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        assertThat(output).exists();

        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs()).hasSize(10);
            assertThat(document.getParagraphs().get(5).getText()).contains("日常采集场景");
            var citationRun = document.getParagraphs().get(5).getRuns().stream()
                    .filter(run -> "[12]".equals(run.text()))
                    .findFirst()
                    .orElseThrow();
            assertThat(citationRun.getCTR().xmlText()).contains("superscript");
            assertThat(citationRun.isItalic()).isTrue();
            assertThat(citationRun.getColor()).isEqualTo("C00000");
            assertThat(document.getParagraphs().get(6).getText()).isEqualTo(
                    "该段包含手工换行，因此即使长度足够也必须完整保留，不能删除复杂的 Word 结构。\n下一行仍是原文。");
            assertThat(document.getParagraphs().get(8).getText()).isEqualTo("[1] 原始参考文献，2025。");
            assertThat(document.getParagraphs().get(9).getText()).isEqualTo("致谢之后的内容不能改写。");
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("表格原文");
        }
    }

    @Test
    void dayaSelectsBothAbstractsAndBodyButSkipsCatalogReferencesAndAcknowledgements() throws Exception {
        Path source = temporaryDirectory.resolve("daya-scope-source.docx");
        writeDayaScopeFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                gateway, new PlatformDocumentTextProtector());

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).extracting(PlatformDoubaoDocumentProcessor.Target::originalText)
                    .containsExactly(
                            "中文摘要正文说明研究对象、技术选型与已经完成的主要功能，所有事实都来自论文原稿。",
                            "This English abstract must remain untouched even when it is long enough for processing.",
                            "正文自然语言段落描述系统的实际操作、数据变化与已有结果，长度满足处理条件。"
                    );
            assertThat(targets).extracting(PlatformDoubaoDocumentProcessor.Target::context)
                    .containsExactly("中文摘要", "英文摘要", "第一章 绪论");
        }
    }

    @Test
    void identicalModelOutputIsNotReportedAsRewritten() throws Exception {
        Path source = temporaryDirectory.resolve("same-output-source.docx");
        Path output = temporaryDirectory.resolve("same-output-result.docx");
        writeTwoBodyParagraphFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    rewritten.put(segments.get(0).id(), segments.get(0).text());
                    rewritten.put(segments.get(1).id(), segments.get(1).text().replace("同样包含", "同时包含"));
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                gateway, new PlatformDocumentTextProtector());

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(2);
        assertThat(result.processedParagraphs()).isEqualTo(2);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
    }

    @Test
    void dayaKeepsBatchesSmallAndWithinOneSectionContext() throws Exception {
        Path source = temporaryDirectory.resolve("daya-context-batches-source.docx");
        Path output = temporaryDirectory.resolve("daya-context-batches-result.docx");
        writeContextBatchFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        List<List<PlatformDoubaoRewriteGateway.Segment>> observed = new java.util.ArrayList<>();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = List.copyOf(invocation.getArgument(0));
                    observed.add(segments);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    segments.forEach(segment -> rewritten.put(segment.id(),
                            segment.text().replace("记录已经存在", "记录现有")));
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                gateway, new PlatformDocumentTextProtector());

        processor.process(source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(observed).allSatisfy(batch -> {
            assertThat(batch).hasSizeLessThanOrEqualTo(
                    PlatformDoubaoDocumentProcessor.DAYA_MAX_BATCH_PARAGRAPHS);
            assertThat(batch.stream().mapToInt(segment -> segment.text().length()).sum())
                    .isLessThanOrEqualTo(PlatformDoubaoDocumentProcessor.DAYA_MAX_BATCH_CHARACTERS);
            assertThat(batch.stream().map(PlatformDoubaoRewriteGateway.Segment::context).distinct())
                    .hasSize(1);
        });
        assertThat(observed).hasSize(3);
    }

    @Test
    void dayaCollectsManualArabicListItemsAsOneEnumerationInsteadOfHeadings() throws Exception {
        Path source = temporaryDirectory.resolve("daya-manual-list.docx");
        writeDayaListFixture(source, false);
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                mock(PlatformDoubaoRewriteGateway.class), new PlatformDocumentTextProtector());

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).preparation())
                    .isEqualTo(PlatformDoubaoDocumentProcessor.DayaPreparation.MERGED_LIST);
            assertThat(targets.get(0).sourceParagraphs()).hasSize(3);
            assertThat(targets.get(0).originalText())
                    .startsWith("第一项，责任主体需要逐项明确")
                    .contains("第二项，现场台账必须每天核验", "第三项，处置结果应在当天留痕");
        }
    }

    @Test
    void dayaCollectsManualChineseListItemsAsOneEnumeration() throws Exception {
        Path source = temporaryDirectory.resolve("daya-chinese-list.docx");
        writeDayaChineseListFixture(source);
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                mock(PlatformDoubaoRewriteGateway.class), new PlatformDocumentTextProtector());

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).preparation())
                    .isEqualTo(PlatformDoubaoDocumentProcessor.DayaPreparation.MERGED_LIST);
            assertThat(targets.get(0).sourceParagraphs()).hasSize(3);
        }
    }

    @Test
    void dayaMergesWordAutomaticNumberingAndRemovesTheListStructureAfterSuccess() throws Exception {
        Path source = temporaryDirectory.resolve("daya-auto-list.docx");
        Path output = temporaryDirectory.resolve("daya-auto-list-result.docx");
        writeDayaListFixture(source, true);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(1);
                    assertThat(segments.get(0).text())
                            .contains("第一项", "第二项", "第三项");
                    return Map.of(segments.get(0).id(),
                            "责任主体已经明确。现场台账每天核验。处置结果当天留痕。");
                });
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                gateway, new PlatformDocumentTextProtector());

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(1);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs()).hasSize(2);
            assertThat(document.getParagraphs().get(1).getText())
                    .isEqualTo("责任主体已经明确。现场台账每天核验。处置结果当天留痕。");
            assertThat(document.getParagraphs().get(1).getCTP().getPPr().isSetNumPr()).isFalse();
        }
    }

    private void writeFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("目录");
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("关键词：健康管理；Spring Boot");
            var catalogHeading = document.createParagraph();
            catalogHeading.setStyle("TOC1");
            catalogHeading.createRun().setText("第一章 绪论1");
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("1 绪论");
            var body = document.createParagraph();
            body.createRun().setText("个人健康管理系统面向日常记录场景，相关设计依据见");
            var citation = body.createRun();
            citation.setText("[12]");
            citation.setSubscript(VerticalAlign.SUPERSCRIPT);
            citation.setItalic(true);
            citation.setColor("C00000");
            body.createRun().setText("，用户可录入体重、睡眠和运动数据，并查看阶段变化。");
            var complex = document.createParagraph();
            var complexRun = complex.createRun();
            complexRun.setText("该段包含手工换行，因此即使长度足够也必须完整保留，不能删除复杂的 Word 结构。");
            complexRun.addBreak();
            complexRun.setText("下一行仍是原文。");
            document.createTable(1, 1).getRow(0).getCell(0).setText("表格原文");
            document.createParagraph().createRun().setText("参考文献");
            document.createParagraph().createRun().setText("[1] 原始参考文献，2025。");
            document.createParagraph().createRun().setText("致谢之后的内容不能改写。");
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeDayaScopeFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("封面说明文字即使很长也不属于允许改写范围，必须保持原样。");
            document.createParagraph().createRun().setText("摘 要");
            var abstractParagraph = document.createParagraph();
            var bookmark = abstractParagraph.getCTP().addNewBookmarkStart();
            bookmark.setId(BigInteger.ONE);
            bookmark.setName("OLE_LINK1");
            abstractParagraph.createRun().setText(
                    "中文摘要正文说明研究对象、技术选型与已经完成的主要功能，所有事实都来自论文原稿。");
            document.createParagraph().createRun().setText("关键词：健康管理；Spring Boot");
            document.createParagraph().createRun().setText("ABSTRACT");
            document.createParagraph().createRun().setText(
                    "This English abstract must remain untouched even when it is long enough for processing.");
            document.createParagraph().createRun().setText("Keywords: health management");
            document.createParagraph().createRun().setText("目录");
            var catalogHeading = document.createParagraph();
            catalogHeading.setStyle("TOC1");
            catalogHeading.createRun().setText("第一章 绪论1");
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText(
                    "正文自然语言段落描述系统的实际操作、数据变化与已有结果，长度满足处理条件。");
            document.createParagraph().createRun().setText("参考文献");
            document.createParagraph().createRun().setText(
                    "[1] 参考文献中的中文说明即使很长也不能提交给模型处理，必须保持原样。");
            document.createParagraph().createRun().setText("致    谢");
            document.createParagraph().createRun().setText(
                    "导师在选题与结构梳理阶段提供了原稿已经记载的帮助，这段致谢只重组已有事实。");
            document.createParagraph().createRun().setText("原创性声明");
            document.createParagraph().createRun().setText(
                    "声明之后的中文内容即使长度满足要求也属于受保护范围，不能提交给模型处理。");
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeTwoBodyParagraphFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("关键词：测试");
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText(
                    "第一段正文包含足够多的中文事实，用于确认模型返回原文时不会被统计成已改写段落。");
            document.createParagraph().createRun().setText(
                    "第二段正文同样包含足够多的中文事实，用于确认真正发生变化时才增加改写计数。");
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeContextBatchFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("关键词：测试");
            var firstHeading = document.createParagraph();
            firstHeading.setStyle("Heading1");
            firstHeading.createRun().setText("第一章 绪论");
            for (int index = 1; index <= 5; index++) {
                document.createParagraph().createRun().setText(
                        "本章第" + index + "段记录已经存在的系统事实和操作过程，用于验证同一章节最多只提交四段正文。");
            }
            var secondHeading = document.createParagraph();
            secondHeading.setStyle("Heading2");
            secondHeading.createRun().setText("2.1 功能说明");
            for (int index = 1; index <= 2; index++) {
                document.createParagraph().createRun().setText(
                        "本节第" + index + "段说明另一组既有事实和测试结果，用于验证批次不会跨越章节上下文。");
            }
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeDayaListFixture(Path path, boolean automaticNumbering) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            String[] items = {
                    "责任主体需要逐项明确并记录到项目责任台账中。",
                    "现场台账必须每天核验并保留对应检查证据。",
                    "处置结果应在当天留痕并注明复核人员信息。"
            };
            for (int index = 0; index < items.length; index++) {
                var paragraph = document.createParagraph();
                if (automaticNumbering) {
                    paragraph.setNumID(BigInteger.ONE);
                    paragraph.createRun().setText(items[index]);
                } else {
                    paragraph.createRun().setText((index + 1) + ". " + items[index]);
                }
            }
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeDayaChineseListFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            String[] markers = {"一、", "二、", "三、"};
            String[] items = {
                    "责任主体需要逐项明确并记录到项目责任台账中。",
                    "现场台账必须每天核验并保留对应检查证据。",
                    "处置结果应在当天留痕并注明复核人员信息。"
            };
            for (int index = 0; index < items.length; index++) {
                document.createParagraph().createRun().setText(markers[index] + items[index]);
            }
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }
}
