package com.dropai.rewrite.service.writing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CitationManagerService {
    private static final Pattern REF = Pattern.compile("\\[\\[REF:([^]]+)]]");
    private final JdbcTemplate jdbcTemplate;

    public CitationManagerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void normalize(String projectId) {
        jdbcTemplate.update("DELETE FROM writing_citation WHERE project_id=?", projectId);
        jdbcTemplate.update("UPDATE writing_reference SET final_number=NULL WHERE project_id=?", projectId);
        LinkedHashMap<String, Integer> order = new LinkedHashMap<>();
        for (Map<String, Object> chapter : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chapter WHERE project_id=? ORDER BY chapter_no", projectId)) {
            String chapterId = WritingJdbc.text(chapter.get("id"));
            List<Map<String, Object>> sections = WritingJdbc.list(jdbcTemplate,
                    "SELECT * FROM writing_section WHERE chapter_id=? ORDER BY sort_order", chapterId);
            if (sections.isEmpty()) {
                String replaced = replaceMarkers(projectId, chapterId, WritingJdbc.text(chapter.get("content")), order);
                jdbcTemplate.update("UPDATE writing_chapter SET content=?, updated_at=? WHERE id=?", replaced, LocalDateTime.now(), chapterId);
                continue;
            }
            StringBuilder chapterContent = new StringBuilder();
            for (Map<String, Object> section : sections) {
                String replaced = replaceMarkers(projectId, chapterId, WritingJdbc.text(section.get("content")), order);
                jdbcTemplate.update("UPDATE writing_section SET content=?,updated_at=? WHERE id=?", replaced, LocalDateTime.now(), section.get("id"));
                chapterContent.append(replaced).append('\n');
            }
            jdbcTemplate.update("UPDATE writing_chapter SET content=?,updated_at=? WHERE id=?", chapterContent.toString().trim(), LocalDateTime.now(), chapterId);
        }
        for (Map.Entry<String, Integer> entry : order.entrySet()) {
            jdbcTemplate.update("UPDATE writing_reference SET final_number=? WHERE id=? AND project_id=?",
                    entry.getValue(), entry.getKey(), projectId);
            String formatted = WritingJdbc.text(WritingJdbc.one(jdbcTemplate,
                    "SELECT formatted_text FROM writing_reference WHERE id=? AND project_id=?", entry.getKey(), projectId).get("formatted_text"));
            jdbcTemplate.update("UPDATE writing_reference SET formatted_text=?, updated_at=? WHERE id=? AND project_id=?",
                    renumberFormatted(formatted, entry.getValue()), LocalDateTime.now(), entry.getKey(), projectId);
        }
    }

    private String replaceMarkers(String projectId, String chapterId, String content, LinkedHashMap<String, Integer> order) {
        Matcher matcher = REF.matcher(content == null ? "" : content);
        StringBuffer replaced = new StringBuffer();
        while (matcher.find()) {
            String referenceId = matcher.group(1);
            if (WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_reference WHERE id=? AND project_id=?", referenceId, projectId).isEmpty()) {
                throw new IllegalStateException("正文引用了不存在的参考文献：" + referenceId);
            }
            int number = order.computeIfAbsent(referenceId, key -> order.size() + 1);
            matcher.appendReplacement(replaced, "[" + number + "]");
            insertCitation(projectId, chapterId, referenceId, number, number, content);
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }

    private String renumberFormatted(String formatted, int number) {
        String body = formatted == null ? "" : formatted.replaceFirst("^\\[\\d+]\\s*", "");
        return "[" + number + "] " + body;
    }

    private void insertCitation(String projectId, String chapterId, String referenceId, int finalNumber, int order, String context) {
        List<Map<String, Object>> refs = WritingJdbc.list(jdbcTemplate, "SELECT id FROM writing_reference WHERE id=? AND project_id=?", referenceId, projectId);
        if (refs.isEmpty()) return;
        jdbcTemplate.update("""
                INSERT INTO writing_citation (id, project_id, chapter_id, reference_id, temporary_marker, final_number,
                first_occurrence_order, context_text, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, WritingJdbc.id("wci"), projectId, chapterId, referenceId, "[[REF:" + referenceId + "]]", finalNumber,
                order, context.length() > 500 ? context.substring(0, 500) : context, LocalDateTime.now(), LocalDateTime.now());
    }
}
