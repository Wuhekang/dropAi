package com.dropai.rewrite.modules.projectAnalyzer;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectAnalyzer {
    public DesignProject analyze(DesignProject project) {
        if (project == null) project = new DesignProject();
        if (blank(project.getProjectTitle())) {
            throw new IllegalStateException("缺少题目：请上传任务书或手动填写毕业设计题目");
        }
        addMissingHints(project);

        DesignProject.ProjectAnalysis analysis = new DesignProject.ProjectAnalysis();
        analysis.setTitle(project.getProjectTitle());
        analysis.setEquipmentName(project.getEquipmentName());
        analysis.setProjectType(project.getDesignType());
        analysis.setFunctions(new ArrayList<>(project.getMainFunctions()));
        analysis.setRequirements(requirements(project));
        analysis.setDeliverables(deliverables(project));
        project.setProjectAnalysis(analysis);
        if (blank(project.getProjectCategory())) project.setProjectCategory("机械类毕业设计");
        if (blank(project.getWorkingPrinciple())) {
            String name = blank(project.getEquipmentName()) ? project.getProjectTitle() : project.getEquipmentName();
            String functions = project.getMainFunctions().isEmpty() ? "毕业设计功能目标" : String.join("、", project.getMainFunctions());
            project.setWorkingPrinciple(name + "围绕"
                    + functions
                    + "展开结构组合，结构树、标准件、非标件和装配关系由当前任务书识别和系统补全结果统一驱动。");
        }
        return project;
    }

    private void addMissingHints(DesignProject project) {
        List<String> missing = new ArrayList<>();
        if (blank(project.getEquipmentName())) missing.add("设备名称未明确，系统将根据题目推断。");
        if (blank(project.getDesignType())) missing.add("设计类型未明确，系统将根据设备类型补全。");
        if (project.getMainStructures() == null || project.getMainStructures().stream().filter(v -> !blank(v)).count() < 3) {
            missing.add("结构组成未明确，系统将按设备类型补全主要机构。");
        }
        for (String item : missing) {
            if (project.getVerificationItems().stream().noneMatch(item::equals)) project.getVerificationItems().add(item);
        }
    }

    private List<String> requirements(DesignProject project) {
        List<String> result = new ArrayList<>();
        for (DesignProject.Parameter parameter : project.allParameters()) {
            String name = parameter.getName();
            if (!blank(name)) result.add(name + "=" + parameter.getValue() + parameter.getUnit());
        }
        result.addAll(project.getVerificationItems());
        return result.stream().distinct().toList();
    }

    private List<String> deliverables(DesignProject project) {
        List<String> result = new ArrayList<>(List.of("设计说明书", "设计计算书", "CAD总装图", "CAD零件图", "BOM明细表", "SolidWorks建模步骤"));
        for (String view : project.getDrawingViews()) if (!blank(view)) result.add(view);
        return result.stream().distinct().toList();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
