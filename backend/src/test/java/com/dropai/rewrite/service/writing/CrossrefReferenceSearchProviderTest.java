package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.WritingGenerationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossrefReferenceSearchProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CrossrefReferenceSearchProvider provider = new CrossrefReferenceSearchProvider(
            RestClient.builder(), new WritingGenerationProperties());

    @Test
    void buildsServerSidePublicationDateFiltersAndOverfetches() {
        String url = provider.buildUrl(query());

        assertTrue(url.contains("query.bibliographic=%22digital+economy%22"));
        assertTrue(url.contains("filter=from-pub-date:2021-01-01,until-pub-date:2026-12-31"));
        assertTrue(url.endsWith("rows=10"));
    }

    @Test
    void parsesLanguageAndTypeAndStripsJatsMarkupFromAbstract() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "message": {
                    "items": [
                      {
                        "title": ["Digital economy and firm innovation"],
                        "container-title": ["Proceedings of Example Conference"],
                        "published-online": {"date-parts": [[2024, 5, 1]]},
                        "author": [{"given": "Mei", "family": "Chen"}],
                        "type": "proceedings-article",
                        "language": "en",
                        "DOI": "10.1000/crossref",
                        "URL": "https://doi.org/10.1000/crossref",
                        "abstract": "<jats:p>The digital economy &amp; firm innovation.</jats:p>",
                        "score": 23.5
                      },
                      {
                        "title": ["Too old"],
                        "container-title": ["Old Journal"],
                        "published-online": {"date-parts": [[2010]]},
                        "author": [{"family": "Old"}],
                        "URL": "https://example.org/old"
                      }
                    ]
                  }
                }
                """);

        List<ReferenceCandidate> candidates = provider.candidatesFromResponse(response, query());

        assertEquals(1, candidates.size());
        ReferenceCandidate candidate = candidates.get(0);
        assertEquals("en", candidate.language());
        assertEquals("CONFERENCE", candidate.documentType());
        assertEquals("The digital economy & firm innovation.", candidate.abstractText());
        assertEquals("DOI_PAGE", candidate.sourceType());
    }

    private ReferenceSearchQuery query() {
        ReferenceSearchQuery query = new ReferenceSearchQuery("en", "数字经济", "English academic literature",
                List.of("数字经济"), List.of(), 2021, 2026, 5, 0, 5);
        return query.withProviderKeywords(new LiteratureTopicMatcher().providerQuery(query));
    }
}
