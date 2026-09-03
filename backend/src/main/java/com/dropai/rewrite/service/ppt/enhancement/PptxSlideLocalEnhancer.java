package com.dropai.rewrite.service.ppt.enhancement;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class PptxSlideLocalEnhancer {
    public static final String VERSION = "slide-local-ooxml-enhancer/1.1.0";
    public static final String MARKER_BEGIN = "<!--DOKIAI_ENHANCE_BEGIN:";
    public static final String MARKER_END = "<!--DOKIAI_ENHANCE_END-->";

    private static final Pattern SLIDE = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");
    private static final Pattern SHAPE_ID = Pattern.compile("<p:cNvPr[^>]*\\bid=\"(\\d+)\"");
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_ENTRY_BYTES = 96L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 700L * 1024L * 1024L;

    public EnhancementResult enhance(
        Path source,
        Path output,
        PptEnhancementPlan plan,
        PptxBaselineInspector.DeckInventory inventory
    ) throws Exception {
        Map<Integer, PptEnhancementPlan.SlidePlan> slides = new HashMap<>();
        plan.slides().forEach(slide -> slides.put(slide.slideNumber(), slide));
        Files.createDirectories(output.getParent());
        int entryCount = 0;
        long expanded = 0;
        int patchedSlides = 0;
        int addedShapes = 0;
        Map<Integer, Integer> addedShapesBySlide = new LinkedHashMap<>();
        Set<String> entryNames = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
                if (++entryCount > MAX_ENTRIES) throw new IllegalStateException("PPTX包条目过多");
                validateEntryName(entry.getName());
                if (!entryNames.add(entry.getName())) throw new IllegalStateException("PPTX存在重复包条目");
                byte[] bytes = readBounded(input, Math.min(MAX_ENTRY_BYTES, MAX_EXPANDED_BYTES - expanded));
                expanded += bytes.length;
                if (expanded > MAX_EXPANDED_BYTES) throw new IllegalStateException("PPTX解压后体积过大");
                Matcher slideMatcher = SLIDE.matcher(entry.getName());
                if (slideMatcher.matches()) {
                    int slideNumber = Integer.parseInt(slideMatcher.group(1));
                    PptEnhancementPlan.SlidePlan slidePlan = slides.get(slideNumber);
                    if (slidePlan == null) throw new IllegalStateException("美化计划缺少第" + slideNumber + "页");
                    PatchResult patch = patchSlide(bytes, slidePlan, inventory, plan.profile());
                    bytes = patch.bytes();
                    addedShapes += patch.addedShapes();
                    addedShapesBySlide.put(slideNumber, patch.addedShapes());
                    patchedSlides++;
                }
                ZipEntry copy = new ZipEntry(entry);
                zip.putNextEntry(copy);
                zip.write(bytes);
                zip.closeEntry();
            }
        } catch (Exception exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
        if (patchedSlides != inventory.slideCount()) {
            Files.deleteIfExists(output);
            throw new IllegalStateException("实际美化页数与基础PPT不一致");
        }
        return new EnhancementResult(output, patchedSlides, addedShapes, Map.copyOf(addedShapesBySlide), true, VERSION);
    }

    private PatchResult patchSlide(
        byte[] source,
        PptEnhancementPlan.SlidePlan plan,
        PptxBaselineInspector.DeckInventory inventory,
        String profile
    ) {
        String xml = new String(source, StandardCharsets.UTF_8);
        if (xml.contains(MARKER_BEGIN)) throw new IllegalStateException("基础PPT已经包含增幅美化标记");
        int insertion = xml.lastIndexOf("</p:spTree>");
        if (insertion < 0) throw new IllegalStateException("第" + plan.slideNumber() + "页缺少标准spTree");
        int nextId = maxShapeId(xml) + 1;
        String primary = inventory.palette().get(0);
        String secondary = inventory.palette().size() > 1 ? inventory.palette().get(1) : primary;
        PptxBaselineInspector.SlideInventory sourceSlide = inventory.slides().stream()
            .filter(slide -> slide.slideNumber() == plan.slideNumber()).findFirst()
            .orElseThrow(() -> new IllegalStateException("缺少第" + plan.slideNumber() + "页视觉基线"));
        List<Geometry> geometry = selectSafeGeometry(
            recipe(plan.recipeId(), inventory.widthEmu(), inventory.heightEmu(), profile),
            fallbackGeometry(inventory.widthEmu(), inventory.heightEmu(), profile), sourceSlide,
            inventory.widthEmu(), inventory.heightEmu());
        StringBuilder additions = new StringBuilder();
        additions.append(MARKER_BEGIN).append(plan.slideNumber()).append("-->");
        for (int index = 0; index < geometry.size(); index++) {
            Geometry item = geometry.get(index);
            String color = index % 2 == 0 ? primary : secondary;
            additions.append(shape(nextId++, plan.slideNumber(), index + 1, item, color));
        }
        additions.append(MARKER_END);
        String patched = xml.substring(0, insertion) + additions + xml.substring(insertion);
        return new PatchResult(patched.getBytes(StandardCharsets.UTF_8), geometry.size());
    }

    private List<Geometry> selectSafeGeometry(
        List<Geometry> preferred,
        List<Geometry> fallback,
        PptxBaselineInspector.SlideInventory slide,
        long width,
        long height
    ) {
        List<Geometry> selected = new ArrayList<>();
        for (Geometry candidate : preferred) if (safe(candidate, slide, selected, width, height)) selected.add(candidate);
        if (selected.isEmpty()) {
            for (Geometry candidate : fallback) {
                if (safe(candidate, slide, selected, width, height)) {
                    selected.add(candidate);
                    if (selected.size() == 2) break;
                }
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("第" + slide.slideNumber() + "页没有不遮挡原内容的安全增幅区域");
        }
        return List.copyOf(selected);
    }

    private boolean safe(Geometry candidate, PptxBaselineInspector.SlideInventory slide,
                         List<Geometry> selected, long width, long height) {
        if (candidate.x() < 0 || candidate.y() < 0 || candidate.cx() <= 0 || candidate.cy() <= 0
            || candidate.x() + candidate.cx() > width || candidate.y() + candidate.cy() > height) return false;
        long padding = 18_000L;
        for (PptxBaselineInspector.ShapeBox box : slide.shapeBoxes()) {
            if (box.protectedContent() && box.intersects(candidate.x(), candidate.y(), candidate.cx(), candidate.cy(), padding)) {
                return false;
            }
        }
        for (Geometry other : selected) if (other.intersects(candidate, 4_000L)) return false;
        return true;
    }

    private List<Geometry> fallbackGeometry(long width, long height, String profile) {
        long inch = 914_400L;
        long edge = Math.round(.10 * inch);
        long thin = "showcase".equals(profile) ? 42_000L : "subtle".equals(profile) ? 22_000L : 30_000L;
        long dot = "showcase".equals(profile) ? 96_000L : "subtle".equals(profile) ? 58_000L : 74_000L;
        long rail = Math.round(.58 * inch);
        return List.of(
            new Geometry(edge, edge, rail, thin, "roundRect", 68_000),
            new Geometry(width - edge - rail, edge, rail, thin, "roundRect", 68_000),
            new Geometry(edge, height - edge - thin, rail, thin, "roundRect", 68_000),
            new Geometry(width - edge - rail, height - edge - thin, rail, thin, "roundRect", 68_000),
            new Geometry(edge, Math.max(edge, height / 2 - dot / 2), dot, dot, "ellipse", 62_000),
            new Geometry(width - edge - dot, Math.max(edge, height / 2 - dot / 2), dot, dot, "ellipse", 62_000));
    }

    private List<Geometry> recipe(String recipe, long width, long height, String profile) {
        long inch = 914_400L;
        long thin = "showcase".equals(profile) ? 50_000L : "subtle".equals(profile) ? 24_000L : 34_000L;
        long dot = "showcase".equals(profile) ? 115_000L : "subtle".equals(profile) ? 65_000L : 85_000L;
        long left = Math.round(.38 * inch);
        long top = Math.round(.34 * inch);
        long right = width - left;
        long bottom = height - top;
        return switch (recipe) {
            case "COVER_ACCENT" -> List.of(
                new Geometry(left, Math.round(.95 * inch), thin, Math.round(4.9 * inch), "roundRect", 62_000),
                new Geometry(width - Math.round(.72 * inch), top, dot, dot, "ellipse", 78_000),
                new Geometry(width - Math.round(.50 * inch), top + dot / 2, dot / 2, dot / 2, "ellipse", 50_000));
            case "AGENDA_RAIL" -> List.of(
                new Geometry(Math.round(.72 * inch), height - Math.round(.50 * inch), width - Math.round(1.44 * inch), thin, "roundRect", 54_000),
                new Geometry(Math.round(.72 * inch), height - Math.round(.55 * inch), dot, dot, "ellipse", 82_000),
                new Geometry(width / 2, height - Math.round(.55 * inch), dot, dot, "ellipse", 58_000),
                new Geometry(width - Math.round(.80 * inch), height - Math.round(.55 * inch), dot, dot, "ellipse", 82_000));
            case "SECTION_MOTIF" -> List.of(
                new Geometry(Math.round(.80 * inch), height / 2, Math.round(2.05 * inch), thin, "roundRect", 58_000),
                new Geometry(width - Math.round(2.85 * inch), height / 2, Math.round(2.05 * inch), thin, "roundRect", 58_000),
                new Geometry(Math.round(.62 * inch), height / 2 - dot / 3, dot, dot, "ellipse", 76_000),
                new Geometry(width - Math.round(.72 * inch), height / 2 - dot / 3, dot, dot, "ellipse", 76_000));
            case "IMAGE_FRAME" -> List.of(
                new Geometry(left, top, Math.round(.48 * inch), thin, "rect", 70_000),
                new Geometry(left, top, thin, Math.round(.48 * inch), "rect", 70_000),
                new Geometry(right - Math.round(.48 * inch), top, Math.round(.48 * inch), thin, "rect", 70_000),
                new Geometry(right - thin, top, thin, Math.round(.48 * inch), "rect", 70_000),
                new Geometry(left, bottom - thin, Math.round(.48 * inch), thin, "rect", 70_000),
                new Geometry(left, bottom - Math.round(.48 * inch), thin, Math.round(.48 * inch), "rect", 70_000),
                new Geometry(right - Math.round(.48 * inch), bottom - thin, Math.round(.48 * inch), thin, "rect", 70_000),
                new Geometry(right - thin, bottom - Math.round(.48 * inch), thin, Math.round(.48 * inch), "rect", 70_000));
            case "TABLE_RAIL" -> List.of(
                new Geometry(left, Math.round(1.15 * inch), thin, height - Math.round(1.70 * inch), "roundRect", 55_000),
                new Geometry(left - dot / 3, Math.round(1.15 * inch), dot, dot, "ellipse", 78_000),
                new Geometry(left - dot / 3, height / 2, dot, dot, "ellipse", 58_000),
                new Geometry(left - dot / 3, height - Math.round(.63 * inch), dot, dot, "ellipse", 78_000));
            case "SUMMARY_RAIL" -> List.of(
                new Geometry(left, top, Math.round(1.65 * inch), thin, "roundRect", 68_000),
                new Geometry(left + Math.round(1.78 * inch), top - dot / 3, dot, dot, "ellipse", 62_000));
            case "CLOSING_ECHO" -> List.of(
                new Geometry(width - left - thin, Math.round(.95 * inch), thin, Math.round(4.9 * inch), "roundRect", 62_000),
                new Geometry(left, bottom - dot, dot, dot, "ellipse", 78_000),
                new Geometry(left + dot + Math.round(.08 * inch), bottom - dot / 2, dot / 2, dot / 2, "ellipse", 50_000));
            default -> List.of(
                new Geometry(left, top, Math.round(1.25 * inch), thin, "roundRect", 62_000),
                new Geometry(width - Math.round(.55 * inch), height - Math.round(.48 * inch), dot, dot, "ellipse", 58_000));
        };
    }

    private String shape(int id, int slide, int ordinal, Geometry g, String color) {
        return """
            <p:sp><p:nvSpPr><p:cNvPr id="%d" name="DOKIAI_ENHANCE_%d_%d"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="%d" y="%d"/><a:ext cx="%d" cy="%d"/></a:xfrm><a:prstGeom prst="%s"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val="%s"><a:alpha val="%d"/></a:srgbClr></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>
            """.formatted(id, slide, ordinal, g.x(), g.y(), g.cx(), g.cy(), g.preset(), color, g.alpha());
    }

    private int maxShapeId(String xml) {
        int max = 1;
        Matcher matcher = SHAPE_ID.matcher(xml);
        while (matcher.find()) max = Math.max(max, Integer.parseInt(matcher.group(1)));
        return max;
    }

    private byte[] readBounded(ZipInputStream input, long remaining) throws Exception {
        if (remaining <= 0) throw new IllegalStateException("PPTX解压后体积过大");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) >= 0; ) {
            if (read == 0) continue;
            if (output.size() + (long) read > remaining) throw new IllegalStateException("PPTX解压后体积过大");
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

    private record Geometry(long x, long y, long cx, long cy, String preset, int alpha) {
        boolean intersects(Geometry other, long padding) {
            return x - padding < other.x + other.cx && x + cx + padding > other.x
                && y - padding < other.y + other.cy && y + cy + padding > other.y;
        }
    }
    private record PatchResult(byte[] bytes, int addedShapes) {}
    public record EnhancementResult(
        Path output,
        int patchedSlides,
        int addedShapes,
        Map<Integer, Integer> addedShapesBySlide,
        boolean safeGeometryValidated,
        String executorVersion
    ) {}
}
