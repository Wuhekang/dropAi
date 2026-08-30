package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class ResolvedFontProfileResolver {
    private final FontFaceInventory inventory;

    public ResolvedFontProfileResolver(FontFaceInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public ResolvedFontProfile resolve(
            String profileId,
            Map<String, List<String>> requestedFamilies,
            Map<String, ? extends Set<Integer>> requiredWeights
    ) {
        requireText(profileId, "profileId");
        Objects.requireNonNull(requestedFamilies, "requestedFamilies");
        Objects.requireNonNull(requiredWeights, "requiredWeights");
        if (requestedFamilies.isEmpty() || !requestedFamilies.keySet().equals(requiredWeights.keySet())) {
            throw new IllegalArgumentException("Font roles and required-weight roles must be identical and non-empty");
        }

        Map<String, List<String>> normalizedRequests = new TreeMap<>();
        Map<String, String> selectedFamilies = new TreeMap<>();
        Map<String, Boolean> fallbackApplied = new TreeMap<>();
        Map<String, Map<Integer, ResolvedFontFace>> resolvedFaces = new TreeMap<>();

        for (String role : requestedFamilies.keySet().stream().sorted().toList()) {
            List<String> families = normalizeFamilies(requestedFamilies.get(role), role);
            List<Integer> weights = normalizeWeights(requiredWeights.get(role), role);
            normalizedRequests.put(role, families);

            FamilyResolution resolution = resolveOneFamily(role, families, weights);
            selectedFamilies.put(role, resolution.family());
            fallbackApplied.put(role, resolution.fallbackApplied());
            resolvedFaces.put(role, resolution.faces());
        }

        String hash = hashProfile(
                profileId,
                normalizedRequests,
                selectedFamilies,
                fallbackApplied,
                resolvedFaces);
        return new ResolvedFontProfile(
                profileId,
                normalizedRequests,
                selectedFamilies,
                fallbackApplied,
                resolvedFaces,
                ResolvedFontProfile.MEASUREMENT_ENGINE_VERSION,
                hash);
    }

    private FamilyResolution resolveOneFamily(
            String role,
            List<String> families,
            List<Integer> requiredWeights
    ) {
        for (int familyIndex = 0; familyIndex < families.size(); familyIndex++) {
            String family = families.get(familyIndex);
            Map<Integer, ResolvedFontFace> faces = new TreeMap<>();
            boolean complete = true;
            for (int weight : requiredWeights) {
                List<FontFaceResource> candidates = inventory.find(family, weight);
                if (candidates == null || candidates.isEmpty()) {
                    complete = false;
                    break;
                }
                FontFaceResource selected = requireUnambiguous(family, weight, candidates);
                if (!selected.family().equalsIgnoreCase(family) || selected.weight() != weight) {
                    throw new MeasurementException(
                            PptQualityCode.FONT_UNAVAILABLE,
                            "Font inventory returned a mismatched face for " + family + " weight " + weight);
                }
                String fingerprint = fingerprint(selected.fontBytes());
                faces.put(weight, new ResolvedFontFace(
                        role,
                        weight,
                        family,
                        selected.postScriptName(),
                        selected.source(),
                        fingerprint,
                        familyIndex > 0,
                        selected.fontBytes()));
            }
            if (complete) {
                return new FamilyResolution(family, familyIndex > 0, faces);
            }
        }
        throw new MeasurementException(
                PptQualityCode.FONT_UNAVAILABLE,
                "No single declared font family supplies every required weight for role " + role
                        + ": " + families + " weights=" + requiredWeights);
    }

    private FontFaceResource requireUnambiguous(
            String family,
            int weight,
            List<FontFaceResource> candidates
    ) {
        List<FontFaceResource> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparing((FontFaceResource value) -> fingerprint(value.fontBytes()))
                .thenComparing(FontFaceResource::postScriptName)
                .thenComparing(value -> value.source().name()));
        Set<String> resolvedFaces = new LinkedHashSet<>();
        sorted.forEach(value -> resolvedFaces.add(
                fingerprint(value.fontBytes()) + '|' + value.postScriptName()));
        if (resolvedFaces.size() != 1) {
            throw new MeasurementException(
                    PptQualityCode.FONT_UNAVAILABLE,
                    "Ambiguous font bytes or faces for " + family + " weight " + weight
                            + ": " + resolvedFaces);
        }
        return sorted.get(0);
    }

    private String hashProfile(
            String profileId,
            Map<String, List<String>> requestedFamilies,
            Map<String, String> selectedFamilies,
            Map<String, Boolean> fallbackApplied,
            Map<String, Map<Integer, ResolvedFontFace>> faces
    ) {
        MessageDigest digest = sha256();
        update(digest, "schema", "resolved-font-profile.v1");
        update(digest, "profileId", profileId);
        update(digest, "measurementEngineVersion", ResolvedFontProfile.MEASUREMENT_ENGINE_VERSION);
        for (String role : requestedFamilies.keySet().stream().sorted().toList()) {
            update(digest, "role", role);
            List<String> requested = requestedFamilies.get(role);
            for (int index = 0; index < requested.size(); index++) {
                update(digest, "requestedFamily." + index, requested.get(index));
            }
            update(digest, "selectedFamily", selectedFamilies.get(role));
            update(digest, "fallbackApplied", fallbackApplied.get(role).toString());
            for (Map.Entry<Integer, ResolvedFontFace> face : new TreeMap<>(faces.get(role)).entrySet()) {
                update(digest, "weight", Integer.toString(face.getKey()));
                update(digest, "postScriptName", face.getValue().postScriptName());
                update(digest, "fontSource", face.getValue().fontSource().name());
                update(digest, "fontFingerprint", face.getValue().fontFingerprint());
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    static String fingerprint(byte[] bytes) {
        MessageDigest digest = sha256();
        return "sha256:" + HexFormat.of().formatHex(digest.digest(bytes));
    }

    private static void update(MessageDigest digest, String name, String value) {
        updateBytes(digest, name.getBytes(StandardCharsets.UTF_8));
        updateBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void updateBytes(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static List<String> normalizeFamilies(List<String> values, String role) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No requested font families for role " + role);
        }
        List<String> normalized = values.stream().map(value -> requireText(value, "font family")).toList();
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException("Duplicate requested font family for role " + role);
        }
        return normalized;
    }

    private static List<Integer> normalizeWeights(Set<Integer> values, String role) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No required font weights for role " + role);
        }
        List<Integer> result = values.stream().sorted().toList();
        if (result.stream().anyMatch(weight -> weight < 100 || weight > 900 || weight % 100 != 0)) {
            throw new IllegalArgumentException("Invalid required font weight for role " + role + ": " + result);
        }
        return result;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record FamilyResolution(
            String family,
            boolean fallbackApplied,
            Map<Integer, ResolvedFontFace> faces
    ) {
    }
}
