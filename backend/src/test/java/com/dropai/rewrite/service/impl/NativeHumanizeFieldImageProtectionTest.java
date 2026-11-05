package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.mapper.DocumentJobMapper;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.WorkflowRewriteService;
import com.dropai.rewrite.utils.DocxImageTextPreserver;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Product-owner protections for native 降AI: cross-reference fields, a later TOC
 * field, and drawingML / VML / anchored pictures survive a humanize pass while
 * plain paragraphs are still rewritten in place.
 */
class NativeHumanizeFieldImageProtectionTest {
    @TempDir
    Path temp;

    private static final String REWRITTEN = "现场资料已核对，处理情况另有记录。";
    private static final String PLAIN = "该平台负责核对资料，并记录现场处理情况。";
    private static final String AFTER_TOC = "目录之后的正文仍要改写，现场记录保持完整。";
    private static final String TOC_ENTRY = "总体结构示意这一目录项必须原样保留，不能送去改写。";
    private static final String IMAGE_TEXT = "图片前面的文字用于说明现场情况，图片后面继续记录。";
    private static final String FIELD_IMAGE = "见图所示结构保持图片和交叉引用同时不动，不能送去改写。";
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");

    @Test
    void humanizePassKeepsFieldsImagesAndRunFormatting() throws Exception {
        Path input = temp.resolve("input.docx");
        Path output = temp.resolve("output.docx");
        Map<String, Integer> at = new LinkedHashMap<>();
        try (XWPFDocument document = new XWPFDocument()) {
            text(document, at, "catalog", "目录");
            text(document, at, "catalogEntry", "第一章 绪论\t1");
            text(document, at, "chapter", "第一章 绪论");
            text(document, at, "abstract", "摘要");
            complexField(mark(document, at, "ref"), "如", " REF _Ref148898 \\h ", "图1-1",
                    "所示，该结构在后续分析中保持稳定，编号不能改写。");
            complexField(mark(document, at, "page"), "详见", " PAGEREF _Toc148898 \\h ", "12",
                    "页的分析过程必须保持页码域不变。");
            complexField(mark(document, at, "note"), "脚注", " NOTEREF _RefNote1 \\h ", "1",
                    "对应的说明必须保留脚注引用域。");
            caption(mark(document, at, "caption"));
            complexField(mark(document, at, "captionRef"), "见", " REF _RefCap1 \\h ", "图1-1",
                    "所示题注交叉引用必须保留域代码和书签目标。");
            formatted(mark(document, at, "plain"));
            inlinePicture(mark(document, at, "inline"));
            vmlPicture(mark(document, at, "vml"));
            anchorPicture(mark(document, at, "anchor"));
            XWPFParagraph fieldImage = mark(document, at, "fieldImage");
            fieldImage.createRun().setText(FIELD_IMAGE);
            picture(fieldImage.createRun());
            complexField(fieldImage, "", " REF _RefPic1 \\h ", "图1-1", "");
            openField(mark(document, at, "tocBegin"), " TOC \\c \"图\" \\h \\z ");
            text(document, at, "tocEntry", TOC_ENTRY);
            XWPFParagraph tocEnd = mark(document, at, "tocEnd");
            tocEnd.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            text(document, at, "after", AFTER_TOC);
            text(document, at, "references", "参考文献");
            picture(document.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun());
            complexField(document.createFooter(HeaderFooterType.DEFAULT).createParagraph(),
                    "第", " PAGEREF _TocFooter \\h ", "1", "页");
            try (var stream = Files.newOutputStream(input)) {
                document.write(stream);
            }
        }

        Map<String, String> xml = new LinkedHashMap<>();
        String plainProperties;
        String boldProperties;
        String italicProperties;
        String inlineFingerprint;
        String vmlFingerprint;
        String anchorFingerprint;
        try (var stream = Files.newInputStream(input); XWPFDocument document = new XWPFDocument(stream)) {
            List<String> targets = targetTexts(document, "humanize");
            assertThat(targets).contains(PLAIN, IMAGE_TEXT, AFTER_TOC).doesNotContain(TOC_ENTRY);
            assertThat(targets).noneMatch(NativeHumanizeFieldImageProtectionTest::isProtectedText);
            for (String name : List.of("ref", "page", "note", "caption", "captionRef", "fieldImage",
                    "tocBegin", "tocEntry", "tocEnd")) {
                xml.put(name, paragraph(document, at, name).getCTP().xmlText());
            }
            xml.put("inlineDrawing", drawingXml(paragraph(document, at, "inline")));
            xml.put("anchorDrawing", drawingXml(paragraph(document, at, "anchor")));
            XWPFParagraph plain = paragraph(document, at, "plain");
            plainProperties = plain.getCTP().getPPr().xmlText();
            boldProperties = plain.getRuns().get(0).getCTR().getRPr().xmlText();
            italicProperties = plain.getRuns().get(1).getCTR().getRPr().xmlText();
            inlineFingerprint = DocxImageTextPreserver.structureFingerprint(paragraph(document, at, "inline"));
            vmlFingerprint = DocxImageTextPreserver.structureFingerprint(paragraph(document, at, "vml"));
            anchorFingerprint = DocxImageTextPreserver.structureFingerprint(paragraph(document, at, "anchor"));
            assertThat(xml.get("anchorDrawing")).contains("anchor", "wrapSquare");
            assertThat(paragraph(document, at, "vml").getCTP().xmlText()).contains("imagedata");
        }

        WorkflowRewriteService workflow = mock(WorkflowRewriteService.class);
        when(workflow.execute(anyString(), anyString())).thenAnswer(invocation -> {
            var result = new WorkflowRewriteService.WorkflowRewriteResult();
            result.setRewrittenText(REWRITTEN);
            return result;
        });
        DocumentRewriteServiceImpl service = service(workflow);
        job(service, "field-image-humanize", "humanize");
        try {
            service.process(jobId(service), input, output);
            ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
            verify(workflow, times(5)).execute(texts.capture(), eq("humanize"));
            assertThat(texts.getAllValues()).containsOnly(PLAIN, IMAGE_TEXT, AFTER_TOC);
            assertThat(texts.getAllValues()).noneMatch(NativeHumanizeFieldImageProtectionTest::isProtectedText);
            try (var stream = Files.newInputStream(output); XWPFDocument result = new XWPFDocument(stream)) {
                for (String name : List.of("ref", "page", "note", "caption", "captionRef", "fieldImage",
                        "tocBegin", "tocEntry", "tocEnd")) {
                    assertThat(paragraph(result, at, name).getCTP().xmlText())
                            .as(name)
                            .isEqualTo(xml.get(name));
                }
                XWPFParagraph plain = paragraph(result, at, "plain");
                assertThat(plain.getText()).isEqualTo(REWRITTEN);
                assertThat(plain.getRuns()).hasSize(2);
                assertThat(plain.getRuns().get(0).isBold()).isTrue();
                assertThat(plain.getRuns().get(0).getColor()).isEqualTo("112233");
                assertThat(plain.getRuns().get(1).isItalic()).isTrue();
                assertThat(plain.getRuns().get(1).getUnderline()).isEqualTo(UnderlinePatterns.SINGLE);
                assertThat(plain.getCTP().getPPr().xmlText()).isEqualTo(plainProperties);
                assertThat(plain.getRuns().get(0).getCTR().getRPr().xmlText()).isEqualTo(boldProperties);
                assertThat(plain.getRuns().get(1).getCTR().getRPr().xmlText()).isEqualTo(italicProperties);
                assertThat(paragraph(result, at, "after").getText()).isEqualTo(REWRITTEN);
                assertThat(paragraph(result, at, "inline").getText()).isEqualTo(REWRITTEN);
                assertThat(paragraph(result, at, "vml").getText()).isEqualTo(REWRITTEN);
                assertThat(paragraph(result, at, "anchor").getText()).isEqualTo(REWRITTEN);
                assertThat(drawingXml(paragraph(result, at, "inline"))).isEqualTo(xml.get("inlineDrawing"));
                assertThat(drawingXml(paragraph(result, at, "anchor"))).isEqualTo(xml.get("anchorDrawing"));
                assertThat(DocxImageTextPreserver.structureFingerprint(paragraph(result, at, "inline")))
                        .isEqualTo(inlineFingerprint);
                assertThat(DocxImageTextPreserver.structureFingerprint(paragraph(result, at, "vml")))
                        .isEqualTo(vmlFingerprint);
                assertThat(DocxImageTextPreserver.structureFingerprint(paragraph(result, at, "anchor")))
                        .isEqualTo(anchorFingerprint);
                assertThat(paragraph(result, at, "vml").getCTP().xmlText()).contains("imagedata");
                assertThat(drawingXml(paragraph(result, at, "anchor"))).contains("wrapSquare", "anchor");
            }
            Map<String, byte[]> beforeParts = mediaAndRelations(input);
            Map<String, byte[]> afterParts = mediaAndRelations(output);
            assertThat(afterParts.keySet()).isEqualTo(beforeParts.keySet());
            for (Map.Entry<String, byte[]> part : beforeParts.entrySet()) {
                assertThat(afterParts.get(part.getKey())).as(part.getKey()).isEqualTo(part.getValue());
            }
        } finally {
            service.shutdown();
        }
    }

    @Test
    void rewriteStillSelectsALaterTocEntryThatHumanizeSkips() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("目录");
            document.createParagraph().createRun().setText("第一章 绪论\t1");
            document.createParagraph().createRun().setText("第一章 绪论");
            document.createParagraph().createRun().setText(PLAIN);
            XWPFParagraph begin = document.createParagraph();
            begin.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            begin.createRun().getCTR().addNewInstrText().setStringValue(" TOC \\c \"图\" \\h \\z ");
            begin.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            document.createParagraph().createRun().setText(TOC_ENTRY);
            document.createParagraph().createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            document.createParagraph().createRun().setText(AFTER_TOC);
            document.createParagraph().createRun().setText("参考文献");
            assertThat(targetTexts(document, "humanize")).contains(PLAIN, AFTER_TOC).doesNotContain(TOC_ENTRY);
            assertThat(targetTexts(document, "rewrite")).contains(PLAIN, TOC_ENTRY, AFTER_TOC);
        }
    }

    private static boolean isProtectedText(String text) {
        return text.contains("REF")
                || text.contains("PAGEREF")
                || text.contains("NOTEREF")
                || text.contains("_Ref")
                || text.contains("_Toc")
                || text.contains("TOC")
                || text.contains("目录项必须原样")
                || text.contains("题注")
                || text.contains("页码域")
                || text.contains("脚注引用域")
                || text.contains("交叉引用同时");
    }

    private static XWPFParagraph mark(XWPFDocument document, Map<String, Integer> at, String name) {
        XWPFParagraph paragraph = document.createParagraph();
        at.put(name, document.getParagraphs().size() - 1);
        return paragraph;
    }

    private static void text(XWPFDocument document, Map<String, Integer> at, String name, String value) {
        mark(document, at, name).createRun().setText(value);
    }

    private static XWPFParagraph paragraph(XWPFDocument document, Map<String, Integer> at, String name) {
        return document.getParagraphs().get(at.get(name));
    }

    private static void formatted(XWPFParagraph paragraph) {
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setSpacingBetween(1.5);
        XWPFRun bold = paragraph.createRun();
        bold.setBold(true);
        bold.setFontFamily("宋体");
        bold.setFontSize(12);
        bold.setColor("112233");
        bold.setText("该平台负责核对资料，");
        XWPFRun italic = paragraph.createRun();
        italic.setItalic(true);
        italic.setUnderline(UnderlinePatterns.SINGLE);
        italic.setText("并记录现场处理情况。");
    }

    private static void caption(XWPFParagraph paragraph) {
        var start = paragraph.getCTP().addNewBookmarkStart();
        start.setName("_RefCap1");
        start.setId(BigInteger.ONE);
        var sequence = paragraph.getCTP().addNewFldSimple();
        sequence.setInstr(" SEQ 图 \\* ARABIC ");
        sequence.addNewR().addNewT().setStringValue("3");
        paragraph.createRun().setText("系统结构示意需要保持题注域与书签不被改写。");
        paragraph.getCTP().addNewBookmarkEnd().setId(BigInteger.ONE);
    }

    private static void openField(XWPFParagraph paragraph, String instruction) {
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        paragraph.createRun().getCTR().addNewInstrText().setStringValue(instruction);
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
    }

    private static void complexField(XWPFParagraph paragraph, String before, String instruction,
                                     String result, String after) {
        if (!before.isEmpty()) {
            paragraph.createRun().setText(before);
        }
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
        paragraph.createRun().getCTR().addNewInstrText().setStringValue(instruction);
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
        paragraph.createRun().setText(result);
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
        if (!after.isEmpty()) {
            paragraph.createRun().setText(after);
        }
    }

    private static void inlinePicture(XWPFParagraph paragraph) throws Exception {
        XWPFRun run = paragraph.createRun();
        run.setText(IMAGE_TEXT);
        picture(run);
    }

    private static void vmlPicture(XWPFParagraph paragraph) throws Exception {
        XWPFRun run = paragraph.createRun();
        run.setText(IMAGE_TEXT);
        var picture = picture(run);
        String id = picture.getBlipFill().getBlip().getEmbed();
        String namespaces = " xmlns:w='" + W + "' xmlns:r='" + R + "' xmlns:v='urn:schemas-microsoft-com:vml'";
        run.getCTR().set(CTR.Factory.parse("<xml-fragment" + namespaces + "><w:t>" + IMAGE_TEXT
                + "</w:t><w:pict><v:shape id='fixture' style='width:80pt;height:40pt'><v:imagedata r:id='"
                + id + "'/></v:shape></w:pict></xml-fragment>"));
    }

    private static void anchorPicture(XWPFParagraph paragraph) throws Exception {
        XWPFRun run = paragraph.createRun();
        run.setText(IMAGE_TEXT);
        picture(run);
        var drawing = run.getCTR().getDrawingArray(0);
        var inline = drawing.getInlineArray(0);
        var anchor = drawing.addNewAnchor();
        anchor.setSimplePos2(false);
        anchor.setRelativeHeight(0);
        anchor.setBehindDoc(false);
        anchor.setLocked(false);
        anchor.setLayoutInCell(true);
        anchor.setAllowOverlap(false);
        anchor.addNewSimplePos().setX(0);
        anchor.getSimplePos().setY(0);
        anchor.addNewPositionH().setRelativeFrom(
                org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH.COLUMN);
        anchor.getPositionH().setPosOffset(0);
        anchor.addNewPositionV().setRelativeFrom(
                org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV.PARAGRAPH);
        anchor.getPositionV().setPosOffset(0);
        anchor.setExtent(inline.getExtent());
        try (var cursor = anchor.newCursor()) {
            cursor.toEndToken();
            cursor.beginElement("wrapSquare",
                    "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing");
            cursor.insertAttributeWithValue("wrapText", "bothSides");
        }
        anchor.setDocPr(inline.getDocPr());
        anchor.setGraphic(inline.getGraphic());
        drawing.removeInline(0);
    }

    private static org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture picture(XWPFRun run) throws Exception {
        return run.addPicture(new ByteArrayInputStream(PNG), Document.PICTURE_TYPE_PNG,
                "figure.png", Units.toEMU(80), Units.toEMU(40)).getCTPicture();
    }

    private static String drawingXml(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR().sizeOfDrawingArray() > 0) {
                return run.getCTR().getDrawingArray(0).xmlText();
            }
        }
        throw new AssertionError("missing drawing");
    }

    private static Map<String, byte[]> mediaAndRelations(Path file) throws Exception {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().startsWith("word/media/") || entry.getName().endsWith(".rels")
                        || entry.getName().matches("word/(header|footer).*\\.xml")) {
                    parts.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return parts;
    }

    private static List<String> targetTexts(XWPFDocument document, String mode) throws Exception {
        DocumentRewriteServiceImpl service = new DocumentRewriteServiceImpl(
                null, null, null, null, null, null, null);
        try {
            DocumentRewriteJobVO job = new DocumentRewriteJobVO();
            job.setMode(mode);
            job.setPlatform("GENERAL");
            var collect = DocumentRewriteServiceImpl.class.getDeclaredMethod(
                    "collectRewriteTargets", DocumentRewriteJobVO.class, XWPFDocument.class);
            collect.setAccessible(true);
            List<String> texts = new ArrayList<>();
            for (Object target : (List<?>) collect.invoke(service, job, document)) {
                var text = target.getClass().getDeclaredMethod("text");
                text.setAccessible(true);
                texts.add((String) text.invoke(target));
            }
            return texts;
        } finally {
            service.shutdown();
        }
    }

    private static DocumentRewriteServiceImpl service(WorkflowRewriteService workflow) {
        AiRewriteService ai = mock(AiRewriteService.class);
        when(ai.lastCallProvider()).thenReturn("test-model");
        return new DocumentRewriteServiceImpl(workflow, ai, new DoubaoProperties(),
                mock(DocumentJobMapper.class), new ObjectMapper(), null, null);
    }

    private static void job(DocumentRewriteServiceImpl service, String jobId, String mode) throws Exception {
        DocumentRewriteJobVO job = new DocumentRewriteJobVO();
        job.setJobId(jobId);
        job.setMode(mode);
        job.setModeName(mode);
        job.setPlatform("GENERAL");
        Field field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DocumentRewriteJobVO> jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        jobs.put(jobId, job);
    }

    private static String jobId(DocumentRewriteServiceImpl service) throws Exception {
        Field field = DocumentRewriteServiceImpl.class.getDeclaredField("jobs");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, DocumentRewriteJobVO> jobs = (Map<String, DocumentRewriteJobVO>) field.get(service);
        return jobs.keySet().iterator().next();
    }
}
