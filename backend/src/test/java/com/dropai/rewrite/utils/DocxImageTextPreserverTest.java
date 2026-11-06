package com.dropai.rewrite.utils;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocxImageTextPreserverTest {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String ORIGINAL = "图片前面的文字用于说明现场情况，图片后面的文字用于记录核对结果。";
    private static final String NEW_TEXT = "现场情况见图片，核对结果记在图片旁边。";

    @ParameterizedTest
    @ValueSource(strings = {"inline", "separateRun", "anchor", "vml", "alternate", "paragraphAlternate", "object", "table"})
    void refillPreservesGraphicXmlRelationshipsAndMediaAcrossSave(String kind) throws Exception {
        try (XWPFDocument document = fixture(kind)) {
            XWPFParagraph paragraph = target(document, kind);
            String before = withoutText(paragraph.getCTP().xmlText());
            String relations = document.getPackagePart().getRelationships().getRelationship(0).getTargetURI().toString();
            byte[] media = document.getAllPictures().get(0).getData();
            assertThat(DocxImageTextPreserver.containsGraphics(paragraph)).isTrue();
            assertThat(DocxImageTextPreserver.replaceText(paragraph, NEW_TEXT))
                    .as("%s visible=%s xml=%s", kind, paragraph.getText(), paragraph.getCTP().xmlText()).isTrue();
            assertThat(paragraph.getText()).isEqualTo(NEW_TEXT);
            assertThat(withoutText(paragraph.getCTP().xmlText())).isEqualTo(before);
            try (XWPFDocument reopened = roundTrip(document)) {
                assertThat(target(reopened, kind).getText()).isEqualTo(NEW_TEXT);
                assertThat(withoutText(target(reopened, kind).getCTP().xmlText())).isEqualTo(before);
                assertThat(reopened.getAllPictures().get(0).getData()).isEqualTo(media);
                assertThat(reopened.getPackagePart().getRelationships().getRelationship(0).getTargetURI().toString()).isEqualTo(relations);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"break", "tab", "field", "hyperlink", "revision", "textbox", "blank"})
    void unsupportedStructuresKeepEntireOriginalXml(String kind) throws Exception {
        try (XWPFDocument document = fixture("inline")) {
            var paragraph = target(document, "inline");
            var run = paragraph.getRuns().get(0);
            switch (kind) {
                case "break" -> run.addBreak();
                case "tab" -> run.addTab();
                case "field" -> run.getCTR().addNewInstrText().setStringValue("REF bookmark");
                case "hyperlink" -> paragraph.getCTP().addNewHyperlink().addNewR().addNewT().setStringValue("链接");
                case "revision" -> paragraph.getCTP().addNewIns().addNewR().addNewT().setStringValue("修订");
                case "textbox" -> {
                    run.getCTR().set(CTR.Factory.parse("<xml-fragment xmlns:w='" + W + "' xmlns:v='urn:schemas-microsoft-com:vml'>"
                            + "<w:t>正文</w:t><w:pict><v:shape><v:textbox><w:txbxContent><w:p><w:r><w:t>文本框内容</w:t>"
                            + "</w:r></w:p></w:txbxContent></v:textbox></v:shape></w:pict></xml-fragment>"));
                }
                default -> { }
            }
            // Reparse so POI's view includes the XML-only fixtures such as hyperlinks/textboxes.
            try (XWPFDocument loaded = roundTrip(document)) {
                paragraph = target(loaded, "inline");
                String before = paragraph.getCTP().xmlText();
                assertThat(DocxImageTextPreserver.replaceText(paragraph, "blank".equals(kind) ? "" : NEW_TEXT)).isFalse();
                assertThat(paragraph.getCTP().xmlText()).isEqualTo(before);
            }
        }
    }

    @Test
    void unchangedTextIsExactXmlNoOpIncludingComplexContent() throws Exception {
        try (XWPFDocument document = fixture("inline")) {
            var paragraph = target(document, "inline");
            paragraph.getRuns().get(0).addBreak();
            String before = paragraph.getCTP().xmlText();
            assertThat(DocxImageTextPreserver.replaceText(paragraph, paragraph.getText().trim())).isTrue();
            assertThat(paragraph.getCTP().xmlText()).isEqualTo(before);
        }
    }

    @Test
    void imageBetweenTextStaysAtClauseBoundaryInsteadOfSplittingAWord() throws Exception {
        try (XWPFDocument document = fixture("inline")) {
            var paragraph = target(document, "inline");
            assertThat(DocxImageTextPreserver.replaceText(paragraph, NEW_TEXT)).isTrue();
            var texts = paragraph.getRuns().get(0).getCTR().getTList();
            assertThat(texts.get(0).getStringValue()).isEqualTo("现场情况见图片，");
            assertThat(texts.get(1).getStringValue()).isEqualTo("核对结果记在图片旁边。");
        }
    }

    @Test
    void decimalPointIsNotUsedAsAnImageBoundary() throws Exception {
        try (XWPFDocument document = fixture("inline")) {
            var paragraph = target(document, "inline");
            String replacement = "测量长度为3.14米，记录已核验。";
            assertThat(DocxImageTextPreserver.replaceText(paragraph, replacement)).isTrue();
            assertThat(paragraph.getText()).isEqualTo(replacement);
            assertThat(paragraph.getRuns().get(0).getCTR().getTArray(0).getStringValue()).contains("3.14米，");
        }
    }

    @Test
    void preservesWhitespaceStylesAndUnicodeWithoutSplittingSurrogates() throws Exception {
        try (XWPFDocument document = fixture("separateRun")) {
            var paragraph = target(document, "separateRun");
            var first = paragraph.getRuns().get(0);
            first.setBold(true);
            first.setText("  " + first.text(), 0);
            var last = paragraph.getRuns().get(2);
            last.setItalic(true);
            last.setText(last.text() + "  ", 0);
            String replacement = "𠮷𠮷𠮷新的说明文字𠮷𠮷𠮷";
            assertThat(DocxImageTextPreserver.replaceText(paragraph, replacement)).isTrue();
            assertThat(paragraph.getText()).isEqualTo("  " + replacement + "  ");
            assertThat(first.isBold()).isTrue();
            assertThat(last.isItalic()).isTrue();
            for (var run : paragraph.getRuns()) {
                String text = run.text();
                if (!text.isEmpty()) {
                    assertThat(Character.isLowSurrogate(text.charAt(0))).isFalse();
                    assertThat(Character.isHighSurrogate(text.charAt(text.length() - 1))).isFalse();
                }
            }
        }
    }

    @Test
    void optionalVisualFixture() throws Exception {
        String directory = System.getProperty("dropai.test.images.outputDirectory");
        org.junit.jupiter.api.Assumptions.assumeTrue(directory != null && !directory.isBlank(),
                "Set dropai.test.images.outputDirectory to generate optional visual fixtures");
        Path output = Path.of(directory);
        Files.createDirectories(output);
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("图文回填验证");
            addPictureParagraph(document.createParagraph(), "inline");
            var table = document.createTable(2, 2);
            for (int i = 0; i < 2; i++) {
                addPictureParagraph(table.getRow(0).getCell(i).getParagraphs().get(0), "inline");
                table.getRow(1).getCell(i).setText("图片来源说明保持不变");
            }
            addPictureParagraph(document.createParagraph(), "anchor");
            addPictureParagraph(document.createParagraph(), "vml");
            try (var stream = Files.newOutputStream(output.resolve("before.docx"))) { document.write(stream); }
            for (var p : document.getParagraphs()) {
                if (DocxImageTextPreserver.containsGraphics(p)) assertThat(DocxImageTextPreserver.replaceText(p, NEW_TEXT)).isTrue();
            }
            for (var cell : table.getRow(0).getTableCells())
                assertThat(DocxImageTextPreserver.replaceText(cell.getParagraphs().get(0), NEW_TEXT)).isTrue();
            try (var stream = Files.newOutputStream(output.resolve("after.docx"))) { document.write(stream); }
        }
    }

    private static XWPFDocument fixture(String kind) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = "table".equals(kind)
                    ? document.createTable(1, 1).getRow(0).getCell(0).getParagraphs().get(0)
                    : document.createParagraph();
            addPictureParagraph(paragraph, kind);
            return roundTrip(document);
        }
    }

    private static XWPFParagraph target(XWPFDocument document, String kind) {
        return "table".equals(kind) ? document.getTables().get(0).getRow(0).getCell(0).getParagraphs().get(0)
                : document.getParagraphs().get(0);
    }

    private static void addPictureParagraph(XWPFParagraph paragraph, String kind) throws Exception {
        var run = paragraph.createRun();
        run.setText(ORIGINAL.substring(0, 16));
        if ("separateRun".equals(kind)) run = paragraph.createRun();
        var picture = run.addPicture(new ByteArrayInputStream(png()), Document.PICTURE_TYPE_PNG,
                "picture.png", Units.toEMU(110), Units.toEMU(45));
        if ("anchor".equals(kind)) {
            var drawing = run.getCTR().getDrawingArray(0);
            var inline = drawing.getInlineArray(0);
            var anchor = drawing.addNewAnchor();
            anchor.setSimplePos2(false);
            anchor.setRelativeHeight(0);
            anchor.setBehindDoc(false);
            anchor.setLocked(false);
            anchor.setLayoutInCell(true);
            anchor.setAllowOverlap(false);
            anchor.addNewSimplePos().setX(0);
            anchor.getSimplePos().setY(0);
            anchor.addNewPositionH().setRelativeFrom(org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH.COLUMN);
            anchor.getPositionH().setPosOffset(0);
            anchor.addNewPositionV().setRelativeFrom(org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV.PARAGRAPH);
            anchor.getPositionV().setPosOffset(0);
            anchor.setExtent(inline.getExtent());
            // poi-ooxml-lite omits the typed CTWrapTopBottom class; retain the real OOXML.
            try (var cursor = anchor.newCursor()) {
                cursor.toEndToken();
                cursor.beginElement("wrapTopAndBottom", "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing");
            }
            anchor.setDocPr(inline.getDocPr());
            anchor.setGraphic(inline.getGraphic());
            drawing.removeInline(0);
        } else if (java.util.Set.of("vml", "alternate", "paragraphAlternate", "object").contains(kind)) {
            String id = picture.getCTPicture().getBlipFill().getBlip().getEmbed();
            String shape = "<w:pict><v:shape id='fixture' style='width:110pt;height:45pt'><v:imagedata r:id='"
                    + id + "'/></v:shape></w:pict>";
            String graphic = "object".equals(kind) ? shape.replace("w:pict", "w:object") : shape;
            if (kind.contains("lternate")) graphic = "<mc:AlternateContent><mc:Choice Requires='v'>" + shape
                    + "</mc:Choice><mc:Fallback>" + shape + "</mc:Fallback></mc:AlternateContent>";
            String namespaces = " xmlns:w='" + W + "' xmlns:r='" + R + "' xmlns:mc='" + MC
                    + "' xmlns:v='urn:schemas-microsoft-com:vml'";
            if ("paragraphAlternate".equals(kind)) {
                paragraph.getCTP().set(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP.Factory.parse(
                        "<xml-fragment" + namespaces + "><w:r><w:t>" + ORIGINAL.substring(0, 16) + "</w:t></w:r>"
                                + graphic + "<w:r><w:t>" + ORIGINAL.substring(16) + "</w:t></w:r></xml-fragment>"));
                return;
            }
            run.getCTR().set(CTR.Factory.parse("<xml-fragment" + namespaces + "><w:t>" + ORIGINAL.substring(0, 16)
                    + "</w:t>" + graphic + "</xml-fragment>"));
        }
        if ("separateRun".equals(kind)) run = paragraph.createRun();
        run.setText(ORIGINAL.substring(16));
    }

    private static byte[] png() throws Exception {
        var image = new BufferedImage(220, 90, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(55, 130, 180));
        graphics.fillRect(0, 0, 220, 90);
        graphics.setColor(Color.WHITE);
        graphics.drawString("PICTURE PRESERVATION", 30, 45);
        graphics.dispose();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static XWPFDocument roundTrip(XWPFDocument document) throws Exception {
        var bytes = new ByteArrayOutputStream();
        document.write(bytes);
        return new XWPFDocument(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static String withoutText(String xml) {
        return xml.replaceAll("(?s)<(?:[\\w]+:)?t(?:\\s[^>]*)?>.*?</(?:[\\w]+:)?t>|<(?:[\\w]+:)?t(?:\\s[^>]*)?/>", "");
    }
}
