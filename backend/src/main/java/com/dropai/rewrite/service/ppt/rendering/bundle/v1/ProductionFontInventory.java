package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable actual-font inventory supplied by the production runtime. */
public final class ProductionFontInventory {
    private final String profileId;
    private final String fontProfileHash;
    private final String measurementEngineVersion;
    private final List<ProductionFontFace> faces;
    private final Map<String, ProductionFontFace> facesById;

    public ProductionFontInventory(
            String profileId,
            String fontProfileHash,
            String measurementEngineVersion,
            List<ProductionFontFace> faces
    ) {
        this.profileId = text(profileId, "profileId");
        this.fontProfileHash = hash(fontProfileHash, "fontProfileHash");
        this.measurementEngineVersion = text(measurementEngineVersion, "measurementEngineVersion");
        Objects.requireNonNull(faces, "faces");
        if (faces.isEmpty()) {
            throw new IllegalArgumentException("faces must not be empty");
        }
        ArrayList<ProductionFontFace> sorted = new ArrayList<>(faces);
        sorted.sort(Comparator.comparing(ProductionFontFace::fontFaceId));
        LinkedHashMap<String, ProductionFontFace> index = new LinkedHashMap<>();
        for (ProductionFontFace face : sorted) {
            Objects.requireNonNull(face, "font face");
            if (index.putIfAbsent(face.fontFaceId(), face) != null) {
                throw new IllegalArgumentException("Duplicate fontFaceId: " + face.fontFaceId());
            }
        }
        this.faces = List.copyOf(sorted);
        this.facesById = Collections.unmodifiableMap(index);
    }

    public String profileId() {
        return profileId;
    }

    public String fontProfileHash() {
        return fontProfileHash;
    }

    public String measurementEngineVersion() {
        return measurementEngineVersion;
    }

    public List<ProductionFontFace> faces() {
        return faces;
    }

    public ProductionFontFace requireFace(String fontFaceId) {
        ProductionFontFace face = facesById.get(fontFaceId);
        if (face == null) {
            throw new IllegalArgumentException("Unknown production fontFaceId: " + fontFaceId);
        }
        return face;
    }

    public ObjectNode manifest() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("schemaVersion", "font-manifest.v1");
        root.put("profileId", profileId);
        root.put("fontProfileHash", fontProfileHash);
        root.put("measurementEngineVersion", measurementEngineVersion);
        ArrayNode outputFaces = root.putArray("faces");
        for (ProductionFontFace face : faces) {
            ObjectNode node = outputFaces.addObject();
            node.put("fontFaceId", face.fontFaceId());
            node.put("role", face.role());
            node.put("weight", face.weight());
            node.put("requestedFamily", face.requestedFamily());
            node.put("resolvedFamily", face.resolvedFamily());
            node.put("postScriptName", face.postScriptName());
            node.put("fontSource", face.fontSource());
            node.put("fontFingerprint", face.fontFingerprint());
            node.put("fallbackApplied", face.fallbackApplied());
        }
        return root;
    }

    static ProductionFontInventory fromManifest(ObjectNode root) {
        BundleSupport.requiredSchema(root, "font-manifest.v1");
        JsonNode rawFaces = root.path("faces");
        if (!rawFaces.isArray() || rawFaces.isEmpty()) {
            throw new RenderPlanBundleException(
                    com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode.SCHEMA_INVALID,
                    "font manifest faces must be a non-empty array");
        }
        List<ProductionFontFace> parsed = new ArrayList<>();
        try {
            for (JsonNode raw : rawFaces) {
                if (!raw.isObject()) {
                    throw new IllegalArgumentException("font face must be an object");
                }
                ObjectNode face = (ObjectNode) raw;
                parsed.add(new ProductionFontFace(
                        BundleSupport.requiredText(face, "fontFaceId"),
                        BundleSupport.requiredText(face, "role"),
                        BundleSupport.requiredInt(face, "weight"),
                        BundleSupport.requiredText(face, "requestedFamily"),
                        BundleSupport.requiredText(face, "resolvedFamily"),
                        BundleSupport.requiredText(face, "postScriptName"),
                        BundleSupport.requiredText(face, "fontSource"),
                        BundleSupport.requiredText(face, "fontFingerprint"),
                        BundleSupport.requiredBoolean(face, "fallbackApplied")));
            }
            return new ProductionFontInventory(
                    BundleSupport.requiredText(root, "profileId"),
                    BundleSupport.requiredText(root, "fontProfileHash"),
                    BundleSupport.requiredText(root, "measurementEngineVersion"),
                    parsed);
        } catch (IllegalArgumentException exception) {
            throw new RenderPlanBundleException(
                    com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode.SCHEMA_INVALID,
                    "Invalid production font manifest", exception);
        }
    }

    private static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String hash(String value, String field) {
        value = text(value, field);
        if (!value.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(field + " is not a SHA-256 contract hash");
        }
        return value;
    }
}
