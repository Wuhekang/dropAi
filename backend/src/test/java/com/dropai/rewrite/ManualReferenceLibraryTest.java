package com.dropai.rewrite;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.writing.ChineseReferenceSearchPlanService;
import com.dropai.rewrite.service.writing.GbT7714Formatter;
import com.dropai.rewrite.service.writing.ReferenceSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ManualReferenceLibraryTest {
    @Test
    void fiveAiPlusFiveManualReferencesSatisfyChineseQuota() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:manual-reference;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE writing_project(id VARCHAR(64),user_id BIGINT,title VARCHAR(255),major VARCHAR(255),chinese_reference_count INT,english_reference_count INT)");
        jdbc.execute("""
                CREATE TABLE writing_reference(id VARCHAR(64),project_id VARCHAR(64),reference_key VARCHAR(80),title VARCHAR(500),authors CLOB,
                publication_year INT,journal_or_publisher VARCHAR(500),volume VARCHAR(80),issue VARCHAR(80),pages VARCHAR(120),doi VARCHAR(255),url VARCHAR(700),
                source_platform VARCHAR(120),abstract_text CLOB,search_keywords VARCHAR(500),searched_at TIMESTAMP,applicable_chapters VARCHAR(255),
                verification_status VARCHAR(40),relevance_score DECIMAL(8,4),formatted_text CLOB,final_number INT,created_at TIMESTAMP,updated_at TIMESTAMP,
                language VARCHAR(20),source_type VARCHAR(40),provider VARCHAR(80),citation_number INT,journal VARCHAR(500),publisher VARCHAR(500),
                verified_at TIMESTAMP,verification_message CLOB,document_type VARCHAR(80))
                """);
        jdbc.update("INSERT INTO writing_project VALUES ('p1',7,'测试论文','测试专业',10,0)");
        for (int i = 1; i <= 5; i++) {
            jdbc.update("""
                    INSERT INTO writing_reference(id,project_id,reference_key,title,authors,publication_year,journal_or_publisher,url,
                    source_platform,searched_at,verification_status,relevance_score,formatted_text,final_number,created_at,updated_at,
                    language,source_type,provider,citation_number)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, "ai" + i, "p1", "ref_00" + i, "AI文献" + i, "作者", 2025, "期刊", "https://example.test/ai" + i,
                    "DOUBAO", LocalDateTime.now(), "VERIFIED_PRIMARY_PUBLIC", 1.0, "[" + i + "] 作者. AI文献[J]. 期刊, 2025.", i,
                    LocalDateTime.now(), LocalDateTime.now(), "ZH", "AI_SEARCH", "DOUBAO", i);
        }
        ReferenceSearchService service = new ReferenceSearchService(jdbc, new WritingGenerationProperties(), List.of(),
                new ObjectMapper(), new ChineseReferenceSearchPlanService(), new GbT7714Formatter(), mock(AiRewriteService.class));
        Map<String, Object> result = Map.of();
        for (int i = 1; i <= 5; i++) {
            result = service.addManualReference(7L, "p1", Map.of(
                    "language", "ZH", "title", "手工文献" + i, "authors", "张三;李四", "year", 2024,
                    "source", "测试期刊", "url", "https://example.test/manual" + i));
        }

        assertEquals(10L, ((Number) result.get("chineseCount")).longValue());
        assertTrue(Boolean.TRUE.equals(result.get("quotaSatisfied")));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM writing_reference WHERE project_id='p1' AND source_type='MANUAL'", Integer.class));
        assertEquals(10, service.references(7L, "p1").size());
        assertTrue(service.references(7L, "p1").stream().filter(row -> "MANUAL".equals(row.get("source_type")))
                .allMatch(row -> String.valueOf(row.get("formatted_text")).contains("[J]")));
    }
}
