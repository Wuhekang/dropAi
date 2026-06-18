package com.dropai.rewrite.modules.projectAnalyzer;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectAnalyzer {
    public DesignProject analyze(DesignProject project) {
        if (project == null) project = new DesignProject();
        List<String> missing = missingFields(project);
        if (!missing.isEmpty()) {
            project.getVerificationItems().addAll(missing);
            throw new IllegalStateException("设计目标识别不完整，不能生成默认通用机械设备：" + String.join("；", missing));
        }

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
            project.setWorkingPrinciple(project.getEquipmentName() + "围绕"
                    + String.join("、", project.getMainFunctions())
                    + "展开结构组合，结构树、标准件、非标件和装配关系由当前任务书识别结果统一驱动。");
        }
        return project;
    }

    private List<String> missingFields(DesignProject project) {
        List<String> missing = new ArrayList<>();
        if (blank(project.getProjectTitle())) missing.add("缺少题目：请上传任务书或手动填写毕业设计题目");
        if (blank(project.getEquipmentName())) missing.add("缺少设备名称：请补充具体机械设备名称");
        if (blank(project.getDesignType())) missing.add("缺少设计类型：请补充机械结构设计、机器人结构设计、机电一体化设计等类型");
        if (project.getMainStructures() == null || project.getMainStructures().stream().filter(v -> !blank(v)).count() < 3) {
            missing.add("缺少结构信息：请补充主要机构、关键零部件或设备组成");
        }
        return missing;
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
