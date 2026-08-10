package com.dropai.rewrite;

import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.writing.ChineseReferenceImportService;
import com.dropai.rewrite.service.writing.GbT7714Formatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadedReferenceLibraryTest {
    @Test
    void uploadedFileWithTenReferencesSatisfiesQuotaAndCreatesGbtEntries() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:uploaded-reference;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_project(id VARCHAR(64),user_id BIGINT,chinese_reference_count INT,english_reference_count INT)");
        jdbc.execute("""
                CREATE TABLE writing_reference(id VARCHAR(64),project_id VARCHAR(64),reference_key VARCHAR(80),title VARCHAR(500),authors CLOB,
                publication_year INT,journal_or_publisher VARCHAR(500),doi VARCHAR(255),url VARCHAR(700),source_platform VARCHAR(120),abstract_text CLOB,
                search_keywords VARCHAR(500),searched_at TIMESTAMP,applicable_chapters VARCHAR(255),verification_status VARCHAR(40),relevance_score DECIMAL(8,4),
                formatted_text CLOB,final_number INT,created_at TIMESTAMP,updated_at TIMESTAMP,language VARCHAR(20),source_type VARCHAR(40),provider VARCHAR(80),
                citation_number INT,journal VARCHAR(500),publisher VARCHAR(500),verified_at TIMESTAMP,verification_message CLOB,document_type VARCHAR(80))
                """);
        jdbc.execute("""
                CREATE TABLE writing_reference_import_batch(id VARCHAR(64),project_id VARCHAR(64),user_id BIGINT,source_platform VARCHAR(80),
                original_filename VARCHAR(255),stored_filename VARCHAR(255),file_format VARCHAR(40),file_encoding VARCHAR(40),total_count INT,
                success_count INT,failed_count INT,duplicate_count INT,status VARCHAR(40),error_message CLOB,created_at TIMESTAMP,updated_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO writing_project VALUES ('p1',9,10,0)");
        StringBuilder json = new StringBuilder("[");
        for (int i = 1; i <= 10; i++) {
            if (i > 1) json.append(',');
            json.append("{\"language\":\"ZH\",\"title\":\"上传文献").append(i)
                    .append("\",\"authors\":[\"作者").append(i)
                    .append("\"],\"year\":2025,\"source\":\"测试期刊\",\"doi\":\"\",\"url\":\"\",\"documentType\":\"JOURNAL\"}");
        }
        json.append(']');
        AiRewriteService ai = mock(AiRewriteService.class);
        when(ai.rewrite(anyString(), anyString())).thenReturn(json.toString());
        ChineseReferenceImportService service = new ChineseReferenceImportService(jdbc, ai, new ObjectMapper(), new GbT7714Formatter());
        MockMultipartFile file = new MockMultipartFile("files", "references.txt", "text/plain",
                "10篇参考文献原始文本".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.importFiles(9L, "p1", List.of(file));

        assertEquals(10, ((Number) result.get("successCount")).intValue());
        assertEquals(10L, ((Number) result.get("chineseCount")).longValue());
        assertTrue(Boolean.TRUE.equals(result.get("quotaSatisfied")));
        assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM writing_reference WHERE source_type='UPLOAD'", Integer.class));
        assertEquals(10, jdbc.queryForObject("SELECT COUNT(*) FROM writing_reference WHERE formatted_text LIKE '%[J]%'", Integer.class));
    }
}
