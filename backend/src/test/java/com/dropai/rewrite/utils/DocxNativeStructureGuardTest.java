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
}
