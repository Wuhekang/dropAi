package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenRenderPlanCodec;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict read boundary for immutable production RenderPlan bundle revisions. */
public final class RenderPlanBundleLoader {
    private static final long MAX_PLAN_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_ASSET_BYTES = 256L * 1024L * 1024L;
    private final FrozenRenderPlanCodec codec = new FrozenRenderPlanCodec();

    public LoadedRenderPlanBundle load(Path bundleDirectory, BundleRuntimeExpectations runtime) {
        Objects.requireNonNull(bundleDirectory, "bundleDirectory");
        Objects.requireNonNull(runtime, "runtime");
        Path root = BundleSupport.requireBundleRoot(bundleDirectory);
        if (Files.exists(root.resolve(BundleSupport.PENDING_FILE), LinkOption.NOFOLLOW_LINKS)) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "RenderPlan bundle publication is pending");
        }
        Path revision = resolveCurrentRevision(root);

        byte[] planBytes = BundleSupport.readRequired(revision.resolve(BundleSupport.PLAN_FILE), MAX_PLAN_BYTES);
        String persistedPlanHash = BundleSupport.readHashFile(revision.resolve(BundleSupport.PLAN_HASH_FILE));
        requireHashEquals(persistedPlanHash, BundleSupport.sha256(planBytes), "render plan bytes");
        FrozenSlideRenderPlan plan;
        try {
            plan = codec.decodeCanonical(planBytes);
        } catch (RuntimeException exception) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    "Persisted RenderPlan is not canonical", exception);
        }

        ObjectNode generation = BundleSupport.readCanonicalObject(
                revision.resolve(BundleSupport.GENERATION_MANIFEST_FILE));
        ObjectNode assetManifest = BundleSupport.readCanonicalObject(
                revision.resolve(BundleSupport.ASSET_MANIFEST_FILE));
        ObjectNode fontManifest = BundleSupport.readCanonicalObject(
                revision.resolve(BundleSupport.FONT_MANIFEST_FILE));
        BundleSupport.requiredSchema(generation, "generation-manifest.v1");
        BundleSupport.requiredSchema(assetManifest, "asset-manifest.v1");

        requireHashEquals(BundleSupport.requiredText(generation, "renderPlanHash"),
                persistedPlanHash, "generation renderPlanHash");
        requireHashEquals(BundleSupport.requiredText(generation, "assetManifestHash"),
                BundleSupport.sha256(BundleSupport.canonical(assetManifest)), "asset manifest");
        requireHashEquals(BundleSupport.requiredText(generation, "fontManifestHash"),
                BundleSupport.sha256(BundleSupport.canonical(fontManifest)), "font manifest");
        requireEqual(BundleSupport.requiredText(generation, "rendererVersion"),
                runtime.rendererVersion(), "rendererVersion");
        requireEqual(BundleSupport.requiredText(generation, "gitCommit"),
                runtime.gitCommit(), "gitCommit");

        ObjectNode document = plan.document();
        verifyGeneration(document, generation);
        verifyRuntime(document, runtime);
        ProductionFontInventory persistedFonts = ProductionFontInventory.fromManifest(fontManifest);
        RenderPlanBundleStore.validateFontInventory(document, persistedFonts);
        if (!Arrays.equals(BundleSupport.canonical(persistedFonts.manifest()),
                BundleSupport.canonical(runtime.fontInventory().manifest()))) {
            throw new RenderPlanBundleException(PptQualityCode.FONT_SUBSTITUTED,
                    "Runtime font inventory differs from the production bundle");
        }

        Map<String, VerifiedAssetBytes> assets = loadAssets(revision, document, assetManifest);
        AssetBinaryResolver resolver = strictResolver(assets);
        return new LoadedRenderPlanBundle(root, plan, persistedPlanHash, resolver, assets.size());
    }

    private static Path resolveCurrentRevision(Path root) {
        String pointer = new String(
                BundleSupport.readRequired(root.resolve(BundleSupport.CURRENT_FILE), 128),
                StandardCharsets.UTF_8);
        if (!pointer.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\n")) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Bundle current pointer is invalid");
        }
        String revisionName = pointer.substring(0, pointer.length() - 1);
        Path revisions = root.resolve(BundleSupport.REVISIONS_DIRECTORY);
        revisions = BundleSupport.requireDirectDirectory(root, revisions, "Bundle revisions directory");
        Path revision = revisions.resolve(revisionName).normalize();
        return BundleSupport.requireDirectDirectory(revisions, revision, "Current bundle revision");
    }

    private static void verifyGeneration(ObjectNode plan, ObjectNode generation) {
        requireEqual(BundleSupport.requiredText(generation, "presentationId"),
                BundleSupport.requiredText(plan, "presentationId"), "presentationId");
        requireHashEquals(BundleSupport.requiredText(generation, "sourceTreeHash"),
                BundleSupport.requiredText(plan, "sourceTreeHash"), "sourceTreeHash");
        ObjectNode plannedEngine = BundleSupport.requiredObject(plan, "engine");
        ObjectNode recordedEngine = BundleSupport.requiredObject(generation, "engine");
        for (String field : new String[]{
                "engineVersion", "themeId", "themeVersion", "themeHash",
                "layoutCatalogVersion", "layoutCatalogHash", "fontProfileHash"
        }) {
            requireEqual(BundleSupport.requiredText(recordedEngine, field),
                    BundleSupport.requiredText(plannedEngine, field), "engine." + field);
        }
    }

    private static void verifyRuntime(ObjectNode plan, BundleRuntimeExpectations runtime) {
        ObjectNode engine = BundleSupport.requiredObject(plan, "engine");
        requireEqual(runtime.engineVersion(), BundleSupport.requiredText(engine, "engineVersion"), "engineVersion");
        requireEqual(runtime.themeId(), BundleSupport.requiredText(engine, "themeId"), "themeId");
        requireEqual(runtime.themeVersion(), BundleSupport.requiredText(engine, "themeVersion"), "themeVersion");
        requireHashEquals(runtime.themeHash(), BundleSupport.requiredText(engine, "themeHash"), "themeHash");
        requireEqual(runtime.layoutCatalogVersion(),
                BundleSupport.requiredText(engine, "layoutCatalogVersion"), "layoutCatalogVersion");
        requireHashEquals(runtime.layoutCatalogHash(),
                BundleSupport.requiredText(engine, "layoutCatalogHash"), "layoutCatalogHash");
        RenderPlanBundleStore.validateFontInventory(plan, runtime.fontInventory());
    }

    private static Map<String, VerifiedAssetBytes> loadAssets(
            Path revision,
            ObjectNode plan,
            ObjectNode manifest
    ) {
        JsonNode rawPlanAssets = plan.path("assets");
        JsonNode rawManifestAssets = manifest.path("assets");
        if (!rawPlanAssets.isArray() || !rawManifestAssets.isArray()
                || rawPlanAssets.size() != rawManifestAssets.size()) {
            throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                    "Asset manifest does not match RenderPlan asset count");
        }
        Map<String, ObjectNode> entries = new HashMap<>();
        Set<String> manifestPaths = new HashSet<>();
        for (JsonNode raw : rawManifestAssets) {
            if (!raw.isObject()) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        "Asset manifest entry must be an object");
            }
            ObjectNode entry = (ObjectNode) raw;
            String id = BundleSupport.requiredText(entry, "assetId");
            String path = BundleSupport.requiredText(entry, "bundlePath");
            if (entries.putIfAbsent(id, entry) != null || !manifestPaths.add(path)) {
                throw new RenderPlanBundleException(PptQualityCode.DUPLICATE_ID,
                        "Duplicate asset identity in manifest: " + id);
            }
        }
        Map<String, VerifiedAssetBytes> loaded = new HashMap<>();
        for (JsonNode raw : rawPlanAssets) {
            if (!raw.isObject()) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        "RenderPlan asset entry must be an object");
            }
            ObjectNode planned = (ObjectNode) raw;
            String id = BundleSupport.requiredText(planned, "assetId");
            ObjectNode entry = entries.remove(id);
            if (entry == null) {
                throw new RenderPlanBundleException(PptQualityCode.MANDATORY_ASSET_MISSING,
                        "Asset manifest is missing " + id);
            }
            String path = BundleSupport.requiredText(planned, "bundlePath");
            String mime = BundleSupport.requiredText(planned, "mimeType");
            String hash = BundleSupport.requiredText(planned, "sha256");
            requireEqual(BundleSupport.requiredText(entry, "bundlePath"), path, "asset bundlePath");
            requireEqual(BundleSupport.requiredText(entry, "mimeType"), mime, "asset mimeType");
            requireHashEquals(BundleSupport.requiredText(entry, "sha256"), hash, "asset sha256");
            JsonNode bytesNode = entry.path("bytes");
            if (!bytesNode.isIntegralNumber() || !bytesNode.canConvertToLong() || bytesNode.longValue() < 1) {
                throw new RenderPlanBundleException(PptQualityCode.SCHEMA_INVALID,
                        "Asset byte count is invalid: " + id);
            }
            Path file = BundleSupport.resolveBundlePath(revision, path);
            BundleSupport.requireNoSymbolicPath(revision, file);
            byte[] bytes = BundleSupport.readRequired(file, MAX_ASSET_BYTES);
            if (bytes.length != bytesNode.longValue()) {
                throw new RenderPlanBundleException(PptQualityCode.ASSET_HASH_MISMATCH,
                        "Asset byte count changed: " + id);
            }
            requireHashEquals(hash, BundleSupport.sha256(bytes), "asset bytes " + id);
            loaded.put(id, new VerifiedAssetBytes(id, path, hash, mime, bytes));
        }
        if (!entries.isEmpty()) {
            throw new RenderPlanBundleException(PptQualityCode.INVALID_REFERENCE,
                    "Asset manifest contains unplanned assets");
        }
        return Map.copyOf(loaded);
    }

    private static AssetBinaryResolver strictResolver(Map<String, VerifiedAssetBytes> assets) {
        return (assetId, bundlePath, expectedSha256) -> {
            VerifiedAssetBytes asset = assets.get(assetId);
            if (asset == null) {
                throw new RenderPlanBundleException(PptQualityCode.MANDATORY_ASSET_MISSING,
                        "Bundle has no asset " + assetId);
            }
            if (!asset.bundlePath().equals(bundlePath) || !asset.sha256().equals(expectedSha256)) {
                throw new RenderPlanBundleException(PptQualityCode.ASSET_HASH_MISMATCH,
                        "Requested asset identity differs from bundle: " + assetId);
            }
            return asset;
        };
    }

    private static void requireHashEquals(String actual, String expected, String field) {
        BundleSupport.requireHash(actual, field);
        BundleSupport.requireHash(expected, field);
        if (!actual.equals(expected)) {
            throw new RenderPlanBundleException(PptQualityCode.RENDER_PLAN_HASH_MISMATCH,
                    field + " differs from the immutable bundle");
        }
    }

    private static void requireEqual(String actual, String expected, String field) {
        if (!Objects.equals(actual, expected)) {
            throw new RenderPlanBundleException(PptQualityCode.NON_DETERMINISTIC_RENDER_PLAN,
                    field + " differs from the immutable bundle");
        }
    }
}
