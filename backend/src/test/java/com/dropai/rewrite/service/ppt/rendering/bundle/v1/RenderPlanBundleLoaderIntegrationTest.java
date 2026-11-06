package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.PptEngineV1Service;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPlanBundleLoaderIntegrationTest {
    @TempDir
    Path temp;

    @Test
    void validProductionBundleLoadsAndTheGenerationServiceWritesExactlyFortySlides() throws Exception {
        Path bundleRoot = temp.resolve("bundle");
        RenderPlanBundleTestSupport.store(bundleRoot);

        LoadedRenderPlanBundle loaded = new RenderPlanBundleLoader().load(
                bundleRoot,
                RenderPlanBundleTestSupport.expectations());
        Path output = temp.resolve("output").resolve("health-management.pptx");
        var rendered = new PptEngineV1Service().generate(
                loaded.renderPlan(),
                loaded.assetResolver(),
                output);

        assertEquals(bundleRoot.toAbsolutePath().normalize(), loaded.bundleDirectory());
        assertEquals(RenderPlanBundleTestSupport.expectedPlanHash(), loaded.renderPlanHash());
        assertEquals(25, loaded.assetCount());
        assertEquals(40, rendered.slideCount());
        assertEquals(RenderPlanBundleTestSupport.expectedPlanHash(), rendered.renderPlanHash());
        assertTrue(Files.isRegularFile(output));
        try (XMLSlideShow show = new XMLSlideShow(Files.newInputStream(output))) {
            assertEquals(40, show.getSlides().size());
        }
    }

    @Test
    void missingBundleFailsAsNotReadyInsteadOfFallingBackOrCreatingOutput() {
        Path missing = temp.resolve("not-ready");

        RenderPlanBundleException failure = assertThrows(
                RenderPlanBundleException.class,
                () -> new RenderPlanBundleLoader().load(
                        missing,
                        RenderPlanBundleTestSupport.expectations()));

        assertTrue(failure.getMessage().toLowerCase(java.util.Locale.ROOT).matches(
                ".*(missing|does not exist|not ready).*"), failure.getMessage());
        assertFalse(Files.exists(temp.resolve("not-ready.pptx")));
    }

    @Test
    void rejectsPersistedPlanHashThemeAndLayoutTampering() throws Exception {
        assertLoadFailsAfter("plan-hash", revision -> {
            try {
                Files.writeString(revision.resolve(BundleSupport.PLAN_HASH_FILE),
                        "sha256:" + "0".repeat(64) + "\n");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
        assertLoadFailsAfter("theme", revision -> mutateGenerationEngine(
                revision, "themeHash", "sha256:" + "0".repeat(64)));
        assertLoadFailsAfter("layout", revision -> mutateGenerationEngine(
                revision, "layoutCatalogHash", "sha256:" + "0".repeat(64)));
    }

    @Test
    void rejectsFontAndAssetManifestTamperingEvenWhenJsonRemainsCanonical() {
        assertLoadFailsAfter("font-manifest", revision -> {
            Path manifest = revision.resolve(BundleSupport.FONT_MANIFEST_FILE);
            ObjectNode font = RenderPlanBundleTestSupport.readObject(manifest);
            ((ObjectNode) font.path("faces").get(0))
                    .put("fontFingerprint", "sha256:" + "0".repeat(64));
            RenderPlanBundleTestSupport.writeCanonical(manifest, font);
        });
        assertLoadFailsAfter("asset-manifest", revision -> {
            Path manifest = revision.resolve(BundleSupport.ASSET_MANIFEST_FILE);
            ObjectNode assets = RenderPlanBundleTestSupport.readObject(manifest);
            ((ObjectNode) assets.path("assets").get(0))
                    .put("sha256", "sha256:" + "0".repeat(64));
            RenderPlanBundleTestSupport.writeCanonical(manifest, assets);
        });
        assertLoadFailsAfter("asset-bytes", revision -> {
            Path asset = revision.resolve("assets/figure_2_01.png");
            try {
                byte[] bytes = Files.readAllBytes(asset);
                bytes[bytes.length / 2] ^= 0x01;
                Files.write(asset, bytes);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    @Test
    void rejectsRuntimeThemeLayoutAndFontFingerprintDrift() {
        Path root = temp.resolve("runtime-drift");
        RenderPlanBundleTestSupport.store(root);
        BundleRuntimeExpectations exact = RenderPlanBundleTestSupport.expectations();

        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                new BundleRuntimeExpectations(
                        "9.9.9",
                        exact.rendererVersion(),
                        exact.gitCommit(),
                        exact.themeId(), exact.themeVersion(), exact.themeHash(),
                        exact.layoutCatalogVersion(), exact.layoutCatalogHash(), exact.fontInventory())));
        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                new BundleRuntimeExpectations(
                        exact.engineVersion(),
                        "9.9.9",
                        exact.gitCommit(),
                        exact.themeId(), exact.themeVersion(), exact.themeHash(),
                        exact.layoutCatalogVersion(), exact.layoutCatalogHash(), exact.fontInventory())));
        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                new BundleRuntimeExpectations(
                        exact.engineVersion(),
                        exact.rendererVersion(),
                        "2".repeat(40),
                        exact.themeId(), exact.themeVersion(), exact.themeHash(),
                        exact.layoutCatalogVersion(), exact.layoutCatalogHash(), exact.fontInventory())));
        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                new BundleRuntimeExpectations(
                        exact.engineVersion(),
                        exact.rendererVersion(),
                        exact.gitCommit(),
                        exact.themeId(), exact.themeVersion(), "sha256:" + "0".repeat(64),
                        exact.layoutCatalogVersion(), exact.layoutCatalogHash(), exact.fontInventory())));
        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                new BundleRuntimeExpectations(
                        exact.engineVersion(),
                        exact.rendererVersion(),
                        exact.gitCommit(),
                        exact.themeId(), exact.themeVersion(), exact.themeHash(),
                        exact.layoutCatalogVersion(), "sha256:" + "0".repeat(64), exact.fontInventory())));

        ProductionFontInventory fonts = exact.fontInventory();
        var faces = new ArrayList<>(fonts.faces());
        ProductionFontFace first = faces.get(0);
        faces.set(0, new ProductionFontFace(
                first.fontFaceId(), first.role(), first.weight(), first.requestedFamily(),
                first.resolvedFamily(), first.postScriptName(), first.fontSource(),
                "sha256:" + "0".repeat(64), first.fallbackApplied()));
        ProductionFontInventory drifted = new ProductionFontInventory(
                fonts.profileId(), fonts.fontProfileHash(), fonts.measurementEngineVersion(), faces);
        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                new BundleRuntimeExpectations(
                        exact.engineVersion(),
                        exact.rendererVersion(),
                        exact.gitCommit(),
                        exact.themeId(), exact.themeVersion(), exact.themeHash(),
                        exact.layoutCatalogVersion(), exact.layoutCatalogHash(), drifted)));
    }

    private void assertLoadFailsAfter(
            String name,
            java.util.function.Consumer<Path> mutation
    ) {
        Path root = temp.resolve(name);
        RenderPlanBundleTestSupport.store(root);
        mutation.accept(RenderPlanBundleTestSupport.currentRevision(root));
        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleLoader().load(
                root,
                RenderPlanBundleTestSupport.expectations()));
    }

    private void mutateGenerationEngine(Path revision, String field, String value) {
        Path manifest = revision.resolve(BundleSupport.GENERATION_MANIFEST_FILE);
        ObjectNode generation = RenderPlanBundleTestSupport.readObject(manifest);
        ((ObjectNode) generation.path("engine")).put(field, value);
        RenderPlanBundleTestSupport.writeCanonical(manifest, generation);
    }
}
