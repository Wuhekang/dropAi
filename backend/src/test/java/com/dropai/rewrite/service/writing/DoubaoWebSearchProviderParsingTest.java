package com.dropai.rewrite.service.writing;

import com.dropai.rewrite.config.DoubaoProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubaoWebSearchProviderParsingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DoubaoWebSearchProvider provider = new DoubaoWebSearchProvider(
            new DoubaoProperties(), RestClient.builder(), objectMapper);

    @Test
    void parsesFencedJsonStringYearAndObjectAuthors() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {
                  "output": [
                    {"type":"web_search_call","action":{"sources":[{"url":"https://doi.org/10.1000/doki","title":"source"}]}},
                    {"type":"message","content":[{"type":"output_text","text":"```json\\n[{\\"title\\":\\"数字经济研究\\",\\"authors\\":[{\\"name\\":\\"张三\\"},{\\"given\\":\\"Li\\",\\"family\\":\\"Ming\\"}],\\"year\\":\\"2024\\",\\"journalOrPublisher\\":\\"示例学报\\",\\"url\\":\\"https://doi.org/10.1000/doki\\"}]\\n```"}]}
                  ]
                }
                """);

        List<ReferenceCandidate> candidates = provider.candidatesFromResponse(response, query());

        assertEquals(1, candidates.size());
        assertEquals(2024, candidates.get(0).year());
        assertEquals(List.of("张三", "Li Ming"), candidates.get(0).authors());
    }

    @Test
    void rejectsModelOnlyCandidatesWhenWebSearchToolWasNotInvoked() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"output":[{"type":"message","content":[{"type":"output_text","text":"[{\\"title\\":\\"Unverified\\",\\"authors\\":[\\"A\\"],\\"year\\":2024,\\"journalOrPublisher\\":\\"J\\",\\"url\\":\\"https://example.com/paper\\"}]"}]}]}
                """);

        assertTrue(provider.candidatesFromResponse(response, query()).isEmpty());
    }

    @Test
    void clampsProviderTimeoutToFiniteBounds() {
        assertEquals(1, DoubaoWebSearchProvider.boundedTimeoutSeconds(0));
        assertEquals(12, DoubaoWebSearchProvider.boundedTimeoutSeconds(12));
        assertEquals(20, DoubaoWebSearchProvider.boundedTimeoutSeconds(300));
    }

    @Test
    void requiresWebSearchToolWhenForceIsEnabled() {
        DoubaoProperties properties = new DoubaoProperties();
        properties.setWebSearchForce(true);
        DoubaoWebSearchProvider forced = new DoubaoWebSearchProvider(properties, RestClient.builder(), objectMapper);
        assertEquals("required", forced.responsesRequest(query(), true).get("tool_choice"));

        properties.setWebSearchForce(false);
        assertEquals("auto", forced.responsesRequest(query(), true).get("tool_choice"));
    }

    private ReferenceSearchQuery query() {
        return new ReferenceSearchQuery("test", "数字经济", "management", List.of("数字经济"),
                List.of(), 2020, 2026, 5, 5, 0);
    }
}
