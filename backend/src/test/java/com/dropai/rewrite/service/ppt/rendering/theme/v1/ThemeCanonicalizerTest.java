package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeCanonicalizerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ThemeCanonicalizer canonicalizer = new ThemeCanonicalizer();
    private final ThemeHasher hasher = new ThemeHasher(canonicalizer);

    @Test
    void canonicalFormSortsKeysNormalizesColorsNumbersUnicodeAndLf() throws Exception {
        ObjectNode value = (ObjectNode) mapper.readTree(
                "{\"text\":\"e\\u0301\",\"n\":1.000,\"array\":[2,1],\"a\":\"#aabbcc\"}");

        String canonical = canonicalizer.canonicalize(value);

        assertEquals("{\"a\":\"#AABBCC\",\"array\":[2,1],\"n\":1,\"text\":\"é\"}\n", canonical);
        assertTrue(canonical.endsWith("\n"));
    }

    @Test
    void logicallyEquivalentDocumentsHaveIdenticalHashes() throws Exception {
        ObjectNode first = (ObjectNode) mapper.readTree("{\"b\":1.0,\"a\":\"#ffffff\"}");
        ObjectNode second = (ObjectNode) mapper.readTree("{\"a\":\"#FFFFFF\",\"b\":1}");
        assertEquals(hasher.hash(first), hasher.hash(second));
        assertNotEquals(
                hasher.hash(mapper.readTree("[1,2]")),
                hasher.hash(mapper.readTree("[2,1]")));
    }
}
