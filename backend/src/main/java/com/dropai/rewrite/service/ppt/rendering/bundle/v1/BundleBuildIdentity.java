package com.dropai.rewrite.service.ppt.rendering.bundle.v1;

import java.util.Locale;
import java.util.Objects;

/** Immutable executable identity recorded with every production bundle. */
public record BundleBuildIdentity(String rendererVersion, String gitCommit) {
    public BundleBuildIdentity {
        rendererVersion = requireText(rendererVersion, "rendererVersion");
        gitCommit = requireText(gitCommit, "gitCommit").toLowerCase(Locale.ROOT);
        if (!gitCommit.matches("^(?:[a-f0-9]{40}|[a-f0-9]{64})$")) {
            throw new IllegalArgumentException("gitCommit must be a full 40 or 64 character hash");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
