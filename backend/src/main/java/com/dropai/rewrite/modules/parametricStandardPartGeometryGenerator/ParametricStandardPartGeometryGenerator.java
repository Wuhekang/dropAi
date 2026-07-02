package com.dropai.rewrite.modules.parametricStandardPartGeometryGenerator;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<String> resolveFeatureTree(DesignProject.DesignPart part) {
        String geometry = resolveGeometry(part);
        String name = safe(part.getName());
        List<String> features = switch (geometry) {
            case "BEARING_PARAMETRIC" -> List.of("外圈旋转体", "内圈旋转体", "滚珠环形阵列", "保持架", "中心孔", "两端倒角");
            case "MOTOR_PARAMETRIC" -> List.of("圆柱壳体拉伸", "前端法兰", "输出轴", "键槽", "安装孔阵列", "散热筋阵列", "接线盒");
            case "GEARBOX_PARAMETRIC" -> List.of("箱体拉伸", "输入轴", "输出轴", "安装底脚", "螺栓孔阵列", "加强筋", "圆角过渡");
            case "RAIL_PARAMETRIC" -> List.of("导轨基体", "滑块", "安装沉孔阵列", "导向槽", "端部倒角");
            case "COUPLING_PARAMETRIC" -> List.of("左半联轴器", "右半联轴器", "弹性连接段", "中心孔", "键槽", "紧定螺钉孔");
            case "BOLT_GROUP_PARAMETRIC" -> List.of("六角头", "螺杆", "简化螺纹线", "倒角端部");
            case "FLANGE_PARAMETRIC" -> List.of("法兰盘拉伸", "中心孔", "螺栓孔圆周阵列", "定位台阶", "沉孔", "倒角");
            case "WHEEL_PARAMETRIC" -> List.of("轮缘", "轮毂", "轮辐阵列", "轴孔", "键槽", "挡肩倒角");
            case "SHAFT_PARAMETRIC" -> List.of("台阶轴", "轴肩", "键槽", "中心线", "端部倒角", "挡圈槽");
            case "KEY_PARAMETRIC" -> List.of("平键基体", "端部倒角", "配合面");
            case "PIN_PARAMETRIC" -> List.of("圆柱销体", "端部倒角", "定位面");
            case "SPRING_PARAMETRIC" -> List.of("螺旋线", "弹簧丝截面", "端部座圈");
            case "TRACK_PARAMETRIC" -> List.of("履带外轮廓", "驱动轮", "从动轮", "支重轮阵列", "履带板阵列", "履带销", "内侧导向齿");
            case "BRUSH_PARAMETRIC" -> List.of("圆盘拉伸", "中心轴孔", "刷毛圆周阵列", "压紧盖板", "平衡孔", "电机安装座");
            case "MAGNET_MODULE_PARAMETRIC" -> List.of("安装板拉伸", "磁体槽阵列", "永磁体阵列", "防护盖", "沉头固定孔", "倒角");
            case "SENSOR_RAIL_PARAMETRIC" -> List.of("立板", "横梁", "导轨", "滑块", "传感器安装孔", "调节长槽", "快拆孔");
            case "FRAME" -> List.of("侧板", "横梁", "折弯边", "加强筋", "安装孔阵列", "定位孔", "圆角倒角");
            case "COVER" -> List.of("薄板罩壳", "圆角折边", "检修盖板", "散热槽阵列", "密封槽", "螺钉孔");
            case "PLATE_FEATURED" -> List.of("板件拉伸", "折弯边", "焊接坡口", "安装孔", "密封面", "加强筋");
            default -> List.of("基体拉伸", "安装孔", "定位面", "加强筋", "倒角", "圆角");
        };
        if (!part.getGeometryFeatures().isEmpty() && !"standard".equals(part.getPartType())) {
            return part.getGeometryFeatures().stream().distinct().toList();
        }
        if (features.isEmpty() && !name.isBlank()) return List.of(name + "基体", "安装孔", "倒角");
        return features;
    }

    private String inferNonStandardGeometry(String name) {
        if (containsAny(name, "履带", "履带板", "左侧履带", "右侧履带")) return "TRACK_PARAMETRIC";
        if (containsAny(name, "驱动轮", "从动轮", "支重轮", "滚轮", "轮轴")) return "WHEEL_PARAMETRIC";
        if (containsAny(name, "清扫", "刷盘", "圆盘清扫刷", "刷毛")) return "BRUSH_PARAMETRIC";
        if (containsAny(name, "磁", "吸附")) return "MAGNET_MODULE_PARAMETRIC";
        if (containsAny(name, "检测", "传感", "导轨", "滑轨", "滑块")) return "SENSOR_RAIL_PARAMETRIC";
        if (containsAny(name, "机架", "主板", "侧板", "横梁", "底板", "连接板", "安装板", "安装座", "支架")) return "FRAME";
        if (containsAny(name, "外壳", "防护", "盖板", "电池舱", "控制模块")) return "COVER";
        if (containsAny(name, "灰斗", "导流板", "检修门", "观察窗", "扩散段")) return "PLATE_FEATURED";
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
