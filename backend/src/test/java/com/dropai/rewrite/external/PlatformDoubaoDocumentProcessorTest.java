package com.dropai.rewrite.external;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.VerticalAlign;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformDoubaoDocumentProcessorTest {
    @TempDir
    Path temporaryDirectory;
    private final List<PlatformDoubaoDocumentProcessor> processors = new ArrayList<>();

    @AfterEach
    void shutdownProcessors() {
        processors.forEach(PlatformDoubaoDocumentProcessor::shutdownDayaBatchExecutor);
    }

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
                            segment.text()
                                    .replace("研究内容主要包括个人健康管理系统的日常记录场景，相关设计依据见",
                                            "系统服务于个人日常采集，设计所用依据参见")
                                    .replace("，用户可录入体重、睡眠和运动数据，并查看阶段变化。",
                                            "。体重、睡眠及运动数据由用户填写，随后可观察各阶段变化。")));
                    return rewritten;
                });

        PlatformDoubaoDocumentProcessor processor = processor(gateway);
        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(1);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        assertThat(output).exists();

        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs()).hasSize(10);
            assertThat(document.getParagraphs().get(5).getText()).contains("服务于个人日常采集");
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
    void dayaIgnoresNonVisualRunMetadataAndUsesTheMainBodyRunForRefill() throws Exception {
        Path source = temporaryDirectory.resolve("daya-run-metadata-source.docx");
        Path output = temporaryDirectory.resolve("daya-run-metadata-result.docx");
        String original = "项目记录主要包括现场情况、持续开展的核验工作以及原始台账中的处理结果。";
        writeRunMetadataFixture(source, original);

        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(1);
                    assertThat(segments.get(0).text())
                            .isEqualTo(original)
                            .doesNotContain("[[DROP_AI_PROTECTED_", "[[DROP_STYLE_PROTECTED_");
                    return Map.of(segments.get(0).id(),
                            "现场记录说明项目情况，核验工作持续开展，原始台账保存了处理结果。");
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            var rewritten = document.getParagraphs().get(1);
            assertThat(rewritten.getText())
                    .isEqualTo("现场记录说明项目情况，核验工作持续开展，原始台账保存了处理结果。");
            assertThat(rewritten.getRuns()).hasSize(1);
            assertThat(rewritten.getRuns().get(0).getCTR().isSetRPr()).isFalse();
        }
    }

    @Test
    void dayaKeepsAStyledChineseOrderMarkerEditableInsteadOfProtectingIt() throws Exception {
        Path source = temporaryDirectory.resolve("daya-styled-marker-source.docx");
        Path output = temporaryDirectory.resolve("daya-styled-marker-result.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            var body = document.createParagraph();
            var marker = body.createRun();
            marker.setBold(true);
            marker.setText("第1项，");
            body.createRun().setText("现场资料已经核实，相关台账也保留了原始记录。");
            try (OutputStream stream = Files.newOutputStream(source)) {
                document.write(stream);
            }
        }
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(1);
                    assertThat(segments.get(0).text())
                            .contains("第一项，")
                            .doesNotContain("[[DROP_STYLE_PROTECTED_");
                    return Map.of(segments.get(0).id(), "现场资料和原始台账都已核实留存。");
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs().get(1).getText())
                    .isEqualTo("现场资料和原始台账都已核实留存。");
        }
    }

    @Test
    void dayaRemovesAMarkerSharingAStyledRunWithItsShortTitle() throws Exception {
        Path source = temporaryDirectory.resolve("daya-styled-marker-title-source.docx");
        Path output = temporaryDirectory.resolve("daya-styled-marker-title-result.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            var body = document.createParagraph();
            var markerAndTitle = body.createRun();
            markerAndTitle.setBold(true);
            markerAndTitle.setText("第一，施工准备");
            body.createRun().setText("需要核对现场资料，原始台账保留了实际处理记录。日常复查仍以这些材料为准。");
            try (OutputStream stream = Files.newOutputStream(source)) {
                document.write(stream);
            }
        }
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(1);
                    String modelText = segments.get(0).text();
                    assertThat(modelText)
                            .startsWith("第一，[[DROP_AI_PROTECTED_")
                            .doesNotContain("第一，施工准备");
                    int tokenStart = modelText.indexOf("[[DROP_AI_PROTECTED_");
                    int tokenEnd = modelText.indexOf("]]", tokenStart) + 2;
                    String titleToken = modelText.substring(tokenStart, tokenEnd);
                    return Map.of(segments.get(0).id(), titleToken + "由现场记录说明。");
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            var rewritten = document.getParagraphs().get(1);
            assertThat(rewritten.getText()).isEqualTo("施工准备由现场记录说明。");
            assertThat(rewritten.getText()).doesNotContain("第一");
            assertThat(rewritten.getRuns().get(0).text()).isEqualTo("施工准备");
            assertThat(rewritten.getRuns().get(0).isBold()).isTrue();
        }
    }

    @Test
    void dayaSelectsBothAbstractsAndBodyButSkipsCatalogReferencesAndAcknowledgements() throws Exception {
        Path source = temporaryDirectory.resolve("daya-scope-source.docx");
        writeDayaScopeFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

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
                    .containsExactly("中文摘要", "英文摘要", "绪论");
        }
    }

    @Test
    void dayaEnglishAbstractKeepsOrdinaryProseEditableAndRestoresEvidence() throws Exception {
        Path source = temporaryDirectory.resolve("daya-english-source.docx");
        Path output = temporaryDirectory.resolve("daya-english-result.docx");
        String original = "This study examines whole-process cost control with LCC and BIM; "
                + "results show that the error is 12.5% [12], and the paper proposes a record check.";
        String rewritten = "Project records were checked with LCC and BIM. "
                + "The measured error remains 12.5% [12]. A record check is proposed.";
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("ABSTRACT");
            document.createParagraph().createRun().setText(original);
            try (OutputStream stream = Files.newOutputStream(source)) {
                document.write(stream);
            }
        }

        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(1);
                    String modelText = segments.get(0).text();
                    assertThat(modelText)
                            .contains("This study examines whole-process cost control with ")
                            .contains("results show that the error is ")
                            .doesNotContain("LCC", "BIM", "12.5%", "[12]");
                    return Map.of(segments.get(0).id(), modelText
                            .replace("This study examines whole-process cost control with",
                                    "Project records were checked with")
                            .replace("; results show that the error is", ". The measured error remains")
                            .replace(", and the paper proposes a record check", ". A record check is proposed"));
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs().get(1).getText()).isEqualTo(rewritten);
        }
    }

    @Test
    void dayaRewritesLowRiskOrdinaryParagraphInsteadOfUsingRiskAsAnAdmissionGate() throws Exception {
        Path source = temporaryDirectory.resolve("daya-ordinary-growth-source.docx");
        Path output = temporaryDirectory.resolve("daya-ordinary-growth-result.docx");
        String original = "项目现场已有核验记录，处理时间和复核结论均保存在原始台账中。";
        String rewritten = "原始台账留有项目现场的核验情况，也写下了处理时间和复核结论。";
        writeSingleBodyFixture(source, "第一章 绪论", original);

        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(1);
                    return Map.of(segments.get(0).id(), rewritten);
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(calls).hasValue(1);
        assertThat(result.totalParagraphs()).isEqualTo(1);
        assertThat(result.processedParagraphs()).isEqualTo(1);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs().get(1).getText()).isEqualTo(rewritten);
        }
    }

    @Test
    void dayaRiskParagraphCanUseALongerRelatedRebuildWithoutALengthCap() throws Exception {
        Path source = temporaryDirectory.resolve("daya-risk-growth-source.docx");
        Path output = temporaryDirectory.resolve("daya-risk-growth-result.docx");
        String original = "研究内容包括现场台账缺页、整改时间未记以及复核结论缺少证据。";
        String expanded = "现场台账能够看到缺页。原有材料没有写明整改发生的时间，复核结论也缺少可以对应的证据。";
        writeSingleBodyFixture(source, "第一章 绪论", original);

        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    return Map.of(segments.get(0).id(), expanded);
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(expanded.length()).isGreaterThan((int) Math.ceil(original.length() * 1.15));
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        assertThat(result.failedParagraphs()).isZero();
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs().get(1).getText()).isEqualTo(expanded);
        }
    }

    @Test
    void highSimilarityModelOutputPreventsPublishingAPartiallyRewrittenDocument() throws Exception {
        Path source = temporaryDirectory.resolve("same-output-source.docx");
        Path output = temporaryDirectory.resolve("same-output-result.docx");
        writeTwoBodyParagraphFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    rewritten.put(segments.get(0).id(),
                            segments.get(0).text().replace("足够多", "较多"));
                    if (segments.size() > 1) {
                        rewritten.put(segments.get(1).id(), segments.get(1).text()
                                .replace("第二段研究内容主要包括现场记录、原始依据以及复核结论，只有真正发生变化时才增加改写计数。",
                                        "只有中文事实确实经过重新组织，第二段正文才应纳入改写数量。"));
                    }
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        assertThatThrownBy(() -> processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不生成部分文档");
        assertThat(calls).hasValue(2);
        assertThat(output).doesNotExist();
    }

    @Test
    void finalStyleRiskPreventsPublishingEvenWhenEveryWordWasChanged() throws Exception {
        Path source = temporaryDirectory.resolve("style-risk-source.docx");
        Path output = temporaryDirectory.resolve("style-risk-result.docx");
        String original = "现场资料由项目负责人核对，复核意见保存在当天台账中。";
        String risky = "建设单位负责核对资料，监理单位负责复查台账，项目部负责保存签字记录，"
                + "负责人负责处理问题。资料确保记录完整并形成核查依据，也能保障后续追查。";
        writeSingleBodyFixture(source, "第三章 管理措施", original);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    calls.incrementAndGet();
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    return Map.of(segments.get(0).id(), risky);
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        assertThatThrownBy(() -> processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不生成部分文档");
        assertThat(calls).hasValue(2);
        assertThat(output).doesNotExist();
    }

    @Test
    void dayaRetriesOnlyTheFailedFirstDraftOnceAsASingleSegment() throws Exception {
        Path source = temporaryDirectory.resolve("single-retry-source.docx");
        Path output = temporaryDirectory.resolve("single-retry-result.docx");
        writeTwoBodyParagraphFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        AtomicInteger calls = new AtomicInteger();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    int call = calls.incrementAndGet();
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    if (call == 1) {
                        assertThat(segments).hasSize(2);
                        rewritten.put(segments.get(0).id(),
                                segments.get(0).text().replace("足够多", "较多"));
                        rewritten.put(segments.get(1).id(),
                                "中文事实经过重新组织后再计数。第二段保留现场能够核对的记录。");
                    } else {
                        assertThat(segments).hasSize(1);
                        assertThat(segments.get(0).id()).isEqualTo("p3");
                        rewritten.put(segments.get(0).id(),
                                "系统核对项目范围和原始依据。执行时间与复核结论仍按现场记录保存。");
                    }
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(calls).hasValue(2);
        assertThat(result.totalParagraphs()).isEqualTo(2);
        assertThat(result.processedParagraphs()).isEqualTo(2);
        assertThat(result.rewrittenParagraphs()).isEqualTo(2);
        assertThat(result.failedParagraphs()).isZero();
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs().get(3).getText())
                    .isEqualTo("系统核对项目范围和原始依据。执行时间与复核结论仍按现场记录保存。");
            assertThat(document.getParagraphs().get(4).getText())
                    .isEqualTo("中文事实经过重新组织后再计数。第二段保留现场能够核对的记录。");
        }
    }

    @Test
    void dayaKeepsBatchesSmallAndWithinOneSectionContext() throws Exception {
        Path source = temporaryDirectory.resolve("daya-context-batches-source.docx");
        Path output = temporaryDirectory.resolve("daya-context-batches-result.docx");
        writeContextBatchFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        List<List<PlatformDoubaoRewriteGateway.Segment>> observed = new CopyOnWriteArrayList<>();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = List.copyOf(invocation.getArgument(0));
                    observed.add(segments);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    segments.forEach(segment -> rewritten.put(segment.id(),
                            segment.text()
                                    .replace("段研究内容主要包括系统事实、操作过程以及批次边界，用于验证同一章节最多只提交四段正文。",
                                            "段用系统既有事实和操作经过检查批次边界，每个章节一次至多发送四段正文。")
                                    .replace("段研究内容主要包括既有事实、测试结果以及章节边界，用于验证批次不会跨越章节上下文。",
                                            "段用既有事实和测试结果检查章节边界，各批内容始终留在本节语境内。")));
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

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
    void dayaRunsAtMostThirtyTwoBatchesConcurrentlyAndWritesAllResults() throws Exception {
        Path source = temporaryDirectory.resolve("daya-concurrent-source.docx");
        Path output = temporaryDirectory.resolve("daya-concurrent-result.docx");
        writeConcurrentFixture(source, 33);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        CountDownLatch firstWaveStarted = new CountDownLatch(
                PlatformDoubaoDocumentProcessor.DAYA_MAX_CONCURRENCY);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    int current = active.incrementAndGet();
                    maximumActive.accumulateAndGet(current, Math::max);
                    firstWaveStarted.countDown();
                    try {
                        assertThat(firstWaveStarted.await(5, TimeUnit.SECONDS)).isTrue();
                        List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                        Map<String, String> rewritten = new LinkedHashMap<>();
                        segments.forEach(segment -> rewritten.put(segment.id(),
                                segment.text().replace(
                                        "段记录项目现场事实和既有数据，用于验证大雅批次确实并发处理且文档仍按原顺序写回。",
                                        "段以现场事实和原有数据为依据，检查大雅批任务能否同步运行，内容依照文档原有位置回填。")));
                        return rewritten;
                    } finally {
                        active.decrementAndGet();
                    }
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(maximumActive).hasValue(PlatformDoubaoDocumentProcessor.DAYA_MAX_CONCURRENCY);
        assertThat(result.totalParagraphs()).isEqualTo(33);
        assertThat(result.processedParagraphs()).isEqualTo(33);
        assertThat(result.rewrittenParagraphs()).isEqualTo(33);
        assertThat(result.failedParagraphs()).isZero();
        assertThat(output).exists();
        assertThat(Files.size(output)).isPositive();
    }

    @Test
    void dayaCollectsManualArabicListItemsAsOneEnumerationInsteadOfHeadings() throws Exception {
        Path source = temporaryDirectory.resolve("daya-manual-list.docx");
        writeDayaListFixture(source, false);
        PlatformDoubaoDocumentProcessor processor = processor(
                mock(PlatformDoubaoRewriteGateway.class));

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
        PlatformDoubaoDocumentProcessor processor = processor(
                mock(PlatformDoubaoRewriteGateway.class));

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
    void dayaIncludesUnnumberedContinuationsAndAlsoRewritesFollowingOrdinaryProse() throws Exception {
        Path source = temporaryDirectory.resolve("daya-list-continuations.docx");
        Path output = temporaryDirectory.resolve("daya-list-continuations-result.docx");
        String followingProse = "列表结束后的普通正文只说明现场变化，不属于前面的分条内容。";
        String listRewrite = "台账写清责任主体和复核人员。当天签字单独留存，现场资料按日期核验。核验时还要对照照片和记录。";
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText("1. 责任主体需要写入项目台账。");
            document.createParagraph().createRun().setText("本项还要求当天留下签字记录。");
            document.createParagraph().createRun().setText("2. 现场资料需要按日期核验。");
            document.createParagraph().createRun().setText("这里补充说明照片应与记录对应。");
            document.createParagraph().createRun().setText("3. 复核结论应注明具体人员。");
            document.createParagraph().createRun().setText(followingProse);
            try (OutputStream stream = Files.newOutputStream(source)) {
                document.write(stream);
            }
        }
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    assertThat(segments).hasSize(2);
                    assertThat(segments.get(0).text())
                            .containsOnlyOnce("第一项")
                            .containsOnlyOnce("第二项")
                            .containsOnlyOnce("第三项")
                            .contains("本项还要求当天留下签字记录。", "这里补充说明照片应与记录对应。")
                            .doesNotContain("第二项，本项还要求", "第三项，这里补充说明");
                    return Map.of(
                            segments.get(0).id(),
                            listRewrite,
                            segments.get(1).id(),
                            "现场变化写在分条之后，这段内容与前面的事项无关。");
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).hasSize(2);
            PlatformDoubaoDocumentProcessor.Target list = targets.get(0);
            assertThat(list.preparation())
                    .isEqualTo(PlatformDoubaoDocumentProcessor.DayaPreparation.MERGED_LIST);
            assertThat(list.sourceParagraphs()).hasSize(5);
            assertThat(list.originalText())
                    .contains("第一项，责任主体需要写入项目台账。本项还要求当天留下签字记录。",
                            "第二项，现场资料需要按日期核验。这里补充说明照片应与记录对应。",
                            "第三项，复核结论应注明具体人员。")
                    .doesNotContain("第二项，本项还要求", "第三项，这里补充说明");
            DayaRewriteQualityRules.validateFinal(list.originalText(), listRewrite, list.context());
            assertThat(targets.get(1).originalText()).isEqualTo(followingProse);
        }

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(2);
        assertThat(result.rewrittenParagraphs()).isEqualTo(2);
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs()).hasSize(3);
            assertThat(document.getParagraphs().get(1).getText())
                    .isEqualTo(listRewrite);
            assertThat(document.getParagraphs().get(2).getText())
                    .isEqualTo("现场变化写在分条之后，这段内容与前面的事项无关。");
        }
    }

    @Test
    void dayaStopsContinuationAwareListGroupingAtAHeadingBoundary() throws Exception {
        Path source = temporaryDirectory.resolve("daya-list-heading-boundary.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var firstHeading = document.createParagraph();
            firstHeading.setStyle("Heading1");
            firstHeading.createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText("1. 前一组先记录责任主体。");
            document.createParagraph().createRun().setText("该项的补充记录不带编号。");
            document.createParagraph().createRun().setText("2. 前一组再核对现场资料。");
            var secondHeading = document.createParagraph();
            secondHeading.setStyle("Heading2");
            secondHeading.createRun().setText("1.1 后续安排");
            document.createParagraph().createRun().setText("1. 后一组先查看整改时间。");
            document.createParagraph().createRun().setText("2. 后一组再保存复核结论。");
            try (OutputStream stream = Files.newOutputStream(source)) {
                document.write(stream);
            }
        }
        PlatformDoubaoDocumentProcessor processor = processor(
                mock(PlatformDoubaoRewriteGateway.class));

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).hasSize(2);
            assertThat(targets).allMatch(target -> target.preparation()
                    == PlatformDoubaoDocumentProcessor.DayaPreparation.MERGED_LIST);
            assertThat(targets.get(0).sourceParagraphs()).hasSize(3);
            assertThat(targets.get(0).originalText()).doesNotContain("后一组");
            assertThat(targets.get(1).sourceParagraphs()).hasSize(2);
            assertThat(targets.get(1).originalText()).doesNotContain("前一组", "补充记录");
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
                            "人员姓名记在责任栏。每日检查写入现场记录。当天处置另附复核人。");
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(1);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs()).hasSize(2);
            assertThat(document.getParagraphs().get(1).getText())
                    .isEqualTo("人员姓名记在责任栏。每日检查写入现场记录。当天处置另附复核人。");
            assertThat(document.getParagraphs().get(1).getCTP().getPPr().isSetNumPr()).isFalse();
        }
    }

    @Test
    void dayaRewritesEveryEligibleNarrativeTableCellAndPreservesTableShape() throws Exception {
        Path source = temporaryDirectory.resolve("daya-table-source.docx");
        Path output = temporaryDirectory.resolve("daya-table-result.docx");
        writeDayaTableFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        List<List<String>> observedBatches = new CopyOnWriteArrayList<>();
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    observedBatches.add(segments.stream()
                            .map(PlatformDoubaoRewriteGateway.Segment::id).toList());
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    for (PlatformDoubaoRewriteGateway.Segment segment : segments) {
                        String value = switch (segment.id()) {
                            case "t0r1c1" -> "现有记录并不完整，材料目前还看不出资金安排是否稳定。";
                            case "t0r1c2" -> {
                                String protectedInvestment = segment.text().replaceAll(
                                        ".*?(\\[\\[DROP_AI_PROTECTED_[0-9]+]]).*", "$1");
                                yield "四批修缮的估算投资合计" + protectedInvestment + "万元。"
                                        + "投融资构成、回收期和后续运维资金尚未见于公开材料。";
                            }
                            case "t0r7c2" -> "原台账尚未写明谁负责。配套资料仍未收齐。";
                            default -> throw new AssertionError("unexpected segment " + segment.id());
                        };
                        rewritten.put(segment.id(), value);
                    }
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(3);
        assertThat(result.rewrittenParagraphs()).isEqualTo(3);
        assertThat(result.failedParagraphs()).isZero();
        assertThat(observedBatches).containsExactly(
                List.of("t0r1c1", "t0r1c2", "t0r7c2"));
        try (InputStream sourceStream = Files.newInputStream(source);
             XWPFDocument sourceDocument = new XWPFDocument(sourceStream);
             InputStream outputStream = Files.newInputStream(output);
             XWPFDocument outputDocument = new XWPFDocument(outputStream)) {
            assertThat(outputDocument.getTables()).hasSameSizeAs(sourceDocument.getTables());
            assertThat(outputDocument.getTables().get(0).getRows())
                    .hasSameSizeAs(sourceDocument.getTables().get(0).getRows());
            for (int row = 0; row < sourceDocument.getTables().get(0).getRows().size(); row++) {
                assertThat(outputDocument.getTables().get(0).getRow(row).getTableCells())
                        .hasSameSizeAs(sourceDocument.getTables().get(0).getRow(row).getTableCells());
            }
            assertThat(outputDocument.getTables().get(0).getRow(1).getCell(2).getText())
                    .isNotEqualTo(sourceDocument.getTables().get(0).getRow(1).getCell(2).getText())
                    .contains("5000万元", "投融资构成", "回收期", "后续运维资金", "尚未");
            assertThat(outputDocument.getTables().get(0).getRow(1).getCell(1).getText())
                    .isEqualTo("现有记录并不完整，材料目前还看不出资金安排是否稳定。");
            assertThat(outputDocument.getTables().get(0).getRow(7).getCell(2).getText())
                    .isEqualTo("原台账尚未写明谁负责。配套资料仍未收齐。");
            assertThat(outputDocument.getTables().get(0).getRow(2).getCell(2).getText())
                    .isEqualTo(sourceDocument.getTables().get(0).getRow(2).getCell(2).getText());
            for (int row : List.of(3, 4, 6)) {
                assertThat(outputDocument.getTables().get(0).getRow(row).getCell(2).getText())
                        .isEqualTo(sourceDocument.getTables().get(0).getRow(row).getCell(2).getText());
            }
            assertThat(outputDocument.getTables().get(0).getRow(5).getCell(2).getParagraphs()).hasSize(2);
            assertThat(outputDocument.getTables().get(1).getRow(1).getCell(0).getText())
                    .isEqualTo(sourceDocument.getTables().get(1).getRow(1).getCell(0).getText());
        }
    }

    @Test
    void dayaSelectsACompactTableExplanationThatUsesOnlyAEnumerationComma() throws Exception {
        Path source = temporaryDirectory.resolve("daya-compact-table-note.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第二章 指标体系");
            document.createParagraph().createRun().setText("表2-4 资料说明");
            var table = document.createTable(2, 1);
            setCellText(table.getRow(0).getCell(0), "说明");
            setCellText(table.getRow(1).getCell(0),
                    "上传资料未包含人员培训、持证上岗与班前教育记录");
            try (OutputStream stream = Files.newOutputStream(source)) {
                document.write(stream);
            }
        }
        PlatformDoubaoDocumentProcessor processor = processor(
                mock(PlatformDoubaoRewriteGateway.class));

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).extracting(PlatformDoubaoDocumentProcessor.Target::id)
                    .containsExactly("t0r1c0");
            assertThat(targets.get(0).preparation())
                    .isEqualTo(PlatformDoubaoDocumentProcessor.DayaPreparation.TABLE_TEXT);
        }
    }

    @Test
    void dayaRejectsTableRewriteThatAddsALineBreak() throws Exception {
        Path source = temporaryDirectory.resolve("daya-table-newline-source.docx");
        Path output = temporaryDirectory.resolve("daya-table-newline-result.docx");
        writeDayaTableFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    for (PlatformDoubaoRewriteGateway.Segment segment : segments) {
                        rewritten.put(segment.id(), switch (segment.id()) {
                            case "t0r1c1" -> "现有材料只展示部分记录，资金安排是否稳定还不能完全看清。";
                            case "t0r1c2" -> segment.text()
                                    .replace("已公开四批次修缮工程估算投资", "公开材料列有四批次修缮工程，估算投资")
                                    .replace("，但完整投融资结构、回收期和后续运维资金",
                                            "。完整投融资结构、回收期与后续运维资金");
                            case "t0r7c2" -> segment.text() + "\n补充说明";
                            default -> throw new AssertionError("unexpected segment " + segment.id());
                        });
                    }
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        assertThatThrownBy(() -> processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("大雅表格说明不得新增换行或制表符");
        assertThat(output).doesNotExist();
    }

    @Test
    void dayaRejectsTableRewriteThatChangesARequiredNegativeCondition() throws Exception {
        Path source = temporaryDirectory.resolve("daya-table-invariant-source.docx");
        Path output = temporaryDirectory.resolve("daya-table-invariant-result.docx");
        writeDayaTableFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    for (PlatformDoubaoRewriteGateway.Segment segment : segments) {
                        rewritten.put(segment.id(), switch (segment.id()) {
                            case "t0r1c1" -> "现有材料只展示部分记录，资金安排是否稳定还不能完全看清。";
                            case "t0r1c2" -> segment.text().replace("尚未", "已经");
                            case "t0r7c2" -> "责任主体尚未写入原台账。相关资料未完整记录。";
                            default -> throw new AssertionError("unexpected segment " + segment.id());
                        });
                    }
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        assertThatThrownBy(() -> processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("大雅表格说明未完整保留编号、数据、单位或否定条件");
        assertThat(output).doesNotExist();
    }

    @Test
    void dayaMergesAFormerlyOversizedNumberedGroupAsOneContentGroup() throws Exception {
        Path source = temporaryDirectory.resolve("daya-long-list.docx");
        writeDayaLongListFixture(source);
        PlatformDoubaoDocumentProcessor processor = processor(
                mock(PlatformDoubaoRewriteGateway.class));

        try (InputStream stream = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(stream)) {
            List<PlatformDoubaoDocumentProcessor.Target> targets = processor.collectDayaTargets(document);

            assertThat(targets).hasSize(1);
            assertThat(targets.get(0).preparation())
                    .isEqualTo(PlatformDoubaoDocumentProcessor.DayaPreparation.MERGED_LIST);
            assertThat(targets.get(0).sourceParagraphs()).hasSize(30);
            assertThat(targets.get(0).originalText()).hasSizeGreaterThan(3600);
            assertThat(document.getParagraphs()).hasSize(31);
        }
    }

    @Test
    void dayaMergesLongAutomaticListItemsAndRemovesTheWholeWordList() throws Exception {
        Path source = temporaryDirectory.resolve("daya-long-auto-list.docx");
        Path output = temporaryDirectory.resolve("daya-long-auto-list-result.docx");
        writeDayaAutomaticLongListFixture(source);
        PlatformDoubaoRewriteGateway gateway = mock(PlatformDoubaoRewriteGateway.class);
        when(gateway.configured()).thenReturn(true);
        when(gateway.rewriteBatch(anyList(), eq(XuejiePlatform.DAYA), eq(XuejieRewriteMode.HUMANIZE)))
                .thenAnswer(invocation -> {
                    List<PlatformDoubaoRewriteGateway.Segment> segments = invocation.getArgument(0);
                    Map<String, String> rewritten = new LinkedHashMap<>();
                    assertThat(segments).hasSize(1);
                    segments.forEach(segment -> rewritten.put(segment.id(),
                            "责任已经落到人员。台账和时间一并记录。复核结论另存。"));
                    return rewritten;
                });
        PlatformDoubaoDocumentProcessor processor = processor(gateway);

        PlatformDoubaoDocumentProcessor.ProcessingResult result = processor.process(
                source, output, XuejiePlatform.DAYA, XuejieRewriteMode.HUMANIZE, null);

        assertThat(result.totalParagraphs()).isEqualTo(1);
        assertThat(result.rewrittenParagraphs()).isEqualTo(1);
        try (InputStream stream = Files.newInputStream(output);
             XWPFDocument document = new XWPFDocument(stream)) {
            assertThat(document.getParagraphs()).hasSize(2);
            assertThat(document.getParagraphs().get(1).getText())
                    .isEqualTo("责任已经落到人员。台账和时间一并记录。复核结论另存。");
            assertThat(document.getParagraphs().get(1).getCTP().getPPr().isSetNumPr()).isFalse();
        }
    }

    private void writeDayaTableFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第二章 指标体系");
            document.createParagraph().createRun().setText("表2-3 项目指标与数据来源");
            var table = document.createTable(8, 4);
            setCellText(table.getRow(0).getCell(0), "代码");
            setCellText(table.getRow(0).getCell(1), "指标");
            setCellText(table.getRow(0).getCell(2), "主要依据/数据来源");
            setCellText(table.getRow(0).getCell(3), "权重");

            setCellText(table.getRow(1).getCell(0), "C11");
            setCellText(table.getRow(1).getCell(1),
                    "该指标用于说明资金安排是否稳定，现有材料只展示部分记录。");
            setCellText(table.getRow(1).getCell(2),
                    "已公开四批次修缮工程估算投资5000万元，但完整投融资结构、回收期和后续运维资金尚未公开。");
            setCellText(table.getRow(1).getCell(3), "0.0416");
            // Regression: real Word files may carry w:trHeight without an hRule value.
            // POI getHeightRule() throws for this valid-but-incomplete combination.
            table.getRow(1).getCtRow().addNewTrPr().addNewTrHeight()
                    .setVal(BigInteger.valueOf(420));

            setCellText(table.getRow(2).getCell(0), "C12");
            setCellText(table.getRow(2).getCell(1), "商业活力");
            setCellText(table.getRow(2).getCell(2),
                    "这是被标记为重复表头的长说明，即使内容像正文也必须保持原样。");
            setCellText(table.getRow(2).getCell(3), "10");
            table.getRow(2).getCtRow().addNewTrPr().addNewTblHeader();

            setCellText(table.getRow(3).getCell(2),
                    "这是一个固定行高中的长说明，即使汉字和标点都足够也必须保留原文。");
            table.getRow(3).setHeight(400);
            table.getRow(3).setHeightRule(TableRowHeightRule.EXACT);

            XWPFTableCell merged = table.getRow(4).getCell(2);
            setCellText(merged, "这是合并单元格里的长说明，处理它可能影响表格网格与排版。");
            var mergedProperties = merged.getCTTc().getTcPr();
            if (mergedProperties == null) mergedProperties = merged.getCTTc().addNewTcPr();
            mergedProperties.addNewGridSpan().setVal(BigInteger.TWO);

            XWPFTableCell multiple = table.getRow(5).getCell(2);
            setCellText(multiple, "这个单元格有两个段落，第一段的文字已经足够长，但整格仍不能处理。");
            multiple.addParagraph().createRun().setText("第二段必须保留。");

            XWPFTableCell breakCell = table.getRow(6).getCell(2);
            var breakRun = breakCell.getParagraphs().get(0).createRun();
            breakRun.setText("这个长说明包含手工换行，即使内容像普通句子也不能重建run。");
            breakRun.addBreak();
            breakRun.setText("换行后内容保留。");

            setCellText(table.getRow(7).getCell(2),
                    "第一项，责任主体尚未写入原台账；第二项，相关资料也未完整记录。");

            document.createParagraph().createRun().setText("参考文献");
            var trailingTable = document.createTable(2, 1);
            setCellText(trailingTable.getRow(0).getCell(0), "附加说明");
            setCellText(trailingTable.getRow(1).getCell(0),
                    "参考文献后的长表格文字，即使形式完全符合也不得提交给模型处理。");
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void setCellText(XWPFTableCell cell, String text) {
        cell.getParagraphs().get(0).createRun().setText(text);
    }

    private void writeDayaLongListFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            for (int index = 1; index <= 30; index++) {
                document.createParagraph().createRun().setText(index + ". "
                        + "该项记录项目现场已有的责任主体、台账证据、处置时点和复核结果，"
                        + "这些事实需要留在同一内容组内，不能因为条目较多重新拆回逐项处理。"
                        + "现场原始资料、处理边界、复核依据和已有结论都继续放在该项说明中。"
                        + "大雅处理时从第一项一直合并到最后一项，最终删除整个列表外壳。");
            }
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeDayaAutomaticLongListFixture(Path path) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("第一章 绪论");
            for (int index = 0; index < 5; index++) {
                var paragraph = document.createParagraph();
                paragraph.setNumID(BigInteger.ONE);
                paragraph.createRun().setText(
                        "责任主体已经记录。现场台账留有证据。处置时间已经注明。复核结果保存在本段。");
            }
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
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
            body.createRun().setText("研究内容主要包括个人健康管理系统的日常记录场景，相关设计依据见");
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
            document.createParagraph().createRun().setText("5 参考文献");
            document.createParagraph().createRun().setText("[1] 原始参考文献，2025。");
            document.createParagraph().createRun().setText("致谢之后的内容不能改写。");
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeSingleBodyFixture(Path path, String headingText, String bodyText) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText(headingText);
            document.createParagraph().createRun().setText(bodyText);
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private String highRiskAbstract(String projectName) {
        return "本文以" + projectName + "为对象，采用现场观察和资料核对分析管理情况，"
                + "结果显示现有记录仍有不足，研究据此提出优化建议，为后续工作提供参考。";
    }

    private void writeRunMetadataFixture(Path path, String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("绪论");

            var body = document.createParagraph();
            var prefix = body.createRun();
            var properties = prefix.getCTR().addNewRPr();
            properties.addNewRFonts().setHint(STHint.EAST_ASIA);
            var language = properties.addNewLang();
            language.setVal("en-US");
            language.setEastAsia("zh-CN");
            prefix.setText(text.substring(0, 2));
            body.createRun().setText(text.substring(2));

            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private PlatformDoubaoDocumentProcessor processor(PlatformDoubaoRewriteGateway gateway) {
        PlatformDoubaoDocumentProcessor processor = new PlatformDoubaoDocumentProcessor(
                gateway, new PlatformDocumentTextProtector());
        processors.add(processor);
        return processor;
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
            var catalogReferences = document.createParagraph();
            catalogReferences.setStyle("TOC1");
            catalogReferences.createRun().setText("参考文献 33");
            document.createParagraph().createRun().setText("1 绪论 7");
            document.createParagraph().createRun().setText("5 参考文献 33");
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("绪论");
            document.createParagraph().createRun().setText(
                    "正文自然语言段落描述系统的实际操作、数据变化与已有结果，长度满足处理条件。");
            document.createParagraph().createRun().setText("致    谢");
            document.createParagraph().createRun().setText(
                    "导师在选题与结构梳理阶段提供了原稿已经记载的帮助，这段致谢只重组已有事实。");
            document.createParagraph().createRun().setText("附录A");
            var appendixHeading = document.createParagraph();
            appendixHeading.setStyle("Heading1");
            appendixHeading.createRun().setText("调查问卷");
            document.createParagraph().createRun().setText(
                    "附录中的长段文字即使位于普通标题之后也不能重新进入大雅处理范围，必须保持原样。");
            document.createParagraph().createRun().setText("参考文献");
            document.createParagraph().createRun().setText(
                    "[1] 参考文献中的中文说明即使很长也不能提交给模型处理，必须保持原样。");
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
                    "第一段正文包含足够多的中文事实，用于确认模型只替换一个词时仍会被最终门禁拒绝；"
                            + "项目范围、原始依据、执行时间和复核结论都保留在段落中。");
            document.createParagraph().createRun().setText(
                    "第二段研究内容主要包括现场记录、原始依据以及复核结论，只有真正发生变化时才增加改写计数。");
            document.createParagraph().createRun().setText("第6章 致谢");
            document.createParagraph().createRun().setText(
                    "编号致谢之后的自然语言即使长度满足条件，也必须保持原文且不得提交模型处理。");
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
                        "本章第" + index + "段研究内容主要包括系统事实、操作过程以及批次边界，用于验证同一章节最多只提交四段正文。");
            }
            var secondHeading = document.createParagraph();
            secondHeading.setStyle("Heading2");
            secondHeading.createRun().setText("2.1 功能说明");
            for (int index = 1; index <= 2; index++) {
                document.createParagraph().createRun().setText(
                        "本节第" + index + "段研究内容主要包括既有事实、测试结果以及章节边界，用于验证批次不会跨越章节上下文。");
            }
            try (OutputStream stream = Files.newOutputStream(path)) {
                document.write(stream);
            }
        }
    }

    private void writeConcurrentFixture(Path path, int paragraphCount) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("关键词：并发测试");
            document.createParagraph().createRun().setText("目录");
            var catalogReferences = document.createParagraph();
            catalogReferences.setStyle("TOC1");
            catalogReferences.createRun().setText("参考文献 33");
            var heading = document.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("绪论");
            for (int index = 1; index <= paragraphCount; index++) {
                String sentence = "项目第" + index
                        + "段记录项目现场事实和既有数据，用于验证大雅批次确实并发处理且文档仍按原顺序写回。";
                document.createParagraph().createRun().setText(sentence.repeat(24));
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
