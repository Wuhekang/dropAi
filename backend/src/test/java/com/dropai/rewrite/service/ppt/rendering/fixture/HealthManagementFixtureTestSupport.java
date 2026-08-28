package com.dropai.rewrite.service.ppt.rendering.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class HealthManagementFixtureTestSupport {
    static final String ROOT = "ppt/rendering-fixtures/health-management/v1/";
    static final String MANIFEST_FILE = "fixture-manifest.json";
    static final String TREE_FILE = "validated-presentation-tree.json";
    static final String FORBIDDEN_TEXTS_FILE = "forbidden-texts.json";
    static final String PAGE_SEQUENCE_FILE = "expected-page-sequence.json";
    static final String RENDER_PLAN_STRUCTURE_FILE = "expected-render-plan-structure.json";

    static final List<String> ROOT_JSON_FILES = List.of(
            MANIFEST_FILE,
            TREE_FILE,
            FORBIDDEN_TEXTS_FILE,
            PAGE_SEQUENCE_FILE,
            RENDER_PLAN_STRUCTURE_FILE
    );

    static final ObjectMapper MAPPER = new ObjectMapper();

    private HealthManagementFixtureTestSupport() {
    }

    static JsonNode manifest() {
        return readJson(MANIFEST_FILE);
    }

    static JsonNode tree() {
        return readJson(TREE_FILE);
    }

    static JsonNode readJson(String relativePath) {
        try {
            return MAPPER.readTree(readBytes(relativePath));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot parse fixture JSON: " + relativePath, exception);
        }
    }

    static byte[] readBytes(String relativePath) {
        String classpathResource = ROOT + relativePath;
        try (var input = HealthManagementFixtureTestSupport.class.getClassLoader()
                .getResourceAsStream(classpathResource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing fixture resource: " + classpathResource);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fixture resource: " + classpathResource, exception);
        }
    }

    static Path fixtureRoot() {
        URL resource = HealthManagementFixtureTestSupport.class.getClassLoader().getResource(ROOT);
        if (resource == null) {
            throw new IllegalArgumentException("Missing fixture directory: " + ROOT);
        }
        if (!"file".equals(resource.getProtocol())) {
            throw new IllegalStateException("Fixture tests require file-backed test resources, got: " + resource);
        }
        try {
            URI uri = resource.toURI();
            return Path.of(uri).toAbsolutePath().normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid fixture resource URI: " + resource, exception);
        }
    }

    static Path resolveBundlePath(String bundlePath) {
        assertPortableBundlePath(bundlePath);
        Path root = fixtureRoot();
        Path resolved = root.resolve(bundlePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Bundle path escapes fixture root: " + bundlePath);
        }
        return resolved;
    }

    static void assertPortableBundlePath(String bundlePath) {
        if (bundlePath == null || bundlePath.isBlank()) {
            throw new IllegalArgumentException("Bundle path must not be blank");
        }
        if (bundlePath.contains("\\")) {
            throw new IllegalArgumentException("Bundle path must use '/' separators: " + bundlePath);
        }
        if (bundlePath.startsWith("/") || bundlePath.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Bundle path must be relative: " + bundlePath);
        }
        if (bundlePath.contains("://")) {
            throw new IllegalArgumentException("Bundle path must not be a URL: " + bundlePath);
        }
        for (String segment : bundlePath.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Bundle path contains an unsafe segment: " + bundlePath);
            }
        }
    }

    static String requiredText(JsonNode owner, String field, String context) {
        JsonNode value = owner.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new AssertionError(context + "." + field + " must be a non-blank string");
        }
        return value.textValue();
    }

    static JsonNode requiredArray(JsonNode owner, String field, String context) {
        JsonNode value = owner.get(field);
        if (value == null || !value.isArray()) {
            throw new AssertionError(context + "." + field + " must be an array");
        }
        return value;
    }

    static JsonNode requiredObject(JsonNode owner, String field, String context) {
        JsonNode value = owner.get(field);
        if (value == null || !value.isObject()) {
            throw new AssertionError(context + "." + field + " must be an object");
        }
        return value;
    }

    static int requiredPositiveInt(JsonNode owner, String field, String context) {
        JsonNode value = owner.get(field);
        if (value == null || !value.canConvertToInt() || value.intValue() <= 0) {
            throw new AssertionError(context + "." + field + " must be a positive integer");
        }
        return value.intValue();
    }

    static boolean requiredBoolean(JsonNode owner, String field, String context) {
        JsonNode value = owner.get(field);
        if (value == null || !value.isBoolean()) {
            throw new AssertionError(context + "." + field + " must be a boolean");
        }
        return value.booleanValue();
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            var hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static byte[] canonicalJsonBytes(JsonNode node) {
        var output = new StringBuilder();
        appendCanonical(node, output);
        output.append('\n');
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String canonicalSha256(JsonNode node) {
        return sha256(canonicalJsonBytes(node));
    }

    private static void appendCanonical(JsonNode node, StringBuilder output) {
        if (node == null || node.isNull()) {
            output.append("null");
            return;
        }
        if (node.isObject()) {
            output.append('{');
            Map<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            boolean first = true;
            for (Map.Entry<String, JsonNode> entry : sorted.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                appendQuoted(entry.getKey(), output);
                output.append(':');
                appendCanonical(entry.getValue(), output);
                first = false;
            }
            output.append('}');
            return;
        }
        if (node.isArray()) {
            output.append('[');
            for (int index = 0; index < node.size(); index++) {
                if (index > 0) {
                    output.append(',');
                }
                appendCanonical(node.get(index), output);
            }
            output.append(']');
            return;
        }
        if (node.isTextual()) {
            appendQuoted(node.textValue(), output);
            return;
        }
        if (node.isIntegralNumber()) {
            output.append(node.bigIntegerValue());
            return;
        }
        if (node.isFloatingPointNumber()) {
            BigDecimal decimal = node.decimalValue().stripTrailingZeros();
            output.append(decimal.signum() == 0 ? "0" : decimal.toPlainString());
            return;
        }
        if (node.isBoolean()) {
            output.append(node.booleanValue());
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON value for canonicalization: " + node);
    }

    private static void appendQuoted(String value, StringBuilder output) {
        try {
            output.append(MAPPER.writeValueAsString(value));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot JSON-escape string", exception);
        }
    }

    static void assertObjectKeysLexicographic(JsonNode node, String path) {
        if (node.isObject()) {
            List<String> encountered = new ArrayList<>();
            Iterator<String> names = node.fieldNames();
            names.forEachRemaining(encountered::add);
            List<String> sorted = encountered.stream().sorted(Comparator.naturalOrder()).toList();
            if (!encountered.equals(sorted)) {
                throw new AssertionError("JSON object keys are not lexicographic at " + path
                        + ": expected " + sorted + " but got " + encountered);
            }
            for (String field : encountered) {
                assertObjectKeysLexicographic(node.get(field), path + "." + field);
            }
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertObjectKeysLexicographic(node.get(index), path + "[" + index + "]");
            }
        }
    }

    static List<String> regularFilesUnder(String directory) {
        Path root = resolveBundlePath(directory);
        if (!Files.isDirectory(root)) {
            throw new AssertionError("Missing fixture directory: " + directory);
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> fixtureRoot().relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot enumerate fixture directory: " + directory, exception);
        }
    }
}
