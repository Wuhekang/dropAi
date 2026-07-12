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
            String content = WritingJdbc.text(chapter.get("content"));
            Matcher matcher = REF.matcher(content);
            StringBuffer replaced = new StringBuffer();
            while (matcher.find()) {
                String referenceId = matcher.group(1);
                int number = order.computeIfAbsent(referenceId, key -> order.size() + 1);
                matcher.appendReplacement(replaced, "[" + number + "]");
                insertCitation(projectId, WritingJdbc.text(chapter.get("id")), referenceId, number, order.size(), content);
            }
            matcher.appendTail(replaced);
            jdbcTemplate.update("UPDATE writing_chapter SET content=?, updated_at=? WHERE id=?", replaced.toString(), LocalDateTime.now(), chapter.get("id"));
        }
        for (Map.Entry<String, Integer> entry : order.entrySet()) {
            jdbcTemplate.update("UPDATE writing_reference SET final_number=? WHERE id=? AND project_id=?",
                    entry.getValue(), entry.getKey(), projectId);
        }
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
