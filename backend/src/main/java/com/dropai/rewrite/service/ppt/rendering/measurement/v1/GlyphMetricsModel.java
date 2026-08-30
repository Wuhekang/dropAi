package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

public interface GlyphMetricsModel {
    long textWidthEmu(ResolvedFontFace face, int fontSizeHundredthPt, String text);

    long naturalLineHeightEmu(ResolvedFontFace face, int fontSizeHundredthPt);
}
