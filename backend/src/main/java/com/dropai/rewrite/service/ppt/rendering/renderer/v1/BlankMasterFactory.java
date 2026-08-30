package com.dropai.rewrite.service.ppt.rendering.renderer.v1;

import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;

import java.util.Objects;

/** Creates slides with no visible master graphics or placeholder shapes. */
public final class BlankMasterFactory {
    public void removeDefaultPlaceholders(XMLSlideShow document) {
        Objects.requireNonNull(document, "document");
        for (XSLFSlideMaster master : document.getSlideMasters()) {
            master.clear();
            for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                layout.clear();
            }
        }
    }

    public XSLFSlide createBlankSlide(XMLSlideShow document) {
        Objects.requireNonNull(document, "document");
        XSLFSlideLayout blank = null;
        for (XSLFSlideMaster master : document.getSlideMasters()) {
            blank = master.getLayout(SlideLayout.BLANK);
            if (blank != null) {
                break;
            }
        }
        XSLFSlide slide = blank == null ? document.createSlide() : document.createSlide(blank);
        slide.clear();
        slide.setFollowMasterGraphics(false);
        slide.setFollowMasterObjects(false);
        // POI 5.3 deliberately throws from setFollowMasterBackground/ColourScheme.
        // The master and layout shape trees are already empty; these explicit CT flags
        // suppress master shapes without introducing unsupported high-level setters.
        slide.getXmlObject().setShowMasterSp(false);
        slide.getXmlObject().setShowMasterPhAnim(false);
        slide.setHidden(false);
        return slide;
    }
}
