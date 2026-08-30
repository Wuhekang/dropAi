package com.dropai.rewrite.service.ppt.rendering.renderer.v1.element;

import com.dropai.rewrite.service.ppt.rendering.contract.v1.PptQualityCode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.common.usermodel.fonts.FontGroup;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;

/** Writes text exactly as supplied by the RenderPlan with PowerPoint autofit disabled. */
public final class TextElementRenderer implements ElementRenderer {
    @Override
    public String elementType() {
        return "TEXT";
    }

    @Override
    public void render(ObjectNode element, ElementRenderContext context) {
        String elementId = PlanElementSupport.requiredText(element, "elementId");
        try {
            ObjectNode style = PlanElementSupport.requiredObject(element, "resolvedStyle");
            XSLFTextBox box = context.slide().createTextBox();
            box.setAnchor(PlanElementSupport.anchor(element));
            box.setTextAutofit(TextShape.TextAutofit.NONE);
            box.setWordWrap(false);
            box.setLeftInset(PlanElementSupport.points(PlanElementSupport.requiredLong(style, "marginLeftEmu")));
            box.setRightInset(PlanElementSupport.points(PlanElementSupport.requiredLong(style, "marginRightEmu")));
            box.setTopInset(PlanElementSupport.points(PlanElementSupport.requiredLong(style, "marginTopEmu")));
            box.setBottomInset(PlanElementSupport.points(PlanElementSupport.requiredLong(style, "marginBottomEmu")));
            box.setVerticalAlignment(VerticalAlignment.valueOf(
                    PlanElementSupport.requiredText(style, "verticalAlign")));
            box.setText(PlanElementSupport.requiredText(element, "text"));

            TextParagraph.TextAlign alignment = TextParagraph.TextAlign.valueOf(
                    PlanElementSupport.requiredText(style, "horizontalAlign"));
            double lineSpacingPercent = PlanElementSupport.requiredInt(style, "lineSpacingPermille") / 10d;
            double spaceBeforePt = PlanElementSupport.points(
                    PlanElementSupport.requiredLong(style, "paragraphSpaceBeforeEmu"));
            double spaceAfterPt = PlanElementSupport.points(
                    PlanElementSupport.requiredLong(style, "paragraphSpaceAfterEmu"));
            double fontSize = PlanElementSupport.requiredInt(style, "fontSizeHundredthPt") / 100d;
            int fontWeight = PlanElementSupport.requiredInt(style, "fontWeight");
            String fontFamily = PlanElementSupport.requiredText(style, "fontFamily");
            for (XSLFTextParagraph paragraph : box.getTextParagraphs()) {
                paragraph.setTextAlign(alignment);
                paragraph.setLineSpacing(lineSpacingPercent);
                paragraph.setSpaceBefore(-spaceBeforePt);
                paragraph.setSpaceAfter(-spaceAfterPt);
                paragraph.setBullet(false);
                for (XSLFTextRun run : paragraph.getTextRuns()) {
                    applyRun(run, fontFamily, fontSize, fontWeight,
                            PlanElementSupport.color(style, "textColor"));
                }
            }
        } catch (RuntimeException exception) {
            if (exception instanceof com.dropai.rewrite.service.ppt.rendering.renderer.v1.RendererExecutionException renderer) {
                throw renderer;
            }
            throw context.failure(PptQualityCode.OOXML_PACKAGE_INVALID,
                    "Failed to execute TEXT element " + elementId, elementId, exception);
        }
    }

    static void applyRun(
            XSLFTextRun run,
            String fontFamily,
            double fontSize,
            int fontWeight,
            java.awt.Color color
    ) {
        run.setFontFamily(fontFamily);
        run.setFontFamily(fontFamily, FontGroup.LATIN);
        run.setFontFamily(fontFamily, FontGroup.EAST_ASIAN);
        run.setFontFamily(fontFamily, FontGroup.COMPLEX_SCRIPT);
        run.setFontSize(fontSize);
        run.setBold(fontWeight >= 600);
        run.setFontColor(color);
    }
}
