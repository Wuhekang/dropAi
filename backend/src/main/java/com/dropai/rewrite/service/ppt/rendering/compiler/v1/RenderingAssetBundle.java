package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exact asset/table registry supplied by the upstream binding stage. Paths are portable
 * bundle paths; this type never scans a document or performs fuzzy matching.
 */
public final class RenderingAssetBundle {
    private final ArrayNode assets;
    private final ArrayNode tableRegistry;
    private final Map<String, ObjectNode> assetsById;
    private final Map<String, ObjectNode> tablesById;

    public RenderingAssetBundle(ArrayNode assets, Map<String, ObjectNode> tablesById) {
        this(assets, deriveTableRegistry(tablesById), tablesById);
    }

    public RenderingAssetBundle(
            ArrayNode assets,
            ArrayNode tableRegistry,
            Map<String, ObjectNode> tablesById
    ) {
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(tableRegistry, "tableRegistry");
        Objects.requireNonNull(tablesById, "tablesById");
        this.assets = assets.deepCopy();
        this.tableRegistry = tableRegistry.deepCopy();
        LinkedHashMap<String, ObjectNode> assetCopy = new LinkedHashMap<>();
        for (JsonNode asset : this.assets) {
            if (!asset.isObject()) {
                throw new IllegalArgumentException("Each asset must be an object");
            }
            ObjectNode object = (ObjectNode) asset;
            String assetId = requiredText(object, "assetId");
            validateBundlePath(requiredText(object, "bundlePath"), "assets/");
            if (assetCopy.putIfAbsent(assetId, object.deepCopy()) != null) {
                throw new IllegalArgumentException("Duplicate assetId: " + assetId);
            }
        }
        this.assetsById = immutableNodeMap(assetCopy);

        LinkedHashMap<String, ObjectNode> tableCopy = new LinkedHashMap<>();
        tablesById.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String tableId = requireId(entry.getKey(), "tableId");
            ObjectNode table = Objects.requireNonNull(entry.getValue(), "table " + tableId).deepCopy();
            if (!tableId.equals(requiredText(table, "tableId"))) {
                throw new IllegalArgumentException("Table registry key does not match tableId: " + tableId);
            }
            tableCopy.put(tableId, table);
        });
        this.tablesById = immutableNodeMap(tableCopy);

        for (JsonNode registration : this.tableRegistry) {
            if (!registration.isObject()) {
                throw new IllegalArgumentException("Each table registration must be an object");
            }
            String tableId = requiredText((ObjectNode) registration, "tableId");
            if (!this.tablesById.containsKey(tableId)) {
                throw new IllegalArgumentException("Missing table model for registration: " + tableId);
            }
            if (registration.has("bundlePath")) {
                validateBundlePath(requiredText((ObjectNode) registration, "bundlePath"), "tables/");
            }
        }
    }

    public ArrayNode assets() {
        return assets.deepCopy();
    }

    public ObjectNode requireAsset(String assetId) {
        ObjectNode asset = assetsById.get(assetId);
        if (asset == null) {
            throw new IllegalArgumentException("Unknown assetId: " + assetId);
        }
        return asset.deepCopy();
    }

    public ObjectNode requireTable(String tableId) {
        ObjectNode table = tablesById.get(tableId);
        if (table == null) {
            throw new IllegalArgumentException("Unknown tableId: " + tableId);
        }
        return table.deepCopy();
    }

    public boolean hasAsset(String assetId) {
        return assetsById.containsKey(assetId);
    }

    public boolean hasTable(String tableId) {
        return tablesById.containsKey(tableId);
    }

    public Map<String, ObjectNode> assetIndex() {
        return copyMap(assetsById);
    }

    public Map<String, ObjectNode> tableIndex() {
        return copyMap(tablesById);
    }

    public ObjectNode manifestDocument() {
        ObjectNode manifest = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        manifest.set("assets", assets.deepCopy());
        manifest.set("tables", tableRegistry.deepCopy());
        return manifest;
    }

    private static Map<String, ObjectNode> immutableNodeMap(Map<String, ObjectNode> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, ObjectNode> copyMap(Map<String, ObjectNode> source) {
        LinkedHashMap<String, ObjectNode> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.deepCopy()));
        return Collections.unmodifiableMap(copy);
    }

    private static ArrayNode deriveTableRegistry(Map<String, ObjectNode> tables) {
        Objects.requireNonNull(tables, "tables");
        ArrayNode registry = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        tables.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ObjectNode registration = registry.addObject();
            registration.put("tableId", entry.getKey());
            registration.put("tableKind", entry.getValue().path("tableKind").asText());
        });
        return registry;
    }

    private static String requiredText(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private static String requireId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new IllegalArgumentException(field + " is not a portable id: " + value);
        }
        return value;
    }

    private static void validateBundlePath(String value, String requiredPrefix) {
        if (!value.startsWith(requiredPrefix)
                || value.startsWith("/")
                || value.contains("\\")
                || value.contains("../")
                || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Unsafe bundle path: " + value);
        }
    }
}
