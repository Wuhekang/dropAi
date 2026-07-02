package com.dropai.rewrite.modules.assemblyPlannerAgent;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AssemblyPlannerAgent {
    public DesignProject plan(DesignProject project) {
        if (project.getAssemblyTree() != null) {
            project.getAssemblyTree().setAssemblyName(clean(project.getEquipmentName(), "机械设备") + "整机装配");
            project.getAssemblyTree().setCoordinateSystem(new LinkedHashMap<>(Map.of(
                    "x", "整机长度方向",
                    "y", "整机宽度方向",
                    "z", "整机高度方向"
            )));
        }
        project.getAssemblyConstraints().forEach(constraint -> {
            if (blank(constraint.getSource())) constraint.setSource("AssemblyPlannerAgent：由零件功能、安装面、轴线和孔位关系推导");
            if (blank(constraint.getMountingFace())) constraint.setMountingFace("基准安装面");
            if (blank(constraint.getContactFace())) constraint.setContactFace("装配接触面");
            if (blank(constraint.getHolePattern()) && looksBolted(constraint)) constraint.setHolePattern("标准螺栓孔阵列");
            if (blank(constraint.getAxisId()) && looksRotating(constraint)) constraint.setAxisId(constraint.getPartId() + "-AXIS");
            if (blank(constraint.getSymmetryPlane())) constraint.setSymmetryPlane("整机中心基准面");
        });
        project.getEnhancementNotes().removeIf(note -> note != null && note.contains("AssemblyPlannerAgent"));
        project.getEnhancementNotes().add("AssemblyPlannerAgent：已补充装配顺序、坐标系、安装面、轴线、孔位和接触约束语义。");
        return project;
    }

    private boolean looksRotating(DesignProject.AssemblyConstraint c) {
        String text = safe(c.getPartName()) + safe(c.getConstraintType()) + safe(c.getMountTo());
        return contains(text, "轮", "轴", "轴承", "电机", "减速器", "联轴器");
    }

    private boolean looksBolted(DesignProject.AssemblyConstraint c) {
        String text = safe(c.getPartName()) + safe(c.getMountTo());
        return contains(text, "板", "座", "架", "壳", "盖", "螺栓");
    }

    private boolean contains(String value, String... words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String clean(String value, String fallback) { return blank(value) ? fallback : value; }
    private String safe(String value) { return value == null ? "" : value; }
}
