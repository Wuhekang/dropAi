package com.dropai.rewrite.external;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XuejieDocxValidatorTest {
    private final XuejieDocxValidator validator = new XuejieDocxValidator();

    @Test
    void requiresBothCoreDocxEntriesForUploadAndResult() throws Exception {
        byte[] docx = zip("[Content_Types].xml", "word/document.xml");
        MockMultipartFile upload = new MockMultipartFile("file", "paper.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        assertThatCode(() -> validator.validateUpload(upload)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateResult(docx)).doesNotThrowAnyException();
        Path staged = Files.createTempFile("xuejie-docx-", ".docx");
        try {
            Files.write(staged, docx);
            assertThatCode(() -> validator.validateStagedFile(staged)).doesNotThrowAnyException();
        } finally {
            Files.deleteIfExists(staged);
        }
        assertThatThrownBy(() -> validator.validateResult(zip("word/document.xml")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateUpload(new MockMultipartFile(
                "file", "fake.docx", "application/octet-stream", "PK fake".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scansWholeArchiveAndRejectsEntryFloodEvenWhenCoreEntriesComeFirst() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "[Content_Types].xml");
            add(zip, "word/document.xml");
            for (int index = 0; index < XuejieDocxValidator.MAX_ZIP_ENTRIES; index++) {
                add(zip, "word/media/empty-" + index + ".bin");
            }
        }
        assertThatThrownBy(() -> validator.validateResult(bytes.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(java.util.zip.ZipException.class);
    }

    private byte[] zip(String... names) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : names) {
                add(zip, name);
            }
        }
        return bytes.toByteArray();
    }

    private void add(ZipOutputStream zip, String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write("<xml/>".getBytes());
        zip.closeEntry();
    }
}
