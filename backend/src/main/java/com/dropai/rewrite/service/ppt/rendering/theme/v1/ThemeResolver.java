package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class ThemeResolver {
    private final ThemeRegistry registry;
    private final ThemeLoader loader;
    private final ThemeValidator validator;
    private final FontAvailabilityChecker fontChecker;
    private final ColorContrastChecker contrastChecker;
    private final ThemeCanonicalizer canonicalizer;
    private final ThemeHasher hasher;

    public ThemeResolver(
            ThemeRegistry registry,
            ThemeLoader loader,
            ThemeValidator validator,
            FontAvailabilityChecker fontChecker,
            ColorContrastChecker contrastChecker,
            ThemeCanonicalizer canonicalizer,
            ThemeHasher hasher
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.fontChecker = Objects.requireNonNull(fontChecker, "fontChecker");
        this.contrastChecker = Objects.requireNonNull(contrastChecker, "contrastChecker");
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    public ResolvedTheme resolve(ThemeResolutionRequest request) {
        Objects.requireNonNull(request, "request");
        ThemeRegistry.Registration registration = registry.requireOfficial(
                request.themeId(),
                request.expectedVersion());

        ResolvedSource source = resolveSource(
                registration.coordinate(),
                new LinkedHashSet<>());
        ObjectNode effective = source.effectiveDocument();
        validator.validate(effective);

        FontProfile fontProfile = fontChecker.resolve(
                request.fontProfileId(),
                effective.path("typography"));
        List<ColorContrastChecker.ContrastResult> contrastResults = contrastChecker.evaluate(effective);
        contrastChecker.requireReadable(contrastResults);

        List<String> inheritanceChain = source.sources().stream()
                .map(loaded -> loaded.coordinate().reference())
                .toList();
        Map<String, String> sourceHashes = new LinkedHashMap<>();
        source.sources().forEach(loaded -> sourceHashes.put(
                loaded.coordinate().reference(),
                loaded.sourceHash()));

        ObjectNode resolvedDocument = buildResolvedDocument(
                effective,
                inheritanceChain,
                fontProfile);
        String canonicalDocument = canonicalizer.canonicalize(resolvedDocument);
        String resolvedThemeHash = hasher.hashUtf8(canonicalDocument);
        String aggregateSourceHash = hasher.hashNamedValues(sourceHashes);

        return new ResolvedTheme(
                request.themeId(),
                request.expectedVersion(),
                inheritanceChain,
                canonicalDocument,
                sourceHashes,
                aggregateSourceHash,
                resolvedThemeHash,
                fontProfile,
                contrastResults);
    }

    private ResolvedSource resolveSource(ThemeCoordinate coordinate, Set<ThemeCoordinate> active) {
        if (!active.add(coordinate)) {
            List<String> cycle = new ArrayList<>();
            active.forEach(value -> cycle.add(value.reference()));
            cycle.add(coordinate.reference());
            throw new ThemeValidationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Circular theme inheritance: " + String.join(" -> ", cycle));
        }
        try {
            LoadedTheme current = loader.load(registry.require(coordinate));
            ObjectNode currentDocument = current.sourceDocument();
            validator.validate(currentDocument);

            JsonNode inherits = currentDocument.path("inherits");
            if (inherits.isEmpty()) {
                return new ResolvedSource(currentDocument, List.of(current));
            }

            ThemeCoordinate parentCoordinate;
            try {
                parentCoordinate = ThemeCoordinate.parse(inherits.get(0).asText());
            } catch (IllegalArgumentException exception) {
                throw new ThemeValidationException(PptQualityCode.INVALID_REFERENCE, exception.getMessage());
            }
            ResolvedSource parent = resolveSource(parentCoordinate, active);
            ObjectNode merged = deepMerge(parent.effectiveDocument(), currentDocument);
            List<LoadedTheme> sources = new ArrayList<>(parent.sources());
            sources.add(current);
            return new ResolvedSource(merged, sources);
        } finally {
            active.remove(coordinate);
        }
    }

    private ObjectNode deepMerge(ObjectNode parent, ObjectNode child) {
        ObjectNode result = parent.deepCopy();
        Iterator<Map.Entry<String, JsonNode>> childFields = child.fields();
        while (childFields.hasNext()) {
            Map.Entry<String, JsonNode> field = childFields.next();
            JsonNode parentValue = result.get(field.getKey());
            JsonNode childValue = field.getValue();
            if (parentValue != null && parentValue.isObject() && childValue.isObject()) {
                result.set(
                        field.getKey(),
                        deepMerge((ObjectNode) parentValue, (ObjectNode) childValue));
            } else {
                result.set(field.getKey(), childValue.deepCopy());
            }
        }
        return result;
    }

    private ObjectNode buildResolvedDocument(
            ObjectNode effective,
            List<String> inheritanceChain,
            FontProfile fontProfile
    ) {
        ObjectNode resolved = JsonNodeFactory.instance.objectNode();
        resolved.put("contractVersion", effective.path("schemaVersion").asText());
        resolved.put("themeId", effective.path("themeId").asText());
        resolved.put("themeVersion", effective.path("themeVersion").asText());
        resolved.set("inheritanceChain", stringArray(inheritanceChain));
        resolved.set("slide", effective.path("slide").deepCopy());
        resolved.set("colors", effective.path("colors").deepCopy());
        resolved.set("typography", resolveTypography(effective.path("typography"), fontProfile));
        resolved.set("spacing", effective.path("spacing").deepCopy());
        resolved.set("shape", effective.path("shape").deepCopy());
        resolved.set("shadow", effective.path("shadow").deepCopy());
        resolved.set("components", resolveComponents(effective));
        return resolved;
    }

    private ObjectNode resolveTypography(JsonNode typography, FontProfile fontProfile) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("fontPolicy", fontProfile.policy());
        result.put("fontProfileId", fontProfile.profileId());
        result.put("fontProfileHash", fontProfile.profileHash());
        result.put("fontHashBasis", fontProfile.hashBasis());
        result.set("declaredFamilies", listMapNode(fontProfile.declaredFamilies()));
        result.set("allowedFallbackFamilies", listMapNode(fontProfile.allowedFallbackFamilies()));
        result.set("resolvedFamilies", stringMapNode(fontProfile.resolvedFamilies()));
        result.set("fontConfigurationHashes", stringMapNode(fontProfile.fontConfigurationHashes()));
        result.set("styles", typography.path("styles").deepCopy());
        return result;
    }

    private ObjectNode resolveComponents(ObjectNode effective) {
        ObjectNode components = JsonNodeFactory.instance.objectNode();
        TreeMap<String, JsonNode> ordered = new TreeMap<>();
        effective.path("components").fields().forEachRemaining(entry -> ordered.put(entry.getKey(), entry.getValue()));
        ordered.forEach((componentName, sourceStyle) -> {
            ObjectNode style = JsonNodeFactory.instance.objectNode();
            copyResolvedToken(effective, sourceStyle, "fillToken", "fillColor", style);
            copyResolvedToken(effective, sourceStyle, "textToken", "textColor", style);
            copyResolvedToken(effective, sourceStyle, "accentToken", "accentColor", style);
            copyResolvedToken(effective, sourceStyle, "borderToken", "borderColor", style);
            copyResolvedToken(effective, sourceStyle, "radiusToken", "radiusPt", style);
            copyResolvedToken(effective, sourceStyle, "shadowToken", "shadowStyle", style);
            copyResolvedToken(effective, sourceStyle, "typographyToken", "typographyStyle", style);
            components.set(componentName, style);
        });
        return components;
    }

    private void copyResolvedToken(
            JsonNode theme,
            JsonNode sourceStyle,
            String tokenProperty,
            String resolvedProperty,
            ObjectNode target
    ) {
        if (!sourceStyle.has(tokenProperty)) {
            return;
        }
        String token = sourceStyle.path(tokenProperty).asText();
        JsonNode value = resolvePath(theme, token);
        if (value.isMissingNode()) {
            throw new ThemeValidationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Unknown theme token: " + token);
        }
        target.set(resolvedProperty, value.deepCopy());
    }

    private JsonNode resolvePath(JsonNode root, String dottedPath) {
        JsonNode current = root;
        for (String segment : dottedPath.split("\\.")) {
            current = current.path(segment);
        }
        return current;
    }

    private ObjectNode listMapNode(Map<String, List<String>> values) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        values.forEach((key, value) -> result.set(key, stringArray(value)));
        return result;
    }

    private ObjectNode stringMapNode(Map<String, String> values) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        values.forEach(result::put);
        return result;
    }

    private ArrayNode stringArray(Collection<String> values) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.forEach(result::add);
        return result;
    }

    private record ResolvedSource(ObjectNode effectiveDocument, List<LoadedTheme> sources) {
        private ResolvedSource {
            effectiveDocument = effectiveDocument.deepCopy();
            sources = List.copyOf(sources);
        }

        @Override
        public ObjectNode effectiveDocument() {
            return effectiveDocument.deepCopy();
        }
    }
}
