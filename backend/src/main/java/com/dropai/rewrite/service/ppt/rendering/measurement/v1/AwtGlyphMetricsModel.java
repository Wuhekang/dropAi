package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class AwtGlyphMetricsModel implements GlyphMetricsModel {
    private static final FontRenderContext FONT_RENDER_CONTEXT =
            new FontRenderContext(new AffineTransform(), true, true);

    private final Map<String, Font> fontsByResolvedFace = new ConcurrentHashMap<>();

    @Override
    public long textWidthEmu(ResolvedFontFace face, int fontSizeHundredthPt, String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return 0L;
        }
        Font font = sizedFont(face, fontSizeHundredthPt);
        TextLayout layout = new TextLayout(text, font, FONT_RENDER_CONTEXT);
        return Emu.fromPoints(layout.getAdvance());
    }

    @Override
    public long naturalLineHeightEmu(ResolvedFontFace face, int fontSizeHundredthPt) {
        Font font = sizedFont(face, fontSizeHundredthPt);
        LineMetrics metrics = font.getLineMetrics("国Ag", FONT_RENDER_CONTEXT);
        return Emu.fromPoints(metrics.getHeight());
    }

    private Font sizedFont(ResolvedFontFace face, int fontSizeHundredthPt) {
        Objects.requireNonNull(face, "face");
        if (fontSizeHundredthPt <= 0) {
            throw new IllegalArgumentException("fontSizeHundredthPt must be positive");
        }
        String cacheKey = face.fontFingerprint() + '|' + face.postScriptName();
        Font base = fontsByResolvedFace.computeIfAbsent(cacheKey, ignored -> load(face));
        return base.deriveFont(fontSizeHundredthPt / 100.0f);
    }

    private Font load(ResolvedFontFace face) {
        byte[] bytes = face.fontBytes();
        String actualFingerprint = ResolvedFontProfileResolver.fingerprint(bytes);
        if (!actualFingerprint.equals(face.fontFingerprint())) {
            throw new MeasurementException(
                    PptQualityCode.FONT_UNAVAILABLE,
                    "Font bytes no longer match resolved fingerprint for " + face.postScriptName());
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            Font[] fonts = Font.createFonts(input);
            return Arrays.stream(fonts)
                    .filter(font -> font.getPSName().equalsIgnoreCase(face.postScriptName()))
                    .findFirst()
                    .orElseThrow(() -> new MeasurementException(
                            PptQualityCode.FONT_UNAVAILABLE,
                            "Resolved PostScript face is absent from fingerprinted font bytes: "
                                    + face.postScriptName()));
        } catch (MeasurementException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MeasurementException(
                    PptQualityCode.FONT_UNAVAILABLE,
                    "Unable to load fingerprinted font bytes for " + face.postScriptName(),
                    exception);
        }
    }
}
