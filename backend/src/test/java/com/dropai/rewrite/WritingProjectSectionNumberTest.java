package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.DoubaoWritingService;
import com.dropai.rewrite.service.writing.WritingProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WritingProjectSectionNumberTest {
    @Test
    void thirdLevelPlaceholdersAreUniqueAcrossParentsAndRenumberInOriginalOrder() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:section-number;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_project (id VARCHAR(64) PRIMARY KEY, chapter_count INT, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_chapter (id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64), chapter_no INT, sort_order INT, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_section (id VARCHAR(64) PRIMARY KEY, project_id VARCHAR(64), chapter_id VARCHAR(64), parent_section_id VARCHAR(64), heading_level INT, section_no VARCHAR(40), title VARCHAR(255), target_word_count INT, sort_order INT, status VARCHAR(30), created_at TIMESTAMP, updated_at TIMESTAMP, CONSTRAINT uk_writing_section_no UNIQUE(chapter_id,section_no))");
        jdbc.execute("CREATE TABLE writing_chart (id VARCHAR(64), chapter_id VARCHAR(64), chart_no VARCHAR(40), sort_order INT, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_table (id VARCHAR(64), chapter_id VARCHAR(64), table_no VARCHAR(40), sort_order INT, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO writing_project (id) VALUES ('p1')");
        jdbc.update("INSERT INTO writing_chapter (id,project_id,chapter_no,sort_order,created_at) VALUES ('ch1','p1',1,1,CURRENT_TIMESTAMP())");
        WritingProjectService service = new WritingProjectService(jdbc, new ObjectMapper(), mock(DoubaoWritingService.class));

        insert(service, "", 2, 1, "问题识别");
        insert(service, "", 2, 2, "方案设计");
        List<String> parents = jdbc.queryForList("SELECT id FROM writing_section WHERE heading_level=2 ORDER BY sort_order", String.class);
        insert(service, parents.get(0), 3, 1, "现状");
        insert(service, parents.get(0), 3, 2, "成因");
        insert(service, parents.get(1), 3, 1, "目标");
        insert(service, parents.get(1), 3, 2, "措施");

        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM writing_section", Integer.class));
        ReflectionTestUtils.invokeMethod(service, "renumberProject", "p1");
        List<Map<String, Object>> children = jdbc.queryForList("SELECT section_no,title FROM writing_section WHERE heading_level=3 ORDER BY section_no");
        assertEquals(List.of("1.1.1", "1.1.2", "1.2.1", "1.2.2"), children.stream().map(row -> String.valueOf(row.get("SECTION_NO"))).toList());
        assertEquals(List.of("现状", "成因", "目标", "措施"), children.stream().map(row -> String.valueOf(row.get("TITLE"))).toList());
        assertTrue(children.stream().noneMatch(row -> String.valueOf(row.get("SECTION_NO")).startsWith("tmp")));
    }

    private void insert(WritingProjectService service, String parent, int level, int order, String title) {
        ReflectionTestUtils.invokeMethod(service, "insertSection", "p1", "ch1", parent, level, order, title, 300);
    }
}
