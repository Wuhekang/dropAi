package com.dropai.rewrite.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentCharacterCountService {

    public int countFromAbstractOrCatalog(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            return countFromAbstractOrCatalog(document);
        } catch (IOException exception) {
            throw new IllegalStateException("文档字符数解析失败：" + exception.getMessage(), exception);
        }
    }

    int countFromAbstractOrCatalog(XWPFDocument document) {
        List<String> documentBlocks = document.getBodyElements().stream()
                .map(this::bodyElementText)
                .toList();
        int startIndex = firstMatchingIndex(documentBlocks, this::isAbstractTitle);
        if (startIndex < 0) {
            startIndex = firstMatchingIndex(documentBlocks, this::isCatalogBlock);
        }
        if (startIndex < 0) {
            throw new IllegalArgumentException("未识别到摘要或目录，请确认文档结构后再上传");
        }

        List<String> textBlocks = new ArrayList<>();
        for (int index = startIndex; index < documentBlocks.size(); index++) {
            String text = documentBlocks.get(index);
            if (!text.isBlank()) {
                textBlocks.add(text);
            }
        }
        return normalizeText(String.join("\n", textBlocks)).length();
    }

    private int firstMatchingIndex(List<String> blocks, java.util.function.Predicate<String> predicate) {
        for (int index = 0; index < blocks.size(); index++) {
            if (predicate.test(blocks.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private String bodyElementText(IBodyElement element) {
        if (element instanceof XWPFParagraph paragraph) {
            return normalizeText(paragraph.getText());
        }
        if (element instanceof XWPFTable table) {
            return normalizeText(table.getText());
        }
        if (element instanceof XWPFSDT contentControl) {
            return normalizeText(contentControl.getContent().getText());
        }
        return "";
    }

    private boolean isAbstractTitle(String text) {
        return normalizeText(text).matches("^(摘要|摘\\s*要|Abstract|ABSTRACT)$");
    }

    private boolean isCatalogBlock(String text) {
        return normalizeText(text).matches("(?s)^目\\s*录(?:\\s.*)?$");
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }
}
