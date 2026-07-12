package com.dropai.rewrite.modules.drawingEngine;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

class DrawingPreviewRenderer {
    private static final List<String> FONTS = List.of("Noto Sans CJK SC", "Microsoft YaHei", "SimHei", "Dialog");

    DrawingArtifact svg(String fileName, DrawingEngine.Canvas canvas) {
        return new DrawingArtifact(fileName, canvas.svg(false).getBytes(StandardCharsets.UTF_8), "image/svg+xml");
    }

    DrawingArtifact png(String fileName, DrawingEngine.Canvas canvas) {
        return new DrawingArtifact(fileName, render(canvas), "image/png");
    }

    private byte[] render(DrawingEngine.Canvas canvas) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = new BufferedImage(1680, 1180, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(24, 34, 48));
            graphics.setStroke(new BasicStroke(2));
            for (DrawingEngine.Shape shape : canvas.shapes) draw(graphics, shape, 2);
            graphics.dispose();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("DRAWING_PREVIEW_RENDER_FAILED: " + exception.getMessage(), exception);
        }
    }

    private void draw(Graphics2D graphics, DrawingEngine.Shape shape, double scale) {
        if ("LINE".equals(shape.type())) {
            graphics.drawLine((int) (shape.x1() * scale), (int) ((590 - shape.y1()) * scale),
                    (int) (shape.x2() * scale), (int) ((590 - shape.y2()) * scale));
        } else if ("CIRCLE".equals(shape.type())) {
            int x = (int) (shape.x1() * scale);
            int y = (int) ((590 - shape.y1()) * scale);
            int r = (int) (shape.size() * scale);
            graphics.drawOval(x - r, y - r, 2 * r, 2 * r);
        } else if ("FILL_RECT".equals(shape.type())) {
            graphics.fillRect((int) (shape.x1() * scale), (int) ((590 - shape.y2()) * scale),
                    (int) ((shape.x2() - shape.x1()) * scale), (int) ((shape.y2() - shape.y1()) * scale));
        } else {
            graphics.setFont(font().deriveFont(Math.max(12f, (float) (shape.size() * scale))));
            graphics.drawString(shape.text(), (int) (shape.x1() * scale), (int) ((590 - shape.y1()) * scale));
        }
    }

    private Font font() {
        for (String name : FONTS) {
            Font font = new Font(name, Font.PLAIN, 14);
            if (font.canDisplay('\u4e2d')) return font;
        }
        return new Font("Dialog", Font.PLAIN, 14);
    }
}
