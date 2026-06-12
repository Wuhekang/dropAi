package com.dropai.rewrite.modules.structureEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StructureEngine {
    public DesignProject design(DesignProject project) {
        double l = project.number("总长", 4200), w = project.number("总宽", 1600), h = project.number("总高", 1800);
        String material = text(project, "材料", "Q235B");
        List<DesignProject.Component> parts = switch (project.getDesignType()) {
            case "沉降分离设备" -> sedimentation(l, w, h, material);
            case "带式输送设备" -> conveyor(l, w, h, material);
            case "关节机械手" -> manipulator(l, w, h, material);
            default -> functionalScheme(project, l, w, h, material);
        };
        project.setComponents(parts);
        project.setBom(parts.stream().map(p -> new DesignProject.BomItem(
                p.getSequence(), p.getName(), p.getMaterial(), p.getQuantity(), p.getFunction())).toList());
        project.setDimensionChains(List.of(
                new DesignProject.DimensionChain("总长", l, "mm", "整机"),
                new DesignProject.DimensionChain("总宽", w, "mm", "整机"),
                new DesignProject.DimensionChain("总高", h, "mm", "整机"),
                new DesignProject.DimensionChain("安装中心距", l * .72, "mm", "安装结构"),
                new DesignProject.DimensionChain("功能中心高度", h * .55, "mm", "功能结构"),
                new DesignProject.DimensionChain("支撑跨距", l * .64, "mm", "支撑结构")));
        project.setTechnicalRequirements(List.of(
                "图纸尺寸与统一设计参数表保持一致，修改参数后须重新校核。",
                "各功能部件按总装图空间关系定位，装配后检查运动与维护空间。",
                "焊接件完成后清理焊渣和毛刺，关键安装面校正后加工。",
                "方案级结构须结合载荷、工况和制造条件完成工程复核。"));
        return project;
    }

    private List<DesignProject.Component> sedimentation(double l, double w, double h, String m) {
        List<DesignProject.Component> p = new ArrayList<>();
        add(p, "INTERFACE", "进风口", "导入含尘气流", m, 1, "DUCT_X", 0, .30, .53, .18, .40, .22, true, l,w,h);
        add(p, "BODY", "沉降腔", "降低流速并提供颗粒沉降空间", m, 1, "CHAMBER", .16, .12, .36, .66, .76, .50, true, l,w,h);
        add(p, "FUNCTION", "排灰斗", "收集并排出沉降颗粒", m, 2, "HOPPER", .28, .20, .08, .20, .60, .30, true, l,w,h);
        add(p, "MAINTENANCE", "检修门", "提供内部检查和清灰通道", m, 1, "DOOR", .44, .10, .48, .17, .03, .23, true, l,w,h);
        add(p, "INTERFACE", "出风口", "导出净化后气流", m, 1, "DUCT_X", .82, .30, .58, .18, .40, .20, true, l,w,h);
        add(p, "SUPPORT", "支撑架", "承载沉降腔及灰斗", m, 4, "FRAME", .18, .15, .02, .64, .70, .15, true, l,w,h);
        return p;
    }

    private List<DesignProject.Component> conveyor(double l, double w, double h, String m) {
        List<DesignProject.Component> p = new ArrayList<>();
        add(p, "FUNCTION", "驱动滚筒", "驱动输送带运行", "45钢", 1, "CYLINDER_Y", .78, .18, .55, .10, .64, .20, true, l,w,h);
        add(p, "FUNCTION", "从动滚筒", "改变输送带运行方向", "45钢", 1, "CYLINDER_Y", .12, .18, .55, .10, .64, .20, true, l,w,h);
        add(p, "FUNCTION", "输送带", "连续承载和输送物料", "橡胶复合材料", 1, "BELT", .14, .20, .58, .68, .60, .08, true, l,w,h);
        add(p, "SUPPORT", "机架", "支撑滚筒、托辊与输送带", m, 1, "TRUSS", .08, .12, .18, .80, .76, .38, true, l,w,h);
        add(p, "DRIVE", "电机减速机", "向驱动滚筒提供动力", "标准件", 1, "MOTOR", .77, .02, .28, .16, .22, .22, true, l,w,h);
        add(p, "SUPPORT", "支腿", "将运行载荷传递至基础", m, 4, "FRAME", .18, .16, .02, .62, .68, .20, false, l,w,h);
        return p;
    }

    private List<DesignProject.Component> manipulator(double l, double w, double h, String m) {
        List<DesignProject.Component> p = new ArrayList<>();
        add(p, "BASE", "底座", "固定机械手并承受倾覆力矩", m, 1, "CYLINDER_Z", .34, .28, .02, .32, .44, .12, true, l,w,h);
        add(p, "SUPPORT", "立柱", "支撑回转和手臂组件", m, 1, "CYLINDER_Z", .42, .38, .12, .16, .24, .42, true, l,w,h);
        add(p, "FUNCTION", "大臂", "实现主要工作半径运动", m, 1, "ARM_XZ", .48, .40, .48, .28, .18, .18, true, l,w,h);
        add(p, "FUNCTION", "小臂", "扩展末端执行器工作范围", m, 1, "ARM_XZ", .70, .40, .64, .20, .15, .15, true, l,w,h);
        add(p, "FUNCTION", "夹爪", "夹持和释放工件", "合金钢", 1, "CLAW", .86, .36, .68, .12, .24, .18, true, l,w,h);
        add(p, "DRIVE", "关节驱动组件", "驱动各关节转动", "标准件", 3, "JOINT", .54, .36, .52, .12, .24, .16, false, l,w,h);
        return p;
    }

    private List<DesignProject.Component> functionalScheme(DesignProject project, double l, double w, double h, String m) {
        List<DesignProject.Component> p = new ArrayList<>();
        add(p, "BODY", "主体工作腔", "形成主要工艺空间", m, 1, "CHAMBER", .14,.18,.28,.68,.64,.48,true,l,w,h);
        add(p, "SUPPORT", "承载机架", "承载功能组件并传递载荷", m, 1, "TRUSS", .08,.12,.04,.80,.76,.22,true,l,w,h);
        add(p, "INTERFACE", "入口组件", "连接上游系统", m, 1, "DUCT_X", 0,.32,.46,.16,.36,.20,true,l,w,h);
        add(p, "INTERFACE", "出口组件", "连接下游系统", m, 1, "DUCT_X", .82,.32,.46,.16,.36,.20,true,l,w,h);
        add(p, "MAINTENANCE", "检修组件", "提供检查、调整和维护空间", m, 1, "DOOR", .42,.17,.38,.16,.03,.22,true,l,w,h);
        int index = 0;
        for (String function : project.getMainFunctions()) {
            add(p, "FUNCTION", function + "组件", function, m, 1, "ROTOR",
                    .28 + index++ * .18, .30, .40, .16, .40, .22, true, l,w,h);
        }
        return p;
    }

    private void add(List<DesignProject.Component> list, String role, String name, String function, String material, int qty,
                     String geometry, double x, double y, double z, double length, double width, double height, boolean key,
                     double totalL, double totalW, double totalH) {
        DesignProject.Component component = new DesignProject.Component(list.size() + 1, role, name, function, material, qty,
                x * totalL, y * totalW, z * totalH, length * totalL, width * totalW, height * totalH, key);
        component.setGeometry(geometry);
        list.add(component);
    }

    private String text(DesignProject project, String name, String fallback) {
        return project.allParameters().stream().filter(p -> name.equals(p.getName()))
                .map(p -> String.valueOf(p.getValue())).findFirst().orElse(fallback);
    }
}
