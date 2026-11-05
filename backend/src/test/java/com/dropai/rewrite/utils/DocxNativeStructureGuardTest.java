package com.dropai.rewrite.utils;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DocxNativeStructureGuardTest {

    @Test
    void refBookmarkAndCaptionFieldParagraphsStayByteForByte() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph ref = document.createParagraph();
            ref.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            ref.createRun().getCTR().addNewInstrText().setStringValue(" REF _Ref148898 \\h ");
            ref.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            ref.createRun().setText("图1-1");
            ref.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            ref.createRun().setText("所示结构在后续分析中保持稳定，编号不能改写。");

            XWPFParagraph bookmark = document.createParagraph();
            var start = bookmark.getCTP().addNewBookmarkStart();
            start.setName("_Ref148898");
            start.setId(BigInteger.ONE);
            bookmark.createRun().setText("这段被书签包裹的正文必须原样保留，不能送去改写。");
            bookmark.getCTP().addNewBookmarkEnd().setId(BigInteger.ONE);

            XWPFParagraph caption = document.createParagraph();
            var sequence = caption.getCTP().addNewFldSimple();
            sequence.setInstr(" SEQ 图 \\* ARABIC ");
            sequence.addNewR().addNewT().setStringValue("3");
            caption.createRun().setText("图注编号由题注域生成，域指令保持不动。");

            XWPFParagraph link = document.createParagraph();
            var hyperlink = link.getCTP().addNewHyperlink();
            hyperlink.setAnchor("_Ref148898");
            hyperlink.addNewR().addNewT().setStringValue("见图1-1的交叉引用需要保留锚点。");

            for (XWPFParagraph paragraph : new XWPFParagraph[]{ref, bookmark, caption, link}) {
                String before = paragraph.getCTP().xmlText();
                assertThat(DocxNativeStructureGuard.containsLockedStructure(paragraph)).isTrue();
                assertThat(DocxNativeStructureGuard.replacePreservingRuns(paragraph, "改写后的句子。")).isFalse();
                assertThat(paragraph.getCTP().xmlText()).isEqualTo(before);
            }
        }
    }

    @Test
    void plainRunsKeepPropertiesWhileTextIsReplaced() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setStyle("Normal");
            paragraph.setSpacingBetween(1.5);
            var bold = paragraph.createRun();
            bold.setBold(true);
            bold.setText("该平台负责核对资料，");
            var italic = paragraph.createRun();
            italic.setItalic(true);
            italic.setText("并记录现场处理情况。");
            String properties = paragraph.getCTP().getPPr().xmlText();

            assertThat(DocxNativeStructureGuard.containsLockedStructure(paragraph)).isFalse();
            assertThat(DocxNativeStructureGuard.replacePreservingRuns(paragraph, "现场资料已核对，处理情况另有记录。")).isTrue();

            assertThat(paragraph.getText()).isEqualTo("现场资料已核对，处理情况另有记录。");
            assertThat(paragraph.getRuns()).hasSize(2);
            assertThat(paragraph.getRuns().get(0).isBold()).isTrue();
            assertThat(paragraph.getRuns().get(1).isItalic()).isTrue();
            assertThat(paragraph.getCTP().getPPr().xmlText()).isEqualTo(properties);
            assertThat(paragraph.getCTP().xmlText()).doesNotContain("fldChar", "bookmarkStart", "drawing");
        }
    }

    @Test
    void pageNoteAndTocEntriesStayOutOfTheRewriteSpan() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph page = document.createParagraph();
            page.createRun().setText("详见");
            page.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            page.createRun().getCTR().addNewInstrText().setStringValue(" PAGEREF _Toc148898 \\h ");
            page.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            page.createRun().setText("12");
            page.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            page.createRun().setText("页的分析过程必须保持页码域不变。");

            XWPFParagraph note = document.createParagraph();
            note.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            note.createRun().getCTR().addNewInstrText().setStringValue(" NOTEREF _RefNote1 \\h ");
            note.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            note.createRun().setText("1");
            note.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            note.createRun().setText("对应的说明必须保留脚注引用域。");

            XWPFParagraph begin = document.createParagraph();
            begin.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            begin.createRun().getCTR().addNewInstrText().setStringValue(" TOC \\c \"图\" \\h \\z ");
            begin.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);

            XWPFParagraph entry = document.createParagraph();
            entry.createRun().setText("总体结构示意这一目录项必须原样保留，不能送去改写。");

            XWPFParagraph end = document.createParagraph();
            end.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            XWPFParagraph body = document.createParagraph();
            body.createRun().setText("目录之后的正文仍要改写，现场记录保持完整。");

            for (XWPFParagraph paragraph : new XWPFParagraph[]{page, note, begin, end}) {
                String before = paragraph.getCTP().xmlText();
                assertThat(DocxNativeStructureGuard.containsLockedStructure(paragraph)).isTrue();
                assertThat(DocxNativeStructureGuard.replacePreservingRuns(paragraph, "改写后的句子。")).isFalse();
                assertThat(paragraph.getCTP().xmlText()).isEqualTo(before);
            }
            assertThat(DocxNativeStructureGuard.containsLockedStructure(entry)).isFalse();

            DocxNativeStructureGuard.TocFieldScan scan = new DocxNativeStructureGuard.TocFieldScan();
            assertThat(scan.observe(page)).isFalse();
            assertThat(scan.observe(note)).isFalse();
            assertThat(scan.observe(begin)).isTrue();
            assertThat(scan.observe(entry)).isTrue();
            assertThat(scan.observe(end)).isTrue();
            assertThat(scan.observe(body)).isFalse();
        }
    }
}
