package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MeasurementTestSupport {
    private MeasurementTestSupport() {
    }

    public static ResolvedFontProfile exactProfile() {
        return profile("Microsoft YaHei", "exact-v1");
    }

    static ResolvedFontProfile profile(String family, String byteSeed) {
        Map<String, List<FontFaceResource>> faces = new LinkedHashMap<>();
        for (int weight : List.of(400, 500, 600, 700)) {
            faces.put(key(family, weight), List.of(resource(family, weight, byteSeed + '-' + weight)));
        }
        FontFaceInventory inventory = (requestedFamily, weight) ->
                faces.getOrDefault(key(requestedFamily, weight), List.of());
        return new ResolvedFontProfileResolver(inventory).resolve(
                "cjk-academic-v1",
                Map.of(
                        "body", List.of(family),
                        "display", List.of(family)),
                Map.of(
                        "body", Set.of(400, 500, 600, 700),
                        "display", Set.of(700)));
    }

    static FontFaceResource resource(String family, int weight, String byteSeed) {
        return new FontFaceResource(
                family,
                family.replace(" ", "") + '-' + weight,
                weight,
                FontSource.PROVIDED,
                byteSeed.getBytes(StandardCharsets.UTF_8));
    }

    public static DeterministicTextMetricsService textMetrics() {
        return new DeterministicTextMetricsService(new FixedGlyphMetricsModel());
    }

    static long em(int sizeHundredthPt, int permille) {
        return Emu.scale(Emu.fromHundredthPoints(sizeHundredthPt), permille, 1_000);
    }

    private static String key(String family, int weight) {
        return family.toLowerCase(java.util.Locale.ROOT) + ':' + weight;
    }

    private static final class FixedGlyphMetricsModel implements GlyphMetricsModel {
        @Override
        public long textWidthEmu(ResolvedFontFace face, int fontSizeHundredthPt, String text) {
            long emu = Emu.fromHundredthPoints(fontSizeHundredthPt);
            List<Integer> widthsPermille = new ArrayList<>();
            text.codePoints().forEach(codePoint -> {
                if (Character.isWhitespace(codePoint)) {
                    widthsPermille.add(300);
                } else if (isCjk(codePoint)) {
                    widthsPermille.add(1_000);
                } else if (Character.isLetterOrDigit(codePoint)) {
                    widthsPermille.add(550);
                } else {
                    widthsPermille.add(500);
                }
            });
            return widthsPermille.stream()
                    .mapToLong(width -> Emu.scale(emu, width, 1_000))
                    .reduce(0L, Math::addExact);
        }

        @Override
        public long naturalLineHeightEmu(ResolvedFontFace face, int fontSizeHundredthPt) {
            return Emu.fromHundredthPoints(fontSizeHundredthPt);
        }

        private static boolean isCjk(int codePoint) {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
        }
    }
}
