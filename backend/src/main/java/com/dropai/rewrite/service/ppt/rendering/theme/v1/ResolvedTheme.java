package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResolvedTheme {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String themeId;
    private final String themeVersion;
    private final List<String> inheritanceChain;
    private final String canonicalDocument;
    private final Map<String, String> sourceHashes;
    private final String themeSourceHash;
    private final String resolvedThemeHash;
    private final FontProfile fontProfile;
    private final List<ColorContrastChecker.ContrastResult> contrastResults;

    ResolvedTheme(
            String themeId,
            String themeVersion,
            List<String> inheritanceChain,
            String canonicalDocument,
            Map<String, String> sourceHashes,
            String themeSourceHash,
            String resolvedThemeHash,
            FontProfile fontProfile,
            List<ColorContrastChecker.ContrastResult> contrastResults
    ) {
        this.themeId = Objects.requireNonNull(themeId, "themeId");
        this.themeVersion = Objects.requireNonNull(themeVersion, "themeVersion");
        this.inheritanceChain = List.copyOf(inheritanceChain);
        this.canonicalDocument = Objects.requireNonNull(canonicalDocument, "canonicalDocument");
        this.sourceHashes = sortedImmutableMap(sourceHashes);
        this.themeSourceHash = Objects.requireNonNull(themeSourceHash, "themeSourceHash");
        this.resolvedThemeHash = Objects.requireNonNull(resolvedThemeHash, "resolvedThemeHash");
        this.fontProfile = Objects.requireNonNull(fontProfile, "fontProfile");
        this.contrastResults = List.copyOf(contrastResults);
    }

    public String themeId() {
        return themeId;
    }

    public String themeVersion() {
        return themeVersion;
    }

    public List<String> inheritanceChain() {
        return inheritanceChain;
    }

    public String canonicalDocument() {
        return canonicalDocument;
    }

    public ObjectNode document() {
        try {
            return (ObjectNode) MAPPER.readTree(canonicalDocument);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored resolved theme is not valid JSON", exception);
        }
    }

    public Map<String, String> sourceHashes() {
        return sourceHashes;
    }

    public String themeSourceHash() {
        return themeSourceHash;
    }

    public String resolvedThemeHash() {
        return resolvedThemeHash;
    }

    public FontProfile fontProfile() {
        return fontProfile;
    }

    public List<ColorContrastChecker.ContrastResult> contrastResults() {
        return contrastResults;
    }

    public Map<String, String> hashManifest() {
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
        hashes.put("font-profile.sha256", fontProfile.profileHash());
        hashes.put("resolved-theme.sha256", resolvedThemeHash);
        hashes.put("theme-source.sha256", themeSourceHash);
        return Collections.unmodifiableMap(hashes);
    }

    private static Map<String, String> sortedImmutableMap(Map<String, String> source) {
        List<String> keys = new ArrayList<>(source.keySet());
        Collections.sort(keys);
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        keys.forEach(key -> copy.put(key, source.get(key)));
        return Collections.unmodifiableMap(copy);
    }
}
