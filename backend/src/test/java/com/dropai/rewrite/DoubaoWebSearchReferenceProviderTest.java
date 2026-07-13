package com.dropai.rewrite;

import com.dropai.rewrite.config.DoubaoProperties;
import com.dropai.rewrite.service.writing.DoubaoWebSearchProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubaoWebSearchReferenceProviderTest {
    @Test
    void rejectsNonPublicUrls() {
        DoubaoWebSearchProvider provider = new DoubaoWebSearchProvider(new DoubaoProperties(), RestClient.builder(), new ObjectMapper());
        assertFalse(provider.isSafePublicUrl("file:///C:/secret.txt"));
        assertFalse(provider.isSafePublicUrl("http://localhost:8080"));
        assertFalse(provider.isSafePublicUrl("http://127.0.0.1/private"));
        assertTrue(provider.isSafePublicUrl("https://www.doi.org/"));
    }
}
