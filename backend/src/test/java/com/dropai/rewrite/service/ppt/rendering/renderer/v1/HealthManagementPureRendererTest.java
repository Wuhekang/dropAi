package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFConnectorShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthManagementPureRendererTest {
    private static final List<String> INTERNAL_FIELDS = List.of(
            "pagePurpose",
            "answerQuestion",
            "sourceChapter",
            "mandatoryAsset",
            "semanticAlignmentScore",
            "sourceRefs");

    @Test
    void rendersTheFortyPageFixtureOneForOneWithoutHiddenOrExtraSlides() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            assertEquals(40, fixture.document().path("slides").size());
            assertEquals(40, show.getSlides().size());
            assertTrue(show.getSlides().stream().noneMatch(slide -> slide.isHidden()));

            for (int index = 0; index < show.getSlides().size(); index++) {
                JsonNode planned = fixture.document().path("slides").get(index);
                assertEquals(index + 1, planned.path("index").asInt());
                assertEquals(index + 1, show.getSlides().get(index).getSlideNumber());
                assertEquals(planned.path("elements").size(), show.getSlides().get(index).getShapes().size(),
                        planned.path("slideId").asText());
            }
        }
    }

    @Test
    void preservesAllNativeElementKindsAndUserVisibleText() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        int text = 0;
        int images = 0;
        int shapes = 0;
        int tables = 0;
        int connectors = 0;
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            assertEquals(
                    RendererTestSupport.plannedVisibleText(fixture.document()),
                    RendererTestSupport.renderedVisibleText(show));
            for (var slide : show.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTable) {
                        tables++;
                    } else if (shape instanceof XSLFPictureShape) {
                        images++;
                    } else if (shape instanceof XSLFConnectorShape) {
                        connectors++;
                    } else if (shape instanceof XSLFTextBox) {
                        text++;
                    } else {
                        shapes++;
                    }
                }
            }
        }

        assertEquals(RendererTestSupport.countPlanElements(fixture.document(), "TEXT"), text);
        assertEquals(175, text);
        assertEquals(RendererTestSupport.countPlanElements(fixture.document(), "IMAGE"), images);
        assertEquals(25, images);
        assertEquals(RendererTestSupport.countPlanElements(fixture.document(), "SHAPE"), shapes);
        assertEquals(69, shapes);
        assertEquals(RendererTestSupport.countPlanElements(fixture.document(), "TABLE"), tables);
        assertEquals(2, tables);
        assertEquals(RendererTestSupport.countPlanElements(fixture.document(), "CONNECTOR"), connectors);
        assertEquals(4, connectors);
    }

    @Test
    void neverLeaksInternalPlanningFieldsOrDefaultMasterCopy() {
        String allXml = RendererTestSupport.allXml(RendererTestSupport.renderedFixture().pptx());
        for (String forbidden : INTERNAL_FIELDS) {
            assertFalse(allXml.contains(forbidden), forbidden);
        }
        for (String forbidden : List.of(
                "Click to edit Master text styles",
                "Second level",
                "Third level",
                "Fourth level",
                "Fifth level",
                "未填写")) {
            assertFalse(allXml.contains(forbidden), forbidden);
        }
        assertFalse(allXml.contains("<p:ph"), "Renderer must not emit PPT placeholder elements");
    }
}
