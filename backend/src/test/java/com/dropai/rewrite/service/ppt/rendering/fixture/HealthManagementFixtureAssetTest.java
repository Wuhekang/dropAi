package com.dropai.rewrite.service.ppt.rendering.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthManagementFixtureAssetTest {
    @Test
    void everyReferencedImageIsFrozenWithExactBytesMimeDimensionsAndMetadata() throws IOException {
        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        JsonNode manifestAssets = HealthManagementFixtureTestSupport.requiredArray(
                manifest, "assets", "manifest");
        Map<String, JsonNode> assetRegistry = uniqueById(manifestAssets, "assetId", "manifest.assets");

        Set<String> bundlePaths = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        int mandatoryAssetCount = 0;
        for (Map.Entry<String, JsonNode> entry : assetRegistry.entrySet()) {
            String assetId = entry.getKey();
            JsonNode asset = entry.getValue();
            String context = "manifest.assets[" + assetId + "]";
            String bundlePath = HealthManagementFixtureTestSupport.requiredText(asset, "bundlePath", context);
            HealthManagementFixtureTestSupport.assertPortableBundlePath(bundlePath);
            assertTrue(bundlePath.startsWith("assets/"), context + " must be stored under assets/");
            assertTrue(bundlePaths.add(bundlePath), "Duplicate asset bundlePath: " + bundlePath);

            Path file = HealthManagementFixtureTestSupport.resolveBundlePath(bundlePath);
            assertTrue(Files.isRegularFile(file), "Missing frozen asset: " + bundlePath);
            byte[] bytes = Files.readAllBytes(file);
            String expectedHash = HealthManagementFixtureTestSupport.requiredText(asset, "sha256", context);
            assertTrue(expectedHash.matches("sha256:[0-9a-f]{64}"), context + " has an invalid hash");
            assertEquals(expectedHash, HealthManagementFixtureTestSupport.sha256(bytes),
                    context + " binary hash changed");
            assertTrue(hashes.add(expectedHash), "Duplicate binary asset bytes should share one assetId: " + assetId);

            String actualMime = detectImageMime(bytes);
            assertEquals(HealthManagementFixtureTestSupport.requiredText(asset, "mimeType", context), actualMime,
                    context + " MIME signature mismatch");
            assertMimeExtensionMatches(actualMime, bundlePath);

            BufferedImage image = ImageIO.read(file.toFile());
            assertNotNull(image, context + " is not a decodable image");
            assertEquals(HealthManagementFixtureTestSupport.requiredPositiveInt(asset, "widthPx", context),
                    image.getWidth(), context + " width changed");
            assertEquals(HealthManagementFixtureTestSupport.requiredPositiveInt(asset, "heightPx", context),
                    image.getHeight(), context + " height changed");
            HealthManagementFixtureTestSupport.requiredText(asset, "imageRole", context);
            HealthManagementFixtureTestSupport.requiredText(asset, "assetKind", context);
            if (HealthManagementFixtureTestSupport.requiredBoolean(asset, "mandatory", context)) {
                mandatoryAssetCount++;
            }
        }

        JsonNode expectations = HealthManagementFixtureTestSupport.requiredObject(
                manifest, "expectations", "manifest");
        assertEquals(expectations.path("mandatoryAssetCount").asInt(-1), mandatoryAssetCount,
                "Manifest mandatoryAssetCount must be computed from the asset registry");

        Map<String, JsonNode> references = referencedAssetsById();
        assertEquals(assetRegistry.keySet(), references.keySet(),
                "Every frozen asset must be referenced and every reference must be registered");
        for (String assetId : assetRegistry.keySet()) {
            JsonNode registered = assetRegistry.get(assetId);
            JsonNode reference = references.get(assetId);
            assertEquals(registered.path("imageRole"), reference.path("imageRole"),
                    assetId + " imageRole differs between tree and manifest");
            assertEquals(registered.path("assetKind"), reference.path("assetKind"),
                    assetId + " assetKind differs between tree and manifest");
            assertEquals(registered.path("mandatory"), reference.path("mandatory"),
                    assetId + " mandatory flag differs between tree and manifest");
        }

        assertEquals(bundlePaths.stream().sorted().toList(),
                HealthManagementFixtureTestSupport.regularFilesUnder("assets"),
                "assets/ must not contain unreferenced or undeclared files");
    }

    @Test
    void everyReferencedTableIsAHashedStructuredPortableModel() throws IOException {
        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        JsonNode manifestTables = HealthManagementFixtureTestSupport.requiredArray(
                manifest, "tables", "manifest");
        Map<String, JsonNode> tableRegistry = uniqueById(manifestTables, "tableId", "manifest.tables");
        Set<String> bundlePaths = new HashSet<>();

        for (Map.Entry<String, JsonNode> entry : tableRegistry.entrySet()) {
            String tableId = entry.getKey();
            JsonNode registered = entry.getValue();
            String context = "manifest.tables[" + tableId + "]";
            String bundlePath = HealthManagementFixtureTestSupport.requiredText(
                    registered, "bundlePath", context);
            HealthManagementFixtureTestSupport.assertPortableBundlePath(bundlePath);
            assertTrue(bundlePath.startsWith("tables/"), context + " must be stored under tables/");
            assertTrue(bundlePaths.add(bundlePath), "Duplicate table bundlePath: " + bundlePath);

            Path file = HealthManagementFixtureTestSupport.resolveBundlePath(bundlePath);
            assertTrue(Files.isRegularFile(file), "Missing structured table: " + bundlePath);
            byte[] bytes = Files.readAllBytes(file);
            String expectedHash = HealthManagementFixtureTestSupport.requiredText(
                    registered, "sha256", context);
            assertTrue(expectedHash.matches("sha256:[0-9a-f]{64}"), context + " has an invalid hash");
            assertEquals(expectedHash, HealthManagementFixtureTestSupport.sha256(bytes),
                    context + " file hash changed");

            JsonNode model = HealthManagementFixtureTestSupport.readJson(bundlePath);
            assertEquals(tableId, HealthManagementFixtureTestSupport.requiredText(model, "tableId", bundlePath));
            assertEquals(
                    HealthManagementFixtureTestSupport.requiredText(registered, "tableKind", context),
                    HealthManagementFixtureTestSupport.requiredText(model, "tableKind", bundlePath)
            );
            assertStructuredTable(model, bundlePath);
        }

        JsonNode expectations = HealthManagementFixtureTestSupport.requiredObject(
                manifest, "expectations", "manifest");
        assertEquals(expectations.path("tableModelCount").asInt(-1), tableRegistry.size(),
                "Manifest tableModelCount must be computed from tables[]");

        Map<String, JsonNode> references = referencedTablesById();
        assertEquals(tableRegistry.keySet(), references.keySet(),
                "Every structured table must be referenced and every reference must be registered");
        for (String tableId : tableRegistry.keySet()) {
            assertEquals(tableRegistry.get(tableId).path("tableKind"), references.get(tableId).path("tableKind"),
                    tableId + " tableKind differs between tree and manifest");
        }

        assertEquals(bundlePaths.stream().sorted().toList(),
                HealthManagementFixtureTestSupport.regularFilesUnder("tables"),
                "tables/ must not contain unreferenced or undeclared files");
    }

    @Test
    void everyBundlePathIsPortableAndContainedByTheFixture() {
        JsonNode manifest = HealthManagementFixtureTestSupport.manifest();
        JsonNode treeReference = HealthManagementFixtureTestSupport.requiredObject(
                manifest, "presentationTree", "manifest");
        assertPortableAndContained(HealthManagementFixtureTestSupport.requiredText(
                treeReference, "path", "manifest.presentationTree"));

        for (JsonNode asset : HealthManagementFixtureTestSupport.requiredArray(manifest, "assets", "manifest")) {
            assertPortableAndContained(HealthManagementFixtureTestSupport.requiredText(
                    asset, "bundlePath", "manifest.asset"));
        }
        for (JsonNode table : HealthManagementFixtureTestSupport.requiredArray(manifest, "tables", "manifest")) {
            assertPortableAndContained(HealthManagementFixtureTestSupport.requiredText(
                    table, "bundlePath", "manifest.table"));
        }
    }

    private static void assertPortableAndContained(String bundlePath) {
        HealthManagementFixtureTestSupport.assertPortableBundlePath(bundlePath);
        Path resolved = HealthManagementFixtureTestSupport.resolveBundlePath(bundlePath);
        assertTrue(resolved.startsWith(HealthManagementFixtureTestSupport.fixtureRoot()));
    }

    private static Map<String, JsonNode> referencedAssetsById() {
        Map<String, JsonNode> references = new LinkedHashMap<>();
        JsonNode pages = HealthManagementFixtureTestSupport.requiredArray(
                HealthManagementFixtureTestSupport.tree(), "pages", "tree");
        for (JsonNode page : pages) {
            String pageId = page.path("sourcePageId").asText("unknown-page");
            JsonNode assets = HealthManagementFixtureTestSupport.requiredArray(page, "assets", pageId);
            for (JsonNode asset : assets) {
                String assetId = HealthManagementFixtureTestSupport.requiredText(asset, "assetId", pageId + ".asset");
                HealthManagementFixtureTestSupport.requiredText(asset, "imageRole", pageId + ".asset");
                HealthManagementFixtureTestSupport.requiredText(asset, "assetKind", pageId + ".asset");
                HealthManagementFixtureTestSupport.requiredBoolean(asset, "mandatory", pageId + ".asset");
                JsonNode previous = references.putIfAbsent(assetId, asset);
                if (previous != null) {
                    assertEquals(previous, asset, "Repeated asset reference metadata differs: " + assetId);
                }
            }
        }
        return references;
    }

    private static Map<String, JsonNode> referencedTablesById() {
        Map<String, JsonNode> references = new LinkedHashMap<>();
        JsonNode pages = HealthManagementFixtureTestSupport.requiredArray(
                HealthManagementFixtureTestSupport.tree(), "pages", "tree");
        for (JsonNode page : pages) {
            String pageId = page.path("sourcePageId").asText("unknown-page");
            JsonNode tables = HealthManagementFixtureTestSupport.requiredArray(page, "tables", pageId);
            for (JsonNode table : tables) {
                String tableId = HealthManagementFixtureTestSupport.requiredText(table, "tableId", pageId + ".table");
                HealthManagementFixtureTestSupport.requiredText(table, "tableKind", pageId + ".table");
                JsonNode previous = references.putIfAbsent(tableId, table);
                if (previous != null) {
                    assertEquals(previous, table, "Repeated table reference metadata differs: " + tableId);
                }
            }
        }
        return references;
    }

    private static Map<String, JsonNode> uniqueById(JsonNode array, String idField, String context) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        for (int index = 0; index < array.size(); index++) {
            JsonNode item = array.get(index);
            String id = HealthManagementFixtureTestSupport.requiredText(
                    item, idField, context + "[" + index + "]");
            assertTrue(indexed.putIfAbsent(id, item) == null, "Duplicate " + idField + ": " + id);
        }
        return indexed;
    }

    private static void assertStructuredTable(JsonNode model, String context) {
        JsonNode columns = HealthManagementFixtureTestSupport.requiredArray(model, "columns", context);
        JsonNode rows = HealthManagementFixtureTestSupport.requiredArray(model, "rows", context);
        assertFalse(columns.isEmpty(), context + " columns must not be empty");
        assertFalse(rows.isEmpty(), context + " rows must not be empty");

        Set<String> columnNames = new LinkedHashSet<>();
        for (JsonNode column : columns) {
            assertTrue(column.isTextual() && !column.textValue().isBlank(),
                    context + " columns must be non-blank strings");
            assertTrue(columnNames.add(column.textValue()), context + " contains duplicate column " + column);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            assertTrue(row.isArray(), context + ".rows[" + rowIndex + "] must be an array");
            assertEquals(columns.size(), row.size(), context + ".rows[" + rowIndex + "] width mismatch");
            for (JsonNode cell : row) {
                assertTrue(cell.isValueNode() && !cell.isNull(),
                        context + " table cells must be scalar JSON values");
            }
        }
    }

    private static String detectImageMime(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(header) || "GIF89a".equals(header)) {
                return "image/gif";
            }
        }
        throw new AssertionError("Unsupported or invalid frozen image signature");
    }

    private static void assertMimeExtensionMatches(String mimeType, String bundlePath) {
        String lower = bundlePath.toLowerCase(Locale.ROOT);
        boolean matches = switch (mimeType) {
            case "image/png" -> lower.endsWith(".png");
            case "image/jpeg" -> lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            case "image/gif" -> lower.endsWith(".gif");
            default -> false;
        };
        assertTrue(matches, bundlePath + " extension does not match " + mimeType);
    }
}
