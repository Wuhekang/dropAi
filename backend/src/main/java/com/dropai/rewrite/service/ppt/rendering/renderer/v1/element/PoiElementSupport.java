package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGeomGuide;
import org.openxmlformats.schemas.drawingml.x2006.main.CTOuterShadowEffect;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPresetGeometry2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSRgbColor;
import org.openxmlformats.schemas.drawingml.x2006.main.STShapeType;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

/** Low-level DrawingML execution for properties not exposed for writing by POI. */
final class PoiElementSupport {
    private PoiElementSupport() {
    }

    static CTShapeProperties shapeProperties(XSLFAutoShape shape) {
        return ((CTShape) shape.getXmlObject()).getSpPr();
    }

    static CTShapeProperties shapeProperties(XSLFPictureShape shape) {
        return ((CTPicture) shape.getXmlObject()).getSpPr();
    }

    static void applySolidFillOpacity(CTShapeProperties properties, int opacityPermille) {
        if (!properties.isSetSolidFill() || !properties.getSolidFill().isSetSrgbClr()) {
            throw new IllegalStateException("Solid RGB fill is required before applying opacity");
        }
        CTSRgbColor rgb = properties.getSolidFill().getSrgbClr();
        while (rgb.sizeOfAlphaArray() > 0) {
            rgb.removeAlpha(0);
        }
        rgb.addNewAlpha().setVal(opacityPermille * 100);
    }

    static void applyImageOpacity(XSLFPictureShape shape, int opacityPermille) {
        CTPicture picture = (CTPicture) shape.getXmlObject();
        while (picture.getBlipFill().getBlip().sizeOfAlphaModFixArray() > 0) {
            picture.getBlipFill().getBlip().removeAlphaModFix(0);
        }
        picture.getBlipFill().getBlip().addNewAlphaModFix().setAmt(opacityPermille * 100);
    }

    static void applyCrop(XSLFPictureShape shape, ObjectNode crop) {
        CTPicture picture = (CTPicture) shape.getXmlObject();
        var fill = picture.getBlipFill();
        var source = fill.isSetSrcRect() ? fill.getSrcRect() : fill.addNewSrcRect();
        source.setL(PlanElementSupport.requiredInt(crop, "leftPermille") * 100);
        source.setT(PlanElementSupport.requiredInt(crop, "topPermille") * 100);
        source.setR(PlanElementSupport.requiredInt(crop, "rightPermille") * 100);
        source.setB(PlanElementSupport.requiredInt(crop, "bottomPermille") * 100);
    }

    static void applyRoundedGeometry(
            CTShapeProperties properties,
            long cornerRadiusEmu,
            long widthEmu,
            long heightEmu
    ) {
        if (cornerRadiusEmu <= 0) {
            return;
        }
        if (properties.isSetCustGeom()) {
            properties.unsetCustGeom();
        }
        CTPresetGeometry2D geometry = properties.isSetPrstGeom()
                ? properties.getPrstGeom()
                : properties.addNewPrstGeom();
        geometry.setPrst(STShapeType.Enum.forString(ShapeType.ROUND_RECT.getOoxmlName()));
        if (geometry.isSetAvLst()) {
            geometry.unsetAvLst();
        }
        long shortSide = Math.min(widthEmu, heightEmu);
        long adjustment = Math.max(0, Math.min(50_000,
                Math.round(cornerRadiusEmu * 100_000d / shortSide)));
        CTGeomGuide guide = geometry.addNewAvLst().addNewGd();
        guide.setName("adj");
        guide.setFmla("val " + adjustment);
    }

    static void applyShadow(CTShapeProperties properties, ObjectNode shadow) {
        var effectList = properties.isSetEffectLst()
                ? properties.getEffectLst()
                : properties.addNewEffectLst();
        if (effectList.isSetOuterShdw()) {
            effectList.unsetOuterShdw();
        }
        CTOuterShadowEffect outer = effectList.addNewOuterShdw();
        CTSRgbColor rgb = outer.addNewSrgbClr();
        rgb.setVal(PlanElementSupport.rgbBytes(shadow, "color"));
        rgb.addNewAlpha().setVal(PlanElementSupport.requiredInt(shadow, "opacityPermille") * 100);
        outer.setBlurRad(PlanElementSupport.requiredLong(shadow, "blurRadiusEmu"));
        outer.setDist(PlanElementSupport.requiredLong(shadow, "distanceEmu"));
        outer.setDir(Math.multiplyExact(
                PlanElementSupport.requiredInt(shadow, "angleThousandthDegree"), 60));
        outer.setRotWithShape(false);
    }
}
