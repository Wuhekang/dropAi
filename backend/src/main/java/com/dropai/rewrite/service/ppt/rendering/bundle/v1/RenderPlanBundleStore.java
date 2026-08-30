package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanHasher;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Atomically publishes the exact frozen RenderPlan, assets and execution fingerprints. */
public final class RenderPlanBundleStore {
    private final RenderPlanHasher planHasher = new RenderPlanHasher();

    public StoredRenderPlanBundle store(
            Path targetDirectory,
            FrozenSlideRenderPlan plan,
            AssetBinaryResolver sourceAssets,
            ProductionFontInventory actualFonts,
            BundleBuildIdentity buildIdentity
    ) {
        StagedRenderPlanBundle staged = stage(
                targetDirectory, plan, sourceAssets, actualFonts, buildIdentity);
        try {
            return publish(staged);
        } catch (RuntimeException exception) {
            discard(staged);
            throw exception;
        }
    }

    public StagedRenderPlanBundle stage(
            Path targetDirectory,
            FrozenSlideRenderPlan plan,
            AssetBinaryResolver sourceAssets,
            ProductionFontInventory actualFonts,
            BundleBuildIdentity buildIdentity
    ) {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sourceAssets, "sourceAssets");
        Objects.requireNonNull(actualFonts, "actualFonts");
        Objects.requireNonNull(buildIdentity, "buildIdentity");
        Path target = targetDirectory.toAbsolutePath().normalize();
        String revisionName = UUID.randomUUID().toString();
        Path revisions = target.resolve(BundleSupport.REVISIONS_DIRECTORY);
        Path temp = revisions.resolve("." + revisionName + ".tmp");
        Path revision = revisions.resolve(revisionName);
        boolean revisionPublished = false;

        try {
            if (Files.exists(target) && (Files.isSymbolicLink(target) || !Files.isDirectory(target))) {
                throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                        "Bundle root is not a safe directory: " + target);
            }
            Files.createDirectories(revisions);
            target = BundleSupport.requireBundleRoot(target);
            revisions = BundleSupport.requireDirectDirectory(
                    target, revisions, "Bundle revisions directory");
            Files.createDirectory(temp);
            BundleSupport.requireDirectDirectory(revisions, temp, "Bundle staging directory");
            ObjectNode planDocument = plan.document();
            validateFontInventory(planDocument, actualFonts);
            String renderPlanHash = planHasher.hash(plan);
            BundleSupport.writeNew(temp.resolve(BundleSupport.PLAN_FILE), plan.canonicalBytes());
            BundleSupport.writeNew(
                    temp.resolve(BundleSupport.PLAN_HASH_FILE),
                    (renderPlanHash + "\n").getBytes(StandardCharsets.UTF_8));

            ObjectNode assetManifest = writeAssets(temp, planDocument, sourceAssets);
            byte[] assetManifestBytes = BundleSupport.canonical(assetManifest);
            BundleSupport.writeNew(temp.resolve(BundleSupport.ASSET_MANIFEST_FILE), assetManifestBytes);
            String assetManifestHash = BundleSupport.sha256(assetManifestBytes);

            ObjectNode fontManifest = actualFonts.manifest();
            byte[] fontManifestBytes = BundleSupport.canonical(fontManifest);
            BundleSupport.writeNew(temp.resolve(BundleSupport.FONT_MANIFEST_FILE), fontManifestBytes);
            String fontManifestHash = BundleSupport.sha256(fontManifestBytes);

            ObjectNode generationManifest = generationManifest(
                    planDocument,
                    renderPlanHash,
                    assetManifestHash,
                    fontManifestHash,
                    buildIdentity);
            byte[] generationBytes = BundleSupport.canonical(generationManifest);
            BundleSupport.writeNew(temp.resolve(BundleSupport.GENERATION_MANIFEST_FILE), generationBytes);
            String generationHash = BundleSupport.sha256(generationBytes);

            try {
                Files.move(temp, revision, StandardCopyOption.ATOMIC_MOVE);
                revisionPublished = true;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                        "Filesystem does not support atomic RenderPlan bundle publication", exception);
            }
            BundleSupport.writeNew(
                    target.resolve(BundleSupport.PENDING_FILE),
                    (revisionName + "\n").getBytes(StandardCharsets.UTF_8));
            return new StagedRenderPlanBundle(
                    target,
                    revisionName,
                    renderPlanHash,
                    generationHash,
                    assetManifestHash,
                    fontManifestHash,
                    assetManifest.withArray("assets").size());
        } catch (RenderPlanBundleException exception) {
            BundleSupport.deleteTree(temp);
            if (revisionPublished) BundleSupport.deleteTree(revision);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            BundleSupport.deleteTree(temp);
            if (revisionPublished) BundleSupport.deleteTree(revision);
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Cannot atomically store RenderPlan bundle", exception);
        }
    }

    public StoredRenderPlanBundle publish(StagedRenderPlanBundle staged) {
        Objects.requireNonNull(staged, "staged");
        Path target = BundleSupport.requireBundleRoot(staged.bundleDirectory());
        Path revisions = BundleSupport.requireDirectDirectory(
                target, target.resolve(BundleSupport.REVISIONS_DIRECTORY),
                "Bundle revisions directory");
        BundleSupport.requireDirectDirectory(
                revisions, revisions.resolve(staged.revisionName()), "Staged bundle revision");
        String pending = readPointer(target.resolve(BundleSupport.PENDING_FILE), "bundle pending pointer");
        if (!staged.revisionName().equals(pending)) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Bundle pending pointer does not name the staged revision");
        }

        Path pointerTemp = target.resolve(".current-" + staged.revisionName() + ".tmp");
        try {
            BundleSupport.writeNew(pointerTemp,
                    (staged.revisionName() + "\n").getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(pointerTemp, target.resolve(BundleSupport.CURRENT_FILE),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                        "Filesystem does not support atomic bundle pointer publication", exception);
            }
            Files.delete(target.resolve(BundleSupport.PENDING_FILE));
            return new StoredRenderPlanBundle(
                    target,
                    staged.renderPlanHash(),
                    staged.generationManifestHash(),
                    staged.assetManifestHash(),
                    staged.fontManifestHash(),
                    staged.assetCount());
        } catch (RenderPlanBundleException exception) {
            try { Files.deleteIfExists(pointerTemp); } catch (IOException ignored) { }
            throw exception;
        } catch (IOException exception) {
            try { Files.deleteIfExists(pointerTemp); } catch (IOException ignored) { }
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Cannot atomically publish RenderPlan bundle", exception);
        }
    }

    public void discard(StagedRenderPlanBundle staged) {
        if (staged == null) {
            return;
        }
        Path target = staged.bundleDirectory();
        String current = optionalPointer(target.resolve(BundleSupport.CURRENT_FILE));
        if (staged.revisionName().equals(current)) {
            return;
        }
        String pending = optionalPointer(target.resolve(BundleSupport.PENDING_FILE));
        if (staged.revisionName().equals(pending)) {
            try { Files.deleteIfExists(target.resolve(BundleSupport.PENDING_FILE)); }
            catch (IOException ignored) { return; }
        }
        BundleSupport.deleteTree(target.resolve(BundleSupport.REVISIONS_DIRECTORY)
                .resolve(staged.revisionName()));
    }

    private static String readPointer(Path file, String label) {
        String value = new String(BundleSupport.readRequired(file, 128), StandardCharsets.UTF_8);
        if (!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\n")) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    label + " is invalid");
        }
        return value.substring(0, value.length() - 1);
    }

    private static String optionalPointer(Path file) {
        try {
            return Files.exists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    ? readPointer(file, file.getFileName().toString())
                    : "";
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static ObjectNode writeAssets(
            Path temp,
            ObjectNode planDocument,
            AssetBinaryResolver sourceAssets
    ) throws IOException {
        JsonNode rawAssets = planDocument.path("assets");
        if (!rawAssets.isArray()) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    "RenderPlan assets must be an array");
        }
        ObjectNode manifest = JsonNodeFactory.instance.objectNode();
        manifest.put("schemaVersion", "asset-manifest.v1");
        ArrayNode entries = manifest.putArray("assets");
        Set<String> paths = new HashSet<>();
        for (JsonNode raw : rawAssets) {
            if (!raw.isObject()) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        "RenderPlan asset entry must be an object");
            }
            ObjectNode asset = (ObjectNode) raw;
            String assetId = BundleSupport.requiredText(asset, "assetId");
            String bundlePath = BundleSupport.requiredText(asset, "bundlePath");
            String mimeType = BundleSupport.requiredText(asset, "mimeType");
            String expectedSha256 = BundleSupport.requiredText(asset, "sha256");
            BundleSupport.requireHash(expectedSha256, "asset sha256");
            if (!paths.add(bundlePath)) {
                throw new RenderPlanBundleException(PptQualityCode.DUPLICATE_ID,
                        "Duplicate asset bundlePath: " + bundlePath);
            }
            Path destination = BundleSupport.resolveBundlePath(temp, bundlePath);
            VerifiedAssetBytes resolved;
            try {
                resolved = sourceAssets.resolve(assetId, bundlePath, expectedSha256);
            } catch (RuntimeException exception) {
                throw new RenderPlanBundleException(PptQualityCode.MANDATORY_ASSET_MISSING,
                        "Source asset resolution failed for " + assetId, exception);
            }
            if (resolved == null) {
                throw new RenderPlanBundleException(PptQualityCode.MANDATORY_ASSET_MISSING,
                        "Source asset is missing: " + assetId);
            }
            byte[] bytes = resolved.bytes();
            String actualSha256 = BundleSupport.sha256(bytes);
            boolean mimeMatches = resolved.mimeType() == null || mimeType.equals(resolved.mimeType());
            if (!assetId.equals(resolved.assetId())
                    || !bundlePath.equals(resolved.bundlePath())
                    || !expectedSha256.equals(resolved.sha256())
                    || !expectedSha256.equals(actualSha256)
                    || !mimeMatches) {
                throw new RenderPlanBundleException(PptQualityCode.ASSET_HASH_MISMATCH,
                        "Source asset identity or bytes do not match plan: " + assetId);
            }
            BundleSupport.writeNew(destination, bytes);
            ObjectNode entry = entries.addObject();
            entry.put("assetId", assetId);
            entry.put("bundlePath", bundlePath);
            entry.put("mimeType", mimeType);
            entry.put("sha256", expectedSha256);
            entry.put("bytes", bytes.length);
        }
        return manifest;
    }

    static void validateFontInventory(ObjectNode planDocument, ProductionFontInventory inventory) {
        ObjectNode engine = BundleSupport.requiredObject(planDocument, "engine");
        ObjectNode resolved = BundleSupport.requiredObject(engine, "resolvedFontProfile");
        requireEqual(inventory.profileId(), BundleSupport.requiredText(resolved, "profileId"), "font profileId");
        requireEqual(inventory.fontProfileHash(), BundleSupport.requiredText(engine, "fontProfileHash"),
                "fontProfileHash");
        requireEqual(inventory.measurementEngineVersion(),
                BundleSupport.requiredText(resolved, "measurementEngineVersion"),
                "measurementEngineVersion");
        JsonNode planFaces = resolved.path("faces");
        if (!planFaces.isArray() || planFaces.size() != inventory.faces().size()) {
            throw new RenderPlanBundleException(PptQualityCode.FONT_UNAVAILABLE,
                    "Production font inventory does not match RenderPlan face count");
        }
        Set<String> planIds = new HashSet<>();
        for (JsonNode raw : planFaces) {
            ObjectNode face = (ObjectNode) raw;
            String faceId = BundleSupport.requiredText(face, "fontFaceId");
            ProductionFontFace actual;
            try {
                actual = inventory.requireFace(faceId);
            } catch (IllegalArgumentException exception) {
                throw new RenderPlanBundleException(PptQualityCode.FONT_UNAVAILABLE,
                        "Missing actual font face " + faceId, exception);
            }
            planIds.add(faceId);
            requireEqual(actual.role(), BundleSupport.requiredText(face, "role"), "font role");
            if (actual.weight() != BundleSupport.requiredInt(face, "weight")) {
                throw fontMismatch("font weight", faceId);
            }
            requireEqual(actual.resolvedFamily(), BundleSupport.requiredText(face, "selectedFamily"),
                    "resolved font family");
            requireEqual(actual.postScriptName(), BundleSupport.requiredText(face, "postScriptName"),
                    "PostScript font name");
            requireEqual(actual.fontSource(), BundleSupport.requiredText(face, "fontSource"), "font source");
            requireEqual(actual.fontFingerprint(), BundleSupport.requiredText(face, "fontFingerprint"),
                    "font fingerprint");
            if (actual.fallbackApplied() != BundleSupport.requiredBoolean(face, "fallbackApplied")) {
                throw fontMismatch("fallbackApplied", faceId);
            }
        }
        if (planIds.size() != inventory.faces().size()) {
            throw new RenderPlanBundleException(PptQualityCode.FONT_UNAVAILABLE,
                    "Production font inventory contains unplanned faces");
        }
    }

    private static ObjectNode generationManifest(
            ObjectNode plan,
            String renderPlanHash,
            String assetManifestHash,
            String fontManifestHash,
            BundleBuildIdentity buildIdentity
    ) {
        ObjectNode engine = BundleSupport.requiredObject(plan, "engine");
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        output.put("schemaVersion", "generation-manifest.v1");
        output.put("presentationId", BundleSupport.requiredText(plan, "presentationId"));
        output.put("sourceTreeHash", BundleSupport.requiredText(plan, "sourceTreeHash"));
        output.put("renderPlanHash", renderPlanHash);
        output.put("assetManifestHash", assetManifestHash);
        output.put("fontManifestHash", fontManifestHash);
        output.put("rendererVersion", buildIdentity.rendererVersion());
        output.put("gitCommit", buildIdentity.gitCommit());
        ObjectNode outputEngine = output.putObject("engine");
        for (String field : new String[]{
                "engineVersion",
                "themeId",
                "themeVersion",
                "themeHash",
                "layoutCatalogVersion",
                "layoutCatalogHash",
                "fontProfileHash"
        }) {
            outputEngine.put(field, BundleSupport.requiredText(engine, field));
        }
        return output;
    }

    private static void requireEqual(String actual, String planned, String field) {
        if (!Objects.equals(actual, planned)) {
            throw new RenderPlanBundleException(PptQualityCode.FONT_SUBSTITUTED,
                    field + " does not match the frozen RenderPlan");
        }
    }

    private static RenderPlanBundleException fontMismatch(String field, String faceId) {
        return new RenderPlanBundleException(PptQualityCode.FONT_SUBSTITUTED,
                field + " does not match for " + faceId);
    }
}
