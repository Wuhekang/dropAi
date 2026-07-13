package com.dropai.rewrite.service.writing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ChineseReferenceImportService {
    private final JdbcTemplate jdbcTemplate;

    public ChineseReferenceImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> importFile(Long userId, String projectId, MultipartFile file, String sourcePlatform) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        try {
            byte[] bytes = file.getBytes();
            String encoding = detectEncoding(bytes);
            String text = Charset.forName(encoding).decode(ByteBuffer.wrap(stripBom(bytes))).toString();
            return importText(userId, projectId, text, sourcePlatform, file.getOriginalFilename(), encoding);
        } catch (Exception exception) {
            throw new IllegalArgumentException("REFERENCE_IMPORT_FAILED: " + exception.getMessage(), exception);
        }
    }

    public Map<String, Object> importText(Long userId, String projectId, String text, String sourcePlatform, String filename, String encoding) {
        WritingJdbc.one(jdbcTemplate, "SELECT id FROM writing_project WHERE id=? AND user_id=?", projectId, userId);
        LocalDateTime now = LocalDateTime.now();
        String batchId = WritingJdbc.id("wrib");
        List<ReferenceCandidate> parsed = parseGbTLines(text, sourcePlatform);
        int duplicate = 0;
        int success = 0;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int next = WritingJdbc.integer(WritingJdbc.one(jdbcTemplate, "SELECT COALESCE(MAX(citation_number),0)+1 AS n FROM writing_reference WHERE project_id=?", projectId).get("n"), 1);
        for (ReferenceCandidate candidate : parsed) {
            String key = normalize(candidate.title()) + ":" + candidate.year();
            if (!seen.add(key) || exists(projectId, candidate)) {
                duplicate++;
                continue;
            }
            insert(projectId, candidate, next++);
            success++;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO writing_reference_import_batch (id, project_id, user_id, source_platform, original_filename,
                    stored_filename, file_format, file_encoding, total_count, success_count, failed_count, duplicate_count,
                    status, error_message, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, batchId, projectId, userId, sourcePlatform, filename, "", "TEXT", encoding,
                    parsed.size(), success, Math.max(0, parsed.size() - success - duplicate), duplicate,
                    "SUCCESS", "", now, now);
        } catch (Exception ignored) {
        }
        return Map.of("batchId", batchId, "totalCount", parsed.size(), "successCount", success,
                "failedCount", Math.max(0, parsed.size() - success - duplicate), "duplicateCount", duplicate,
                "fileEncoding", encoding);
    }

    private List<ReferenceCandidate> parseGbTLines(String text, String sourcePlatform) {
        List<ReferenceCandidate> result = new ArrayList<>();
        if (text == null) return result;
        for (String raw : text.split("\\R+")) {
            String line = raw.replaceFirst("^\\s*\\[?\\d+]?[.、\\s]*", "").trim();
            if (line.length() < 8) continue;
            Integer year = null;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(line);
            if (matcher.find()) year = Integer.parseInt(matcher.group());
            String[] parts = line.split("[.．]", 3);
            if (parts.length < 2) continue;
            List<String> authors = splitAuthors(parts[0]);
            String title = parts[1].replaceAll("\\[[JMDCRSPEB/OL-]+]", "").trim();
            if (title.isBlank()) continue;
            result.add(new ReferenceCandidate(title, authors, year, parts.length > 2 ? parts[2].trim() : "",
                    "", "", "", "", "", importPlatform(sourcePlatform), "", "USER_IMPORT",
                    LocalDateTime.now(), List.of(), 0.8, "IMPORTED"));
        }
        return result;
    }

    private void insert(String projectId, ReferenceCandidate candidate, int index) {
        LocalDateTime now = LocalDateTime.now();
        String id = WritingJdbc.id("ref");
        String authors = String.join("; ", candidate.authors());
        String formatted = "[" + index + "] " + authors + ". " + candidate.title() + ". " + candidate.container();
        jdbcTemplate.update("""
                INSERT INTO writing_reference (id, project_id, reference_key, title, authors, publication_year,
                journal_or_publisher, volume, issue, pages, doi, url, source_platform, abstract_text, search_keywords,
                searched_at, applicable_chapters, verification_status, relevance_score, formatted_text, final_number,
                created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, projectId, "ref_" + String.format("%03d", index), candidate.title(), authors,
                candidate.year(), candidate.container(), "", "", "", null, "", candidate.sourcePlatform(), "",
                "USER_IMPORT", now, "", "IMPORTED", candidate.relevanceScore(), formatted, index, now, now);
        try {
            jdbcTemplate.update("UPDATE writing_reference SET language='ZH', provider=?, citation_number=?, retrieved_at=?, source_database=? WHERE id=?",
                    candidate.sourcePlatform(), index, now, candidate.sourcePlatform(), id);
        } catch (Exception ignored) {
        }
    }

    private boolean exists(String projectId, ReferenceCandidate candidate) {
        return !WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_reference WHERE project_id=? AND LOWER(title)=LOWER(?)",
                projectId, candidate.title()).isEmpty();
    }

    private String detectEncoding(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) return "UTF-8";
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) return "UTF-16LE";
        if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff) return "UTF-16BE";
        String utf8 = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
        return utf8.contains("\uFFFD") ? "GB18030" : "UTF-8";
    }

    private byte[] stripBom(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            return java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        return bytes;
    }

    private List<String> splitAuthors(String authors) {
        if (authors == null || authors.isBlank()) return List.of();
        return List.of(authors.split("[,;；，、]+")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private String importPlatform(String sourcePlatform) {
        String source = sourcePlatform == null ? "" : sourcePlatform.toUpperCase();
        if (source.contains("CNKI")) return "IMPORTED_CNKI";
        if (source.contains("WANFANG")) return "IMPORTED_WANFANG";
        if (source.contains("CQVIP")) return "IMPORTED_CQVIP";
        return "IMPORTED_OTHER";
    }

    private String normalize(String title) {
        return title == null ? "" : title.toLowerCase().replaceAll("[^\\p{IsHan}a-z0-9]+", "");
    }
}
