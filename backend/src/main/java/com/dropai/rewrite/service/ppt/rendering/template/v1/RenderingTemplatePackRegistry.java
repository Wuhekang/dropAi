package com.dropai.rewrite.service.ppt.rendering.template.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.FontProfile;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ThemeRegistry;
import com.dropai.rewrite.service.ppt.rendering.theme.v1.ThemeResolutionRequest;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Trusted, classpath-only registry for production Rendering V1 template packs. */
public final class RenderingTemplatePackRegistry {
    public static final String ACADEMIC_PURPLE = ThemeRegistry.ACADEMIC_PURPLE;
    public static final String SMALL_BEAR_WATERCOLOR_BLUE_V1 =
            ThemeRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1;

    private static final String SMALL_BEAR_MANIFEST =
            "ppt/templates/v1/small-bear-watercolor-blue-v1/template-pack.json";
    private static final Set<String> LEGACY_ACADEMIC_SELECTIONS = Set.of(
            "AI_RECOMMEND",
            "TECH_DEFENSE",
            "SIMPLE_ACADEMIC",
            "ENVIRONMENT_DESIGN",
            "VISUAL_COMMUNICATION",
            "BUSINESS",
            "MINIMAL_PREMIUM");

    private final Map<String, RenderingTemplatePack> packs;

    public RenderingTemplatePackRegistry() {
        this(new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build()));
    }

    public RenderingTemplatePackRegistry(ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper");
        Map<String, RenderingTemplatePack> registered = new LinkedHashMap<>();
        RenderingTemplatePack academic = academicPurple();
        RenderingTemplatePack smallBear = load(mapper, SMALL_BEAR_MANIFEST);
        registered.put(academic.templatePackId(), academic);
        registered.put(smallBear.templatePackId(), smallBear);
        this.packs = Collections.unmodifiableMap(registered);
    }

    public RenderingTemplatePack require(String templatePackId) {
        if (templatePackId == null || templatePackId.isBlank()) {
            throw new IllegalArgumentException("templatePackId must not be blank");
        }
        String requested = templatePackId.strip();
        String canonicalId = requested.toLowerCase(Locale.ROOT);
        RenderingTemplatePack pack = packs.get(canonicalId);
        if (pack == null) {
            throw new IllegalArgumentException("Unsupported Rendering V1 templatePackId: " + templatePackId);
        }
        return pack;
    }

    public List<RenderingTemplatePack> available() {
        return List.copyOf(packs.values());
    }

    /** Only explicit trusted pack ids and the finite set of legacy built-ins may be normalized. */
    public static String normalizeSelection(String value) {
        if (value == null || value.isBlank()) {
            return ACADEMIC_PURPLE;
        }
        String normalized = value.strip();
        if (ACADEMIC_PURPLE.equalsIgnoreCase(normalized)) {
            return ACADEMIC_PURPLE;
        }
        if (SMALL_BEAR_WATERCOLOR_BLUE_V1.equalsIgnoreCase(normalized)) {
            return SMALL_BEAR_WATERCOLOR_BLUE_V1;
        }
        if (LEGACY_ACADEMIC_SELECTIONS.contains(normalized.toUpperCase(Locale.ROOT))) {
            return ACADEMIC_PURPLE;
        }
        throw invalidPersistedSelection("unsupported template selection: " + normalized);
    }

    public static String selectedPackId(Map<String, Object> project) {
        Objects.requireNonNull(project, "project");
        String templateId = string(project.get("template_id")).strip();
        String templateStyle = string(project.get("template_style")).strip();
        if (!templateId.isBlank()) {
            String selectedById = trustedPackId(templateId);
            if (selectedById == null) {
                throw invalidPersistedSelection("untrusted template_id: " + templateId);
            }
            if (!templateStyle.isBlank()) {
                String selectedByStyle = trustedPackId(templateStyle);
                if (selectedByStyle == null || !selectedById.equals(selectedByStyle)) {
                    throw invalidPersistedSelection(
                            "template_id and template_style do not identify the same trusted pack");
                }
            }
            return selectedById;
        }
        return normalizeSelection(templateStyle);
    }

    private static String trustedPackId(String value) {
        if (ACADEMIC_PURPLE.equalsIgnoreCase(value)) {
            return ACADEMIC_PURPLE;
        }
        if (SMALL_BEAR_WATERCOLOR_BLUE_V1.equalsIgnoreCase(value)) {
            return SMALL_BEAR_WATERCOLOR_BLUE_V1;
        }
        return null;
    }

    private static IllegalArgumentException invalidPersistedSelection(String detail) {
        return new IllegalArgumentException(
                "PPT template selection is invalid; select a trusted Rendering V1 template again (" + detail + ")");
    }

    private static RenderingTemplatePack academicPurple() {
        ArrayNode assets = JsonNodeFactory.instance.arrayNode();
        AssetBinaryResolver resolver = (assetId, bundlePath, expectedSha256) -> null;
        return new RenderingTemplatePack(
                ACADEMIC_PURPLE,
                ThemeRegistry.VERSION_1_0_0,
                "Academic Purple",
                "DokiAI Academic Rendering V1 紫色基线主题",
                ThemeResolutionRequest.academicPurpleV1(),
                sha256("academic-purple:no-source-template".getBytes(StandardCharsets.UTF_8)),
                sha256("academic-purple@1.0.0".getBytes(StandardCharsets.UTF_8)),
                assets,
                Map.of(),
                resolver);
    }

    private static RenderingTemplatePack load(ObjectMapper mapper, String manifestResource) {
        byte[] manifestBytes = resource(manifestResource);
        ObjectNode manifest;
        try {
            JsonNode parsed = mapper.readTree(manifestBytes);
            if (!(parsed instanceof ObjectNode object)) {
                throw new IllegalArgumentException("Template pack manifest must be a JSON object");
            }
            manifest = object;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot parse template pack manifest " + manifestResource, exception);
        }
        requireExactFields(manifest, Set.of(
                "schemaVersion", "templatePackId", "templatePackVersion", "displayName", "description",
                "sourceTemplate", "theme", "assets", "surfaces"), "template pack manifest");
        requireText(manifest, "schemaVersion", "template-pack.v1");
        String packId = portableId(manifest, "templatePackId");
        String packVersion = semanticVersion(manifest, "templatePackVersion");
        ObjectNode source = requiredObject(manifest, "sourceTemplate");
        requireExactFields(source, Set.of("fileName", "sha256", "slideCount", "sanitization"), "sourceTemplate");
        String sourceHash = hash(source, "sha256");
        if (source.path("slideCount").asInt(0) < 1) {
            throw new IllegalArgumentException("sourceTemplate.slideCount must be positive");
        }
        requireText(source, "sanitization", "VISUAL_REGION_ALLOWLIST_V1");

        ObjectNode theme = requiredObject(manifest, "theme");
        requireExactFields(theme, Set.of("themeId", "themeVersion", "fontProfileId"), "theme");
        ThemeResolutionRequest themeRequest = new ThemeResolutionRequest(
                portableId(theme, "themeId"), semanticVersion(theme, "themeVersion"),
                requireText(theme, "fontProfileId"));
        if (!packId.equals(themeRequest.themeId())) {
            throw new IllegalArgumentException(
                    "templatePackId must equal theme.themeId for frozen bundle identity");
        }
        if (!FontProfile.CJK_ACADEMIC_V1.equals(themeRequest.fontProfileId())) {
            throw new IllegalArgumentException("Template packs must use the production CJK font profile");
        }

        ArrayNode assetManifest = JsonNodeFactory.instance.arrayNode();
        Map<String, VerifiedAssetBytes> assetBytes = new LinkedHashMap<>();
        JsonNode rawAssets = manifest.path("assets");
        if (!rawAssets.isArray() || rawAssets.isEmpty()) {
            throw new IllegalArgumentException("Decorated template pack must declare assets");
        }
        for (JsonNode raw : rawAssets) {
            if (!(raw instanceof ObjectNode asset)) {
                throw new IllegalArgumentException("Template asset must be an object");
            }
            requireExactFields(asset, Set.of(
                    "assetId", "classpathResource", "bundlePath", "sha256", "mimeType", "widthPx", "heightPx"),
                    "template asset");
            String assetId = portableId(asset, "assetId");
            String classpathResource = safeResource(requireText(asset, "classpathResource"));
            String bundlePath = safeBundlePath(requireText(asset, "bundlePath"));
            String expectedHash = hash(asset, "sha256");
            String mimeType = requireText(asset, "mimeType", "image/png");
            int width = positiveInt(asset, "widthPx");
            int height = positiveInt(asset, "heightPx");
            byte[] bytes = resource(classpathResource);
            String actualHash = sha256(bytes);
            if (!actualHash.equals(expectedHash)) {
                throw new IllegalStateException("Template asset hash mismatch: " + assetId);
            }
            if (assetBytes.putIfAbsent(assetId,
                    new VerifiedAssetBytes(assetId, bundlePath, expectedHash, mimeType, bytes)) != null) {
                throw new IllegalArgumentException("Duplicate template assetId: " + assetId);
            }
            ObjectNode registration = assetManifest.addObject();
            registration.put("assetId", assetId);
            registration.put("assetKind", "TEMPLATE_DECORATION");
            registration.put("bundlePath", bundlePath);
            registration.put("widthPx", width);
            registration.put("heightPx", height);
            registration.put("imageRole", "INFORMATION");
            registration.put("mandatory", true);
            registration.put("mimeType", mimeType);
            registration.put("sha256", expectedHash);
        }

        ObjectNode rawSurfaces = requiredObject(manifest, "surfaces");
        EnumMap<PageType, String> surfaces = new EnumMap<>(PageType.class);
        rawSurfaces.fields().forEachRemaining(entry -> {
            PageType type;
            try {
                type = PageType.valueOf(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown template surface page type: " + entry.getKey());
            }
            if (!entry.getValue().isTextual() || entry.getValue().textValue().isBlank()) {
                throw new IllegalArgumentException("Template surface asset id is blank: " + entry.getKey());
            }
            String assetId = entry.getValue().textValue();
            if (!assetBytes.containsKey(assetId)) {
                throw new IllegalArgumentException("Template surface references unknown asset: " + assetId);
            }
            surfaces.put(type, assetId);
        });
        if (!surfaces.keySet().equals(Set.of(PageType.values()))) {
            throw new IllegalArgumentException("Template pack must define one surface for every page type");
        }
        AssetBinaryResolver resolver = (assetId, bundlePath, expectedSha256) -> {
            VerifiedAssetBytes found = assetBytes.get(assetId);
            return found != null && found.bundlePath().equals(bundlePath)
                    && found.sha256().equals(expectedSha256) ? found : null;
        };
        return new RenderingTemplatePack(
                packId,
                packVersion,
                requireText(manifest, "displayName"),
                requireText(manifest, "description"),
                themeRequest,
                sourceHash,
                sha256(canonical(mapper, manifest)),
                assetManifest,
                surfaces,
                resolver);
    }

    private static byte[] canonical(ObjectMapper mapper, JsonNode node) {
        try {
            return mapper.writer().writeValueAsBytes(sort(node));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize template pack manifest", exception);
        }
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode output = JsonNodeFactory.instance.objectNode();
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            Collections.sort(fields);
            fields.forEach(field -> output.set(field, sort(node.get(field))));
            return output;
        }
        if (node.isArray()) {
            ArrayNode output = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> output.add(sort(value)));
            return output;
        }
        return node;
    }

    private static byte[] resource(String name) {
        try (InputStream input = RenderingTemplatePackRegistry.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing template pack resource: " + name);
            }
            return input.readAllBytes();
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("Cannot read template pack resource: " + name, exception);
        }
    }

    private static ObjectNode requiredObject(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!(value instanceof ObjectNode object)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return object;
    }

    private static String portableId(ObjectNode owner, String field) {
        String value = requireText(owner, field);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9._-]*$")) {
            throw new IllegalArgumentException(field + " is not a portable id: " + value);
        }
        return value;
    }

    private static String semanticVersion(ObjectNode owner, String field) {
        String value = requireText(owner, field);
        if (!value.matches("^[0-9]+\\.[0-9]+\\.[0-9]+$")) {
            throw new IllegalArgumentException(field + " is not a semantic version: " + value);
        }
        return value;
    }

    private static String hash(ObjectNode owner, String field) {
        String value = requireText(owner, field).toLowerCase(Locale.ROOT);
        if (!value.matches("^sha256:[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(field + " is not a SHA-256 hash");
        }
        return value;
    }

    private static String requireText(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue();
    }

    private static String requireText(ObjectNode owner, String field, String expected) {
        String value = requireText(owner, field);
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(field + " must equal " + expected);
        }
        return value;
    }

    private static int positiveInt(ObjectNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.intValue();
    }

    private static String safeResource(String value) {
        if (value.startsWith("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException("Unsafe classpath resource: " + value);
        }
        return value;
    }

    private static String safeBundlePath(String value) {
        if (!value.startsWith("assets/templates/") || value.startsWith("/")
                || value.contains("\\") || value.contains("../") || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Unsafe template bundle path: " + value);
        }
        return value;
    }

    private static void requireExactFields(ObjectNode object, Set<String> allowed, String owner) {
        List<String> unknown = new ArrayList<>();
        object.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                unknown.add(field);
            }
        });
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(owner + " contains unsupported fields: " + unknown);
        }
        List<String> missing = allowed.stream().filter(field -> !object.has(field)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(owner + " is missing fields: " + missing);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
