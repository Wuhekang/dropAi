package com.dropai.rewrite.service.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChartRenderService {
    private static final Color[] COLORS = {
            new Color(83, 125, 255), new Color(255, 109, 166), new Color(61, 189, 166), new Color(245, 166, 35)
    };
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChartRenderService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void renderProjectCharts(String projectId, Path root) {
        for (Map<String, Object> chart : WritingJdbc.list(jdbcTemplate, "SELECT * FROM writing_chart WHERE project_id=? ORDER BY chart_no", projectId)) {
            try {
                Path image = root.resolve("chart-" + chart.get("chart_no") + ".png");
                Files.createDirectories(image.getParent());
                renderChart(chart, image);
                jdbcTemplate.update("UPDATE writing_chart SET image_path=?, data_json=?, updated_at=? WHERE id=?",
                        image.toString(), defaultChartDataJson(), LocalDateTime.now(), chart.get("id"));
            } catch (Exception exception) {
                throw new IllegalStateException("图表渲染失败：" + chart.get("chart_no") + " " + exception.getMessage(), exception);
            }
        }
    }

    private void renderChart(Map<String, Object> chart, Path output) throws Exception {
        int width = 1100;
        int height = 620;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(34, 40, 58));
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, 28));
        g.drawString(WritingJdbc.text(chart.get("title")), 60, 56);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.setColor(new Color(100, 108, 128));
        g.drawString("数据根据研究情境构建，仅用于趋势分析", 60, 88);

        int left = 90;
        int top = 130;
        int chartWidth = 920;
        int chartHeight = 360;
        g.setColor(new Color(226, 232, 245));
        for (int i = 0; i <= 5; i++) {
            int y = top + i * chartHeight / 5;
            g.drawLine(left, y, left + chartWidth, y);
        }
        g.setColor(new Color(82, 93, 121));
        g.drawLine(left, top, left, top + chartHeight);
        g.drawLine(left, top + chartHeight, left + chartWidth, top + chartHeight);

        List<String> categories = List.of("2022", "2023", "2024", "2025", "2026");
        List<Map<String, Object>> series = WritingJdbc.list(jdbcTemplate,
                "SELECT * FROM writing_chart_series WHERE chart_id=? ORDER BY sort_order", chart.get("id"));
        if (series.isEmpty()) series = List.of(Map.of("series_name", "趋势值", "chart_type", "BAR"));
        List<List<Integer>> values = defaultValues(series.size());
        int groupWidth = chartWidth / categories.size();
        int barWidth = Math.max(18, groupWidth / (series.size() + 2));
        for (int s = 0; s < series.size(); s++) {
            String type = WritingJdbc.text(series.get(s).get("chart_type")).toUpperCase();
            List<Integer> data = values.get(s);
            g.setColor(COLORS[s % COLORS.length]);
            if (type.contains("LINE")) {
                Path2D path = new Path2D.Double();
                for (int i = 0; i < data.size(); i++) {
                    int x = left + groupWidth * i + groupWidth / 2;
                    int y = top + chartHeight - data.get(i) * chartHeight / 100;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    g.fillOval(x - 5, y - 5, 10, 10);
                }
                g.setStroke(new BasicStroke(4f));
                g.draw(path);
                g.setStroke(new BasicStroke(1f));
            } else {
                for (int i = 0; i < data.size(); i++) {
                    int x = left + groupWidth * i + 18 + s * barWidth;
                    int barHeight = data.get(i) * chartHeight / 100;
                    g.fillRoundRect(x, top + chartHeight - barHeight, barWidth - 4, barHeight, 6, 6);
                }
            }
            jdbcTemplate.update("UPDATE writing_chart_series SET data_json=?, updated_at=? WHERE id=?",
                    objectMapper.writeValueAsString(data), LocalDateTime.now(), series.get(s).get("id"));
        }
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 18));
        g.setColor(new Color(80, 88, 105));
        for (int i = 0; i < categories.size(); i++) {
            g.drawString(categories.get(i), left + groupWidth * i + groupWidth / 2 - 20, top + chartHeight + 34);
        }
        int legendX = 70;
        int legendY = 540;
        for (int s = 0; s < series.size(); s++) {
            g.setColor(COLORS[s % COLORS.length]);
            g.fillRoundRect(legendX, legendY - 14, 26, 14, 4, 4);
            g.setColor(new Color(54, 62, 82));
            g.drawString(WritingJdbc.text(series.get(s).get("series_name")), legendX + 36, legendY);
            legendX += 180;
        }
        g.dispose();
        ImageIO.write(image, "png", output.toFile());
    }

    private List<List<Integer>> defaultValues(int count) {
        List<List<Integer>> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(List.of(35 + i * 8, 42 + i * 6, 55 + i * 5, 66 + i * 4, 76 + i * 3));
        }
        return values;
    }

    private String defaultChartDataJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "categories", List.of("2022", "2023", "2024", "2025", "2026"),
                "note", "数据根据研究情境构建，仅用于趋势分析"
        ));
    }
}
