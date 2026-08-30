package com.dropai.rewrite.service.ppt.rendering.canonical.v1;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** SHA-256 over the exact canonical bytes, returned in contract format. */
public final class RenderPlanHasher {
    public String hash(FrozenSlideRenderPlan frozen) {
        Objects.requireNonNull(frozen, "frozen");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(frozen.canonicalBytes());
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
