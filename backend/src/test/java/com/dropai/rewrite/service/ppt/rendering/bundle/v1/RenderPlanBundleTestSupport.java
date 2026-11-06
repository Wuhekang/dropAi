package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanCanonicalizer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.VerifiedAssetBytes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RenderPlanBundleTestSupport {
    static final String FIXTURE_ROOT = "ppt/rendering-fixtures/health-management/v1/";
    private static final String PLAN = FIXTURE_ROOT + "expected-render-plan.v1.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final RenderPlanCanonicalizer CANONICALIZER = new RenderPlanCanonicalizer();
    private static final BundleBuildIdentity BUILD_IDENTITY = new BundleBuildIdentity(
            com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererVersion.VERSION,
            "1111111111111111111111111111111111111111");

    private RenderPlanBundleTestSupport() {
    }

    static FrozenSlideRenderPlan plan() {
        try {
            return frozen(CANONICALIZER.canonicalBytes(MAPPER.readTree(readResource(PLAN))));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load canonical RenderPlan fixture", exception);
        }
    }

    static ObjectNode document() {
        return plan().document();
    }

    static ProductionFontInventory fonts() {
        ObjectNode plan = document();
        ObjectNode engine = (ObjectNode) plan.path("engine");
        ObjectNode profile = (ObjectNode) engine.path("resolvedFontProfile");
        List<ProductionFontFace> faces = new ArrayList<>();
        for (JsonNode face : profile.path("faces")) {
            String selected = face.path("selectedFamily").asText();
            faces.add(new ProductionFontFace(
                    face.path("fontFaceId").asText(),
                    face.path("role").asText(),
                    face.path("weight").asInt(),
                    selected,
                    selected,
                    face.path("postScriptName").asText(),
                    face.path("fontSource").asText(),
                    face.path("fontFingerprint").asText(),
                    face.path("fallbackApplied").asBoolean()));
        }
        return new ProductionFontInventory(
                profile.path("profileId").asText(),
                engine.path("fontProfileHash").asText(),
                profile.path("measurementEngineVersion").asText(),
                faces);
    }

    public static BundleRuntimeExpectations expectations() {
        ObjectNode engine = (ObjectNode) document().path("engine");
        return new BundleRuntimeExpectations(
                engine.path("engineVersion").asText(),
                com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererVersion.VERSION,
                BUILD_IDENTITY.gitCommit(),
                engine.path("themeId").asText(),
                engine.path("themeVersion").asText(),
                engine.path("themeHash").asText(),
                engine.path("layoutCatalogVersion").asText(),
                engine.path("layoutCatalogHash").asText(),
                fonts());
    }

    static AssetBinaryResolver assets() {
        return (assetId, bundlePath, expectedSha256) -> new VerifiedAssetBytes(
                assetId,
                bundlePath,
                expectedSha256,
                mimeType(bundlePath),
                readResource(FIXTURE_ROOT + bundlePath));
    }

    public static StoredRenderPlanBundle store(Path directory) {
        return new RenderPlanBundleStore().store(
                directory, plan(), assets(), fonts(), BUILD_IDENTITY);
    }

    static BundleBuildIdentity buildIdentity() {
        return BUILD_IDENTITY;
    }

    static Path currentRevision(Path bundleRoot) {
        try {
            String revision = Files.readString(
                    bundleRoot.resolve(BundleSupport.CURRENT_FILE), StandardCharsets.UTF_8).strip();
            return bundleRoot.resolve(BundleSupport.REVISIONS_DIRECTORY).resolve(revision);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot resolve current test bundle revision", exception);
        }
    }

    static ObjectNode readObject(Path file) {
        try {
            JsonNode node = MAPPER.readTree(Files.readAllBytes(file));
            if (!(node instanceof ObjectNode object)) {
                throw new AssertionError("Expected JSON object in " + file);
            }
            return object;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read test JSON " + file, exception);
        }
    }

    static void writeCanonical(Path file, JsonNode document) {
        try {
            Files.write(file, CANONICALIZER.canonicalBytes(document));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot mutate test bundle " + file, exception);
        }
    }

    static Path copyDirectory(Path source, Path destination) {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path target = destination.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            return destination;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot copy test bundle", exception);
        }
    }

    static byte[] readResource(String resource) {
        try (InputStream input = RenderPlanBundleTestSupport.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + resource);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read classpath resource: " + resource, exception);
        }
    }

    static String expectedPlanHash() {
        return new String(readResource(
                FIXTURE_ROOT + "expected-render-plan.v1.sha256"), StandardCharsets.UTF_8).strip();
    }

    private static FrozenSlideRenderPlan frozen(byte[] bytes) {
        try {
            Method factory = FrozenSlideRenderPlan.class
                    .getDeclaredMethod("fromCanonicalBytes", byte[].class);
            factory.setAccessible(true);
            return (FrozenSlideRenderPlan) factory.invoke(null, (Object) bytes);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access frozen RenderPlan fixture boundary", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Cannot freeze fixture", exception.getCause());
        }
    }

    private static String mimeType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") ? "image/png" : "image/jpeg";
    }
}
