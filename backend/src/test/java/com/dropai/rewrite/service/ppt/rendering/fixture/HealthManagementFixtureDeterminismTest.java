package com.dropai.rewrite.service.ppt.rendering.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthManagementFixtureDeterminismTest {
    @Test
    void fixtureDeclaresTheFrozenCanonicalizationContract() {
        JsonNode canonicalization = HealthManagementFixtureTestSupport.requiredObject(
                HealthManagementFixtureTestSupport.manifest(), "canonicalization", "manifest");
        assertEquals("UTF-8", canonicalization.path("encoding").asText());
        assertEquals("LF", canonicalization.path("lineEnding").asText());
        assertEquals("/", canonicalization.path("pathSeparator").asText());
        assertEquals("LEXICOGRAPHIC", canonicalization.path("jsonKeyOrdering").asText());
        assertEquals("PRESERVE", canonicalization.path("arrayOrdering").asText());
    }

    @Test
    void allFixtureJsonSourcesAreCanonicalTextFiles() {
        List<String> jsonFiles = new ArrayList<>(HealthManagementFixtureTestSupport.ROOT_JSON_FILES);
        JsonNode tables = HealthManagementFixtureTestSupport.requiredArray(
                HealthManagementFixtureTestSupport.manifest(), "tables", "manifest");
        for (JsonNode table : tables) {
            jsonFiles.add(HealthManagementFixtureTestSupport.requiredText(
                    table, "bundlePath", "manifest.table"));
        }

        for (String relativePath : jsonFiles) {
            byte[] bytes = HealthManagementFixtureTestSupport.readBytes(relativePath);
            assertTrue(bytes.length > 0, relativePath + " must not be empty");
            assertFalse(hasUtf8Bom(bytes), relativePath + " must not contain a UTF-8 BOM");
            String text = decodeUtf8Strictly(bytes, relativePath);
            assertFalse(text.contains("\r"), relativePath + " must use LF line endings");
            assertTrue(text.endsWith("\n"), relativePath + " must end with one LF");
            assertFalse(text.endsWith("\n\n"), relativePath + " must not end with blank lines");

            String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
            for (int line = 0; line < lines.length; line++) {
                assertFalse(lines[line].matches(".*[ \\t]+$"),
                        relativePath + " has trailing whitespace on line " + (line + 1));
            }

            JsonNode node = HealthManagementFixtureTestSupport.readJson(relativePath);
            HealthManagementFixtureTestSupport.assertObjectKeysLexicographic(node, relativePath + "$");
        }
    }

    @Test
    void presentationTreeCanonicalHashIsStableAndMatchesTheManifest() throws Exception {
        JsonNode tree = HealthManagementFixtureTestSupport.tree();
        byte[] first = HealthManagementFixtureTestSupport.canonicalJsonBytes(tree);
        byte[] second = HealthManagementFixtureTestSupport.canonicalJsonBytes(
                HealthManagementFixtureTestSupport.tree());
        assertArrayEquals(first, second, "Canonicalization must be deterministic across independent reads");

        JsonNode reparsed = HealthManagementFixtureTestSupport.MAPPER.readTree(first);
        byte[] third = HealthManagementFixtureTestSupport.canonicalJsonBytes(reparsed);
        assertArrayEquals(first, third, "Canonicalization must be idempotent");

        String expectedHash = HealthManagementFixtureTestSupport.requiredText(
                HealthManagementFixtureTestSupport.requiredObject(
                        HealthManagementFixtureTestSupport.manifest(), "presentationTree", "manifest"),
                "sha256", "manifest.presentationTree");
        assertEquals(expectedHash, HealthManagementFixtureTestSupport.sha256(first));
        assertEquals(expectedHash, HealthManagementFixtureTestSupport.canonicalSha256(tree));
    }

    @Test
    void canonicalizationPreservesEveryArrayOrder() throws Exception {
        JsonNode original = HealthManagementFixtureTestSupport.tree();
        JsonNode canonical = HealthManagementFixtureTestSupport.MAPPER.readTree(
                HealthManagementFixtureTestSupport.canonicalJsonBytes(original));
        assertArrayOrderRecursively(original, canonical, "tree$");
    }

    private static void assertArrayOrderRecursively(JsonNode expected, JsonNode actual, String path) {
        assertEquals(expected.getNodeType(), actual.getNodeType(), path + " node type changed");
        if (expected.isArray()) {
            assertEquals(expected.size(), actual.size(), path + " array size changed");
            for (int index = 0; index < expected.size(); index++) {
                assertArrayOrderRecursively(expected.get(index), actual.get(index), path + "[" + index + "]");
            }
        } else if (expected.isObject()) {
            expected.fields().forEachRemaining(entry -> {
                assertTrue(actual.has(entry.getKey()), path + " lost field " + entry.getKey());
                assertArrayOrderRecursively(entry.getValue(), actual.get(entry.getKey()),
                        path + "." + entry.getKey());
            });
        } else {
            assertEquals(expected, actual, path + " scalar changed");
        }
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf;
    }

    private static String decodeUtf8Strictly(byte[] bytes, String relativePath) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new AssertionError(relativePath + " is not valid UTF-8", exception);
        }
    }
}
