package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.ImageFitMode;

import java.math.BigInteger;
import java.util.Optional;

public final class ImageFitCalculator {
    private static final BigInteger TWO = BigInteger.valueOf(2L);
    private static final BigInteger ONE_THOUSAND = BigInteger.valueOf(1_000L);

    public ImageFitResult calculate(ImageFitRequest request) {
        if (request.fitMode() == ImageFitMode.CONTAIN) {
            return contain(request);
        }
        if (!request.cropAllowed()) {
            throw new MeasurementException(
                    PptQualityCode.CROP_NOT_ALLOWED,
                    "COVER requires cropAllowed=true");
        }
        return cover(request);
    }

    private ImageFitResult contain(ImageFitRequest request) {
        BigInteger targetBySourceHeight = multiply(request.targetWidthEmu(), request.sourceHeightPx());
        BigInteger targetHeightBySourceWidth = multiply(request.targetHeightEmu(), request.sourceWidthPx());
        long width;
        long height;
        if (targetBySourceHeight.compareTo(targetHeightBySourceWidth) <= 0) {
            width = request.targetWidthEmu();
            height = divideHalfUp(
                    multiply(width, request.sourceHeightPx()),
                    BigInteger.valueOf(request.sourceWidthPx()));
        } else {
            height = request.targetHeightEmu();
            width = divideHalfUp(
                    multiply(height, request.sourceWidthPx()),
                    BigInteger.valueOf(request.sourceHeightPx()));
        }
        width = Math.max(1L, Math.min(width, request.targetWidthEmu()));
        height = Math.max(1L, Math.min(height, request.targetHeightEmu()));
        long x = Math.addExact(request.targetXEmu(), (request.targetWidthEmu() - width) / 2L);
        long y = Math.addExact(request.targetYEmu(), (request.targetHeightEmu() - height) / 2L);
        return new ImageFitResult(
                x,
                y,
                width,
                height,
                ImageFitMode.CONTAIN,
                false,
                Optional.empty());
    }

    private ImageFitResult cover(ImageFitRequest request) {
        BigInteger sourceByTargetHeight = multiply(request.sourceWidthPx(), request.targetHeightEmu());
        BigInteger sourceHeightByTargetWidth = multiply(request.sourceHeightPx(), request.targetWidthEmu());
        int horizontalCrop = 0;
        int verticalCrop = 0;
        int comparison = sourceByTargetHeight.compareTo(sourceHeightByTargetWidth);
        if (comparison > 0) {
            BigInteger cropped = sourceByTargetHeight.subtract(sourceHeightByTargetWidth);
            horizontalCrop = Math.toIntExact(divideHalfUp(
                    cropped.multiply(ONE_THOUSAND),
                    sourceByTargetHeight));
        } else if (comparison < 0) {
            BigInteger cropped = sourceHeightByTargetWidth.subtract(sourceByTargetHeight);
            verticalCrop = Math.toIntExact(divideHalfUp(
                    cropped.multiply(ONE_THOUSAND),
                    sourceHeightByTargetWidth));
        }
        int left = horizontalCrop / 2;
        int right = horizontalCrop - left;
        int top = verticalCrop / 2;
        int bottom = verticalCrop - top;
        SourceCrop crop = new SourceCrop(left, top, right, bottom);
        return new ImageFitResult(
                request.targetXEmu(),
                request.targetYEmu(),
                request.targetWidthEmu(),
                request.targetHeightEmu(),
                ImageFitMode.COVER,
                true,
                Optional.of(crop));
    }

    private static BigInteger multiply(long first, long second) {
        return BigInteger.valueOf(first).multiply(BigInteger.valueOf(second));
    }

    private static long divideHalfUp(BigInteger numerator, BigInteger denominator) {
        BigInteger[] result = numerator.divideAndRemainder(denominator);
        if (result[1].multiply(TWO).compareTo(denominator) >= 0) {
            result[0] = result[0].add(BigInteger.ONE);
        }
        return result[0].longValueExact();
    }
}
