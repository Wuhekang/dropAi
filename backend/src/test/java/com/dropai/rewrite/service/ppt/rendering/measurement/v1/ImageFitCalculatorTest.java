package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFitCalculatorTest {
    private final ImageFitCalculator calculator = new ImageFitCalculator();

    @Test
    void containKeepsWideImageAspectRatioAndCentersTheActualDrawingRectangle() {
        ImageFitResult result = calculator.calculate(new ImageFitRequest(
                1_600,
                900,
                100,
                200,
                1_000,
                1_000,
                ImageFitMode.CONTAIN,
                false));

        assertEquals(100, result.xEmu());
        assertEquals(418, result.yEmu());
        assertEquals(1_000, result.widthEmu());
        assertEquals(563, result.heightEmu());
        assertEquals(Optional.empty(), result.sourceCrop());
        assertFalse(result.cropAllowed());
    }

    @Test
    void containKeepsPortraitImageAspectRatioWithoutStretching() {
        ImageFitResult result = calculator.calculate(new ImageFitRequest(
                900,
                1_600,
                0,
                0,
                1_000,
                1_000,
                ImageFitMode.CONTAIN,
                false));

        assertEquals(218, result.xEmu());
        assertEquals(0, result.yEmu());
        assertEquals(563, result.widthEmu());
        assertEquals(1_000, result.heightEmu());
    }

    @Test
    void coverComputesAStableSymmetricSourceCropInPermille() {
        ImageFitResult result = calculator.calculate(new ImageFitRequest(
                1_600,
                900,
                10,
                20,
                1_000,
                1_000,
                ImageFitMode.COVER,
                true));

        assertEquals(10, result.xEmu());
        assertEquals(20, result.yEmu());
        assertEquals(1_000, result.widthEmu());
        assertEquals(1_000, result.heightEmu());
        assertEquals(new SourceCrop(219, 0, 219, 0), result.sourceCrop().orElseThrow());
    }

    @Test
    void coverWithoutExplicitCropPermissionIsBlocked() {
        MeasurementException exception = assertThrows(
                MeasurementException.class,
                () -> calculator.calculate(new ImageFitRequest(
                        1_600,
                        900,
                        0,
                        0,
                        1_000,
                        1_000,
                        ImageFitMode.COVER,
                        false)));

        assertEquals(PptQualityCode.CROP_NOT_ALLOWED, exception.qualityCode());
    }

    @Test
    void veryLargeIntegerGeometryRemainsDeterministicAndOverflowSafe() {
        ImageFitRequest request = new ImageFitRequest(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE - 1,
                0,
                0,
                Long.MAX_VALUE / 4,
                Long.MAX_VALUE / 5,
                ImageFitMode.CONTAIN,
                false);

        ImageFitResult first = calculator.calculate(request);
        ImageFitResult second = calculator.calculate(request);

        assertEquals(first, second);
        assertTrue(first.widthEmu() > 0);
        assertTrue(first.heightEmu() > 0);
    }
}
