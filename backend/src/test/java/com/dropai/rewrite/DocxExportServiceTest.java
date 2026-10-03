package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.DocxExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DocxExportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsTemplateWithEditableChartTableAndAutomaticToc() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:docx-export;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_project (id VARCHAR(64), title VARCHAR(255), abstract_text CLOB, keywords_json CLOB, generate_english_abstract BOOLEAN, english_abstract CLOB, generate_toc BOOLEAN, skip_references BOOLEAN)");
        jdbc.execute("CREATE TABLE writing_chapter (id VARCHAR(64), project_id VARCHAR(64), chapter_no INT, title VARCHAR(255))");
        jdbc.execute("CREATE TABLE writing_section (id VARCHAR(64), chapter_id VARCHAR(64), parent_section_id VARCHAR(64), heading_level INT, section_no VARCHAR(32), title VARCHAR(255), sort_order INT, content CLOB)");
        jdbc.execute("CREATE TABLE writing_chart (id VARCHAR(64), chapter_id VARCHAR(64), insert_after_section VARCHAR(64), chart_no VARCHAR(32), title VARCHAR(255), description CLOB, source_name VARCHAR(255), chart_type VARCHAR(32), data_json CLOB, sort_order INT)");
        jdbc.execute("CREATE TABLE writing_table (id VARCHAR(64), chapter_id VARCHAR(64), insert_after_section VARCHAR(64), table_no VARCHAR(32), title VARCHAR(255), note CLOB, header_json CLOB, rows_json CLOB, sort_order INT)");
        jdbc.execute("CREATE TABLE writing_reference (id VARCHAR(64), project_id VARCHAR(64), final_number INT, formatted_text CLOB)");
        jdbc.update("INSERT INTO writing_project VALUES ('p1','测试题目','摘要正文','[\"关键词一\",\"关键词二\"]',FALSE,'',TRUE,TRUE)");
        jdbc.update("INSERT INTO writing_chapter VALUES ('c1','p1',1,'绪论')");
        jdbc.update("INSERT INTO writing_section VALUES ('s1','c1',NULL,2,'1.1','研究背景',1,'这里是研究背景正文。')");
        jdbc.update("INSERT INTO writing_chart VALUES ('g1','c1','s1','1-1','章节结构分布','按大纲统计','本稿大纲','BAR','{\"categories\":[\"第一章\",\"第二章\"],\"series\":[{\"name\":\"二级标题数\",\"values\":[2,3]}]}',1)");
        jdbc.update("INSERT INTO writing_table VALUES ('t1','c1','s1','1-1','研究要点整理','根据本稿正文整理。','[\"小节\",\"要点\"]','[[\"1.1\",\"研究背景\"]]',1)");

        Path output = tempDir.resolve("writing.docx");
        new DocxExportService(jdbc, new ObjectMapper()).export("p1", output);

        assertTrue(Files.size(output) > 1024);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().matches("word/charts/chart\\d+\\.xml")));
            String documentXml = new String(zip.getInputStream(zip.getEntry("word/document.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(documentXml.contains("TOC \\o"));
            assertTrue(documentXml.contains("测试题目"));
            assertTrue(documentXml.contains("研究要点整理"));
            assertFalse(documentXml.contains("参考文献"));
            assertFalse(documentXml.contains("本人声明"));
            assertFalse(documentXml.contains("毕业设计（论文）"));
            assertTrue(documentXml.contains("wp:extent cx=\"5669280\" cy=\"3291840\""));
            assertTrue(documentXml.contains("w:pageBreakBefore"));
        }
    }
}
