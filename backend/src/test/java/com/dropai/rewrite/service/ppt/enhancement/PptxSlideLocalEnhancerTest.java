package com.dropai.rewrite.service.ppt.enhancement;

import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptxSlideLocalEnhancerTest {
    @TempDir Path temp;

    @Test
    void enhancesEverySlideWithoutChangingSourceTextOrProtectedPackageParts() throws Exception {
        Path source = temp.resolve("base.pptx");
        createDeck(source);
        String sourceHash = PptxBaselineInspector.sha256(source);
        PptxBaselineInspector inspector = new PptxBaselineInspector();
        var inventory = inspector.inspect(source, "academic-purple");
        var plan = new PptEnhancementPlan("1.0", sourceHash, "ppt-enhancement", "1.0.0", "a".repeat(64),
            "polish", "balanced", "locked", "doubao_ark", "test-model", true, "SUCCESS", List.of(
                slide(1, "cover", "COVER_ACCENT"),
                slide(2, "catalog", "AGENDA_RAIL"),
                slide(3, "content", "CONTENT_RAIL"),
                slide(4, "closing", "CLOSING_ECHO")));
        Path enhanced = temp.resolve("enhanced.pptx");
        var execution = new PptxSlideLocalEnhancer().enhance(source, enhanced, plan, inventory);
        var quality = new PptEnhancementQualityGate().validate(source, enhanced, temp.resolve("qa"), inventory);

        assertEquals(sourceHash, PptxBaselineInspector.sha256(source));
        assertNotEquals(sourceHash, PptxBaselineInspector.sha256(enhanced));
        assertEquals(4, execution.patchedSlides());
        assertTrue(execution.addedShapes() >= 8);
        assertEquals("PASSED", quality.status());
        assertEquals(4, quality.renderedPages());
        assertEquals(4, quality.visiblyChangedSlides());
        assertEquals(8, Files.list(temp.resolve("qa")).flatMap(path -> {
            try { return Files.list(path); } catch (Exception exception) { throw new RuntimeException(exception); }
        }).count());
    }

    private void createDeck(Path output) throws Exception {
        try (XMLSlideShow show = new XMLSlideShow()) {
            for (int page = 1; page <= 4; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                XSLFTextBox text = slide.createTextBox();
                text.setAnchor(new Rectangle(90, 80, 700, 100));
                text.setText(switch (page) {
                    case 1 -> "学术答辩封面";
                    case 2 -> "目录";
                    case 4 -> "谢谢大家";
                    default -> "系统总体架构";
                });
            }
            try (var stream = Files.newOutputStream(output)) { show.write(stream); }
        }
    }

    private PptEnhancementPlan.SlidePlan slide(int page, String archetype, String recipe) {
        return new PptEnhancementPlan.SlidePlan(page, page, archetype, recipe, "focal", List.of("detail"),
            List.of(new PptEnhancementPlan.Addition("shape", "accent", "safe", true, false)));
    }
}
