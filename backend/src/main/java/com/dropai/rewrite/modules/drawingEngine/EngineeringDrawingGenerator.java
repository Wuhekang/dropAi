package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EngineeringDrawingGenerator {
    public void drawPartStandardBlock(DrawingEngine.Canvas c, DesignProject.Component part,
                                      MechanicalFeatureLibrary.FeatureSet features) {
        drawViewLabels(c);
        drawStandardAndDatum(c, 42, 438);
        drawTechnicalRequirements(c, part, features, 535, 176);
        drawBom(c, part, 42, 62);
    }

    private void drawViewLabels(DrawingEngine.Canvas c) {
        c.text("TEXT", 92, 402, 3.2, "Front View");
        c.text("TEXT", 92, 160, 3.2, "Top View");
        c.text("TEXT", 392, 160, 3.2, "Side View");
    }

    private void drawStandardAndDatum(DrawingEngine.Canvas c, double x, double y) {
        c.rect("TABLE", x, y - 68, 180, 68);
        c.text("TABLE", x + 8, y - 14, 3.6, "图纸标准: GB/T / ISO");
        c.text("TABLE", x + 8, y - 30, 3.2, "Datum A: 基准A 安装面");
        c.text("TABLE", x + 8, y - 46, 3.2, "Datum B: 导轨/轴孔中心");
        c.text("TABLE", x + 8, y - 62, 3.2, "General tolerance: 未注尺寸公差 GB/T 1804-m");
    }

    private void drawTechnicalRequirements(DrawingEngine.Canvas c, DesignProject.Component part,
                                           MechanicalFeatureLibrary.FeatureSet features, double x, double y) {
        List<String> items = List.of(
                "结构特征: " + safe(features == null ? "" : features.family(), "主要机械零件"),
                "材料: " + safe(part == null ? "" : part.getMaterial(), "Q235B"),
                "表面处理: 去毛刺，锐边倒钝，必要表面防锈处理",
                "未注尺寸公差按 GB/T 1804-m 执行",
                "基准A为关键安装面，基准B为轴孔或导轨中心线"
        );
        c.text("TEXT", x, y + 68, 4, "技术要求");
        int row = 0;
        for (String item : items) {
            c.text("TEXT", x, y + 50 - row * 14, 2.8, (row + 1) + ". " + trim(item, 32));
            row++;
        }
    }

    private void drawBom(DrawingEngine.Canvas c, DesignProject.Component part, double x, double y) {
        c.rect("TABLE", x, y, 285, 54);
        c.text("TABLE", x + 8, y + 38, 3.4, "BOM");
        c.text("TABLE", x + 8, y + 22, 3.0, "No.  Name              Material  Qty");
        c.text("TABLE", x + 8, y + 8, 3.0, "01   "
                + trim(safe(part == null ? "" : part.getName(), "Part"), 16)
                + "  " + trim(safe(part == null ? "" : part.getMaterial(), "Q235B"), 8)
                + "  " + Math.max(1, part == null ? 1 : part.getQuantity()));
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trim(String value, int length) {
        return value == null ? "" : value.length() > length ? value.substring(0, length) : value;
    }
}
