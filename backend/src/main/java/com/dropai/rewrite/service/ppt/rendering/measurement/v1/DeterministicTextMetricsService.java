package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DeterministicTextMetricsService {
    public static final int FONT_STEP_HUNDREDTH_PT = 50;

    private static final Set<Integer> FORBIDDEN_LINE_START = codePoints(
            "，。！？；：、）》】〕〉”’…％‰℃°,.!?;:%)]}»”’");
    private static final Set<Integer> FORBIDDEN_LINE_END = codePoints(
            "（《【〔〈“‘([{«“‘");

    private final GlyphMetricsModel metricsModel;

    public DeterministicTextMetricsService(GlyphMetricsModel metricsModel) {
        this.metricsModel = Objects.requireNonNull(metricsModel, "metricsModel");
    }

    public TextFitResult fit(TextFitRequest request) {
        Objects.requireNonNull(request, "request");
        ResolvedFontFace face = request.fontProfile().requireFace(request.fontRole(), request.fontWeight());
        String normalized = normalizeLineEndings(request.text());

        int size = request.defaultFontSizeHundredthPt();
        while (true) {
            WrapResult wrapped = wrap(normalized, face, size, request.maxWidthEmu());
            if (wrapped.fits() && wrapped.lines().size() <= request.maxLines()) {
                long naturalLineHeight = metricsModel.naturalLineHeightEmu(face, size);
                long configuredAdvance = Emu.scale(
                        Emu.fromHundredthPoints(size),
                        request.lineSpacingPermille(),
                        1_000);
                long lineAdvance = Math.max(naturalLineHeight, configuredAdvance);
                long requiredHeight = Math.addExact(
                        naturalLineHeight,
                        Math.multiplyExact(Math.max(0, wrapped.lines().size() - 1), lineAdvance));
                if (requiredHeight <= request.maxHeightEmu()) {
                    return new TextFitResult(
                            size == request.defaultFontSizeHundredthPt()
                                    ? TextFitStatus.FIT
                                    : TextFitStatus.FIT_WITH_FONT_SCALE,
                            normalized,
                            String.join("\n", wrapped.lines()),
                            wrapped.lines(),
                            face.selectedFamily(),
                            face.fontFingerprint(),
                            size,
                            request.lineSpacingPermille(),
                            lineAdvance,
                            wrapped.maximumWidthEmu(),
                            requiredHeight,
                            "");
                }
            }
            if (size == request.minimumFontSizeHundredthPt()) {
                break;
            }
            size = Math.max(request.minimumFontSizeHundredthPt(), size - FONT_STEP_HUNDREDTH_PT);
        }

        return new TextFitResult(
                TextFitStatus.UNFIT,
                normalized,
                "",
                List.of(),
                face.selectedFamily(),
                face.fontFingerprint(),
                request.minimumFontSizeHundredthPt(),
                request.lineSpacingPermille(),
                0L,
                0L,
                0L,
                "Text cannot fit without truncation or dropping below the minimum font size");
    }

    public long widthEmu(
            ResolvedFontProfile profile,
            String role,
            int weight,
            int fontSizeHundredthPt,
            String text
    ) {
        return metricsModel.textWidthEmu(
                profile.requireFace(role, weight),
                fontSizeHundredthPt,
                Objects.requireNonNull(text, "text"));
    }

    public long naturalLineHeightEmu(
            ResolvedFontProfile profile,
            String role,
            int weight,
            int fontSizeHundredthPt
    ) {
        return metricsModel.naturalLineHeightEmu(
                profile.requireFace(role, weight),
                fontSizeHundredthPt);
    }

    private WrapResult wrap(
            String text,
            ResolvedFontFace face,
            int fontSizeHundredthPt,
            long maxWidthEmu
    ) {
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\\n", -1);
        long maximumWidth = 0L;
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            WrapResult paragraphResult = wrapParagraph(paragraph, face, fontSizeHundredthPt, maxWidthEmu);
            if (!paragraphResult.fits()) {
                return paragraphResult;
            }
            lines.addAll(paragraphResult.lines());
            maximumWidth = Math.max(maximumWidth, paragraphResult.maximumWidthEmu());
        }
        return new WrapResult(true, List.copyOf(lines), maximumWidth);
    }

    private WrapResult wrapParagraph(
            String paragraph,
            ResolvedFontFace face,
            int fontSizeHundredthPt,
            long maxWidthEmu
    ) {
        List<String> clusters = graphemeLikeClusters(paragraph);
        List<String> lines = new ArrayList<>();
        long maximumWidth = 0L;
        int start = 0;
        while (start < clusters.size()) {
            int bestLegalEnd = -1;
            long bestLegalWidth = 0L;
            for (int end = start + 1; end <= clusters.size(); end++) {
                String candidate = concatenate(clusters, start, end);
                long width = metricsModel.textWidthEmu(face, fontSizeHundredthPt, candidate);
                if (width > maxWidthEmu) {
                    break;
                }
                if (end == clusters.size() || legalBreak(clusters, end)) {
                    bestLegalEnd = end;
                    bestLegalWidth = width;
                }
            }
            if (bestLegalEnd <= start) {
                return new WrapResult(false, List.of(), 0L);
            }
            lines.add(concatenate(clusters, start, bestLegalEnd));
            maximumWidth = Math.max(maximumWidth, bestLegalWidth);
            start = bestLegalEnd;
        }
        return new WrapResult(true, List.copyOf(lines), maximumWidth);
    }

    private boolean legalBreak(List<String> clusters, int boundary) {
        if (boundary <= 0 || boundary >= clusters.size()) {
            return true;
        }
        String left = clusters.get(boundary - 1);
        String right = clusters.get(boundary);
        int leftCodePoint = left.codePointAt(0);
        int rightCodePoint = right.codePointAt(0);
        if (FORBIDDEN_LINE_END.contains(leftCodePoint) || FORBIDDEN_LINE_START.contains(rightCodePoint)) {
            return false;
        }
        if (Character.isWhitespace(rightCodePoint)) {
            return false;
        }
        if (Character.isWhitespace(leftCodePoint)) {
            if (boundary >= 2 && isListMarker(clusters.get(boundary - 2))) {
                return false;
            }
            return true;
        }
        return !(isWordCodePoint(leftCodePoint) && isWordCodePoint(rightCodePoint));
    }

    private static boolean isWordCodePoint(int codePoint) {
        if (isCjk(codePoint)) {
            return false;
        }
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '-'
                || codePoint == '/'
                || codePoint == '+'
                || codePoint == '#'
                || codePoint == '@'
                || codePoint == ':';
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static boolean isListMarker(String cluster) {
        return "•".equals(cluster) || "·".equals(cluster) || "▪".equals(cluster);
    }

    private static List<String> graphemeLikeClusters(String text) {
        List<String> clusters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean joinNext = false;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            boolean combining = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK
                    || isVariationSelector(codePoint);
            if (current.isEmpty() || combining || joinNext || codePoint == 0x200D) {
                current.appendCodePoint(codePoint);
            } else {
                clusters.add(current.toString());
                current.setLength(0);
                current.appendCodePoint(codePoint);
            }
            joinNext = codePoint == 0x200D;
        }
        if (!current.isEmpty()) {
            clusters.add(current.toString());
        }
        return List.copyOf(clusters);
    }

    private static boolean isVariationSelector(int codePoint) {
        return (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }

    private static String concatenate(List<String> values, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int index = start; index < end; index++) {
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Set<Integer> codePoints(String value) {
        return value.codePoints().boxed().collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private record WrapResult(boolean fits, List<String> lines, long maximumWidthEmu) {
    }
}
