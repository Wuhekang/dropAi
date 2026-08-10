package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.WritingQualityGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingQualityGateOutlineNormalizeTest {
    @Test
    void automaticallyNormalizesDuplicateNumberingWithoutFailing() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:outline-normalize;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_chapter(id VARCHAR(64),project_id VARCHAR(64),title VARCHAR(255),sort_order INT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_section(id VARCHAR(64),project_id VARCHAR(64),title VARCHAR(255),sort_order INT,updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO writing_chapter VALUES ('c1','p1','第一章 第1章 绪论',1,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO writing_section VALUES ('s1','p1','1.1 1.1 课题背景',1,CURRENT_TIMESTAMP)");

        WritingQualityGate gate = new WritingQualityGate(jdbc, new ObjectMapper());
        List<String> fixes = gate.normalizeOutline("p1");
        Map<String, Object> chapter = jdbc.queryForMap("SELECT title FROM writing_chapter WHERE id='c1'");
        Map<String, Object> section = jdbc.queryForMap("SELECT title FROM writing_section WHERE id='s1'");

        assertEquals("绪论", chapter.get("title"));
        assertEquals("课题背景", section.get("title"));
        assertEquals(2, fixes.size());
        assertTrue(fixes.stream().allMatch(item -> item.startsWith("AUTO_FIX")));
    }
}
