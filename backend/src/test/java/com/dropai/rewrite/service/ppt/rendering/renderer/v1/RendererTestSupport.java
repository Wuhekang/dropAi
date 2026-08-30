package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.canonical.v1.RenderPlanCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTextBox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class RendererTestSupport {
    static final String FIXTURE_ROOT = "ppt/rendering-fixtures/health-management/v1/";
    static final String PLAN_RESOURCE = FIXTURE_ROOT + "expected-render-plan.v1.json";
    static final String PLAN_HASH_RESOURCE = FIXTURE_ROOT + "expected-render-plan.v1.sha256";
    private static final Pattern SLIDE_XML = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RendererTestSupport() {
    }

    static RenderedFixture renderedFixture() {
        return FixtureHolder.RENDERED;
    }

    static FrozenSlideRenderPlan frozenFixture() {
        try {
            JsonNode resourceDocument = MAPPER.readTree(readResource(PLAN_RESOURCE));
            return frozenFromBytes(new RenderPlanCanonicalizer().canonicalBytes(resourceDocument));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot canonicalize the committed RenderPlan fixture", exception);
        }
    }

    static ObjectNode fixtureDocument() {
        return frozenFixture().document();
    }

    static FrozenSlideRenderPlan mutateFixture(UnaryOperator<ObjectNode> mutation) {
        ObjectNode document = fixtureDocument();
        ObjectNode mutated = Objects.requireNonNull(mutation.apply(document), "mutated plan");
        return frozenFromBytes(new RenderPlanCanonicalizer().canonicalBytes(mutated));
    }

    static RenderedFixture render(FrozenSlideRenderPlan plan, AssetBinaryResolver resolver) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RenderedPptx result = new PurePptxRendererImpl().render(plan, resolver, output);
        return new RenderedFixture(plan, plan.document(), output.toByteArray(), result);
    }

    static AssetBinaryResolver classpathAssetResolver() {
        return (assetId, bundlePath, expectedSha256) -> {
            byte[] bytes = readResource(FIXTURE_ROOT + bundlePath);
            String actual = sha256(bytes);
            if (!expectedSha256.equals(actual)) {
                throw new AssertionError("Fixture asset hash drift for " + assetId
                        + ": expected=" + expectedSha256 + ", actual=" + actual);
            }
            return new VerifiedAssetBytes(
                    assetId,
                    bundlePath,
                    expectedSha256,
                    mimeType(bundlePath),
                    bytes);
        };
    }

    static byte[] readResource(String resource) {
        try (InputStream input = RendererTestSupport.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + resource);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read classpath resource: " + resource, exception);
        }
    }

    static String expectedPlanHash() {
        return new String(readResource(PLAN_HASH_RESOURCE), StandardCharsets.UTF_8).strip();
    }

    static XMLSlideShow open(byte[] pptx) {
        try {
            return new XMLSlideShow(new ByteArrayInputStream(pptx));
        } catch (IOException exception) {
            throw new IllegalStateException("Renderer output is not an openable PPTX", exception);
        }
    }

    static Map<String, byte[]> zipEntries(byte[] pptx) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pptx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect renderer OOXML", exception);
        }
        return entries;
    }

    static List<String> slideXml(byte[] pptx) {
        return zipEntries(pptx).entrySet().stream()
                .filter(entry -> SLIDE_XML.matcher(entry.getKey()).matches())
                .sorted(Comparator.comparingInt(entry -> slideNumber(entry.getKey())))
                .map(entry -> new String(entry.getValue(), StandardCharsets.UTF_8))
                .toList();
    }

    static String allXml(byte[] pptx) {
        StringBuilder xml = new StringBuilder();
        zipEntries(pptx).forEach((name, bytes) -> {
            if (name.endsWith(".xml") || name.endsWith(".rels")) {
                xml.append('\n').append(name).append('\n')
                        .append(new String(bytes, StandardCharsets.UTF_8));
            }
        });
        return xml.toString();
    }

    static List<List<String>> plannedVisibleText(ObjectNode plan) {
        List<List<String>> result = new ArrayList<>();
        for (JsonNode slide : plan.path("slides")) {
            List<String> slideText = new ArrayList<>();
            for (JsonNode element : slide.path("elements")) {
                switch (element.path("elementType").asText()) {
                    case "TEXT" -> slideText.add(element.path("text").asText());
                    case "TABLE" -> {
                        element.path("columns").forEach(column ->
                                slideText.add(column.path("header").asText()));
                        element.path("rows").forEach(row -> row.path("cells").forEach(cell ->
                                slideText.add(cell.asText())));
                    }
                    default -> {
                        // Non-text render elements intentionally contribute no visible text.
                    }
                }
            }
            result.add(List.copyOf(slideText));
        }
        return List.copyOf(result);
    }

    static List<List<String>> renderedVisibleText(XMLSlideShow show) {
        List<List<String>> result = new ArrayList<>();
        for (XSLFSlide slide : show.getSlides()) {
            List<String> slideText = new ArrayList<>();
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTable table) {
                    for (int row = 0; row < table.getNumberOfRows(); row++) {
                        for (int column = 0; column < table.getNumberOfColumns(); column++) {
                            XSLFTableCell cell = table.getCell(row, column);
                            slideText.add(cell == null ? "" : cell.getText());
                        }
                    }
                } else if (shape instanceof XSLFTextBox textShape) {
                    slideText.add(textShape.getText());
                }
            }
            result.add(List.copyOf(slideText));
        }
        return List.copyOf(result);
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder output = new StringBuilder("sha256:");
            for (byte value : digest) {
                output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static int countPlanElements(ObjectNode plan, String elementType) {
        int count = 0;
        for (JsonNode slide : plan.path("slides")) {
            for (JsonNode element : slide.path("elements")) {
                if (elementType.equals(element.path("elementType").asText())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static FrozenSlideRenderPlan frozenFromBytes(byte[] bytes) {
        try {
            Method factory = FrozenSlideRenderPlan.class
                    .getDeclaredMethod("fromCanonicalBytes", byte[].class);
            factory.setAccessible(true);
            return (FrozenSlideRenderPlan) factory.invoke(null, (Object) bytes);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access the frozen RenderPlan test boundary", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Cannot construct a frozen fixture", cause);
        }
    }

    private static int slideNumber(String entryName) {
        Matcher matcher = SLIDE_XML.matcher(entryName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a slide XML entry: " + entryName);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String mimeType(String bundlePath) {
        String lower = bundlePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("Unsupported fixture image type: " + bundlePath);
    }

    record RenderedFixture(
            FrozenSlideRenderPlan plan,
            ObjectNode document,
            byte[] pptx,
            RenderedPptx result
    ) {
        RenderedFixture {
            pptx = Arrays.copyOf(pptx, pptx.length);
        }

        @Override
        public byte[] pptx() {
            return Arrays.copyOf(pptx, pptx.length);
        }
    }

    private static final class FixtureHolder {
        private static final RenderedFixture RENDERED =
                render(frozenFixture(), classpathAssetResolver());

        private FixtureHolder() {
        }
    }
}
