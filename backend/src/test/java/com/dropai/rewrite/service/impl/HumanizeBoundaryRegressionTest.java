package com.dropai.rewrite.service.impl;

import com.dropai.rewrite.service.TextStructureProtector;
import com.dropai.rewrite.utils.DocxRewriteBoundary;
import com.dropai.rewrite.vo.DocumentRewriteJobVO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HumanizeBoundaryRegressionTest {
    @ParameterizedTest
    @ValueSource(strings = {"humanize", "double"})
    void includesAllEditableTextAfterManualCatalogWithoutWaitingForHeading(String mode) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            paragraph(document, "封面中的长说明文字不应被处理。");
            paragraph(document, "目 录");
            paragraph(document, "摘要\tI");
            paragraph(document, "第一章 绪论\t1");
            paragraph(document, "参考文献……25");
            String abstractText = "[摘 要]本研究讨论幼小衔接中的学习品质培养，研究对象和结论均来自原文。";
            String body = "没有一级标题也应处理此段，不能因为前一段是关键词就停止改写。";
            paragraph(document, abstractText);
            paragraph(document, "[关键词]幼小衔接；学习品质；教育研究");
            paragraph(document, body);
            paragraph(document, "图 1.1 培养流程");
            document.createTable(1, 1).getRow(0).getCell(0).setText("表格内容不得进入原生正文改写。");
            paragraph(document, "参考文献");
            paragraph(document, "参考内容不应处理，必须保留其原始格式。");
            String before = document.getDocument().xmlText();
            assertThat(targetTexts(document, mode)).containsExactly(abstractText, body);
            assertThat(document.getDocument().xmlText()).isEqualTo(before);
        }
    }

    @Test
    void sdtCatalogWithNoVisibleTitleIsProtectedAndDoesNotNeedAbstractRecognition() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            paragraph(document, "封面中普通正文样式的长句仍属于封面，不能参与改写。");
            var sdt = document.getDocument().getBody().addNewSdt();
            sdt.addNewSdtPr().addNewDocPartObj().addNewDocPartGallery().setVal("Table of Contents");
            sdt.addNewSdtContent().addNewP().addNewR().addNewT().setStringValue("参考文献 20");
            String body = "这是一段没有任何摘要标签或一级标题的正文，目录结束后即可进入处理。";
            paragraph(document, body);
            paragraph(document, "参考文献");
            try (XWPFDocument parsed = reload(document)) {
                assertThat(targetTexts(parsed, "humanize")).containsExactly(body);
            }
        }
    }

    @Test
    void complexTocFieldKeepsNestedPageFieldsAndEntriesOutsideBody() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            var begin = document.createParagraph();
            begin.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            begin.createRun().getCTR().addNewInstrText().setStringValue(" TOC \\o \"1-3\" ");
            begin.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            var entry = document.createParagraph();
            entry.createRun().setText("看起来像正文的目录条目，不得送入模型");
            entry.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            entry.createRun().getCTR().addNewInstrText().setStringValue(" PAGEREF _Toc1 ");
            entry.createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            paragraph(document, "参考文献");
            document.createParagraph().createRun().getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            String body = "这是自动目录域结束后应当完整处理的自然语言段落。";
            paragraph(document, body);
            paragraph(document, "致谢");
            assertThat(targetTexts(document, "humanize")).containsExactly(body);
        }
    }

    @Test
    void abstractBeforeCatalogStillWorksAndCoverHeadingDoesNotOpenScope() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            paragraph(document, "第一章 封面中的目录示例");
            paragraph(document, "封面上的介绍内容应保持原样，不进入模型。");
            paragraph(document, "【摘要】");
            String abstractText = "本研究保留目录之前已经能够明确识别的摘要正文。";
            paragraph(document, abstractText);
            paragraph(document, "【关键词】幼小衔接；学习品质");
            paragraph(document, "目录");
            paragraph(document, "摘要\tI");
            paragraph(document, "参考文献\t9");
            String body = "目录之后没有一级标题，也需要把这段正常正文送入模型。";
            paragraph(document, body);
            paragraph(document, "参考文献");
            assertThat(targetTexts(document, "humanize")).containsExactly(abstractText, body);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[摘 要]", "【摘要】", "（摘要）：", "Abstract:", "[ABSTRACT] "})
    void inlineLabelsAreProtectedAndRestoredWithoutLockingTheWholeParagraph(String label) {
        String source = label + "本段讨论5—6岁幼儿的活动安排，依据见[4]。";
        var protectedText = new TextStructureProtector().protect(source, true);
        assertThat(DocxRewriteBoundary.isInlineAbstract(source)).isTrue();
        assertThat(protectedText.text()).contains("本段讨论").doesNotContain(label.trim());
        assertThat(protectedText.restore(protectedText.text().replace("本段讨论", "这里讨论")))
                .isEqualTo(source.replace("本段讨论", "这里讨论"));
    }

    private static void paragraph(XWPFDocument document, String text) {
        document.createParagraph().createRun().setText(text);
    }

    private static XWPFDocument reload(XWPFDocument document) throws Exception {
        var bytes = new ByteArrayOutputStream();
        document.write(bytes);
        return new XWPFDocument(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static List<String> targetTexts(XWPFDocument document, String mode) throws Exception {
        var service = new DocumentRewriteServiceImpl(null, null, null, null, null, null, null);
        try {
            var job = new DocumentRewriteJobVO();
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
}
