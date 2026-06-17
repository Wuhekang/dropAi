package com.dropai.rewrite.modules.documentParser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
            String name = file.getOriginalFilename() == null ? "未命名资料" : file.getOriginalFilename();
            String lower = name.toLowerCase();
            String type = classify(lower);
            try {
                String text = readText(file, lower);
                boolean readable = !text.isBlank();
                String status = readable || type.equals("IMAGE_REFERENCE") || type.equals("CAD_REFERENCE") ? "success" : "failed";
                String reason = readable ? "" : switch (type) {
                    case "IMAGE_REFERENCE" -> "图片参考图已接收，当前阶段不读取图片文字";
                    case "CAD_REFERENCE" -> "CAD参考图已接收，当前阶段仅作为结构参考";
                    default -> "未读取到可用文字内容";
                };
                result.add(new ParsedDocument(name, type, trim(text), status, readable, reason));
            } catch (Exception exception) {
                result.add(new ParsedDocument(name, type, "", "failed", false, "资料解析失败：" + compact(exception.getMessage())));
            }
        }
        return result;
    }

    private String readText(MultipartFile file, String lower) throws Exception {
        if (lower.endsWith(".docx")) {
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file.getBytes()))) {
                return doc.getParagraphs().stream()
                        .map(XWPFParagraph::getText)
                        .filter(value -> value != null && !value.isBlank())
                        .reduce("", (a, b) -> a + "\n" + b);
            }
        }
        if (lower.endsWith(".pdf")) {
            try (PDDocument pdf = Loader.loadPDF(file.getBytes())) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".dxf")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
        return "";
    }

    private String classify(String name) {
        if (name.contains("任务书") || name.contains("task")) return "TASK_BOOK";
        if (name.contains("开题") || name.contains("proposal")) return "PROPOSAL";
        if (name.contains("模板") || name.contains("template")) return "THESIS_TEMPLATE";
        if (name.contains("参考文献") || name.contains("reference") || name.endsWith(".pdf")) return "REFERENCE";
        if (name.endsWith(".dxf") || name.endsWith(".dwg")) return "CAD_REFERENCE";
        if (name.matches(".*\\.(png|jpg|jpeg|webp|bmp)$")) return "IMAGE_REFERENCE";
        return "DOCUMENT";
    }

    private String trim(String text) {
        String value = text == null ? "" : text.trim();
        return value.substring(0, Math.min(value.length(), 12000));
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) return "无详细错误";
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 260 ? result.substring(0, 260) + "..." : result;
    }

    public record ParsedDocument(String fileName, String type, String text, String status, boolean textReadable, String failureReason) {
        public ParsedDocument(String fileName, String type, String text) {
            this(fileName, type, text, text == null || text.isBlank() ? "failed" : "success",
                    text != null && !text.isBlank(), text == null || text.isBlank() ? "未读取到可用文字内容" : "");
        }
    }
}
