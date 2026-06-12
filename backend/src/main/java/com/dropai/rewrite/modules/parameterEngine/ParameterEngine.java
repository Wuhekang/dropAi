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
        addSuggested(project, "支撑数量", 4, "个", "根据设备外形尺寸与稳定性要求建议");
        addSuggested(project, "进出口尺寸", "600×500", "mm", "根据通用机械设备接口空间建议");
        addSuggested(project, "连接方式", "焊接与螺栓连接", "", "兼顾制造、装配与维护要求");
        addSuggested(project, "制造方式", "板材下料、折弯与焊接", "", "适用于通用机械结构方案阶段");
        addSuggested(project, "工作温度", 20, "℃", "任务资料未明确时采用常温工况");
        addSuggested(project, "运行速度", 1.0, "m/s", "任务资料未明确时采用的方案初值");
        return project;
    }
    private void addSuggested(DesignProject project, String name, Object value, String unit, String basis) {
        if (project.allParameters().stream().noneMatch(item -> name.equals(item.getName()))) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, null, basis));
        }
    }
}
