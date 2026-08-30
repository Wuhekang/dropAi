package com.dropai.rewrite.service.ppt;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.AssetBinaryResolver;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.PurePptxRenderer;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.PurePptxRendererImpl;
import com.dropai.rewrite.service.ppt.rendering.renderer.v1.RenderedPptx;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Filesystem boundary around the pure Rendering V1 executor.
 *
 * <p>The service accepts only an already validated and frozen RenderPlan. It does not
 * compile content, select a layout, resolve a theme, query persistence, or fall back to
 * a legacy renderer.</p>
 */
@Service
public class PptEngineV1Service {
    private final PurePptxRenderer renderer;

    public PptEngineV1Service() {
        this(new PurePptxRendererImpl());
    }

    public PptEngineV1Service(PurePptxRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public RenderedPptx generate(
            FrozenSlideRenderPlan plan,
            AssetBinaryResolver assetResolver,
            Path output
    ) throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(assetResolver, "assetResolver");
        Objects.requireNonNull(output, "output");

        Path target = output.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(target.getParent(), "output parent");
        Files.createDirectories(parent);
        if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(target.toString());
        }
        Path temporary = Files.createTempFile(parent, target.getFileName() + ".", ".rendering");
        try {
            RenderedPptx result;
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                result = renderer.render(plan, assetResolver, stream);
            }
            moveIntoPlace(temporary, target);
            return result;
        } catch (RuntimeException | IOException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Filesystem does not support atomic PPTX publication", exception);
        }
    }
}
