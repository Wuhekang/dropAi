package com.dropai.rewrite.modules.assemblyConstraintEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.modules.parametricStandardPartGeometryGenerator.ParametricStandardPartGeometryGenerator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssemblyConstraintEngine {
    private final ParametricStandardPartGeometryGenerator geometryGenerator = new ParametricStandardPartGeometryGenerator();

    public AssemblyResult solve(DesignProject project) {
        double l = project.number("总长", project.number("整机长度", 800));
        double w = project.number("总宽", project.number("整机宽度", 600));
        double h = project.number("总高", project.number("整机高度", 300));
        String signature = project.getProjectTitle() + project.getEquipmentName() + project.getDesignType() + String.join("", project.getMainStructures());
        boolean crawler = containsAny(signature, "爬壁", "履带", "磁吸附", "油罐检测");

        List<DesignProject.Component> components = new ArrayList<>();
        List<DesignProject.AssemblyConstraint> constraints = new ArrayList<>();
        int index = 0;
        for (DesignProject.DesignPart part : project.getResolvedParts()) {
            Layout layout = crawler ? crawlerLayout(part.getName(), index, l, w, h) : genericLayout(part.getName(), index, l, w, h);
            DesignProject.Component component = new DesignProject.Component(index + 1, role(part), part.getName(),
                    function(part), part.getMaterial(), Math.max(1, part.getQuantity()),
                    layout.x(), layout.y(), layout.z(), layout.sx(), layout.sy(), layout.sz(), index < 16);
            component.setGeometry(geometryGenerator.resolveGeometry(part));
            component.setFeatureTree(geometryGenerator.resolveFeatureTree(part));
            component.setModelingMethod("feature_based_parametric");
            component.setPartId("P%03d".formatted(index + 1));
            component.setParentAssembly(parentAssembly(part));
            component.setMountTo(layout.mountTo());
            component.setConstraintType(layout.constraintType());
            component.setMateReferences(layout.refs());
            components.add(component);

            DesignProject.AssemblyConstraint constraint = new DesignProject.AssemblyConstraint();
            constraint.setPartId(component.getPartId());
            constraint.setPartName(component.getName());
            constraint.setParentAssembly(component.getParentAssembly());
            constraint.setMountTo(component.getMountTo());
            constraint.setConstraintType(component.getConstraintType());
            constraint.setMateReferences(component.getMateReferences());
            enrichInterfaceConstraint(constraint, component);
            constraints.add(constraint);
            index++;
        }
        return new AssemblyResult(components, constraints);
    }

    private void enrichInterfaceConstraint(DesignProject.AssemblyConstraint constraint, DesignProject.Component component) {
        String geometry = component.getGeometry() == null ? "" : component.getGeometry().toUpperCase();
        String id = component.getPartId();
        if (geometry.contains("MOTOR") || geometry.contains("GEARBOX") || geometry.contains("WHEEL") || geometry.contains("BEARING") || geometry.contains("SHAFT")) {
            constraint.setAxisId(id + "-AXIS-X");
            constraint.setMountingFace("端面安装面");
            constraint.setHolePattern("法兰/端盖安装孔阵列");
            constraint.setContactFace("轴端或轮系接触面");
            constraint.setSource("由标准件类别和装配关系推导，待CAD孔距复核");
        } else if (geometry.contains("TRACK")) {
            constraint.setAxisId(id + "-TRACK-CENTER");
            constraint.setContactFace("履带内侧与轮系接触面");
            constraint.setSymmetryPlane("整机Y向中心面对称");
            constraint.setOffsetDistance(component.getY());
            constraint.setSource("由履带机构左右布局和整机坐标系推导");
        } else if (geometry.contains("MAGNET")) {
            constraint.setMountingFace("底部安装面");
            constraint.setHolePattern("磁吸附模块固定孔阵列");
            constraint.setContactFace("磁体工作面");
            constraint.setOffsetDistance(component.getZ());
            constraint.setSource("由磁吸附模块底部安装要求推导");
        } else if (geometry.contains("SENSOR") || geometry.contains("RAIL")) {
            constraint.setAxisId(id + "-GUIDE-AXIS");
            constraint.setMountingFace("滑轨安装面");
            constraint.setHolePattern("滑轨长圆孔/安装孔阵列");
            constraint.setSource("由检测支架与滑轨调节机构推导");
        } else {
            constraint.setMountingFace("基准安装面");
            constraint.setContactFace("装配接触面");
            constraint.setSource("由结构树节点和规则装配坐标推导，需人工校核");
        }
    }

    private Layout crawlerLayout(String name, int index, double l, double w, double h) {
        if (containsAny(name, "机架", "主板", "底板", "侧板", "横梁", "加强筋")) return layout(l * .18, w * .22, h * .24, l * .64, w * .56, h * .12, "整机坐标系", "fixed", "机架基准面", "整机中心面");
        if (containsAny(name, "左侧履带")) return layout(l * .08, w * .05, h * .06, l * .78, w * .16, h * .18, "机架", "parallel", "左侧安装面", "履带中心线");
        if (containsAny(name, "右侧履带")) return layout(l * .08, w * .79, h * .06, l * .78, w * .16, h * .18, "机架", "parallel", "右侧安装面", "履带中心线");
        if (containsAny(name, "履带板", "履带")) return layout(l * .08, w * .05, h * .06, l * .78, w * .90, h * .18, "机架", "symmetric", "左右履带中心面");
        if (containsAny(name, "驱动轮")) return layout(l * .10, w * .08, h * .08, l * .12, w * .84, h * .16, "履带", "coaxial", "驱动轮轴线", "履带端部圆弧");
        if (containsAny(name, "从动轮")) return layout(l * .78, w * .08, h * .08, l * .12, w * .84, h * .16, "履带", "coaxial", "从动轮轴线", "履带端部圆弧");
        if (containsAny(name, "支重轮", "滚轮", "轴承")) return layout(l * (.25 + (index % 4) * .12), w * .08, h * .07, l * .08, w * .84, h * .12, "履带", "contact", "支重轮外圆", "履带内侧接触面");
        if (containsAny(name, "磁", "吸附")) return layout(l * (.15 + (index % 6) * .1), w * .30, h * .01, l * .08, w * .40, h * .05, "机架", "offset", "底部安装孔", "磁吸附安装间距");
        if (containsAny(name, "清扫", "刷")) return layout(l * .86, w * .38, h * .04, l * .16, w * .24, h * .16, "机架", "coaxial", "清扫电机轴", "刷盘中心孔");
        if (containsAny(name, "检测", "传感", "导轨", "滑轨", "滑块")) return layout(l * .70, w * .28, h * .42, l * .24, w * .44, h * .12, "机架", "parallel", "导轨安装面", "检测模块调节方向");
        if (containsAny(name, "电机")) return layout(l * .12, w * .32, h * .30, l * .13, w * .15, h * .16, "驱动轮", "coaxial", "电机输出轴", "驱动轮轴线");
        if (containsAny(name, "减速")) return layout(l * .24, w * .32, h * .30, l * .12, w * .15, h * .15, "电机", "coaxial", "减速器输入轴", "电机输出轴");
        if (containsAny(name, "外壳", "防护", "电池", "控制", "盖板", "散热")) return layout(l * .32, w * .28, h * .48, l * .34, w * .42, h * .30, "机架", "fixed", "外壳安装孔", "机架上平面");
        return genericLayout(name, index, l, w, h);
    }

    private Layout genericLayout(String name, int index, double l, double w, double h) {
        if (containsAny(name, "机架", "主体", "底座", "支撑", "箱体", "壳体", "侧板", "底板", "顶板")) return layout(l * .18, w * .18, h * .12, l * .64, w * .64, h * .16, "整机坐标系", "fixed", "基准安装面");
        if (containsAny(name, "电机", "减速", "轴", "轮", "带", "联轴器")) return layout(l * .16 + (index % 3) * l * .18, w * .20, h * .36, l * .16, w * .18, h * .16, "机架", "coaxial", "传动轴线", "安装孔");
        if (containsAny(name, "检测", "导轨", "滑轨", "传感")) return layout(l * .62, w * .32, h * .42, l * .22, w * .36, h * .12, "机架", "parallel", "导轨面", "调节方向");
        if (containsAny(name, "外壳", "防护", "罩", "盖")) return layout(l * .28, w * .25, h * .48, l * .44, w * .50, h * .28, "机架", "fixed", "罩壳安装边");
        if (containsAny(name, "法兰", "进气", "出气", "导流", "灰斗", "卸灰", "检修", "观察")) return layout(l * (.12 + (index % 4) * .16), w * (.18 + (index % 3) * .18), h * (.16 + (index % 2) * .18), l * .18, w * .16, h * .14, "箱体", "fixed", "安装孔", "焊接边");
        return layout(l * (.18 + (index % 4) * .15), w * (.24 + (index % 3) * .15), h * (.24 + (index % 2) * .18), l * .14, w * .14, h * .12, "机架", "fixed", "安装孔");
    }

    private Layout layout(double x, double y, double z, double sx, double sy, double sz, String mountTo, String type, String... refs) {
        return new Layout(x, y, z, sx, sy, sz, mountTo, type, List.of(refs));
    }

    private String role(DesignProject.DesignPart part) {
        String name = part.getName();
        if ("standard".equals(part.getPartType())) return containsAny(name, "电机", "减速", "轴", "轮", "带") ? "DRIVE" : "CONNECT";
        if (containsAny(name, "机架", "支撑", "底座")) return "SUPPORT";
        if (containsAny(name, "外壳", "防护")) return "SAFETY";
        if (containsAny(name, "检修", "维护", "快拆")) return "MAINTENANCE";
        if (containsAny(name, "安装", "吸附", "磁")) return "MOUNT";
        return "FUNCTION";
    }

    private String geometry(DesignProject.DesignPart part) {
        String name = part.getName();
        String category = part.getCategory();
        if ("bearing".equals(category)) return "BEARING";
        if ("motor".equals(category)) return "MOTOR";
        if ("reducer".equals(category)) return "GEARBOX";
        if ("rail".equals(category)) return "RAIL";
        if ("coupling".equals(category)) return "COUPLING";
        if ("bolt".equals(category)) return "BOLT_GROUP";
        if ("flange".equals(category)) return "FLANGE";
        if (containsAny(name, "履带", "带")) return "TRACK";
        if (containsAny(name, "轮")) return "WHEEL";
        if (containsAny(name, "刷")) return "BRUSH";
        if (containsAny(name, "磁", "吸附")) return "MAGNET_BLOCK";
        if (containsAny(name, "导轨", "滑轨", "检测")) return "SENSOR_RAIL";
        if (containsAny(name, "机架", "支架")) return "FRAME";
        if (containsAny(name, "外壳", "防护", "盖")) return "COVER";
        return "PLATE";
    }

    private String function(DesignProject.DesignPart part) {
        if ("standard".equals(part.getPartType())) {
            return "标准件：" + part.getModel()
                    + "；品牌：" + blank(part.getBrand(), "-")
                    + "；平台：" + blank(part.getSourcePlatform(), part.getSource())
                    + "；状态：" + blank(part.getRetrievalStatus(), "-");
        }
        return "非标件：" + String.join("；", part.getGeometryFeatures());
    }

    private String parentAssembly(DesignProject.DesignPart part) {
        return part.getParentStructure() == null || part.getParentStructure().isBlank() ? "整机装配" : part.getParentStructure();
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (word != null && !word.isBlank() && value.contains(word)) return true;
        return false;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record AssemblyResult(List<DesignProject.Component> components, List<DesignProject.AssemblyConstraint> constraints) {}
    private record Layout(double x, double y, double z, double sx, double sy, double sz, String mountTo, String constraintType, List<String> refs) {}
}
