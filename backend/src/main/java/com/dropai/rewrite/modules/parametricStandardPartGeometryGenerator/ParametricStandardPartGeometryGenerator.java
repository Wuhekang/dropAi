package com.dropai.rewrite.modules.parametricStandardPartGeometryGenerator;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class ParametricStandardPartGeometryGenerator {
    public String resolveGeometry(DesignProject.DesignPart part) {
        String category = safe(part.getCategory());
        String name = safe(part.getName());
        String type = safe(part.getPartType());

        if ("standard".equals(type)) {
            return switch (category) {
                case "bearing" -> "BEARING_PARAMETRIC";
                case "motor" -> "MOTOR_PARAMETRIC";
                case "reducer" -> "GEARBOX_PARAMETRIC";
                case "rail" -> "RAIL_PARAMETRIC";
                case "coupling" -> "COUPLING_PARAMETRIC";
                case "bolt" -> "BOLT_GROUP_PARAMETRIC";
                case "flange" -> "FLANGE_PARAMETRIC";
                case "roller", "sprocket", "timing_pulley" -> "WHEEL_PARAMETRIC";
                case "shaft" -> "SHAFT_PARAMETRIC";
                case "key" -> "KEY_PARAMETRIC";
                case "pin" -> "PIN_PARAMETRIC";
                case "spring" -> "SPRING_PARAMETRIC";
                default -> inferNonStandardGeometry(name);
            };
        }
        return inferNonStandardGeometry(name);
    }

    public boolean isFallbackPrimitive(String geometry) {
        String value = safe(geometry);
        return "BOX".equals(value) || "PLATE".equals(value) || value.startsWith("CYLINDER");
    }

    private String inferNonStandardGeometry(String name) {
        if (containsAny(name, "履带", "灞ュ甫", "鐏炪儱鐢")) return "TRACK_PARAMETRIC";
        if (containsAny(name, "滚轮", "支重轮", "驱动轮", "从动轮", "杞", "婊氳疆", "鏀噸杞")) return "WHEEL_PARAMETRIC";
        if (containsAny(name, "清扫", "刷", "鍒", "娓呮壂")) return "BRUSH_PARAMETRIC";
        if (containsAny(name, "磁", "吸附", "纾", "鍚搁檮")) return "MAGNET_MODULE_PARAMETRIC";
        if (containsAny(name, "检测", "传感", "导轨", "滑轨", "妫€娴", "浼犳劅", "瀵艰建", "婊戣建")) return "SENSOR_RAIL_PARAMETRIC";
        if (containsAny(name, "机架", "支架", "底座", "鏈烘灦", "鏀灦", "搴曞骇")) return "FRAME";
        if (containsAny(name, "外壳", "防护", "罩", "澶栧３", "闃叉姢")) return "COVER";
        return "PLATE";
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (word != null && !word.isBlank() && value.contains(word)) return true;
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
