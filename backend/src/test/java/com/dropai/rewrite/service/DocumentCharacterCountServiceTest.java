package com.dropai.rewrite.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCharacterCountServiceTest {

    private final DocumentCharacterCountService service = new DocumentCharacterCountService();

    @Test
    void countsEveryDocumentCharacterFromAbstractAndIgnoresFrontMatter() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("封面文字不计费");
            document.createParagraph().createRun().setText("学位论文原创性声明");
            document.createParagraph().createRun().setText("摘要");
            document.createParagraph().createRun().setText("摘要正文");
            document.createTable(1, 1).getRow(0).getCell(0).setText("表格文字");
            document.createParagraph().createRun().setText("第一章 正文");
            document.write(output);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    output.toByteArray()
            );

            assertThat(service.countFromAbstractOrCatalog(file)).isEqualTo("摘要\n摘要正文\n表格文字\n第一章 正文".length());
        }
    }

    @Test
    void fallsBackToCatalogWhenAbstractIsMissing() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("封面文字不计费");
            document.createParagraph().createRun().setText("目录");
            document.createParagraph().createRun().setText("第一章 正文");
            document.write(output);
            MockMultipartFile file = new MockMultipartFile("file", "test.docx", null, output.toByteArray());

            assertThat(service.countFromAbstractOrCatalog(file)).isEqualTo("目录\n第一章 正文".length());
        }
    }

    @Test
    void rejectsDocumentWithoutAbstractOrCatalogInsteadOfChargingIncorrectly() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("第一章 正文");
            document.write(output);
            MockMultipartFile file = new MockMultipartFile("file", "test.docx", null, output.toByteArray());

            assertThatThrownBy(() -> service.countFromAbstractOrCatalog(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未识别到摘要或目录");
        }
    }
}
