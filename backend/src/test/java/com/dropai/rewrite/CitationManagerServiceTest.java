package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.CitationManagerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitationManagerServiceTest {
    @Test
    void normalizeRenumbersFormattedReferencesByFirstCitationOrder() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:citation-manager;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbcTemplate.execute("CREATE TABLE writing_chapter (id VARCHAR(64), project_id VARCHAR(64), chapter_no INT, content CLOB, updated_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE writing_reference (id VARCHAR(64), project_id VARCHAR(64), final_number INT, formatted_text CLOB, updated_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE writing_citation (id VARCHAR(64), project_id VARCHAR(64), chapter_id VARCHAR(64), reference_id VARCHAR(64), temporary_marker VARCHAR(120), final_number INT, first_occurrence_order INT, context_text CLOB, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO writing_chapter (id, project_id, chapter_no, content) VALUES ('ch1', 'p1', 1, 'first [[REF:refB]] then [[REF:refA]]')");
        jdbcTemplate.update("INSERT INTO writing_reference (id, project_id, final_number, formatted_text) VALUES ('refA', 'p1', NULL, '[1] Author A. Title A[J]. Journal, 2024.')");
        jdbcTemplate.update("INSERT INTO writing_reference (id, project_id, final_number, formatted_text) VALUES ('refB', 'p1', NULL, '[2] Author B. Title B[J]. Journal, 2024.')");

        new CitationManagerService(jdbcTemplate).normalize("p1");

        Map<String, Object> refB = jdbcTemplate.queryForMap("SELECT final_number, formatted_text FROM writing_reference WHERE id='refB'");
        Map<String, Object> refA = jdbcTemplate.queryForMap("SELECT final_number, formatted_text FROM writing_reference WHERE id='refA'");
        assertEquals(1, ((Number) refB.get("FINAL_NUMBER")).intValue());
        assertEquals("[1] Author B. Title B[J]. Journal, 2024.", refB.get("FORMATTED_TEXT"));
        assertEquals(2, ((Number) refA.get("FINAL_NUMBER")).intValue());
        assertEquals("[2] Author A. Title A[J]. Journal, 2024.", refA.get("FORMATTED_TEXT"));
    }
}
