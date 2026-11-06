package com.dropai.rewrite.service.ppt.enhancement;

import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void imageSlideChangesOnlyItsBackgroundAndKeepsEveryInheritedForegroundElement() throws Exception {
        Path source = temp.resolve("image-content-base.pptx");
        createImageAndContentDeck(source);
        String sourceHash = PptxBaselineInspector.sha256(source);
        PptxBaselineInspector inspector = new PptxBaselineInspector();
        var inventory = inspector.inspect(source, "academic-purple");
        var plan = new PptEnhancementPlan("1.0", sourceHash, "ppt-enhancement", "1.1.0", "a".repeat(64),
            "polish", "balanced", "locked", "doubao_ark", "test-model", true, "SUCCESS", List.of(
                slide(1, "cover", "COVER_ACCENT"),
                slide(2, "catalog", "AGENDA_RAIL"),
                backgroundSlide(3),
                backgroundSlide(4),
                slide(5, "content", "CONTENT_RAIL"),
                slide(6, "closing", "CLOSING_ECHO")));
        List<ShapeFingerprint> imageForegroundBefore = inheritedShapes(source, 3);
        List<ShapeFingerprint> fullBleedForegroundBefore = inheritedShapes(source, 4);

        Path enhanced = temp.resolve("image-content-enhanced.pptx");
        var execution = new PptxSlideLocalEnhancer().enhance(source, enhanced, plan, inventory);
        var quality = new PptEnhancementQualityGate().validate(
            source, enhanced, temp.resolve("image-content-qa"), inventory);

        assertEquals(imageForegroundBefore, inheritedShapes(enhanced, 3),
            "image页的图片、文字、位置、z-order与已有元素必须逐项保持");
        try (InputStream input = Files.newInputStream(enhanced); XMLSlideShow show = new XMLSlideShow(input)) {
            XSLFSlide imageSlide = show.getSlides().get(2);
            List<XSLFShape> imageEnhancements = enhancements(imageSlide);
            assertTrue(!imageEnhancements.isEmpty(), "image页必须产生可见背景变化");
            int firstInherited = firstInheritedIndex(imageSlide);
            assertTrue(firstInherited > 0, "image页背景增幅必须位于全部继承前景元素下方，实际z-order="
                + imageSlide.getShapes().stream().map(XSLFShape::getShapeName).toList());
            assertTrue(imageSlide.getShapes().subList(0, firstInherited).stream()
                .allMatch(this::isEnhancement));
            assertTrue(imageEnhancements.stream().allMatch(shape -> isFullSlideBackground(shape, show)),
                "image页不允许新增角标、边框或其他前景装饰");

            XSLFSlide fullBleedImage = show.getSlides().get(3);
            assertEquals(fullBleedForegroundBefore, inheritedShapes(enhanced, 4));
            assertTrue(enhancements(fullBleedImage).isEmpty(),
                "全幅图片覆盖整页时必须安全no-op，不得为追求变化增加前景装饰");

            XSLFSlide contentSlide = show.getSlides().get(4);
            List<XSLFShape> contentEnhancements = enhancements(contentSlide);
            assertTrue(!contentEnhancements.isEmpty(), "非image页面仍应执行原Skill增幅");
            assertTrue(contentEnhancements.stream().anyMatch(shape -> !isFullSlideBackground(shape, show)),
                "content页应保留原有局部shape accent，而不是退化为统一背景");
        }
        assertEquals(6, execution.patchedSlides());
        assertEquals(1, execution.addedShapesBySlide().get(3));
        assertEquals(0, execution.addedShapesBySlide().get(4));
        assertEquals("PASSED", quality.status());
        assertEquals(5, quality.visiblyChangedSlides());
        PptEnhancementQualityGate.PageRender fullBleedRender = quality.pageRenders().get(3);
        assertFalse(fullBleedRender.visuallyChanged());
        assertEquals(0, fullBleedRender.addedShapeCount());
        assertEquals("PASSED", fullBleedRender.status());
    }

    @Test
    void baselineInspectorProtectsEverySourcePicturePageIncludingFixedPositions() throws Exception {
        Path source = temp.resolve("inspector-picture-priority.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFPictureData pictureData = show.addPicture(
                screenshotPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 5; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(60, 35, 760, 60));
                title.setText(switch (page) {
                    case 1 -> "答辩封面";
                    case 2 -> "目录";
                    case 3 -> "图片、文字与表格混排成果";
                    case 4 -> "数据库摘要";
                    default -> "谢谢大家";
                });
                if (page == 1 || page == 2 || page == 3 || page == 5) {
                    XSLFPictureShape picture = slide.createPicture(pictureData);
                    picture.setAnchor(new Rectangle(180, 130, 560, 250));
                }
                if (page == 3 || page == 4) {
                    XSLFTable table = slide.createTable(2, 2);
                    table.setAnchor(new Rectangle(200, 400, 520, 80));
                    table.getCell(0, 0).setText("指标");
                    table.getCell(0, 1).setText("结果");
                    table.getCell(1, 0).setText("响应时间");
                    table.getCell(1, 1).setText("通过");
                }
            }
            try (var stream = Files.newOutputStream(source)) {
                show.write(stream);
            }
        }

        var slides = new PptxBaselineInspector().inspect(source, "academic-purple").slides();

        assertEquals(List.of("image", "image", "image", "table", "image"),
            slides.stream().map(PptxBaselineInspector.SlideInventory::suggestedArchetype).toList(),
            "模板底图之外的源图片必须在封面、目录、正文和结尾位置全部优先进入媒体保护");
        assertEquals(1, slides.get(2).pictureCount());
        assertEquals(1, slides.get(2).tableCount());
    }

    @Test
    void repeatedFullBleedPictureBecomesTrustedTemplateBackgroundAndEnhancementSitsAboveIt() throws Exception {
        Path source = temp.resolve("repeated-template-background.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFPictureData shared = show.addPicture(
                screenshotPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 6; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                XSLFPictureShape templateBackground = slide.createPicture(shared);
                templateBackground.setAnchor(new Rectangle(
                    0, 0, show.getPageSize().width, show.getPageSize().height));
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(90, 50, 700, 72));
                title.setText(switch (page) {
                    case 1 -> "答辩封面";
                    case 2 -> "目录";
                    case 4 -> "用户功能截图";
                    case 6 -> "谢谢大家";
                    default -> "项目内容说明";
                });
                if (page == 4) {
                    XSLFPictureShape screenshot = slide.createPicture(shared);
                    screenshot.setAnchor(new Rectangle(175, 145, 610, 300));
                }
            }
            try (var stream = Files.newOutputStream(source)) {
                show.write(stream);
            }
        }

        PptxBaselineInspector inspector = new PptxBaselineInspector();
        var inventory = inspector.inspect(source, "small-bear");
        assertEquals(List.of("cover", "catalog", "content", "image", "content", "closing"),
            inventory.slides().stream().map(PptxBaselineInspector.SlideInventory::suggestedArchetype).toList());
        assertTrue(inventory.slides().stream().allMatch(slide -> slide.shapeBoxes().stream()
            .filter(PptxBaselineInspector.ShapeBox::trustedTemplateBackground).count() == 1));
        assertEquals(0, inventory.slides().get(2).pictureCount(),
            "重复模板背景不应把普通页误判为image");
        assertEquals(1, inventory.slides().get(3).pictureCount(),
            "非全幅内容图仍必须受保护");

        String sourceHash = PptxBaselineInspector.sha256(source);
        var plan = new PptEnhancementPlan("1.0", sourceHash, "ppt-enhancement", "1.2.0", "a".repeat(64),
            "polish", "balanced", "locked", "doubao_ark", "test-model", true, "SUCCESS", List.of(
                slide(1, "cover", "COVER_ACCENT"),
                slide(2, "catalog", "AGENDA_RAIL"),
                slide(3, "content", "CONTENT_RAIL"),
                backgroundSlide(4),
                slide(5, "content", "CONTENT_RAIL"),
                slide(6, "closing", "CLOSING_ECHO")));
        Path enhanced = temp.resolve("repeated-template-background-enhanced.pptx");
        var execution = new PptxSlideLocalEnhancer().enhance(source, enhanced, plan, inventory);
        var quality = new PptEnhancementQualityGate().validate(
            source, enhanced, temp.resolve("repeated-template-background-qa"), inventory);

        assertEquals(1, execution.addedShapesBySlide().get(4));
        assertEquals("PASSED", quality.status());
        try (InputStream input = Files.newInputStream(enhanced); XMLSlideShow show = new XMLSlideShow(input)) {
            XSLFSlide imageSlide = show.getSlides().get(3);
            int enhancementZ = -1;
            int templateBackgroundZ = -1;
            int firstProtectedZ = Integer.MAX_VALUE;
            var baseline = inventory.slides().get(3);
            for (int index = 0; index < imageSlide.getShapes().size(); index++) {
                XSLFShape shape = imageSlide.getShapes().get(index);
                if (isEnhancement(shape)) enhancementZ = index;
                int shapeId = shape.getShapeId();
                var box = baseline.shapeBoxes().stream()
                    .filter(item -> item.shapeId() == shapeId).findFirst().orElse(null);
                if (box != null && box.trustedTemplateBackground()) templateBackgroundZ = index;
                if (box != null && box.protectedContent()) firstProtectedZ = Math.min(firstProtectedZ, index);
            }
            assertTrue(templateBackgroundZ < enhancementZ,
                "增幅背景必须在可信模板底图之上");
            assertTrue(enhancementZ < firstProtectedZ,
                "增幅背景必须在截图和文字之下");
        }
    }

    @Test
    void repeatedFullBleedForegroundInstanceIsNotTrustedAsTemplateBackground() throws Exception {
        Path source = temp.resolve("repeated-foreground-instance.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFPictureData shared = show.addPicture(
                screenshotPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 6; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                if (page <= 3) {
                    XSLFPictureShape background = slide.createPicture(shared);
                    background.setAnchor(new Rectangle(
                        0, 0, show.getPageSize().width, show.getPageSize().height));
                }
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(90, 50, 700, 72));
                title.setText(switch (page) {
                    case 1 -> "答辩封面";
                    case 2 -> "目录";
                    case 4 -> "全幅成果展示";
                    case 6 -> "谢谢大家";
                    default -> "项目内容说明";
                });
                if (page == 4) {
                    XSLFPictureShape foreground = slide.createPicture(shared);
                    foreground.setAnchor(new Rectangle(
                        0, 0, show.getPageSize().width, show.getPageSize().height));
                }
            }
            try (var stream = Files.newOutputStream(source)) {
                show.write(stream);
            }
        }

        var page = new PptxBaselineInspector().inspect(source, "small-bear").slides().get(3);
        assertEquals("image", page.suggestedArchetype());
        assertEquals(1, page.pictureCount());
        assertTrue(page.shapeBoxes().stream().filter(box -> "PICTURE".equals(box.type()))
            .noneMatch(PptxBaselineInspector.ShapeBox::trustedTemplateBackground),
            "同SHA的全幅前景成果图不得被跨页可信集合误标为模板背景");
    }

    @Test
    void severelyOffsetPictureIsNotTreatedAsFullBleedSafeNoop() throws Exception {
        Path source = temp.resolve("offset-picture.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFPictureData pictureData = show.addPicture(
                screenshotPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 4; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(90, 50, 700, 72));
                title.setText(page == 1 ? "答辩封面" : page == 2 ? "目录"
                    : page == 4 ? "谢谢大家" : "偏移成果图");
                if (page == 3) {
                    XSLFPictureShape picture = slide.createPicture(pictureData);
                    picture.setAnchor(new Rectangle(
                        -show.getPageSize().width / 2, 0,
                        Math.round(show.getPageSize().width * .94f), show.getPageSize().height));
                }
            }
            try (var stream = Files.newOutputStream(source)) {
                show.write(stream);
            }
        }

        var inventory = new PptxBaselineInspector().inspect(source, "academic-purple");
        var page = inventory.slides().get(2);
        assertEquals("image", page.suggestedArchetype());
        assertTrue(page.shapeBoxes().stream().filter(box -> "PICTURE".equals(box.type()))
            .noneMatch(PptxBaselineInspector.ShapeBox::fullBleed));

        var plan = new PptEnhancementPlan("1.0", inventory.sourcePptxSha256(), "ppt-enhancement", "1.2.0",
            "a".repeat(64), "polish", "balanced", "locked", "doubao_ark", "test-model", true,
            "SUCCESS", List.of(
                slide(1, "cover", "COVER_ACCENT"), slide(2, "catalog", "AGENDA_RAIL"),
                backgroundSlide(3), slide(4, "closing", "CLOSING_ECHO")));
        Path enhanced = temp.resolve("offset-picture-enhanced.pptx");
        var execution = new PptxSlideLocalEnhancer().enhance(source, enhanced, plan, inventory);
        var quality = new PptEnhancementQualityGate().validate(
            source, enhanced, temp.resolve("offset-picture-qa"), inventory);
        assertEquals(1, execution.addedShapesBySlide().get(3),
            "偏移图片不得误走全幅安全no-op");
        assertFalse(quality.pageRenders().get(2).safeNoop());
    }

    @Test
    void repeatedFullBleedTemplateBackgroundDoesNotCreateMediaPagesAndEnhancementStaysAboveIt() throws Exception {
        Path source = temp.resolve("repeated-template-background.pptx");
        createRepeatedTemplateBackgroundDeck(source);
        String sourceHash = PptxBaselineInspector.sha256(source);
        PptxBaselineInspector inspector = new PptxBaselineInspector();
        var inventory = inspector.inspect(source, "small-bear-watercolor-blue-v1");

        assertEquals(List.of("cover", "catalog", "content", "image", "content", "closing"),
            inventory.slides().stream().map(PptxBaselineInspector.SlideInventory::suggestedArchetype).toList(),
            "跨页重复的同SHA全幅图应视为模板背景，只有另含非全幅内容图的普通页才是image");
        assertEquals(0, inventory.slides().get(2).pictureCount(),
            "可信模板背景不得计入内容图片数量");
        assertEquals(1, inventory.slides().get(3).pictureCount(),
            "第4页只应计入模板背景之外的内容截图");
        assertTrue(inventory.slides().get(2).shapeBoxes().stream()
            .anyMatch(PptxBaselineInspector.ShapeBox::trustedTemplateBackground));
        assertTrue(inventory.slides().get(3).shapeBoxes().stream()
            .anyMatch(box -> "PICTURE".equals(box.type()) && !box.trustedTemplateBackground()
                && box.protectedContent()));

        var plan = new PptEnhancementPlan("1.0", sourceHash, "ppt-enhancement", "1.2.0", "a".repeat(64),
            "polish", "balanced", "locked", "doubao_ark", "test-model", true, "SUCCESS", List.of(
                slide(1, "cover", "COVER_ACCENT"),
                slide(2, "catalog", "AGENDA_RAIL"),
                slide(3, "content", "CONTENT_RAIL"),
                backgroundSlide(4),
                slide(5, "content", "CONTENT_RAIL"),
                slide(6, "closing", "CLOSING_ECHO")));
        List<ShapeFingerprint> inheritedBefore = inheritedShapes(source, 4);
        Path enhanced = temp.resolve("repeated-template-background-enhanced.pptx");
        var execution = new PptxSlideLocalEnhancer().enhance(source, enhanced, plan, inventory);
        var quality = new PptEnhancementQualityGate().validate(
            source, enhanced, temp.resolve("repeated-template-background-qa"), inventory);

        assertEquals(inheritedBefore, inheritedShapes(enhanced, 4),
            "模板背景、内容截图、文字及其全部原始层级必须逐项保持");
        try (InputStream input = Files.newInputStream(enhanced); XMLSlideShow show = new XMLSlideShow(input)) {
            XSLFSlide mediaSlide = show.getSlides().get(3);
            int trustedTemplateBackground = indexOfFullSlidePicture(mediaSlide, show);
            int enhancement = indexOfEnhancement(mediaSlide);
            int firstProtectedForeground = firstProtectedForegroundIndex(
                mediaSlide, inventory.slides().get(3));
            assertTrue(trustedTemplateBackground >= 0, "应保留可信模板背景");
            assertTrue(enhancement > trustedTemplateBackground,
                "新背景层允许并且必须位于可信模板背景之上");
            assertTrue(enhancement < firstProtectedForeground,
                "新背景层必须低于全部内容图片和文字前景");
        }
        assertEquals(1, execution.addedShapesBySlide().get(4));
        PptEnhancementQualityGate.PageRender mediaRender = quality.pageRenders().get(3);
        assertTrue(mediaRender.backgroundOnly());
        assertFalse(mediaRender.safeNoop());
        assertTrue(mediaRender.visuallyChanged(), "模板背景之上的新背景层必须产生可见变化");
        assertTrue(mediaRender.foregroundObjectsUnchanged());
        assertTrue(mediaRender.newObjectsBehindInherited());
        assertEquals("PASSED", mediaRender.status());
    }

    @Test
    void trustedTemplateBackgroundThresholdUsesDeckSizeCeilingWithoutOffByOne() throws Exception {
        Path source = temp.resolve("template-background-threshold.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFPictureData fourPageBackground = show.addPicture(
                templateBackgroundPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            XSLFPictureData threePageFullBleedContent = show.addPicture(
                screenshotPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 31; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                if (page <= 4 || page >= 29) {
                    XSLFPictureShape picture = slide.createPicture(
                        page <= 4 ? fourPageBackground : threePageFullBleedContent);
                    picture.setAnchor(new Rectangle(0, 0,
                        show.getPageSize().width, show.getPageSize().height));
                }
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(80, 40, 760, 60));
                title.setText(switch (page) {
                    case 1 -> "答辩封面";
                    case 2 -> "目录";
                    case 31 -> "谢谢大家";
                    default -> "内容页 " + page;
                });
            }
            try (var stream = Files.newOutputStream(source)) {
                show.write(stream);
            }
        }

        var slides = new PptxBaselineInspector().inspect(source, "academic-purple").slides();

        // ceil(31 * 10%) = 4: four repeated full-bleed occurrences are trusted; three are not.
        assertEquals("cover", slides.get(0).suggestedArchetype());
        assertEquals("catalog", slides.get(1).suggestedArchetype());
        assertEquals("content", slides.get(2).suggestedArchetype());
        assertEquals("content", slides.get(3).suggestedArchetype());
        assertEquals(0, slides.get(2).pictureCount());
        assertTrue(slides.get(2).shapeBoxes().stream()
            .anyMatch(PptxBaselineInspector.ShapeBox::trustedTemplateBackground));
        assertEquals("image", slides.get(28).suggestedArchetype());
        assertEquals("image", slides.get(29).suggestedArchetype());
        assertEquals(1, slides.get(28).pictureCount());
        assertTrue(slides.get(28).shapeBoxes().stream()
            .noneMatch(PptxBaselineInspector.ShapeBox::trustedTemplateBackground));
        assertEquals("image", slides.get(30).suggestedArchetype(),
            "非重复全幅内容图即使位于结尾页也必须优先进入媒体保护");
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

    private void createImageAndContentDeck(Path output) throws Exception {
        try (XMLSlideShow show = new XMLSlideShow()) {
            byte[] screenshot = screenshotPng();
            XSLFPictureData pictureData = show.addPicture(screenshot, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 6; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                if (page == 4) {
                    XSLFPictureShape picture = slide.createPicture(pictureData);
                    picture.setAnchor(new Rectangle(0, 0,
                        show.getPageSize().width, show.getPageSize().height));
                }
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(90, 50, 700, 72));
                title.setText(switch (page) {
                    case 1 -> "学术答辩封面";
                    case 2 -> "目录";
                    case 3 -> "系统功能截图";
                    case 4 -> "全幅成果截图";
                    case 5 -> "系统总体方案";
                    default -> "谢谢大家";
                });
                if (page == 3) {
                    XSLFPictureShape picture = slide.createPicture(pictureData);
                    picture.setAnchor(new Rectangle(175, 145, 610, 300));
                    XSLFTextBox caption = slide.createTextBox();
                    caption.setAnchor(new Rectangle(175, 465, 610, 45));
                    caption.setText("图中展示用户端健康数据分析结果");
                }
            }
            try (var stream = Files.newOutputStream(output)) {
                show.write(stream);
            }
        }
    }

    private void createRepeatedTemplateBackgroundDeck(Path output) throws Exception {
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFPictureData templateBackground = show.addPicture(
                templateBackgroundPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            XSLFPictureData contentPicture = show.addPicture(
                screenshotPng(), org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            for (int page = 1; page <= 6; page++) {
                XSLFSlide slide = show.createSlide(show.getSlideMasters().get(0).getLayout(SlideLayout.BLANK));
                var canvas = slide.createAutoShape();
                canvas.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
                canvas.setAnchor(new Rectangle(0, 0,
                    show.getPageSize().width, show.getPageSize().height));
                canvas.setFillColor(new Color(250, 248, 252));
                XSLFPictureShape background = slide.createPicture(templateBackground);
                background.setAnchor(new Rectangle(0, 0,
                    show.getPageSize().width, show.getPageSize().height));
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(90, 50, 700, 72));
                title.setText(switch (page) {
                    case 1 -> "学术答辩封面";
                    case 2 -> "目录";
                    case 4 -> "健康数据展示";
                    case 6 -> "谢谢大家";
                    default -> "系统方案说明";
                });
                if (page == 4) {
                    XSLFPictureShape screenshot = slide.createPicture(contentPicture);
                    screenshot.setAnchor(new Rectangle(180, 145, 600, 300));
                    XSLFTextBox caption = slide.createTextBox();
                    caption.setAnchor(new Rectangle(180, 465, 600, 45));
                    caption.setText("用户健康数据趋势及评估结果");
                }
            }
            try (var stream = Files.newOutputStream(output)) {
                show.write(stream);
            }
        }
    }

    private byte[] screenshotPng() throws Exception {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(235, 242, 252));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(60, 105, 168));
            graphics.fillRoundRect(24, 24, 272, 132, 18, 18);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private byte[] templateBackgroundPng() throws Exception {
        BufferedImage image = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(246, 240, 252));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(225, 213, 242));
            graphics.fillOval(-40, 80, 220, 140);
            graphics.setColor(new Color(211, 231, 249));
            graphics.fillOval(190, -55, 180, 145);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private List<ShapeFingerprint> inheritedShapes(Path pptx, int page) throws Exception {
        try (InputStream input = Files.newInputStream(pptx); XMLSlideShow show = new XMLSlideShow(input)) {
            return show.getSlides().get(page - 1).getShapes().stream()
                .filter(shape -> !isEnhancement(shape))
                .map(this::fingerprint)
                .toList();
        }
    }

    private ShapeFingerprint fingerprint(XSLFShape shape) {
        Rectangle2D anchor = shape.getAnchor();
        String text = shape instanceof XSLFTextShape textShape ? textShape.getText() : "";
        String pictureHash = shape instanceof XSLFPictureShape picture
            ? sha256(picture.getPictureData().getData()) : "";
        return new ShapeFingerprint(
            shape.getShapeId(), shape.getShapeName(), shape.getClass().getName(),
            anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight(), text, pictureHash);
    }

    private List<XSLFShape> enhancements(XSLFSlide slide) {
        return slide.getShapes().stream().filter(this::isEnhancement).toList();
    }

    private boolean isEnhancement(XSLFShape shape) {
        return shape.getShapeName() != null && shape.getShapeName().startsWith("DOKIAI_ENHANCE_");
    }

    private int firstInheritedIndex(XSLFSlide slide) {
        for (int index = 0; index < slide.getShapes().size(); index++) {
            if (!isEnhancement(slide.getShapes().get(index))) return index;
        }
        return slide.getShapes().size();
    }

    private int indexOfEnhancement(XSLFSlide slide) {
        for (int index = 0; index < slide.getShapes().size(); index++) {
            if (isEnhancement(slide.getShapes().get(index))) return index;
        }
        return -1;
    }

    private int indexOfFullSlidePicture(XSLFSlide slide, XMLSlideShow show) {
        for (int index = 0; index < slide.getShapes().size(); index++) {
            XSLFShape shape = slide.getShapes().get(index);
            if (shape instanceof XSLFPictureShape && isFullSlideBackground(shape, show)) return index;
        }
        return -1;
    }

    private int firstProtectedForegroundIndex(XSLFSlide slide, XMLSlideShow show) {
        for (int index = 0; index < slide.getShapes().size(); index++) {
            XSLFShape shape = slide.getShapes().get(index);
            if (isEnhancement(shape)) continue;
            if (shape instanceof XSLFPictureShape && isFullSlideBackground(shape, show)) continue;
            return index;
        }
        return slide.getShapes().size();
    }

    private int firstProtectedForegroundIndex(
        XSLFSlide slide,
        PptxBaselineInspector.SlideInventory baseline
    ) {
        for (int index = 0; index < slide.getShapes().size(); index++) {
            XSLFShape shape = slide.getShapes().get(index);
            if (isEnhancement(shape)) continue;
            boolean protectedContent = baseline.shapeBoxes().stream()
                .anyMatch(box -> box.shapeId() == shape.getShapeId() && box.protectedContent());
            if (protectedContent) return index;
        }
        return slide.getShapes().size();
    }

    private boolean isFullSlideBackground(XSLFShape shape, XMLSlideShow show) {
        Rectangle2D anchor = shape.getAnchor();
        return anchor.getX() <= 1d && anchor.getY() <= 1d
            && anchor.getWidth() >= show.getPageSize().getWidth() - 2d
            && anchor.getHeight() >= show.getPageSize().getHeight() - 2d;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PptEnhancementPlan.SlidePlan slide(int page, String archetype, String recipe) {
        return new PptEnhancementPlan.SlidePlan(page, page, archetype, recipe, "focal", List.of("detail"),
            false, List.of(new PptEnhancementPlan.Addition("shape", "accent", "safe", true, false)));
    }

    private PptEnhancementPlan.SlidePlan backgroundSlide(int page) {
        return new PptEnhancementPlan.SlidePlan(page, page, "image", "IMAGE_BACKGROUND", "background", List.of("background"),
            true, List.of(new PptEnhancementPlan.Addition("background", "background only", "full slide", false, false)));
    }

    private record ShapeFingerprint(
        int id,
        String name,
        String type,
        double x,
        double y,
        double width,
        double height,
        String text,
        String pictureSha256
    ) {}
}
