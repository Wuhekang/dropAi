package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.WritingQualityGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.OutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingQualityGateChapterTypeTest {
    @Test
    void acceptsContentConclusionReferenceAndAcknowledgementChapters() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:quality-type;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_project(id VARCHAR(64),abstract_text CLOB,keywords_json CLOB)");
        jdbc.execute("CREATE TABLE writing_chapter(id VARCHAR(64),project_id VARCHAR(64),chapter_no INT,title VARCHAR(255),chapter_type VARCHAR(40),content CLOB)");
        jdbc.execute("CREATE TABLE writing_section(id VARCHAR(64),project_id VARCHAR(64),chapter_id VARCHAR(64),section_no VARCHAR(40),sort_order INT,content CLOB)");
        jdbc.execute("CREATE TABLE writing_image_material(id VARCHAR(64),project_id VARCHAR(64),user_confirmed_chapter VARCHAR(64),user_confirmed_section VARCHAR(64),file_path VARCHAR(700),is_confirmed BOOLEAN,display_order INT,created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE writing_image_task(id VARCHAR(64),project_id VARCHAR(64),status VARCHAR(40))");
        jdbc.execute("CREATE TABLE writing_reference(id VARCHAR(64),project_id VARCHAR(64),title VARCHAR(500),authors CLOB,publication_year INT,journal_or_publisher VARCHAR(500),url VARCHAR(700),doi VARCHAR(255),verification_status VARCHAR(40))");
        jdbc.execute("CREATE TABLE writing_chart(id VARCHAR(64),project_id VARCHAR(64),chart_no VARCHAR(40),image_path VARCHAR(500),is_simulated BOOLEAN,description CLOB)");
        jdbc.execute("CREATE TABLE writing_table(id VARCHAR(64),project_id VARCHAR(64),table_no VARCHAR(40),is_confirmed BOOLEAN)");
        jdbc.execute("CREATE TABLE writing_citation(id VARCHAR(64),project_id VARCHAR(64))");
        jdbc.update("INSERT INTO writing_project VALUES ('p1','摘要','[\"关键词\"]')");
        jdbc.update("INSERT INTO writing_chapter VALUES ('c1','p1',1,'第一章 绪论','content','正文')");
        jdbc.update("INSERT INTO writing_chapter VALUES ('c5','p1',5,'第五章 结论与展望','conclusion','研究总结、主要成果、存在不足、未来展望')");
        jdbc.update("INSERT INTO writing_chapter VALUES ('cr','p1',6,'参考文献','reference',NULL)");
        jdbc.update("INSERT INTO writing_chapter VALUES ('ca','p1',7,'致谢','acknowledgement','感谢指导教师和同学的帮助。')");
        jdbc.update("INSERT INTO writing_reference VALUES ('r1','p1','真实文献','作者',2025,'期刊','https://example.test/r1','10.1/test','VERIFIED')");
        Path docx = Files.createTempFile("writing-quality-", ".docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(docx)) {
            document.createParagraph().createRun().setText("测试文档");
            document.write(output);
        }

        Map<String, Object> result = new WritingQualityGate(jdbc, new ObjectMapper()).check("p1", docx);

        assertTrue(Boolean.TRUE.equals(result.get("passed")), String.valueOf(result.get("errors")));
        Files.deleteIfExists(docx);
    }
}
