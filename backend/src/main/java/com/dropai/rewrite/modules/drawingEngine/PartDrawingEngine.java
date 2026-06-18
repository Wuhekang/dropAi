package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PartDrawingEngine {
    private final MechanicalFeatureLibrary featureLibrary = new MechanicalFeatureLibrary();
    private final DimensionEngine dimensionEngine = new DimensionEngine();
    private final AnnotationEngine annotationEngine = new AnnotationEngine();
    private final ToleranceGenerator toleranceGenerator = new ToleranceGenerator();

    public List<DrawingArtifact> drawPartDrawing(DesignProject project) {
        List<DesignProject.Component> targets = featureLibrary.selectMajorDrawingTargets(project);
        if (targets.size() < 3) {
            targets = project.getComponents().stream().filter(DesignProject.Component::isKeyPart).limit(5).toList();
        }
        if (targets.size() < 3) {
            throw new IllegalStateException("主要零件图生成失败：关键机构数量不足，至少需要3个可出图零件。");
        }

        List<DrawingArtifact> result = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            DesignProject.Component part = targets.get(i);
            MechanicalFeatureLibrary.FeatureSet features = featureLibrary.resolve(part);
            DrawingEngine.Canvas canvas = new DrawingEngine.Canvas(project.getProjectTitle(), drawingName(features, part), "LJ-%02d".formatted(i + 1));
            frame(canvas);
            title(canvas, project, part, features, i + 1);
            drawPartViews(canvas, part, features);
            dimensionEngine.drawPartDimensions(canvas, part);
            annotationEngine.drawPartFeatureList(canvas, features, 535, 410);
            annotationEngine.drawMaterialBlock(canvas, part, 535, 275);
            toleranceGenerator.drawDatumAndGdt(canvas, 535, 235);
            toleranceGenerator.drawToleranceBlock(canvas, 535, 92, toleranceGenerator.partRequirements(part, features));
            result.add(new DrawingArtifact("part_%02d.dxf".formatted(i + 1), canvas.dxf().getBytes(StandardCharsets.UTF_8), "application/dxf"));
        }
        return result;
    }

    private String drawingName(MechanicalFeatureLibrary.FeatureSet features, DesignProject.Component part) {
        return switch (features.family()) {
            case "履带机构" -> "履带机构装配图";
            case "机架结构" -> "机架结构图";
            case "清扫刷组件" -> "清扫刷组件图";
            case "磁吸附模块" -> "磁吸附模块图";
            case "检测支架" -> "检测支架图";
            default -> trim(part.getName(), 12) + "零件图";
        };
    }

    private void drawPartViews(DrawingEngine.Canvas c, DesignProject.Component part, MechanicalFeatureLibrary.FeatureSet features) {
        c.text("TEXT", 160, 420, 4, "主视图");
        c.text("TEXT", 160, 178, 4, "俯视图/侧视图");
        switch (features.family()) {
            case "履带机构" -> drawTrackAssembly(c);
            case "机架结构" -> drawFramePart(c);
            case "清扫刷组件" -> drawBrushAssembly(c);
            case "磁吸附模块" -> drawMagnetModule(c);
            case "检测支架" -> drawSensorBracket(c);
            default -> drawGenericPart(c, part);
        }
    }

    private void drawTrackAssembly(DrawingEngine.Canvas c) {
        roundedTrack(c, 165, 285, 320, 95);
        for (int i = 0; i < 13; i++) {
            double x = 178 + i * 23;
            c.rect("STRUCTURE", x, 373, 15, 8);
            c.rect("STRUCTURE", x, 287, 15, 8);
        }
        wheel(c, 205, 332, 38);
        wheel(c, 445, 332, 38);
        for (int i = 0; i < 3; i++) wheel(c, 270 + i * 55, 312, 18);
        c.line("CENTER", 155, 332, 500, 332);
        c.rect("OUTLINE", 190, 105, 285, 52);
        for (int i = 0; i < 11; i++) c.line("STRUCTURE", 198 + i * 24, 105, 198 + i * 24, 157);
        dimensionEngine.drawHolePattern(c, 225, 118, 200, 25, 4, 1);
    }

    private void drawFramePart(DrawingEngine.Canvas c) {
        c.rect("OUTLINE", 170, 280, 315, 105);
        c.rect("STRUCTURE", 205, 306, 245, 52);
        c.line("STRUCTURE", 170, 280, 205, 306);
        c.line("STRUCTURE", 485, 280, 450, 306);
        c.line("STRUCTURE", 170, 385, 205, 358);
        c.line("STRUCTURE", 485, 385, 450, 358);
        for (double x : List.of(205d, 450d)) {
            c.rect("STRUCTURE", x - 10, 278, 20, 110);
            c.circle("CENTER", x, 305, 4.5);
            c.circle("CENTER", x, 360, 4.5);
        }
        c.rect("OUTLINE", 185, 105, 285, 60);
        for (int i = 0; i < 5; i++) c.circle("CENTER", 220 + i * 55, 135, 4);
        c.line("STRUCTURE", 185, 105, 470, 165);
        c.line("STRUCTURE", 185, 165, 470, 105);
    }

    private void drawBrushAssembly(DrawingEngine.Canvas c) {
        double cx = 330, cy = 333;
        c.circle("OUTLINE", cx, cy, 62);
        c.circle("STRUCTURE", cx, cy, 20);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            c.line("FUNCTION", cx + Math.cos(a) * 22, cy + Math.sin(a) * 22, cx + Math.cos(a) * 72, cy + Math.sin(a) * 72);
        }
        c.rect("STRUCTURE", 292, 240, 76, 38);
        c.circle("CENTER", 330, 259, 12);
        c.rect("OUTLINE", 220, 112, 220, 42);
        c.circle("CENTER", 330, 133, 18);
        dimensionEngine.drawHolePattern(c, 245, 120, 170, 25, 4, 1);
    }

    private void drawMagnetModule(DrawingEngine.Canvas c) {
        c.rect("OUTLINE", 185, 292, 290, 76);
        for (int i = 0; i < 5; i++) c.rect("FUNCTION", 205 + i * 52, 310, 36, 40);
        dimensionEngine.drawHolePattern(c, 195, 300, 270, 58, 4, 2);
        c.rect("OUTLINE", 210, 115, 240, 38);
        for (int i = 0; i < 5; i++) c.rect("FUNCTION", 225 + i * 43, 122, 26, 24);
        c.text("ANNOTATION", 230, 265, 3, "磁块阵列");
    }

    private void drawSensorBracket(DrawingEngine.Canvas c) {
        c.rect("OUTLINE", 205, 285, 250, 90);
        c.rect("STRUCTURE", 225, 305, 210, 18);
        c.rect("STRUCTURE", 245, 330, 72, 32);
        c.rect("STRUCTURE", 335, 330, 72, 32);
        for (int i = 0; i < 4; i++) c.circle("CENTER", 240 + i * 55, 314, 3);
        c.line("CENTER", 205, 330, 455, 330);
        c.rect("OUTLINE", 230, 105, 205, 55);
        c.rect("STRUCTURE", 250, 120, 165, 14);
        c.rect("HIDDEN", 280, 138, 60, 12);
        c.text("ANNOTATION", 290, 170, 3, "调节槽");
    }

    private void drawGenericPart(DrawingEngine.Canvas c, DesignProject.Component part) {
        c.rect("OUTLINE", 180, 285, 300, 95);
        c.rect("STRUCTURE", 210, 310, 240, 45);
        c.line("CENTER", 180, 332, 480, 332);
        dimensionEngine.drawHolePattern(c, 215, 300, 230, 65, 3, 2);
        c.rect("OUTLINE", 205, 110, 250, 48);
        c.text("ANNOTATION", 230, 170, 3, trim(part.getName(), 16));
    }

    private void roundedTrack(DrawingEngine.Canvas c, double x, double y, double w, double h) {
        c.line("OUTLINE", x + h / 2, y, x + w - h / 2, y);
        c.line("OUTLINE", x + h / 2, y + h, x + w - h / 2, y + h);
        c.circle("OUTLINE", x + h / 2, y + h / 2, h / 2);
        c.circle("OUTLINE", x + w - h / 2, y + h / 2, h / 2);
    }

    private void wheel(DrawingEngine.Canvas c, double x, double y, double r) {
        c.circle("STRUCTURE", x, y, r);
        c.circle("CENTER", x, y, r * .42);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            c.line("CENTER", x, y, x + Math.cos(a) * r * .82, y + Math.sin(a) * r * .82);
        }
        c.rect("HIDDEN", x - 4, y - r * .55, 8, r * 1.1);
    }

    private void frame(DrawingEngine.Canvas c) {
        c.rect("FRAME", 20, 20, 800, 550);
        c.rect("FRAME", 30, 30, 780, 530);
    }

    private void title(DrawingEngine.Canvas c, DesignProject project, DesignProject.Component part,
                       MechanicalFeatureLibrary.FeatureSet features, int index) {
        c.rect("TITLE", 570, 30, 240, 75);
        c.line("TITLE", 570, 55, 810, 55);
        c.line("TITLE", 700, 30, 700, 105);
        c.text("TEXT", 580, 83, 4.6, c.name);
        c.text("TEXT", 710, 83, 3.8, "图号 " + c.no);
        c.text("TEXT", 580, 67, 3.0, "零件序号 " + index + "：" + trim(part.getName(), 13));
        c.text("TEXT", 580, 42, 3.2, trim(project.getProjectTitle(), 16));
        c.text("TEXT", 710, 42, 3.2, "比例 1:5");
        c.text("TEXT", 710, 66, 3.0, features.family());
    }

    private String trim(String value, int length) {
        if (value == null) return "";
        String normalized = value.toLowerCase(Locale.ROOT).contains("component") ? "主要零件" : value;
        return normalized.length() > length ? normalized.substring(0, length) : normalized;
    }
}
