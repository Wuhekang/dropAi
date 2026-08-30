package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.dropai.rewrite.service.ppt.rendering.canonical.v1.FrozenSlideRenderPlan;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurePptxRendererContractTest {
    @Test
    void pureRendererExposesOnlyTheFrozenPlanExecutionBoundary() {
        Method[] methods = Arrays.stream(PurePptxRenderer.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        assertEquals(1, methods.length);
        assertEquals("render", methods[0].getName());
        assertArrayEquals(
                new Class<?>[]{FrozenSlideRenderPlan.class, AssetBinaryResolver.class, OutputStream.class},
                methods[0].getParameterTypes());
        assertEquals(RenderedPptx.class, methods[0].getReturnType());
    }

    @Test
    void rendererMetadataDescribesOnlyExecutionResults() {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();

        assertEquals(RendererVersion.VERSION, fixture.result().rendererVersion());
        assertEquals(RendererTestSupport.expectedPlanHash(), fixture.result().renderPlanHash());
        assertEquals(40, fixture.result().slideCount());
        assertEquals(fixture.pptx().length, fixture.result().writtenBytes());
        assertTrue(fixture.result().writtenBytes() > 0);
        assertFalse(fixture.result().getClass().getRecordComponents().length > 4,
                "Commit 6 must not add Commit 7 quality or preview metadata");
    }
}
