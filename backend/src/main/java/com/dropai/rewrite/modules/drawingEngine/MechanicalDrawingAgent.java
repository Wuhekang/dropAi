package com.dropai.rewrite.modules.drawingEngine;

import com.dropai.rewrite.modules.model.DesignProject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class MechanicalDrawingAgent {
    DrawingRepairPlan audit(DesignProject project, DrawingEngine.Canvas candidate) {
        String deviceType = deviceType(project);
        List<String> failed = new ArrayList<>();
        int score = 100;

        if (project.getDrawingPlan() == null) {
            failed.add("drawing_plan_missing");
            score -= 40;
        } else {
            score -= viewPenalty(project.getDrawingPlan().getMainView(), "main_view", failed);
            score -= viewPenalty(project.getDrawingPlan().getTopView(), "top_view", failed);
            score -= viewPenalty(project.getDrawingPlan().getSideView(), "side_view", failed);
        }

        ShapeStats stats = stats(candidate);
        if (stats.viewTitleCount < 3) {
            failed.add("three_views_missing");
            score -= 25;
        }
        if (stats.centerLines + stats.hiddenLines + stats.sectionLines < 1) {
            failed.add("no_center_hidden_or_section_expression");
            score -= 15;
        }
        if (stats.debugText) {
            failed.add("debug_or_projection_text_present");
            score -= 15;
        }
        if (stats.onlyOutlineProjection) {
            failed.add("only_outline_projection");
            score -= 25;
        }
        if ("settling_chamber".equals(deviceType)) {
            List<String> missing = missingSettlingFeatures(candidate);
            if (!missing.isEmpty()) {
                failed.add("settling_chamber_semantic_features_missing:" + String.join(",", missing));
                score -= Math.min(35, missing.size() * 5);
            }
        }

        int qualityScore = Math.max(0, Math.min(100, score));
        return new DrawingRepairPlan(
                qualityScore,
                failed,
                qualityScore < 80,
                mainViewPlan(deviceType),
                topViewPlan(deviceType),
                sideViewPlan(deviceType),
                dimensionPlan(deviceType),
                annotationPlan(deviceType),
                hiddenLinePlan(deviceType),
                centerLinePlan(deviceType),
                sectionSuggestion(deviceType)
        );
    }

    String deviceType(DesignProject project) {
        String text = projectText(project);
        if (has(text, "\u91cd\u529b\u6c89\u964d", "\u6c89\u964d\u5ba4", "\u9664\u5c18", "settling")) return "settling_chamber";
        if (has(text, "\u722c\u58c1", "\u5c65\u5e26", "\u6cb9\u7f50", "\u5438\u9644", "crawler", "track")) return "crawler_robot";
        if (has(text, "\u8f93\u9001\u673a", "\u8f93\u9001\u5e26", "\u6eda\u7b52", "conveyor")) return "conveyor";
        if (has(text, "\u673a\u68b0\u624b", "\u673a\u68b0\u81c2", "\u5939\u722a", "manipulator")) return "manipulator";
        return "generic";
    }

    private int viewPenalty(DesignProject.DrawingViewPlan view, String name, List<String> failed) {
        if (view == null) {
            failed.add(name + "_missing");
            return 30;
        }
        int penalty = 0;
        if (view.getVisibleParts().size() < 5) {
            failed.add(name + "_semantic_structure_count_lt_5");
            penalty += 18;
        }
        if (view.getDimensions().isEmpty()) {
            failed.add(name + "_dimension_plan_missing");
            penalty += 8;
        }
        return penalty;
    }

    private ShapeStats stats(DrawingEngine.Canvas canvas) {
        int center = 0;
        int hidden = 0;
        int section = 0;
        int body = 0;
        int structure = 0;
        int interfaceCount = 0;
        int viewTitles = 0;
        boolean debug = false;
        for (DrawingEngine.Shape shape : canvas.shapes) {
            String layer = safe(shape.layer()).toUpperCase(Locale.ROOT);
            String text = safe(shape.text()).toLowerCase(Locale.ROOT);
            if ("CENTER".equals(layer)) center++;
            if ("HIDDEN".equals(layer)) hidden++;
            if ("SECTION".equals(layer) || text.contains("\u5256")) section++;
            if ("BODY".equals(layer)) body++;
            if ("STRUCTURE".equals(layer) || "SUPPORT".equals(layer)) structure++;
            if ("INTERFACE".equals(layer)) interfaceCount++;
            if (text.contains("\u4e3b\u89c6") || text.contains("\u4fef\u89c6") || text.contains("\u4fa7\u89c6")) viewTitles++;
            if (text.contains("component envelope") || text.contains("checked") || text.contains("source:") || text.contains("debug")) debug = true;
        }
        boolean onlyOutline = body < 3 || structure + interfaceCount < 8;
        return new ShapeStats(center, hidden, section, body, structure, interfaceCount, viewTitles, debug, onlyOutline);
    }

    private List<String> missingSettlingFeatures(DrawingEngine.Canvas canvas) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        seen.put("\u7bb1\u4f53", false);
        seen.put("\u8fdb\u6c14\u7ba1", false);
        seen.put("\u51fa\u6c14\u7ba1", false);
        seen.put("\u6269\u6563\u6bb5", false);
        seen.put("\u7070\u6597", false);
        seen.put("\u5378\u7070\u53e3", false);
        seen.put("\u652f\u6491\u67b6", false);
        seen.put("\u68c0\u4fee\u95e8", false);
        seen.put("\u5bfc\u6d41\u677f", false);
        seen.put("\u52a0\u5f3a\u7b4b", false);
        seen.put("\u6cd5\u5170", false);
        for (DrawingEngine.Shape shape : canvas.shapes) {
            String text = safe(shape.text());
            seen.replaceAll((key, value) -> value || text.contains(key));
        }
        return seen.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).toList();
    }

    private List<String> mainViewPlan(String deviceType) {
        if ("settling_chamber".equals(deviceType)) {
            return List.of("\u5916\u8f6e\u5ed3\u8868\u8fbe\u5367\u5f0f\u77e9\u5f62\u7bb1\u4f53", "\u8fdb\u6c14\u6269\u6563\u6bb5\u548c\u51fa\u6c14\u7ba1\u5206\u7f6e\u4e24\u7aef", "\u7070\u6597\u7528\u68af\u5f62\u8f6e\u5ed3\u548c\u5761\u5ea6\u7ebf\u8868\u8fbe", "\u5185\u90e8\u5bfc\u6d41\u677f\u7528\u865a\u7ebf\u6216\u5256\u5207\u7ebf\u8868\u8fbe", "\u652f\u6491\u817f\u3001\u5e95\u5ea7\u677f\u3001\u52a0\u5f3a\u7b4b\u5fc5\u987b\u53ef\u89c1");
        }
        return List.of("draw outer assembly contour", "show key internal structures with hidden lines", "show supports and mounting interfaces");
    }

    private List<String> topViewPlan(String deviceType) {
        if ("settling_chamber".equals(deviceType)) {
            return List.of("\u8868\u8fbe\u957f\u5bbd\u65b9\u5411\u5e03\u5c40", "\u6807\u51fa\u8fdb\u51fa\u53e3\u65b9\u5411", "\u9876\u677f\u8fb9\u754c\u3001\u5f00\u5b54\u3001\u52a0\u5f3a\u7b4b\u548c\u68c0\u4fee\u95e8\u4f4d\u7f6e", "\u6cd5\u5170\u7528\u5b54\u9635\u5217\u8868\u8fbe");
        }
        return List.of("draw length-width layout", "show top openings and bolt patterns", "show part boundary relation");
    }

    private List<String> sideViewPlan(String deviceType) {
        if ("settling_chamber".equals(deviceType)) {
            return List.of("\u8868\u8fbe\u7bb1\u4f53\u9ad8\u5ea6\u548c\u8fdb\u51fa\u53e3\u9ad8\u5ea6", "\u7070\u6597\u5761\u5ea6\u4e0e\u5378\u7070\u53e3\u5fc5\u987b\u53ef\u89c1", "\u652f\u6491\u817f\u548c\u5e95\u5ea7\u677f\u5fc5\u987b\u53ef\u89c1", "\u5bfc\u6d41\u677f\u7528\u865a\u7ebf\u8868\u8fbe");
        }
        return List.of("draw height-direction structure", "show side supports", "show hidden/internal structure");
    }

    private List<String> dimensionPlan(String deviceType) {
        return List.of("\u603b\u957f\u5c3a\u5bf8\u653e\u5728\u89c6\u56fe\u5916\u4fa7", "\u603b\u5bbd\u548c\u603b\u9ad8\u4e0d\u7a7f\u8fc7\u4e3b\u4f53", "\u5c40\u90e8\u5b54\u4f4d\u548c\u6cd5\u5170\u5b54\u9635\u5217\u5355\u72ec\u6807\u6ce8");
    }

    private List<String> annotationPlan(String deviceType) {
        return List.of("\u5f15\u7ebf\u5206\u5e03\u5230\u89c6\u56fe\u5916\u4fa7", "\u5e8f\u53f7\u4e0eBOM\u4e00\u4e00\u5bf9\u5e94", "\u6587\u5b57\u4e0d\u538b\u7ebf");
    }

    private List<String> hiddenLinePlan(String deviceType) {
        return List.of("\u5185\u90e8\u5bfc\u6d41\u677f\u7528HIDDEN\u5c42", "\u88ab\u906e\u6321\u7ba1\u53e3\u8f6e\u5ed3\u7528HIDDEN\u5c42", "\u7bb1\u4f53\u677f\u539a\u8f85\u52a9\u7ebf\u7528HIDDEN\u5c42");
    }

    private List<String> centerLinePlan(String deviceType) {
        return List.of("\u8fdb\u51fa\u53e3\u7ba1\u9053\u4e2d\u5fc3\u7ebf", "\u6cd5\u5170\u5b54\u9635\u5217\u4e2d\u5fc3\u7ebf", "\u5378\u7070\u53e3\u4e2d\u5fc3\u7ebf");
    }

    private List<String> sectionSuggestion(String deviceType) {
        if ("settling_chamber".equals(deviceType)) {
            return List.of("A-A\u5256\u5207\u7528\u4e8e\u8868\u8fbe\u7bb1\u4f53\u677f\u539a\u548c\u5bfc\u6d41\u677f", "B-B\u5256\u5207\u7528\u4e8e\u8868\u8fbe\u7070\u6597\u5761\u5ea6");
        }
        return List.of("add local section only when hidden structure cannot be expressed clearly");
    }

    private String projectText(DesignProject project) {
        return (safe(project.getProjectTitle()) + " " + safe(project.getEquipmentName()) + " " + safe(project.getDesignType()) + " "
                + safe(project.getEquipmentType()) + " " + project.getMainStructures() + " " + project.getStructureTree() + " "
                + project.getAssemblyTree() + " " + project.getComponents()).toLowerCase(Locale.ROOT);
    }

    private boolean has(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    record DrawingRepairPlan(int drawingQualityScore, List<String> failedReasons, boolean requiredRedraw,
                             List<String> mainViewPlan, List<String> topViewPlan, List<String> sideViewPlan,
                             List<String> dimensionPlan, List<String> annotationPlan, List<String> hiddenLinePlan,
                             List<String> centerLinePlan, List<String> sectionSuggestion) {
    }

    private record ShapeStats(int centerLines, int hiddenLines, int sectionLines, int bodyShapes, int structureShapes,
                              int interfaceShapes, int viewTitleCount, boolean debugText, boolean onlyOutlineProjection) {
    }
}
