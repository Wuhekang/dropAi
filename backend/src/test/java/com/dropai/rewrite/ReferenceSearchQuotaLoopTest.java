package com.dropai.rewrite;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.dropai.rewrite.service.AiRewriteService;
import com.dropai.rewrite.service.writing.ChineseReferenceSearchPlanService;
import com.dropai.rewrite.service.writing.GbT7714Formatter;
import com.dropai.rewrite.service.writing.ReferenceCandidate;
import com.dropai.rewrite.service.writing.ReferenceSearchProvider;
import com.dropai.rewrite.service.writing.ReferenceSearchQuery;
import com.dropai.rewrite.service.writing.ReferenceSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ReferenceSearchQuotaLoopTest {
    @Test
    @SuppressWarnings("unchecked")
    void continuesSearchingUntilBothLanguageQuotasAreSatisfied() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReferenceSearchProvider provider = new ReferenceSearchProvider() {
            public String name() { return "test"; }
            public boolean available() { return true; }
            public List<ReferenceCandidate> search(ReferenceSearchQuery query) {
                int index = calls.incrementAndGet();
                boolean chinese = query.chineseTarget() > 0;
                String language = chinese ? "ZH" : "EN";
                String title = (chinese ? "中文文献" : "English Reference ") + index;
                return List.of(new ReferenceCandidate(title, List.of("Author"), 2025, "Journal", "", "", "", "",
                        "https://example.test/" + index, "TEST", "", "", LocalDateTime.now(), List.of(), 1.0,
                        "VERIFIED", "JOURNAL", language, "JOURNAL_OFFICIAL", title, ""));
            }
        };
        WritingGenerationProperties properties = new WritingGenerationProperties();
        properties.getReferenceSearch().setProvider("test");
        properties.getReferenceSearch().setRetryCount(1);
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:quota-loop;DB_CLOSE_DELAY=-1", "sa", ""));
        ReferenceSearchService service = new ReferenceSearchService(jdbc, properties, List.of(provider), new ObjectMapper(),
                new ChineseReferenceSearchPlanService(), new GbT7714Formatter(), mock(AiRewriteService.class));
        ReferenceSearchQuery query = new ReferenceSearchQuery("p1", "测试主题", "测试专业", List.of(), List.of(),
                2022, 2026, 20, 10, 10);
        Method method = ReferenceSearchService.class.getDeclaredMethod("searchOnline", ReferenceSearchQuery.class);
        method.setAccessible(true);

        List<ReferenceCandidate> result = (List<ReferenceCandidate>) method.invoke(service, query);

        assertEquals(20, result.size());
        assertEquals(10, result.stream().filter(item -> "ZH".equals(item.language())).count());
        assertEquals(10, result.stream().filter(item -> "EN".equals(item.language())).count());
        assertEquals(20, calls.get());
    }
}
