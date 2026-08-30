package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererNoAutofitTest {
    @Test
    void explicitlyDisablesPowerPointAutofitForTextAndTableCells() throws Exception {
        RendererTestSupport.RenderedFixture fixture = RendererTestSupport.renderedFixture();
        int checked = 0;
        try (XMLSlideShow show = RendererTestSupport.open(fixture.pptx())) {
            for (var slide : show.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextBox textBox) {
                        assertEquals(TextShape.TextAutofit.NONE, textBox.getTextAutofit());
                        checked++;
                    } else if (shape instanceof XSLFTable table) {
                        for (int row = 0; row < table.getNumberOfRows(); row++) {
                            for (int column = 0; column < table.getNumberOfColumns(); column++) {
                                assertEquals(TextShape.TextAutofit.NONE,
                                        table.getCell(row, column).getTextAutofit());
                                checked++;
                            }
                        }
                    }
                }
            }
        }

        assertTrue(checked >= 175);
        String xml = RendererTestSupport.allXml(fixture.pptx());
        assertTrue(xml.contains("noAutofit"));
        assertFalse(xml.contains("normAutofit"));
        assertFalse(xml.contains("spAutoFit"));
    }
}
