package com.dropai.rewrite.external;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
public class XuejieDocxValidator {
    static final long MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 20_000;

    public void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择 DOCX 文件");
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("外部平台 DOCX 上传不能超过 100MB");
        }
        try (InputStream input = file.getInputStream()) {
            validateEntries(input, "上传文档");
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取上传 DOCX：" + exception.getMessage(), exception);
        }
    }

    public void validateResult(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("平台适配结果文件为空");
        }
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            validateEntries(input, "外部结果");
        } catch (IOException exception) {
            throw new IllegalArgumentException("平台适配结果不是有效 DOCX", exception);
        }
    }

    public void validateStagedFile(Path path) {
        try {
            if (path == null || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("外部任务的本地源 DOCX 不存在");
            }
            long size = Files.size(path);
            if (size <= 0 || size > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("外部任务的本地源 DOCX 大小无效");
            }
            try (InputStream input = Files.newInputStream(path)) {
                validateEntries(input, "本地源文档");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取外部任务的本地源 DOCX", exception);
        }
    }

    private void validateEntries(InputStream input, String label) throws IOException {
        boolean contentTypes = false;
        boolean documentXml = false;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new ZipException(label + " ZIP 条目数超过上限");
                }
                String name = entry.getName();
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if ("word/document.xml".equals(name)) documentXml = true;
            }
        }
        if (!contentTypes || !documentXml) {
            throw new ZipException(label + " 缺少 DOCX 核心条目 [Content_Types].xml 或 word/document.xml");
        }
    }
}
