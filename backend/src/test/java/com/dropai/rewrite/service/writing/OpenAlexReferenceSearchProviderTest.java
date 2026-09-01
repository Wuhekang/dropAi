package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAlexReferenceSearchProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAlexReferenceSearchProvider provider = new OpenAlexReferenceSearchProvider(
            RestClient.builder(), new WritingGenerationProperties());

    @Test
    void buildsLanguageAndDateFilteredOverfetchUrlFromRefinedQuery() {
        String url = provider.buildUrl(englishQuery());

        assertTrue(url.contains("search=%22digital+economy%22"));
        assertTrue(url.contains("filter=language:en,from_publication_date:2021-01-01,to_publication_date:2026-12-31"));
        assertTrue(url.endsWith("per-page=10"));
    }

    @Test
    void rebuildsInvertedAbstractAndParsesLanguageAndDocumentType() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "results": [{
                    "title": "Digital economy and regional growth",
                    "publication_year": 2025,
                    "language": "en",
                    "type": "dissertation",
                    "relevance_score": 14.2,
                    "doi": "https://doi.org/10.1000/example",
                    "primary_location": {
                      "landing_page_url": "https://example.edu/thesis",
                      "source": {"display_name": "Example University"}
                    },
                    "authorships": [{"author": {"display_name": "Li Ming"}}],
                    "abstract_inverted_index": {
                      "regional": [3],
                      "Digital": [0],
                      "growth": [4],
                      "economy": [1],
                      "supports": [2]
                    }
                  }]
                }
                """);

        List<ReferenceCandidate> candidates = provider.candidatesFromResponse(response, englishQuery());

        assertEquals(1, candidates.size());
        ReferenceCandidate candidate = candidates.get(0);
        assertEquals("en", candidate.language());
        assertEquals("THESIS", candidate.documentType());
        assertEquals("Digital economy supports regional growth", candidate.abstractText());
        assertEquals("10.1000/example", candidate.doi());
    }

    @Test
    void addsChineseLanguageFilterForChineseOnlyBranch() {
        ReferenceSearchQuery baseQuery = new ReferenceSearchQuery("zh", "数字经济", "中文学术文献",
                List.of("数字经济"), List.of(), 2021, 2026, 4, 4, 0);
        ReferenceSearchQuery query = baseQuery.withProviderKeywords(new LiteratureTopicMatcher().providerQuery(baseQuery));

        String url = provider.buildUrl(query);

        assertTrue(url.contains("filter=language:zh,"));
        assertTrue(url.endsWith("per-page=9"));
    }

    private ReferenceSearchQuery englishQuery() {
        ReferenceSearchQuery query = new ReferenceSearchQuery("en", "数字经济", "English academic literature",
                List.of("数字经济"), List.of(), 2021, 2026, 5, 0, 5);
        return query.withProviderKeywords(new LiteratureTopicMatcher().providerQuery(query));
    }
}
