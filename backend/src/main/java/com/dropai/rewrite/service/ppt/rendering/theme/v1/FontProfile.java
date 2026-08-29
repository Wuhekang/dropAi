package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FontProfile {
    public static final String CJK_ACADEMIC_V1 = "cjk-academic-v1";
    public static final String HASH_BASIS_CONFIG_ONLY = "CONFIG_ONLY";

    private final String profileId;
    private final String policy;
    private final Map<String, List<String>> declaredFamilies;
    private final Map<String, List<String>> allowedFallbackFamilies;
    private final Map<String, String> resolvedFamilies;
    private final Map<String, String> fontConfigurationHashes;
    private final String profileHash;

    FontProfile(
            String profileId,
            String policy,
            Map<String, List<String>> declaredFamilies,
            Map<String, List<String>> allowedFallbackFamilies,
            Map<String, String> resolvedFamilies,
            Map<String, String> fontConfigurationHashes,
            String profileHash
    ) {
        this.profileId = requireText(profileId, "profileId");
        this.policy = requireText(policy, "policy");
        this.declaredFamilies = immutableListMap(declaredFamilies);
        this.allowedFallbackFamilies = immutableListMap(allowedFallbackFamilies);
        this.resolvedFamilies = immutableStringMap(resolvedFamilies);
        this.fontConfigurationHashes = immutableStringMap(fontConfigurationHashes);
        this.profileHash = requireText(profileHash, "profileHash");
    }

    public String profileId() {
        return profileId;
    }

    public String policy() {
        return policy;
    }

    public Map<String, List<String>> declaredFamilies() {
        return declaredFamilies;
    }

    public Map<String, List<String>> allowedFallbackFamilies() {
        return allowedFallbackFamilies;
    }

    public Map<String, String> resolvedFamilies() {
        return resolvedFamilies;
    }

    public Map<String, String> fontConfigurationHashes() {
        return fontConfigurationHashes;
    }

    public String profileHash() {
        return profileHash;
    }

    public String hashBasis() {
        return HASH_BASIS_CONFIG_ONLY;
    }

    public boolean usedFallback(String role) {
        List<String> declared = declaredFamilies.get(role);
        String resolved = resolvedFamilies.get(role);
        if (declared == null || declared.isEmpty() || resolved == null) {
            throw new IllegalArgumentException("Unknown font role: " + role);
        }
        return !declared.get(0).equals(resolved);
    }

    private static Map<String, List<String>> immutableListMap(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "source");
        List<String> keys = new ArrayList<>(source.keySet());
        Collections.sort(keys);
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        for (String key : keys) {
            copy.put(requireText(key, "font role"), List.copyOf(source.get(key)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source) {
        Objects.requireNonNull(source, "source");
        List<String> keys = new ArrayList<>(source.keySet());
        Collections.sort(keys);
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (String key : keys) {
            copy.put(requireText(key, "font role"), requireText(source.get(key), key));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
