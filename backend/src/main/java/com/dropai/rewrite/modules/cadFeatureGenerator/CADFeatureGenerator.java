package com.dropai.rewrite.modules.cadFeatureGenerator;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CADFeatureGenerator {
    public DesignProject generate(DesignProject project) {
        for (DesignProject.DesignPart part : project.getResolvedParts()) {
            part.setCadFeatures(featuresFor(part));
            if (part.getFeatureTree().isEmpty()) {
                part.setFeatureTree(part.getCadFeatures().stream()
                        .map(feature -> feature.getType() + ":" + feature.getParameters())
                        .toList());
            }
            part.setModelingMethod("cad_feature_parametric");
        }
        project.getEnhancementNotes().removeIf(note -> note != null && note.contains("CADFeatureGenerator"));
        project.getEnhancementNotes().add("CADFeatureGenerator: resolvedParts已生成参数化CAD特征，STEP/工程图可读取统一特征数据。");
        return project;
    }

    private List<CADFeature> featuresFor(DesignProject.DesignPart part) {
        String category = part.getCategory() == null ? "" : part.getCategory();
        return switch (category) {
            case "bearing" -> bearing(part);
            case "motor" -> motor(part);
            case "reducer" -> reducer(part);
            case "bolt" -> bolt(part);
            case "nut" -> nut(part);
            case "washer" -> washer(part);
            case "roller", "shaft" -> roller(part);
            case "rail" -> rail(part);
            case "coupling" -> coupling(part);
            case "gear", "sprocket" -> gearLike(part);
            default -> nonStandard(part);
        };
    }

    private List<CADFeature> bearing(DesignProject.DesignPart part) {
        double od = number(part, "outerDiameterMm", number(part, "outerDiameter", 52));
        double id = number(part, "innerDiameterMm", number(part, "innerDiameter", 25));
        double width = number(part, "widthMm", number(part, "width", 15));
        return List.of(
                feature("revolve", map("outerDiameter", od, "innerDiameter", id, "width", width), "bearing ring"),
                feature("groove", map("diameter", (od + id) / 2, "width", width * 0.35), "ball groove"),
                feature("pattern", map("count", 10, "feature", "ball"), "rolling elements")
        );
    }

    private List<CADFeature> motor(DesignProject.DesignPart part) {
        return List.of(
                feature("extrusion", map("length", 120, "width", 80, "height", 80), "motor body"),
                feature("revolve", map("diameter", 24, "length", 45), "output shaft"),
                feature("hole_pattern", map("diameter", 8, "pitchX", 90, "pitchY", 55, "count", 4), "mounting flange")
        );
    }

    private List<CADFeature> reducer(DesignProject.DesignPart part) {
        return List.of(
                feature("extrusion", map("length", 120, "width", 90, "height", 95), "reducer housing"),
                feature("revolve", map("diameter", 30, "length", 35), "input shaft"),
                feature("revolve", map("diameter", 35, "length", 40), "output shaft"),
                feature("hole_pattern", map("diameter", 10, "pitchX", 80, "pitchY", 60, "count", 4), "base mounting")
        );
    }

    private List<CADFeature> bolt(DesignProject.DesignPart part) {
        double diameter = number(part, "diameterMm", number(part, "nominalDiameter", 8));
        double length = number(part, "lengthMm", number(part, "length", 25));
        return List.of(
                feature("revolve", map("diameter", diameter, "length", length), "threaded shank"),
                feature("hex_prism", map("acrossFlats", diameter * 1.6, "height", diameter * 0.65), "hex head"),
                feature("chamfer", map("size", diameter * 0.08), "edge treatment")
        );
    }

    private List<CADFeature> nut(DesignProject.DesignPart part) {
        double diameter = number(part, "diameterMm", 8);
        return List.of(
                feature("hex_prism", map("acrossFlats", diameter * 1.8, "height", diameter * 0.8), "hex nut body"),
                feature("hole", map("diameter", diameter, "through", true), "thread hole")
        );
    }

    private List<CADFeature> washer(DesignProject.DesignPart part) {
        double diameter = number(part, "nominalDiameterMm", 8);
        return List.of(feature("revolve", map("outerDiameter", diameter * 2.2, "innerDiameter", diameter, "width", 1.6), "flat washer"));
    }

    private List<CADFeature> roller(DesignProject.DesignPart part) {
        double diameter = number(part, "diameter", number(part, "diameterMm", 80));
        return List.of(
                feature("revolve", map("diameter", diameter, "length", 260), "roller body"),
                feature("hole", map("diameter", Math.max(12, diameter * 0.25), "through", true), "shaft bore")
        );
    }

    private List<CADFeature> rail(DesignProject.DesignPart part) {
        return List.of(
                feature("extrusion", map("length", 500, "width", number(part, "railWidthMm", 15), "height", 16), "guide rail"),
                feature("hole_pattern", map("diameter", 6, "pitch", 60, "count", 6), "mounting holes"),
                feature("extrusion", map("length", 55, "width", 34, "height", 24), "slider block")
        );
    }

    private List<CADFeature> coupling(DesignProject.DesignPart part) {
        return List.of(
                feature("revolve", map("diameter", 45, "length", 55), "coupling body"),
                feature("hole", map("diameter", 20, "through", true), "shaft hole"),
                feature("slot", map("width", 6, "depth", 3), "keyway")
        );
    }

    private List<CADFeature> gearLike(DesignProject.DesignPart part) {
        return List.of(
                feature("revolve", map("diameter", 90, "width", 18), "gear blank"),
                feature("pattern", map("count", Math.max(12, (int) number(part, "teeth", 24)), "feature", "tooth"), "tooth pattern"),
                feature("hole", map("diameter", 25, "through", true), "shaft hole")
        );
    }

    private List<CADFeature> nonStandard(DesignProject.DesignPart part) {
        String name = part.getName() == null ? "" : part.getName();
        List<CADFeature> features = new ArrayList<>();
        if (containsAny(name, "箱体", "灰斗", "导流", "检修")) {
            features.add(feature("sheet_metal", map("length", 900, "width", 420, "thickness", 4), "plate structure"));
            features.add(feature("bend", map("angle", 90, "radius", 4), "bent edge"));
            features.add(feature("hole_pattern", map("diameter", 10, "count", 8), "bolt connection"));
        } else if (containsAny(name, "机架", "支架", "底座", "安装")) {
            features.add(feature("extrusion", map("length", 600, "width", 80, "height", 60), "base bracket"));
            features.add(feature("hole_pattern", map("diameter", 8, "pitch", 80, "count", 4), "mounting holes"));
            features.add(feature("fillet", map("radius", 3), "edge fillet"));
        } else if (containsAny(name, "吸附", "磁")) {
            features.add(feature("extrusion", map("length", 160, "width", 60, "height", 12), "magnet seat"));
            features.add(feature("slot", map("length", 120, "width", 24, "depth", 6), "magnet groove"));
            features.add(feature("hole_pattern", map("diameter", 6, "count", 4), "fixed holes"));
        } else {
            features.add(feature("extrusion", map("length", 120, "width", 60, "height", 12), "base solid"));
            features.add(feature("hole_pattern", map("diameter", 8, "count", 4), "mounting holes"));
            features.add(feature("chamfer", map("size", 2), "safe edges"));
        }
        return features;
    }

    private CADFeature feature(String type, Map<String, Object> parameters, String source) {
        return new CADFeature(type, parameters, source);
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }

    private double number(DesignProject.DesignPart part, String key, double fallback) {
        Object value = part.getTechnicalParams().getOrDefault(key, part.getDimensions().get(key));
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) return true;
        }
        return false;
    }
}
