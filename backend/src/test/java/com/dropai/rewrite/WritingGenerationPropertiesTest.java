package com.dropai.rewrite;

import com.dropai.rewrite.config.WritingGenerationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingGenerationPropertiesTest {
    @Test
    void defaultsToFastMultiSourceFallback() {
        WritingGenerationProperties.ReferenceSearch search = new WritingGenerationProperties().getReferenceSearch();

        assertEquals(List.of("doubao_web", "openalex", "crossref"), search.providerOrder());
        assertEquals(20, search.getTimeoutSeconds());
        assertEquals(1, search.getRetryCount());
        assertEquals(8, search.getParallelism());
        assertTrue(search.isPublicFallbackEnabled());
    }

    @Test
    void appendsPublicFallbacksWithoutDuplicatingConfiguredProviders() {
        WritingGenerationProperties.ReferenceSearch search = new WritingGenerationProperties.ReferenceSearch();
        search.setProvider("crossref, doubao_web, crossref");

        assertEquals(List.of("crossref", "doubao_web", "openalex"), search.providerOrder());
    }
}
