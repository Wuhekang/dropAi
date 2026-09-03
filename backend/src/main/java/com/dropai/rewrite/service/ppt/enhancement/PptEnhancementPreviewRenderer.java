package com.dropai.rewrite.service.ppt.enhancement;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PptEnhancementPreviewRenderer {
    private static final int SLIDE_WIDTH = 480;
    private static final int COLUMNS = 4;
    private static final int ROWS = 3;
    private static final int GAP = 14;

    public PreviewBundle render(Path source, Path directory) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Files.createDirectories(directory);
        List<Path> slideRenders = new ArrayList<>();
        try (InputStream input = Files.newInputStream(source); XMLSlideShow show = new XMLSlideShow(input)) {
            for (int index = 0; index < show.getSlides().size(); index++) {
                Path output = directory.resolve(String.format("slide-%03d.png", index + 1));
                renderSlide(show.getSlides().get(index), show.getPageSize(), output);
                slideRenders.add(output);
            }
        }
        List<Path> contactSheets = contactSheets(slideRenders, directory.resolve("contact-sheets"));
        return new PreviewBundle(List.copyOf(slideRenders), List.copyOf(contactSheets), "Apache POI Java2D/480px");
    }

    private void renderSlide(XSLFSlide slide, Dimension pageSize, Path output) throws Exception {
        int height = Math.max(1, (int) Math.round(SLIDE_WIDTH * pageSize.getHeight() / pageSize.getWidth()));
        BufferedImage image = new BufferedImage(SLIDE_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            AffineTransform transform = graphics.getTransform();
            transform.scale(image.getWidth() / pageSize.getWidth(), image.getHeight() / pageSize.getHeight());
            graphics.setTransform(transform);
            slide.draw(graphics);
        } finally {
            graphics.dispose();
        }
        if (!ImageIO.write(image, "png", output.toFile())) throw new IllegalStateException("基础PPT预览渲染失败");
    }

    private List<Path> contactSheets(List<Path> slides, Path directory) throws Exception {
        Files.createDirectories(directory);
        List<Path> result = new ArrayList<>();
        int perSheet = COLUMNS * ROWS;
        for (int offset = 0, sheet = 1; offset < slides.size(); offset += perSheet, sheet++) {
            BufferedImage first = ImageIO.read(slides.get(offset).toFile());
            int cellWidth = first.getWidth();
            int cellHeight = first.getHeight();
            BufferedImage contact = new BufferedImage(
                COLUMNS * cellWidth + (COLUMNS + 1) * GAP,
                ROWS * cellHeight + (ROWS + 1) * GAP,
                BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = contact.createGraphics();
            try {
                graphics.setColor(new Color(242, 244, 249));
                graphics.fillRect(0, 0, contact.getWidth(), contact.getHeight());
                graphics.setFont(new Font("SansSerif", Font.BOLD, 16));
                for (int item = 0; item < perSheet && offset + item < slides.size(); item++) {
                    BufferedImage slide = ImageIO.read(slides.get(offset + item).toFile());
                    int x = GAP + (item % COLUMNS) * (cellWidth + GAP);
                    int y = GAP + (item / COLUMNS) * (cellHeight + GAP);
                    graphics.drawImage(slide, x, y, null);
                    graphics.setColor(new Color(32, 36, 58, 210));
                    graphics.fillRoundRect(x + 6, y + 6, 42, 24, 8, 8);
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(String.valueOf(offset + item + 1), x + 16, y + 24);
                }
            } finally {
                graphics.dispose();
            }
            Path output = directory.resolve(String.format("contact-%02d.png", sheet));
            if (!ImageIO.write(contact, "png", output.toFile())) throw new IllegalStateException("PPT联系表渲染失败");
            result.add(output);
        }
        return result;
    }

    public record PreviewBundle(List<Path> slideRenders, List<Path> contactSheets, String renderer) {}
}
