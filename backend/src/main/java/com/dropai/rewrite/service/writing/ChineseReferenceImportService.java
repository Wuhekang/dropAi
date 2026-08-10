package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.service.AiRewriteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChineseReferenceImportService {
    private static final Set<String> EXTENSIONS = Set.of("txt", "doc", "docx", "pdf", "ris", "bib", "csv", "xlsx");
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private final JdbcTemplate jdbcTemplate;
    private final AiRewriteService aiRewriteService;
    private final ObjectMapper objectMapper;
    private final GbT7714Formatter formatter;

    public ChineseReferenceImportService(JdbcTemplate jdbcTemplate, AiRewriteService aiRewriteService,
                                         ObjectMapper objectMapper, GbT7714Formatter formatter) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiRewriteService = aiRewriteService;
        this.objectMapper = objectMapper;
        this.formatter = formatter;
    }

    public Map<String, Object> importFiles(Long userId, String projectId, List<MultipartFile> files) {
        Map<String, Object> project = WritingJdbc.one(jdbcTemplate,
                "SELECT * FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("请选择参考文献文件");
        List<Map<String, Object>> fileResults = new ArrayList<>();
        int success = 0, duplicate = 0, failed = 0;
        for (MultipartFile file : files) {
            try {
                Map<String, Object> result = importFile(userId, projectId, file, "UPLOAD");
                success += WritingJdbc.integer(result.get("successCount"), 0);
                duplicate += WritingJdbc.integer(result.get("duplicateCount"), 0);
                failed += WritingJdbc.integer(result.get("failedCount"), 0);
                fileResults.add(result);
            } catch (Exception exception) {
                failed++;
                fileResults.add(Map.of("fileName", safeName(file), "successCount", 0, "failedCount", 1,
                        "duplicateCount", 0, "message", exception.getMessage()));
            }
        }
        List<Map<String, Object>> references = references(projectId);
        long zh = count(references, "ZH"), en = count(references, "EN");
        int targetZh = WritingJdbc.integer(project.get("chinese_reference_count"), 0);
        int targetEn = WritingJdbc.integer(project.get("english_reference_count"), 0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("files", fileResults);
        summary.put("successCount", success);
        summary.put("failedCount", failed);
        summary.put("duplicateCount", duplicate);
        summary.put("references", references);
        summary.put("chineseCount", zh);
        summary.put("englishCount", en);
        summary.put("targetChineseCount", targetZh);
        summary.put("targetEnglishCount", targetEn);
        summary.put("missingChineseCount", Math.max(0, targetZh - (int) zh));
        summary.put("missingEnglishCount", Math.max(0, targetEn - (int) en));
        summary.put("quotaSatisfied", zh >= targetZh && en >= targetEn);
        return summary;
    }

    public Map<String, Object> importFile(Long userId, String projectId, MultipartFile file, String sourcePlatform) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        validate(file);
        try {
            String text = extractText(file);
            List<ReferenceCandidate> parsed = parseWithAi(text);
            return saveBatch(userId, projectId, file, parsed);
        } catch (Exception exception) {
            throw new IllegalArgumentException("REFERENCE_IMPORT_FAILED: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> saveBatch(Long userId, String projectId, MultipartFile file, List<ReferenceCandidate> parsed) {
        int duplicate = 0, success = 0;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate,
                "SELECT COALESCE(MAX(citation_number),0)+1 AS n FROM writing_reference WHERE project_id=?", projectId).get("n"), 1);
        for (ReferenceCandidate candidate : parsed) {
            String key = normalize(candidate.title()) + ":" + candidate.year();
            if (!seen.add(key) || exists(projectId, candidate)) { duplicate++; continue; }
            insert(projectId, candidate, next++);
            success++;
        }
        String batchId = WritingJdbc.id("wrib");
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO writing_reference_import_batch (id,project_id,user_id,source_platform,original_filename,
                    stored_filename,file_format,file_encoding,total_count,success_count,failed_count,duplicate_count,
                    status,error_message,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, batchId, projectId, userId, "UPLOAD", safeName(file), "", extension(file).toUpperCase(Locale.ROOT),
                    "EXTRACTED", parsed.size(), success, Math.max(0, parsed.size() - success - duplicate), duplicate,
                    "SUCCESS", "", now, now);
        } catch (Exception ignored) { }
        return Map.of("batchId", batchId, "fileName", safeName(file), "totalCount", parsed.size(), "successCount", success,
                "failedCount", Math.max(0, parsed.size() - success - duplicate), "duplicateCount", duplicate);
    }

    private List<ReferenceCandidate> parseWithAi(String text) throws Exception {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("文件中未提取到可解析文本");
        String input = text.length() > 50000 ? text.substring(0, 50000) : text;
        String response = aiRewriteService.rewrite("""
                从下面内容中提取所有参考文献，只输出JSON数组，不要Markdown。
                每项字段：language(ZH或EN),title,authors(数组),year(整数),source,doi,url,documentType(JOURNAL/BOOK/THESIS/CONFERENCE/ONLINE)。
                不补造不存在的信息；跳过无法识别为参考文献的正文。内容：
                %s
                """.formatted(input), "REFERENCE_IMPORT_PARSE").trim();
        int start = response.indexOf('['), end = response.lastIndexOf(']');
        if (start < 0 || end <= start) throw new IllegalArgumentException("AI未返回参考文献数组");
        JsonNode array = objectMapper.readTree(response.substring(start, end + 1));
        List<ReferenceCandidate> result = new ArrayList<>();
        for (JsonNode node : array) {
            String title = node.path("title").asText("").trim();
            List<String> authors = new ArrayList<>();
            node.path("authors").forEach(value -> { if (!value.asText().isBlank()) authors.add(value.asText().trim()); });
            int year = node.path("year").asInt(0);
            String source = node.path("source").asText("").trim();
            if (title.isBlank() || authors.isEmpty() || year < 1900 || source.isBlank()) continue;
            String language = node.path("language").asText("");
            if (!"ZH".equalsIgnoreCase(language) && !"EN".equalsIgnoreCase(language)) language = containsChinese(title) ? "ZH" : "EN";
            result.add(new ReferenceCandidate(title, authors, year, source, "", "", "",
                    node.path("doi").asText(""), node.path("url").asText(""), "UPLOAD", "", "UPLOAD",
                    LocalDateTime.now(), List.of(), 0.9, "VERIFIED", node.path("documentType").asText("JOURNAL"),
                    language.toUpperCase(Locale.ROOT), "UPLOAD", source, ""));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("AI未识别出完整参考文献");
        return result;
    }

    private String extractText(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        return switch (extension(file)) {
            case "docx" -> { try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                yield doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b); } }
            case "doc" -> { try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes)); WordExtractor extractor = new WordExtractor(doc)) { yield extractor.getText(); } }
            case "pdf" -> { try (var doc = Loader.loadPDF(bytes)) { yield new PDFTextStripper().getText(doc); } }
            case "xlsx" -> spreadsheetText(bytes);
            default -> decode(bytes);
        };
    }

    private String spreadsheetText(byte[] bytes) throws Exception {
        StringBuilder text = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            for (var sheet : workbook) for (Row row : sheet) {
                for (Cell cell : row) text.append(cell.toString()).append('\t');
                text.append('\n');
            }
        }
        return text.toString();
    }

    private void insert(String projectId, ReferenceCandidate candidate, int index) {
        LocalDateTime now = LocalDateTime.now();
        String id = WritingJdbc.id("ref");
        String formatted = formatter.format(index, candidate, "GBT_7714_2025");
        jdbcTemplate.update("""
                INSERT INTO writing_reference (id,project_id,reference_key,title,authors,publication_year,journal_or_publisher,
                doi,url,source_platform,abstract_text,search_keywords,searched_at,applicable_chapters,verification_status,
                relevance_score,formatted_text,final_number,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, projectId, "ref_" + String.format("%03d", index), candidate.title(), String.join("; ", candidate.authors()),
                candidate.year(), candidate.container(), empty(candidate.doi()), empty(candidate.url()), "UPLOAD", "", "UPLOAD", now, "",
                "VERIFIED", candidate.relevanceScore(), formatted, index, now, now);
        jdbcTemplate.update("""
                UPDATE writing_reference SET language=?,source_type='UPLOAD',provider='UPLOAD',citation_number=?,journal=?,publisher=?,
                verified_at=?,verification_message='AI从用户上传文件解析',document_type=? WHERE id=?
                """, candidate.language(), index, candidate.container(), candidate.container(), now, candidate.documentType(), id);
    }

    private List<Map<String, Object>> references(String projectId) {
        return WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_reference WHERE project_id=? ORDER BY citation_number", projectId);
    }
    private long count(List<Map<String, Object>> rows, String language) { return rows.stream().filter(r -> language.equalsIgnoreCase(WritingJdbc.text(r.get("language")))).filter(r -> !"PENDING".equalsIgnoreCase(WritingJdbc.text(r.get("verification_status")))).count(); }
    private boolean exists(String projectId, ReferenceCandidate candidate) { return !WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_reference WHERE project_id=? AND LOWER(title)=LOWER(?)", projectId, candidate.title()).isEmpty(); }
    private void validate(MultipartFile file) { if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空"); if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("单个文件不能超过20MB"); if (!EXTENSIONS.contains(extension(file))) throw new IllegalArgumentException("仅支持 txt、doc、docx、pdf、ris、bib、csv、xlsx"); }
    private String extension(MultipartFile file) { String name = safeName(file); int dot = name.lastIndexOf('.'); return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private String safeName(MultipartFile file) { String name = file == null || file.getOriginalFilename() == null ? "references" : file.getOriginalFilename(); return name.replaceAll("[\\r\\n]", "_"); }
    private String decode(byte[] bytes) { String utf8 = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString(); String encoding = utf8.contains("\uFFFD") ? "GB18030" : "UTF-8"; return Charset.forName(encoding).decode(ByteBuffer.wrap(bytes)).toString(); }
    private String normalize(String title) { return title == null ? "" : title.toLowerCase().replaceAll("[^\\p{IsHan}a-z0-9]+", ""); }
    private boolean containsChinese(String value) { return value != null && value.matches(".*[\\p{IsHan}].*"); }
    private String empty(String value) { return value == null || value.isBlank() ? null : value; }
}
