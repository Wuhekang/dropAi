package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderPlanBundleStoreTest {
    @TempDir
    Path temp;

    @Test
    void atomicallyPublishesTheExactPlanManifestsAndTwentyFiveAssets() throws Exception {
        Path bundle = temp.resolve("bundle");

        StoredRenderPlanBundle stored = RenderPlanBundleTestSupport.store(bundle);
        Path revision = RenderPlanBundleTestSupport.currentRevision(bundle);

        assertEquals(bundle.toAbsolutePath().normalize(), stored.bundleDirectory());
        assertEquals(RenderPlanBundleTestSupport.expectedPlanHash(), stored.renderPlanHash());
        assertEquals(25, stored.assetCount());
        assertTrue(Files.isRegularFile(bundle.resolve(BundleSupport.CURRENT_FILE)));
        assertTrue(Files.isRegularFile(revision.resolve("render-plan.json")));
        assertTrue(Files.isRegularFile(revision.resolve("render-plan.sha256")));
        assertTrue(Files.isRegularFile(revision.resolve("generation-manifest.json")));
        assertTrue(Files.isRegularFile(revision.resolve("asset-manifest.json")));
        assertTrue(Files.isRegularFile(revision.resolve("font-manifest.json")));
        try (var files = Files.walk(revision.resolve("assets"))) {
            assertEquals(25, files.filter(Files::isRegularFile).count());
        }
        try (var revisions = Files.list(bundle.resolve(BundleSupport.REVISIONS_DIRECTORY))) {
            assertTrue(revisions.noneMatch(path -> path.getFileName().toString().contains(".tmp")));
        }
    }

    @Test
    void failedPublicationNeverExposesACompleteOrPartialTarget() throws Exception {
        Path bundle = temp.resolve("bundle");
        var missingAsset = new java.util.concurrent.atomic.AtomicInteger();

        RenderPlanBundleException failure = assertThrows(
                RenderPlanBundleException.class,
                () -> new RenderPlanBundleStore().store(
                        bundle,
                        RenderPlanBundleTestSupport.plan(),
                        (assetId, bundlePath, expectedSha256) ->
                                missingAsset.incrementAndGet() == 8
                                        ? null
                                                : RenderPlanBundleTestSupport.assets()
                                                .resolve(assetId, bundlePath, expectedSha256),
                        RenderPlanBundleTestSupport.fonts(),
                        RenderPlanBundleTestSupport.buildIdentity()));

        assertEquals(PptQualityCode.MANDATORY_ASSET_MISSING, failure.qualityCode());
        assertFalse(Files.exists(bundle.resolve(BundleSupport.CURRENT_FILE)));
        try (var revisions = Files.list(bundle.resolve(BundleSupport.REVISIONS_DIRECTORY))) {
            assertEquals(0, revisions.count(), "No unpublished revision may be exposed");
        }
    }

    @Test
    void refusesFontSubstitutionBeforePublishingAnything() throws Exception {
        Path bundle = temp.resolve("bundle");
        ProductionFontInventory expected = RenderPlanBundleTestSupport.fonts();
        var faces = new java.util.ArrayList<>(expected.faces());
        ProductionFontFace first = faces.get(0);
        faces.set(0, new ProductionFontFace(
                first.fontFaceId(),
                first.role(),
                first.weight(),
                first.requestedFamily(),
                first.resolvedFamily(),
                first.postScriptName(),
                first.fontSource(),
                "sha256:" + "0".repeat(64),
                first.fallbackApplied()));
        ProductionFontInventory substituted = new ProductionFontInventory(
                expected.profileId(),
                expected.fontProfileHash(),
                expected.measurementEngineVersion(),
                faces);

        RenderPlanBundleException failure = assertThrows(
                RenderPlanBundleException.class,
                () -> new RenderPlanBundleStore().store(
                        bundle,
                        RenderPlanBundleTestSupport.plan(),
                        RenderPlanBundleTestSupport.assets(),
                        substituted,
                        RenderPlanBundleTestSupport.buildIdentity()));

        assertEquals(PptQualityCode.FONT_SUBSTITUTED, failure.qualityCode());
        assertFalse(Files.exists(bundle.resolve(BundleSupport.CURRENT_FILE)));
        try (var revisions = Files.list(bundle.resolve(BundleSupport.REVISIONS_DIRECTORY))) {
            assertEquals(0, revisions.count(), "No unpublished revision may be exposed");
        }
    }

    @Test
    void failedReplacementCannotHideOrCorruptThePreviouslyPublishedRevision() {
        Path bundle = temp.resolve("bundle");
        StoredRenderPlanBundle first = RenderPlanBundleTestSupport.store(bundle);

        assertThrows(RenderPlanBundleException.class, () -> new RenderPlanBundleStore().store(
                bundle,
                RenderPlanBundleTestSupport.plan(),
                (assetId, bundlePath, expectedSha256) -> null,
                RenderPlanBundleTestSupport.fonts(),
                RenderPlanBundleTestSupport.buildIdentity()));

        LoadedRenderPlanBundle stillCurrent = new RenderPlanBundleLoader().load(
                bundle,
                RenderPlanBundleTestSupport.expectations());
        assertEquals(first.renderPlanHash(), stillCurrent.renderPlanHash());
        assertEquals(25, stillCurrent.assetCount());
    }

    @Test
    void cleanupNeverTraversesAReferencedDirectoryOutsideItsStagingRoot() throws Exception {
        Path outside=temp.resolve("outside");Files.createDirectories(outside);
        Path sentinel=outside.resolve("must-survive.txt");Files.writeString(sentinel,"protected");
        Path staging=temp.resolve("revisions").resolve(".staging.tmp");Files.createDirectories(staging);
        Path link=staging.resolve("external-directory");
        try{Files.createSymbolicLink(link,outside);}catch(Exception unsupported){
            Assumptions.assumeTrue(false,"Symbolic links are unavailable in this environment");
        }

        BundleSupport.deleteTree(staging);

        assertTrue(Files.isRegularFile(sentinel));
        assertEquals("protected",Files.readString(sentinel));
    }

    @Test
    void stagedRevisionBlocksTheOldCurrentUntilAfterCommitPublication() {
        Path bundle=temp.resolve("bundle");
        RenderPlanBundleTestSupport.store(bundle);
        RenderPlanBundleStore store=new RenderPlanBundleStore();
        StagedRenderPlanBundle staged=store.stage(
                bundle,RenderPlanBundleTestSupport.plan(),RenderPlanBundleTestSupport.assets(),
                RenderPlanBundleTestSupport.fonts(),RenderPlanBundleTestSupport.buildIdentity());

        assertTrue(Files.isRegularFile(bundle.resolve(BundleSupport.PENDING_FILE)));
        assertThrows(RenderPlanBundleException.class,()->new RenderPlanBundleLoader().load(
                bundle,RenderPlanBundleTestSupport.expectations()));

        store.publish(staged);
        assertFalse(Files.exists(bundle.resolve(BundleSupport.PENDING_FILE)));
        assertEquals(staged.renderPlanHash(),new RenderPlanBundleLoader().load(
                bundle,RenderPlanBundleTestSupport.expectations()).renderPlanHash());
    }

    @Test
    void transactionRollbackDiscardsTheStageAndRestoresThePreviousCurrent() {
        Path bundle=temp.resolve("bundle");
        StoredRenderPlanBundle previous=RenderPlanBundleTestSupport.store(bundle);
        RenderPlanBundleStore store=new RenderPlanBundleStore();
        StagedRenderPlanBundle staged=store.stage(
                bundle,RenderPlanBundleTestSupport.plan(),RenderPlanBundleTestSupport.assets(),
                RenderPlanBundleTestSupport.fonts(),RenderPlanBundleTestSupport.buildIdentity());

        store.discard(staged);

        assertFalse(Files.exists(bundle.resolve(BundleSupport.PENDING_FILE)));
        assertFalse(Files.exists(bundle.resolve(BundleSupport.REVISIONS_DIRECTORY)
                .resolve(staged.revisionName())));
        assertEquals(previous.renderPlanHash(),new RenderPlanBundleLoader().load(
                bundle,RenderPlanBundleTestSupport.expectations()).renderPlanHash());
    }
}
