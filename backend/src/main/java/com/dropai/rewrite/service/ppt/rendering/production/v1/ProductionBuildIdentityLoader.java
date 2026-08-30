package com.dropai.rewrite.service.ppt.rendering.production.v1;

import com.dropai.rewrite.service.ppt.rendering.bundle.v1.BundleBuildIdentity;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererVersion;

/** Loads the exact deployed source revision; an unversioned production renderer is rejected. */
final class ProductionBuildIdentityLoader {
    static final String GIT_COMMIT_PROPERTY = "dokiai.git.commit";

    BundleBuildIdentity load() {
        String gitCommit = System.getProperty(
                GIT_COMMIT_PROPERTY,
                System.getenv().getOrDefault("DOKIAI_GIT_COMMIT", ""));
        if (gitCommit.isBlank()) {
            throw new IllegalStateException("PPT Rendering V1 requires the deployed Git commit via "
                    + GIT_COMMIT_PROPERTY + " or DOKIAI_GIT_COMMIT");
        }
        try {
            return new BundleBuildIdentity(RendererVersion.VERSION, gitCommit);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid deployed Git commit for PPT Rendering V1", exception);
        }
    }
}
