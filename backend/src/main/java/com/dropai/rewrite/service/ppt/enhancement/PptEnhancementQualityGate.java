package com.dropai.rewrite.service.ppt.enhancement;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PptEnhancementQualityGate {
    public static final String RENDERER_NAME = "Apache POI Java2D";
    public static final String RENDERER_VERSION = "poi-5.3.0/960x540";
    private static final Pattern SLIDE_XML = Pattern.compile("ppt/slides/slide\\d+\\.xml");
    private static final Pattern ENHANCEMENT_BLOCK = Pattern.compile(
        "(?s)<!--DOKIAI_ENHANCE_BEGIN:\\d+-->.*?<!--DOKIAI_ENHANCE_END-->");
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long MAX_ENTRY_BYTES = 96L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 700L * 1024L * 1024L;

    public QualityResult validate(Path source, Path enhanced, Path qaDirectory,
                                  PptxBaselineInspector.DeckInventory inventory) throws Exception {
        int expectedSlides = inventory.slideCount();
        Files.createDirectories(qaDirectory);
        Map<String, byte[]> before = entries(source);
        Map<String, byte[]> after = entries(enhanced);
        if (!before.keySet().equals(after.keySet())) throw new IllegalStateException("增强PPTX包条目发生增删");

        int patchedSlides = 0;
        for (String name : new TreeSet<>(before.keySet())) {
            byte[] sourceBytes = before.get(name);
            byte[] outputBytes = after.get(name);
            if (SLIDE_XML.matcher(name).matches()) {
                String outputXml = new String(outputBytes, StandardCharsets.UTF_8);
                String restored = ENHANCEMENT_BLOCK.matcher(outputXml).replaceAll("");
                if (!Arrays.equals(sourceBytes, restored.getBytes(StandardCharsets.UTF_8))) {
                    throw new IllegalStateException("第" + slideNumber(name) + "页存在计划外XML改动");
                }
                if (!outputXml.contains(PptxSlideLocalEnhancer.MARKER_BEGIN + slideNumber(name) + "-->")) {
                    throw new IllegalStateException("第" + slideNumber(name) + "页缺少增幅标记");
                }
                patchedSlides++;
            } else if (!Arrays.equals(sourceBytes, outputBytes)) {
                throw new IllegalStateException("增强阶段修改了受保护包部件：" + name);
            }
        }
        if (patchedSlides != expectedSlides) throw new IllegalStateException("增强页数与计划不一致");

        Path sourceRenderDir = qaDirectory.resolve("baseline-renders");
        Path enhancedRenderDir = qaDirectory.resolve("enhanced-renders");
        Files.createDirectories(sourceRenderDir);
        Files.createDirectories(enhancedRenderDir);
        List<PageRender> renders = new ArrayList<>();
        ValidationCounters counters = compareAndRender(source, enhanced, sourceRenderDir, enhancedRenderDir,
            renders, inventory);
        int changed = counters.changedSlides();
        if (changed != expectedSlides) throw new IllegalStateException("存在未产生可见增幅的页面：" + (expectedSlides - changed) + "页");
        if (counters.outOfBounds() != 0) throw new IllegalStateException("存在越界增幅元素：" + counters.outOfBounds() + "个");
        if (counters.overlaps() != 0) throw new IllegalStateException("存在遮挡原页面对象的增幅元素：" + counters.overlaps() + "个");
        if (counters.lowContrastSlides() != 0) throw new IllegalStateException("存在视觉增幅对比度不足的页面：" + counters.lowContrastSlides() + "页");

        List<String> checks = List.of(
            "PPTX_REIMPORT_OK",
            "SLIDE_COUNT_AND_ORDER_MATCH",
            "ORIGINAL_TEXT_EXACT_MATCH",
            "NOTES_AND_HYPERLINK_PARTS_BYTE_IDENTICAL",
            "THEME_MASTER_LAYOUT_BYTE_IDENTICAL",
            "PAGE_SIZE_BYTE_IDENTICAL",
            "OPAQUE_PACKAGE_PARTS_BYTE_IDENTICAL",
            "EVERY_SLIDE_RENDERED",
            "EVERY_SLIDE_VISUALLY_ENHANCED",
            "ADDED_GEOMETRY_IN_BOUNDS",
            "NO_ADDED_GEOMETRY_OVERLAPS_INHERITED_OBJECTS",
            "ADDED_GEOMETRY_CONTRAST_VISIBLE",
            "NO_TEXT_BEARING_OBJECTS_ADDED"
        );
        return new QualityResult("PASSED", expectedSlides, patchedSlides, changed, List.copyOf(renders), checks,
            List.of(), List.of());
    }

    private ValidationCounters compareAndRender(
        Path source,
        Path enhanced,
        Path sourceDir,
        Path enhancedDir,
        List<PageRender> renders,
        PptxBaselineInspector.DeckInventory inventory
    ) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try (InputStream sourceInput = Files.newInputStream(source);
             InputStream enhancedInput = Files.newInputStream(enhanced);
             XMLSlideShow before = new XMLSlideShow(sourceInput);
             XMLSlideShow after = new XMLSlideShow(enhancedInput)) {
            int expectedSlides = inventory.slideCount();
            if (before.getSlides().size() != expectedSlides || after.getSlides().size() != expectedSlides) {
                throw new IllegalStateException("增强前后页数不一致");
            }
            if (!before.getPageSize().equals(after.getPageSize())) throw new IllegalStateException("增强阶段修改了页面尺寸");
            int changed = 0;
            int outOfBounds = 0;
            int overlaps = 0;
            int lowContrastSlides = 0;
            for (int index = 0; index < expectedSlides; index++) {
                List<String> beforeText = textInventory(before.getSlides().get(index));
                List<String> afterText = textInventory(after.getSlides().get(index));
                if (!beforeText.equals(afterText)) throw new IllegalStateException("第" + (index + 1) + "页文字发生变化");
                List<XSLFShape> added = addedShapes(after.getSlides().get(index));
                if (added.isEmpty()) throw new IllegalStateException("第" + (index + 1) + "页没有计划内增幅元素");
                PptxBaselineInspector.SlideInventory baseline = inventory.slides().get(index);
                GeometryCheck geometry = geometryCheck(added, baseline, after.getPageSize());
                outOfBounds += geometry.outOfBounds();
                overlaps += geometry.overlaps();
                Path beforePng = sourceDir.resolve(String.format("slide-%03d.png", index + 1));
                Path afterPng = enhancedDir.resolve(String.format("slide-%03d.png", index + 1));
                BufferedImage beforeImage = render(before.getSlides().get(index), before.getPageSize(), beforePng);
                BufferedImage afterImage = render(after.getSlides().get(index), after.getPageSize(), afterPng);
                String beforeHash = PptxBaselineInspector.sha256(beforePng);
                String afterHash = PptxBaselineInspector.sha256(afterPng);
                boolean visuallyChanged = !beforeHash.equals(afterHash);
                VisualDelta delta = visualDelta(beforeImage, afterImage, added, before.getPageSize());
                boolean contrastVisible = delta.changedPixels() >= Math.max(12, Math.round(delta.geometryPixels() * .012))
                    && delta.meanChangedChannelDelta() >= 8d;
                if (!contrastVisible) lowContrastSlides++;
                if (visuallyChanged) changed++;
                renders.add(new PageRender(index + 1,
                    "qa/baseline-renders/" + beforePng.getFileName(), beforeHash,
                    "qa/enhanced-renders/" + afterPng.getFileName(), afterHash, visuallyChanged,
                    added.size(), geometry.outOfBounds() == 0, geometry.overlaps() == 0,
                    delta.changedPixels(), round(delta.meanChangedChannelDelta()), contrastVisible,
                    visuallyChanged && geometry.outOfBounds() == 0 && geometry.overlaps() == 0 && contrastVisible ? "PASSED" : "FAILED"));
            }
            return new ValidationCounters(changed, outOfBounds, overlaps, lowContrastSlides);
        }
    }

    private List<XSLFShape> addedShapes(XSLFSlide slide) {
        return slide.getShapes().stream()
            .filter(shape -> shape.getShapeName() != null && shape.getShapeName().startsWith("DOKIAI_ENHANCE_"))
            .toList();
    }

    private GeometryCheck geometryCheck(List<XSLFShape> added,
                                        PptxBaselineInspector.SlideInventory baseline,
                                        Dimension pageSize) {
        int outOfBounds = 0;
        int overlaps = 0;
        long width = Math.round(pageSize.getWidth() * 12_700d);
        long height = Math.round(pageSize.getHeight() * 12_700d);
        for (XSLFShape shape : added) {
            if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                throw new IllegalStateException("增幅阶段新增了文字对象");
            }
            Rectangle2D anchor = shape.getAnchor();
            long x = Math.round(anchor.getX() * 12_700d);
            long y = Math.round(anchor.getY() * 12_700d);
            long cx = Math.round(anchor.getWidth() * 12_700d);
            long cy = Math.round(anchor.getHeight() * 12_700d);
            if (x < 0 || y < 0 || cx <= 0 || cy <= 0 || x + cx > width || y + cy > height) outOfBounds++;
            for (PptxBaselineInspector.ShapeBox inherited : baseline.shapeBoxes()) {
                if (inherited.protectedContent() && inherited.intersects(x, y, cx, cy, 18_000L)) {
                    overlaps++;
                    break;
                }
            }
        }
        return new GeometryCheck(outOfBounds, overlaps);
    }

    private VisualDelta visualDelta(BufferedImage before, BufferedImage after, List<XSLFShape> added,
                                    Dimension pageSize) {
        boolean[][] mask = new boolean[before.getHeight()][before.getWidth()];
        long geometryPixels = 0;
        for (XSLFShape shape : added) {
            Rectangle2D anchor = shape.getAnchor();
            int x1 = clamp((int) Math.floor(anchor.getX() / pageSize.getWidth() * before.getWidth()), 0, before.getWidth() - 1);
            int y1 = clamp((int) Math.floor(anchor.getY() / pageSize.getHeight() * before.getHeight()), 0, before.getHeight() - 1);
            int x2 = clamp((int) Math.ceil((anchor.getX() + anchor.getWidth()) / pageSize.getWidth() * before.getWidth()), x1 + 1, before.getWidth());
            int y2 = clamp((int) Math.ceil((anchor.getY() + anchor.getHeight()) / pageSize.getHeight() * before.getHeight()), y1 + 1, before.getHeight());
            for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++) {
                if (!mask[y][x]) {
                    mask[y][x] = true;
                    geometryPixels++;
                }
            }
        }
        long changed = 0;
        long channelDelta = 0;
        for (int y = 0; y < before.getHeight(); y++) for (int x = 0; x < before.getWidth(); x++) {
            if (!mask[y][x]) continue;
            int a = before.getRGB(x, y);
            int b = after.getRGB(x, y);
            int delta = Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
            if (delta >= 12) {
                changed++;
                channelDelta += delta;
            }
        }
        return new VisualDelta(geometryPixels, changed, changed == 0 ? 0d : channelDelta / (double) changed / 3d);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private List<String> textInventory(XSLFSlide slide) {
        List<String> result = new ArrayList<>();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape.getShapeName() != null && shape.getShapeName().startsWith("DOKIAI_ENHANCE_")) continue;
            if (shape instanceof XSLFTextShape textShape) {
                result.add(shape.getShapeId() + "\u0000" + shape.getShapeName() + "\u0000" + textShape.getText());
            }
        }
        return result;
    }

    private BufferedImage render(XSLFSlide slide, Dimension pageSize, Path output) throws Exception {
        int width = 960;
        int height = Math.max(1, (int) Math.round(width * pageSize.getHeight() / pageSize.getWidth()));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            AffineTransform transform = graphics.getTransform();
            transform.scale(width / pageSize.getWidth(), height / pageSize.getHeight());
            graphics.setTransform(transform);
            slide.draw(graphics);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", output.toFile())) throw new IllegalStateException("PNG渲染器不可用");
        return image;
    }

    private Map<String, byte[]> entries(Path file) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        long expanded = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++entryCount > MAX_ZIP_ENTRIES) throw new IllegalStateException("PPTX包条目过多");
                validateEntryName(entry.getName());
                if (entry.isDirectory()) {
                    result.put(entry.getName(), new byte[0]);
                    continue;
                }
                byte[] bytes = readEntry(zip);
                expanded += bytes.length;
                if (expanded > MAX_EXPANDED_BYTES) throw new IllegalStateException("PPTX解压后体积过大");
                if (result.put(entry.getName(), bytes) != null) throw new IllegalStateException("PPTX存在重复包条目");
            }
        }
        return result;
    }

    private byte[] readEntry(ZipInputStream zip) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = zip.read(buffer)) >= 0; ) {
            if (read == 0) continue;
            if (output.size() + (long) read > MAX_ENTRY_BYTES) throw new IllegalStateException("PPTX单个包部件过大");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void validateEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
            || name.matches("^[A-Za-z]:.*") || name.contains("../") || name.contains("..\\")) {
            throw new IllegalStateException("PPTX包含非法包路径");
        }
    }

    private int slideNumber(String entry) {
        String digits = entry.replaceAll("\\D+", "");
        return Integer.parseInt(digits);
    }

    public record PageRender(
        int page,
        String baselinePath,
        String baselineSha256,
        String enhancedPath,
        String enhancedSha256,
        boolean visuallyChanged,
        int addedShapeCount,
        boolean geometryInBounds,
        boolean overlapFree,
        long visibleChangedPixels,
        double meanChangedChannelDelta,
        boolean contrastVisible,
        String status
    ) {}

    private record GeometryCheck(int outOfBounds, int overlaps) {}
    private record VisualDelta(long geometryPixels, long changedPixels, double meanChangedChannelDelta) {}
    private record ValidationCounters(int changedSlides, int outOfBounds, int overlaps, int lowContrastSlides) {}

    public record QualityResult(
        String status,
        int renderedPages,
        int patchedSlides,
        int visiblyChangedSlides,
        List<PageRender> pageRenders,
        List<String> checks,
        List<String> issues,
        List<String> warnings
    ) {}
}
