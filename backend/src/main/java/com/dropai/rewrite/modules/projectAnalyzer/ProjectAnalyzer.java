package com.dropai.rewrite.modules.projectAnalyzer;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectAnalyzer {
    public DesignProject analyze(DesignProject project) {
        if (project == null) project = new DesignProject();
        if (blank(project.getProjectTitle())) project.setProjectTitle("机械设备结构设计");
        if (blank(project.getEquipmentName())) project.setEquipmentName("机械设备");
        if (blank(project.getDesignType())) project.setDesignType("机械结构设计");
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
            project.setWorkingPrinciple((blank(project.getEquipmentName()) ? "整机" : project.getEquipmentName())
                    + "围绕" + String.join("、", project.getMainFunctions()) + "展开机构组合，结构、标准件、非标件和装配关系由任务书识别结果统一驱动。");
        }
        return project;
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

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
