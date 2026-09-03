package com.dropai.rewrite.service.ppt.rendering.compiler.v1;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.enums.PageType;
import com.dropai.rewrite.service.ppt.rendering.template.v1.RenderingTemplatePack;
import com.dropai.rewrite.service.ppt.rendering.template.v1.RenderingTemplatePackRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderingTemplatePackCompilerTest {
    private static final java.util.Set<String> TITLE_COMPONENTS = java.util.Set.of(
            "coverTitle", "sectionTitle", "slideTitle");
    private static final java.util.Set<String> FORBIDDEN_DEFAULT_BLUES = java.util.Set.of(
            "#4E58A0", "#4472C4", "#5B9BD5", "#0070C0", "#0000FF");

    @Test
    void compilesOneVerifiedDecorativeSurfacePerSlideWithoutChangingContentContracts() {
        RenderingTemplatePack pack = decoratedPack();
        HealthManagementRenderPlanSupport.CompiledFixture base =
                HealthManagementRenderPlanSupport.compile();
        HealthManagementRenderPlanSupport.CompiledFixture fixture =
                HealthManagementRenderPlanSupport.compile(pack);
        JsonNode slides = fixture.draft().document().path("slides");
        JsonNode baseSlides = base.draft().document().path("slides");

        assertEquals(40, slides.size());
        for (int index = 0; index < slides.size(); index++) {
            JsonNode slide = slides.get(index);
            JsonNode baseSlide = baseSlides.get(index);
            long decorations = count(slide, "IMAGE", true);
            long contentImages = count(slide, "IMAGE", false);
            assertEquals(1, decorations, slide.path("slideId").asText());
            assertEquals(baseSlide.path("sourcePageId"), slide.path("sourcePageId"));
            assertEquals(baseSlide.path("pageType"), slide.path("pageType"));
            assertEquals(baseSlide.path("layoutId"), slide.path("layoutId"));
            assertEquals(visibleText(baseSlide), visibleText(slide));
            assertEquals("slide-" + String.format("%03d", slide.path("index").asInt())
                            + "-template-surface",
                    slide.path("elements").get(1).path("elementId").asText());
            assertEquals(5, slide.path("elements").get(1).path("zIndex").asInt());
            assertEquals(0, slide.path("elements").get(1).path("xEmu").asLong());
            assertEquals(0, slide.path("elements").get(1).path("yEmu").asLong());
            if (PageType.IMAGE.name().equals(slide.path("pageType").asText())) {
                assertEquals(1, contentImages, slide.path("slideId").asText());
            } else {
                assertEquals(0, contentImages, slide.path("slideId").asText());
            }
            assertAuditedTitleColors(slide);
        }
        assertTrue(fixture.validationContext() != null);
        assertTrue(fixture.validated() != null);
    }

    @Test
    void sameTemplateInputsProduceByteEquivalentDraftPlans() {
        RenderingTemplatePack pack = decoratedPack();
        String first = HealthManagementRenderPlanSupport.compile(pack).draft().document().toString();
        String second = HealthManagementRenderPlanSupport.compile(pack).draft().document().toString();

        assertEquals(first, second);
        assertFalse(first.contains(".pptx"));
        assertFalse(first.contains(".xlsx"));
    }

    private RenderingTemplatePack decoratedPack() {
        return new RenderingTemplatePackRegistry().available().stream()
                .filter(RenderingTemplatePack::decorated)
                .findFirst()
                .orElseThrow();
    }

    private long count(JsonNode slide, String type, boolean decorative) {
        long count = 0;
        for (JsonNode element : slide.path("elements")) {
            if (type.equals(element.path("elementType").asText())
                    && element.path("decorative").asBoolean(false) == decorative) {
                count++;
            }
        }
        return count;
    }

    private java.util.List<String> visibleText(JsonNode slide) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode element : slide.path("elements")) {
            if ("TEXT".equals(element.path("elementType").asText())) {
                values.add(element.path("text").asText());
            }
        }
        return java.util.List.copyOf(values);
    }

    private void assertAuditedTitleColors(JsonNode slide) {
        for (JsonNode element : slide.path("elements")) {
            if (!"TEXT".equals(element.path("elementType").asText())) {
                continue;
            }
            String component = element.path("styleSource").path("component").asText();
            if (!TITLE_COMPONENTS.contains(component)) {
                continue;
            }
            String titleColor = element.path("resolvedStyle").path("textColor").asText();
            assertEquals("#4A5A69", titleColor, element.path("elementId").asText());
            assertFalse(FORBIDDEN_DEFAULT_BLUES.contains(titleColor), element.path("elementId").asText());
        }
    }
}
