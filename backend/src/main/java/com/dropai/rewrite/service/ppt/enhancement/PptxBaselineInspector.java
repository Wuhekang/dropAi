package com.dropai.rewrite.service.ppt.enhancement;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PptxBaselineInspector {
    private static final Pattern RGB = Pattern.compile("(?:val|lastClr)=\"([0-9A-Fa-f]{6})\"");
    private static final long MAX_PPTX_BYTES = 200L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long MAX_ENTRY_BYTES = 96L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 700L * 1024L * 1024L;

    public DeckInventory inspect(Path source, String templatePackId) throws Exception {
        if (!Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pptx")) {
            throw new IllegalArgumentException("增幅美化只接受已生成的PPTX");
        }
        long size = Files.size(source);
        if (size <= 0 || size > MAX_PPTX_BYTES) {
            throw new IllegalArgumentException("基础PPTX大小异常");
        }
        validatePackage(source);
        List<SlideInventory> slides = new ArrayList<>();
        long width;
        long height;
        try (InputStream input = Files.newInputStream(source); XMLSlideShow show = new XMLSlideShow(input)) {
            Dimension pageSize = show.getPageSize();
            width = Math.round(pageSize.getWidth() * 12_700d);
            height = Math.round(pageSize.getHeight() * 12_700d);
            int count = show.getSlides().size();
            Set<String> trustedTemplateBackgrounds = trustedTemplateBackgrounds(show, width, height);
            for (int index = 0; index < count; index++) {
                List<XSLFShape> shapes = show.getSlides().get(index).getShapes();
                int pictures = 0;
                int tables = 0;
                StringBuilder text = new StringBuilder();
                List<ShapeBox> shapeBoxes = new ArrayList<>();
                for (int zOrder = 0; zOrder < shapes.size(); zOrder++) {
                    XSLFShape shape = shapes.get(zOrder);
                    if (shape instanceof XSLFTable) tables++;
                    if (shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank()) {
                        if (!text.isEmpty()) text.append(" | ");
                        text.append(textShape.getText().replaceAll("\\s+", " ").trim());
                    }
                    ShapeBox box = shapeBox(shape, zOrder, width, height, trustedTemplateBackgrounds,
                        eligibleTemplateBackgroundLayer(shapes, zOrder, width, height));
                    if (box != null) {
                        shapeBoxes.add(box);
                        int nestedPictures = pictureCount(shape);
                        if (shape instanceof XSLFPictureShape) {
                            if (!box.trustedTemplateBackground()) pictures++;
                        } else {
                            pictures += nestedPictures;
                        }
                    }
                }
                String allText = text.toString();
                String title = firstTitle(allText);
                String suggested = inferArchetype(index + 1, count, title, allText, pictures, tables);
                slides.add(new SlideInventory(index + 1, title, suggested, shapes.size(), pictures, tables,
                    Math.min(allText.length(), 20_000), List.copyOf(shapeBoxes)));
            }
        }
        return new DeckInventory(sha256(source), size, slides.size(), width, height,
            templatePackId == null ? "" : templatePackId, palette(source), List.copyOf(slides));
    }

    private Set<String> trustedTemplateBackgrounds(XMLSlideShow show, long width, long height) {
        Map<String, Set<Integer>> fullBleedPagesByHash = new HashMap<>();
        for (int slideIndex = 0; slideIndex < show.getSlides().size(); slideIndex++) {
            List<XSLFShape> shapes = show.getSlides().get(slideIndex).getShapes();
            for (int zOrder = 0; zOrder < shapes.size(); zOrder++) {
                XSLFShape shape = shapes.get(zOrder);
                if (!(shape instanceof XSLFPictureShape picture)) continue;
                // A repeated full-bleed picture is trusted only when every object below it is itself
                // a plain full-slide background layer. Repeated foreground media stays protected.
                if (!eligibleTemplateBackgroundLayer(shapes, zOrder, width, height)) continue;
                Rectangle2D anchor;
                try {
                    anchor = shape.getAnchor();
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (!fullBleed(anchor, width, height)) continue;
                String hash = sha256(picture.getPictureData().getData());
                fullBleedPagesByHash.computeIfAbsent(hash, ignored -> new HashSet<>()).add(slideIndex + 1);
            }
        }
        int threshold = Math.max(3, (int) Math.ceil(show.getSlides().size() * .10d));
        return fullBleedPagesByHash.entrySet().stream()
            .filter(entry -> entry.getValue().size() >= threshold)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ShapeBox shapeBox(
        XSLFShape shape,
        int zOrder,
        long slideWidth,
        long slideHeight,
        Set<String> trustedTemplateBackgrounds,
        boolean eligibleTemplateBackgroundLayer
    ) {
        Rectangle2D anchor;
        try {
            anchor = shape.getAnchor();
        } catch (RuntimeException ignored) {
            return null;
        }
        if (anchor == null || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) return null;
        long x = Math.round(anchor.getX() * 12_700d);
        long y = Math.round(anchor.getY() * 12_700d);
        long width = Math.round(anchor.getWidth() * 12_700d);
        long height = Math.round(anchor.getHeight() * 12_700d);
        boolean fullBleed = fullBleed(anchor, slideWidth, slideHeight);
        boolean textBearing = shape instanceof XSLFTextShape textShape && !textShape.getText().isBlank();
        boolean containsNestedPicture = !(shape instanceof XSLFPictureShape) && pictureCount(shape) > 0;
        String type = shape instanceof XSLFPictureShape ? "PICTURE"
            : containsNestedPicture ? "PICTURE_GROUP"
            : shape instanceof XSLFTable ? "TABLE"
            : textBearing ? "TEXT" : shape.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        String pictureHash = shape instanceof XSLFPictureShape picture
            ? sha256(picture.getPictureData().getData()) : "";
        boolean trustedTemplateBackground = fullBleed && eligibleTemplateBackgroundLayer && !pictureHash.isBlank()
            && trustedTemplateBackgrounds.contains(pictureHash);
        boolean protectedContent = shape instanceof XSLFPictureShape
            ? !trustedTemplateBackground
            : containsNestedPicture || !fullBleed;
        return new ShapeBox(shape.getShapeId(), safe(shape.getShapeName()), type, x, y, width, height,
            zOrder, protectedContent, textBearing, fullBleed, pictureHash, trustedTemplateBackground);
    }

    private int pictureCount(XSLFShape shape) {
        if (shape instanceof XSLFPictureShape) return 1;
        if (shape instanceof XSLFGroupShape group) {
            return group.getShapes().stream().mapToInt(this::pictureCount).sum();
        }
        return 0;
    }

    private boolean eligibleTemplateBackgroundLayer(
        List<XSLFShape> shapes,
        int candidateZOrder,
        long slideWidth,
        long slideHeight
    ) {
        if (candidateZOrder < 0 || candidateZOrder >= shapes.size()) return false;
        XSLFShape candidate = shapes.get(candidateZOrder);
        if (!(candidate instanceof XSLFPictureShape)) return false;
        Rectangle2D candidateAnchor;
        try {
            candidateAnchor = candidate.getAnchor();
        } catch (RuntimeException ignored) {
            return false;
        }
        if (!fullBleed(candidateAnchor, slideWidth, slideHeight)) return false;
        for (int index = 0; index < candidateZOrder; index++) {
            XSLFShape below = shapes.get(index);
            Rectangle2D anchor;
            try {
                anchor = below.getAnchor();
            } catch (RuntimeException ignored) {
                return false;
            }
            boolean textBearing = below instanceof XSLFTextShape textShape && !textShape.getText().isBlank();
            if (!fullBleed(anchor, slideWidth, slideHeight) || textBearing
                || below instanceof XSLFTable || pictureCount(below) > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean fullBleed(Rectangle2D anchor, long slideWidth, long slideHeight) {
        if (anchor == null) return false;
        long x = Math.round(anchor.getX() * 12_700d);
        long y = Math.round(anchor.getY() * 12_700d);
        long width = Math.round(anchor.getWidth() * 12_700d);
        long height = Math.round(anchor.getHeight() * 12_700d);
        return x <= slideWidth * .03 && y <= slideHeight * .03
            && width >= slideWidth * .94 && height >= slideHeight * .94
            && x + width >= slideWidth * .97 && y + height >= slideHeight * .97;
    }

    private String safe(String value) {
        if (value == null) return "";
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.substring(0, Math.min(120, compact.length()));
    }

    private void validatePackage(Path source) throws Exception {
        int entries = 0;
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IllegalStateException("PPTX包条目过多");
                validateEntryName(entry.getName());
                long current = 0;
                for (int read; (read = zip.read(buffer)) >= 0; ) {
                    if (read == 0) continue;
                    current += read;
                    total += read;
                    if (current > MAX_ENTRY_BYTES) throw new IllegalStateException("PPTX单个包部件过大");
                    if (total > MAX_EXPANDED_BYTES) throw new IllegalStateException("PPTX解压后体积过大");
                }
            }
        }
    }

    private void validateEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
            || name.matches("^[A-Za-z]:.*") || name.contains("../") || name.contains("..\\")) {
            throw new IllegalStateException("PPTX包含非法包路径");
        }
    }

    private String firstTitle(String text) {
        if (text == null || text.isBlank()) return "";
        String first = text.split("\\s*\\|\\s*", 2)[0].trim();
        return first.length() > 80 ? first.substring(0, 80) : first;
    }

    private String inferArchetype(int page, int total, String title, String allText, int pictures, int tables) {
        // Media isolation has priority even on cover, agenda, and closing pages. Recurring template
        // backgrounds have already been excluded from pictureCount before this decision.
        if (pictures > 0) return "image";
        if (page == 1) return "cover";
        if (page == 2 || title.contains("目录")) return "catalog";
        if (page == total || title.contains("谢谢") || title.equalsIgnoreCase("THANKS")) return "closing";
        if (tables > 0) return "table";
        if (title.matches("^(0?[1-9]|[1-9][0-9])\\s+.+") && allText.length() < 120) return "section";
        if (title.contains("总结") || title.contains("展望") || title.contains("优化方向")) return "summary";
        return "content";
    }

    private List<String> palette(Path source) throws Exception {
        Map<String, Integer> counts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.isDirectory() || !entry.getName().endsWith(".xml")) continue;
                byte[] bytes = zip.readNBytes(2_000_000);
                String xml = new String(bytes, StandardCharsets.UTF_8);
                Matcher matcher = RGB.matcher(xml);
                while (matcher.find()) {
                    String color = matcher.group(1).toUpperCase(Locale.ROOT);
                    if (!isNeutral(color)) counts.merge(color, 1, Integer::sum);
                }
            }
        }
        List<String> colors = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey).limit(5).toList();
        return colors.isEmpty() ? List.of("7257FF", "E85BB5") : colors;
    }

    private boolean isNeutral(String color) {
        int r = Integer.parseInt(color.substring(0, 2), 16);
        int g = Integer.parseInt(color.substring(2, 4), 16);
        int b = Integer.parseInt(color.substring(4, 6), 16);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max < 45 || min > 238 || max - min < 12;
    }

    public static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("图片指纹计算失败", exception);
        }
    }

    public record SlideInventory(
        int slideNumber,
        String title,
        String suggestedArchetype,
        int shapeCount,
        int pictureCount,
        int tableCount,
        int textLength,
        List<ShapeBox> shapeBoxes
    ) {}

    public record ShapeBox(
        int shapeId,
        String shapeName,
        String type,
        long x,
        long y,
        long width,
        long height,
        int zOrder,
        boolean protectedContent,
        boolean textBearing,
        boolean fullBleed,
        String pictureSha256,
        boolean trustedTemplateBackground
    ) {
        public boolean intersects(long otherX, long otherY, long otherWidth, long otherHeight, long padding) {
            return x - padding < otherX + otherWidth && x + width + padding > otherX
                && y - padding < otherY + otherHeight && y + height + padding > otherY;
        }
    }

    public record DeckInventory(
        String sourcePptxSha256,
        long fileSize,
        int slideCount,
        long widthEmu,
        long heightEmu,
        String templatePackId,
        List<String> palette,
        List<SlideInventory> slides
    ) {
        public Map<String, Object> toPromptMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourcePptxSha256", sourcePptxSha256);
            result.put("slideCount", slideCount);
            result.put("pageSizeEmu", Map.of("width", widthEmu, "height", heightEmu));
            result.put("templatePackId", templatePackId);
            result.put("derivedPalette", palette);
            result.put("slides", slides);
            return result;
        }
    }
}
