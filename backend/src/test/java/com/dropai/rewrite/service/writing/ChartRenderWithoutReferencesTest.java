package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartRenderWithoutReferencesTest {
    @Test
    void createsEditableChartAndTableDataFromManuscriptWithoutReferences() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:chart-no-references;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_chapter (id VARCHAR(64),project_id VARCHAR(64),chapter_no INT,title VARCHAR(255))");
        jdbc.execute("CREATE TABLE writing_section (chapter_id VARCHAR(64),project_id VARCHAR(64),heading_level INT,section_no VARCHAR(32),title VARCHAR(255),content CLOB)");
        jdbc.execute("CREATE TABLE writing_chart (id VARCHAR(64),project_id VARCHAR(64),chart_no VARCHAR(32),title VARCHAR(255),chart_type VARCHAR(32),source_type VARCHAR(64),source_name VARCHAR(255),is_simulated BOOLEAN,data_json CLOB,image_path VARCHAR(255),description CLOB,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_chart_series (id VARCHAR(64),chart_id VARCHAR(64),sort_order INT,series_name VARCHAR(255),source_type VARCHAR(64),data_json CLOB,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_table (id VARCHAR(64),project_id VARCHAR(64),table_no VARCHAR(32),source_type VARCHAR(64),source_name VARCHAR(255),is_simulated BOOLEAN,header_json CLOB,rows_json CLOB,note CLOB,updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO writing_chapter VALUES ('c1','p1',1,'绪论')");
        jdbc.update("INSERT INTO writing_chapter VALUES ('c2','p1',2,'现状')");
        jdbc.update("INSERT INTO writing_section VALUES ('c1','p1',2,'1.1','背景','真实的章节正文')");
        jdbc.update("INSERT INTO writing_section VALUES ('c2','p1',2,'2.1','问题','结构性的分析正文')");
        jdbc.update("INSERT INTO writing_chart(id,project_id,chart_no,title,chart_type) VALUES ('g1','p1','1-1','各章篇幅','LINE')");
        jdbc.update("INSERT INTO writing_chart_series(id,chart_id,sort_order) VALUES ('s1','g1',1)");
        jdbc.update("INSERT INTO writing_table(id,project_id,table_no) VALUES ('t1','p1','2-1')");

        new ChartRenderService(jdbc, new ObjectMapper()).renderProjectCharts("p1", null);

        String chartData = jdbc.queryForObject("SELECT data_json FROM writing_chart WHERE id='g1'", String.class);
        String rows = jdbc.queryForObject("SELECT rows_json FROM writing_table WHERE id='t1'", String.class);
        assertTrue(chartData.contains("正文字符数"));
        assertTrue(chartData.contains("第1章"));
        assertFalse(chartData.contains("文献"));
        assertTrue(rows.contains("结构性的分析正文"));
        assertEquals("MANUSCRIPT_STRUCTURE", jdbc.queryForObject("SELECT source_type FROM writing_chart WHERE id='g1'", String.class));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_simulated FROM writing_chart WHERE id='g1'", Boolean.class));
    }
}
