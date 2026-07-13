package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.DoubaoWebSearchSourceExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubaoWebSearchSourceExtractorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DoubaoWebSearchSourceExtractor extractor = new DoubaoWebSearchSourceExtractor();

    @Test
    void extractsUrlsFromAnnotations() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"type":"web_search_call"},{"content":[{"annotations":[{"url":"https://example.edu.cn/a","title":"A","snippet":"S"}]}]}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals(1, result.sources().size());
        assertEquals("https://example.edu.cn/a", result.sources().get(0).url());
        assertEquals("$.output[1].content[0].annotations[0].url", result.sources().get(0).rawPath());
    }

    @Test
    void extractsUrlsFromUrlCitationAndSources() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"type":"web_search_call","content":[{"annotations":[{"url_citation":{"url":"https://doi.org/10.1/demo","title":"DOI"}}]}],
                "sources":[{"url":"https://journal.example.org/paper","title":"Journal"}]}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals(2, result.sources().size());
    }

    @Test
    void extractsUrlsFromWebSearchActionSources() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"type":"web_search_call","action":{"sources":[{"url":"https://www.gov.cn/source","title":"Gov"}]}}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals("https://www.gov.cn/source", result.sources().get(0).url());
    }

    @Test
    void fallsBackToTextUrlsAndDeduplicates() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"type":"web_search_call"},{"content":[{"text":"see https://example.com/a and https://example.com/a"}]}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals(1, result.sources().size());
        assertEquals("https://example.com/a", result.sources().get(0).url());
    }

    @Test
    void reportsToolInvocationWithoutSourceFields() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"type":"web_search_call","action":{"query":"GB/T 7714"}}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals(0, result.sources().size());
        assertEquals(0, result.rejectedUrls().size());
    }

    @Test
    void extractsCitationFromStreamingLikeEvents() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"events":[{"type":"response.output_item.added","item":{"type":"web_search_call"}},
                {"type":"response.output_text.annotation.added","annotation":{"url":"https://cbpt.cnki.net/demo","title":"CNKI portal"}},
                {"type":"response.completed"}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals(1, result.sources().size());
        assertEquals("$.events[1].annotation.url", result.sources().get(0).rawPath());
    }

    @Test
    void rejectsPrivateUrls() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"type":"web_search_call","sources":[{"url":"http://127.0.0.1/private"},{"url":"file:///c:/x"}]}]}
                """);

        assertTrue(result.toolInvoked());
        assertEquals(0, result.sources().size());
        assertEquals(2, result.rejectedUrls().size());
    }

    @Test
    void doesNotTreatPlainModelTextAsToolInvocation() throws Exception {
        DoubaoWebSearchSourceExtractor.ExtractionResult result = extract("""
                {"output":[{"content":[{"text":"I know https://example.com from memory."}]}]}
                """);

        assertFalse(result.toolInvoked());
        assertEquals(1, result.sources().size());
    }

    private DoubaoWebSearchSourceExtractor.ExtractionResult extract(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return extractor.extract(node);
    }
}
