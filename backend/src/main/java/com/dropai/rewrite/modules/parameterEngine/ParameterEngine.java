package com.dropai.rewrite.modules.parameterEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

@Service
public class ParameterEngine {
    public DesignProject normalize(DesignProject project) {
        addSuggested(project, "总长", 4200, "mm", "任务资料未明确时采用的通用方案初值");
        addSuggested(project, "总宽", 1600, "mm", "任务资料未明确时采用的通用方案初值");
        addSuggested(project, "总高", 1800, "mm", "任务资料未明确时采用的通用方案初值");
        addSuggested(project, "设计载荷", 1200, "kg", "用于通用机械结构初步计算的建议值");
        addSuggested(project, "安全系数", 1.8, "", "用于方案阶段结构计算的建议值");
        addSuggested(project, "材料", "Q235B", "", "方案阶段常用结构材料，需按任务书确认");
        return project;
    }
    private void addSuggested(DesignProject project, String name, Object value, String unit, String basis) {
        if (project.allParameters().stream().noneMatch(item -> name.equals(item.getName()))) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, null, basis));
        }
    }
}
