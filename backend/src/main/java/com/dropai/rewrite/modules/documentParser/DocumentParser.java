package com.dropai.rewrite.modules.documentParser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentParser {
    public List<ParsedDocument> parse(List<MultipartFile> files) {
        List<ParsedDocument> result = new ArrayList<>();
        for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
            try {
                String name = file.getOriginalFilename() == null ? "未命名资料" : file.getOriginalFilename();
                String lower = name.toLowerCase();
                String type = classify(lower);
                String text = "";
                if (lower.endsWith(".docx")) {
                    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file.getBytes()))) {
                        text = doc.getParagraphs().stream().map(XWPFParagraph::getText).filter(value -> value != null && !value.isBlank()).reduce("", (a, b) -> a + "\n" + b);
                    }
                } else if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".dxf")) {
                    text = new String(file.getBytes(), StandardCharsets.UTF_8);
                }
                result.add(new ParsedDocument(name, type, text.substring(0, Math.min(text.length(), 12000))));
            } catch (Exception e) { throw new IllegalStateException("解析资料失败：" + e.getMessage(), e); }
        }
        return result;
    }
    private String classify(String name) {
        if (name.contains("任务书")) return "TASK_BOOK";
        if (name.contains("开题")) return "PROPOSAL";
        if (name.contains("模板")) return "THESIS_TEMPLATE";
        if (name.contains("参考") || name.endsWith(".pdf")) return "REFERENCE";
        if (name.endsWith(".dxf") || name.endsWith(".dwg")) return "CAD_REFERENCE";
        if (name.matches(".*\\.(png|jpg|jpeg|webp|bmp)$")) return "IMAGE_REFERENCE";
        return "DOCUMENT";
    }
    public record ParsedDocument(String fileName, String type, String text) {}
}
