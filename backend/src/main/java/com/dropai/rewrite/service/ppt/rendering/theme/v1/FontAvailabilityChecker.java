package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class FontAvailabilityChecker {
    private static final Set<String> FONT_ROLES = Set.of("display", "body");

    private final Set<String> availableFamilies;
    private final ThemeHasher hasher;

    public FontAvailabilityChecker(Collection<String> availableFamilies, ThemeHasher hasher) {
        Objects.requireNonNull(availableFamilies, "availableFamilies");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String family : availableFamilies) {
            if (family != null && !family.isBlank()) {
                normalized.add(family.trim().toLowerCase(Locale.ROOT));
            }
        }
        this.availableFamilies = Collections.unmodifiableSet(normalized);
    }

    public static FontAvailabilityChecker system(ThemeHasher hasher) {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT);
        return new FontAvailabilityChecker(List.of(families), hasher);
    }

    public FontProfile resolve(String profileId, JsonNode typography) {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(typography, "typography");
        if (!FontProfile.CJK_ACADEMIC_V1.equals(profileId)) {
            throw new ThemeValidationException(
                    PptQualityCode.INVALID_REFERENCE,
                    "Unknown font profile: " + profileId);
        }

        String policy = typography.path("fontPolicy").asText();
        Map<String, List<String>> declared = new TreeMap<>();
        Map<String, List<String>> fallbacks = new TreeMap<>();
        Map<String, String> resolved = new TreeMap<>();
        Map<String, String> configurationHashes = new TreeMap<>();

        for (String role : FONT_ROLES.stream().sorted().toList()) {
            List<String> ordered = textValues(typography.path("fontFamilies").path(role));
            if (ordered.isEmpty()) {
                throw new ThemeValidationException(
                        PptQualityCode.FONT_UNAVAILABLE,
                        "No declared font family for role " + role);
            }
            List<String> declaredForRole = List.of(ordered.get(0));
            List<String> fallbackForRole = "ALLOW_DECLARED_FALLBACK".equals(policy)
                    ? List.copyOf(ordered.subList(1, ordered.size()))
                    : List.of();
            List<String> candidates = new ArrayList<>(declaredForRole);
            candidates.addAll(fallbackForRole);
            String selected = candidates.stream()
                    .filter(this::isAvailable)
                    .findFirst()
                    .orElseThrow(() -> new ThemeValidationException(
                            PptQualityCode.FONT_UNAVAILABLE,
                            "None of the declared fonts are available for " + role + ": " + candidates));

            declared.put(role, declaredForRole);
            fallbacks.put(role, fallbackForRole);
            resolved.put(role, selected);
            configurationHashes.put(role, hashRole(role, policy, declaredForRole, fallbackForRole, selected));
        }

        ObjectNode profileDocument = JsonNodeFactory.instance.objectNode();
        profileDocument.put("profileId", profileId);
        profileDocument.put("policy", policy);
        profileDocument.set("declaredFamilies", listMapNode(declared));
        profileDocument.set("allowedFallbackFamilies", listMapNode(fallbacks));
        profileDocument.set("resolvedFamilies", stringMapNode(resolved));
        profileDocument.set("fontConfigurationHashes", stringMapNode(configurationHashes));
        String profileHash = hasher.hash(profileDocument);

        return new FontProfile(
                profileId,
                policy,
                declared,
                fallbacks,
                resolved,
                configurationHashes,
                profileHash);
    }

    public boolean isAvailable(String declaredFamily) {
        return availableFamilies.contains(declaredFamily.trim().toLowerCase(Locale.ROOT));
    }

    private String hashRole(
            String role,
            String policy,
            List<String> declared,
            List<String> fallbacks,
            String resolved
    ) {
        ObjectNode value = JsonNodeFactory.instance.objectNode();
        value.put("role", role);
        value.put("policy", policy);
        value.set("declaredFamilies", arrayNode(declared));
        value.set("allowedFallbackFamilies", arrayNode(fallbacks));
        value.put("resolvedFamily", resolved);
        value.put("hashBasis", FontProfile.HASH_BASIS_CONFIG_ONLY);
        return hasher.hash(value);
    }

    private List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private ObjectNode listMapNode(Map<String, List<String>> values) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        values.forEach((key, value) -> result.set(key, arrayNode(value)));
        return result;
    }

    private ObjectNode stringMapNode(Map<String, String> values) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        values.forEach(result::put);
        return result;
    }

    private ArrayNode arrayNode(List<String> values) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.forEach(result::add);
        return result;
    }
}
