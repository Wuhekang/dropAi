package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Executes exactly one RenderPlan element type. */
public interface ElementRenderer {
    String elementType();

    void render(ObjectNode element, ElementRenderContext context);
}
