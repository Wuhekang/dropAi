package com.dropai.rewrite.external;

public enum XuejieRewriteMode {
    HUMANIZE("humanize", "智能降AI", "降AI"),
    DOUBLE("double", "双降增强", "双降");

    private final String apiValue;
    private final String displayName;
    private final String remoteMode;

    XuejieRewriteMode(String apiValue, String displayName, String remoteMode) {
        this.apiValue = apiValue;
        this.displayName = displayName;
        this.remoteMode = remoteMode;
    }

    public String apiValue() {
        return apiValue;
    }

    public String displayName() {
        return displayName;
    }

    public String remoteMode() {
        return remoteMode;
    }

    public static XuejieRewriteMode require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("外部平台仅支持 humanize 或 double 模式");
        }
        for (XuejieRewriteMode mode : values()) {
            if (mode.apiValue.equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("外部平台仅支持 humanize 或 double 模式");
    }
}
