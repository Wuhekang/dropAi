package com.dropai.rewrite.external;

/** Daya is the only opt-in route. GENERAL remains on DropAI's existing pipeline. */
public enum XuejiePlatform {
    DAYA("大雅");

    private final String remoteName;

    XuejiePlatform(String remoteName) {
        this.remoteName = remoteName;
    }

    public String remoteName() {
        return remoteName;
    }

    public static XuejiePlatform require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请选择大雅平台");
        String normalized = value.trim();
        if (DAYA.name().equalsIgnoreCase(normalized)) return DAYA;
        throw new IllegalArgumentException("仅支持大雅平台：" + normalized);
    }
}
