package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPresentation;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideSize;

import java.util.Objects;

/** Constructs the deterministic blank PPTX package used by the pure Renderer. */
public final class PptxDocumentFactory {
    private final BlankMasterFactory blankMasterFactory;

    public PptxDocumentFactory() {
        this(new BlankMasterFactory());
    }

    public PptxDocumentFactory(BlankMasterFactory blankMasterFactory) {
        this.blankMasterFactory = Objects.requireNonNull(blankMasterFactory, "blankMasterFactory");
    }

    public XMLSlideShow create(long widthEmu, long heightEmu) {
        int width = positiveInt(widthEmu, "widthEmu");
        int height = positiveInt(heightEmu, "heightEmu");
        XMLSlideShow document = new XMLSlideShow();
        CTPresentation presentation = document.getCTPresentation();
        CTSlideSize size = presentation.isSetSldSz() ? presentation.getSldSz() : presentation.addNewSldSz();
        size.setCx(width);
        size.setCy(height);
        blankMasterFactory.removeDefaultPlaceholders(document);
        return document;
    }

    public XSLFSlide createBlankSlide(XMLSlideShow document) {
        return blankMasterFactory.createBlankSlide(document);
    }

    private static int positiveInt(long value, String field) {
        if (value < 1 || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is outside OOXML slide-size range: " + value);
        }
        return (int) value;
    }
}
