package com.dropai.rewrite.service.ppt.rendering.theme.v1;

import java.util.Objects;

public record ThemeResolutionRequest(String themeId, String expectedVersion, String fontProfileId) {
    public ThemeResolutionRequest {
        requireText(themeId, "themeId");
        requireText(expectedVersion, "expectedVersion");
        requireText(fontProfileId, "fontProfileId");
    }

    public static ThemeResolutionRequest academicPurpleV1() {
        return new ThemeResolutionRequest(
                ThemeRegistry.ACADEMIC_PURPLE,
                ThemeRegistry.VERSION_1_0_0,
                FontProfile.CJK_ACADEMIC_V1);
    }

    public static ThemeResolutionRequest smallBearWatercolorBlueV1() {
        return new ThemeResolutionRequest(
                ThemeRegistry.SMALL_BEAR_WATERCOLOR_BLUE_V1,
                ThemeRegistry.VERSION_1_0_0,
                FontProfile.CJK_ACADEMIC_V1);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
