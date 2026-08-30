package com.dropai.rewrite.service.ppt.rendering.measurement.v1;

public record SourceCrop(
        int leftPermille,
        int topPermille,
        int rightPermille,
        int bottomPermille
) {
    public SourceCrop {
        requirePermille(leftPermille, "leftPermille");
        requirePermille(topPermille, "topPermille");
        requirePermille(rightPermille, "rightPermille");
        requirePermille(bottomPermille, "bottomPermille");
        if (leftPermille + rightPermille >= 1_000
                || topPermille + bottomPermille >= 1_000) {
            throw new IllegalArgumentException("crop must preserve a positive source area");
        }
    }

    public static SourceCrop none() {
        return new SourceCrop(0, 0, 0, 0);
    }

    private static void requirePermille(int value, String name) {
        if (value < 0 || value > 1_000) {
            throw new IllegalArgumentException(name + " must be between 0 and 1000");
        }
    }
}
