package com.dropai.rewrite.modules.mechanicalReasoning;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RequirementReasoner {
    public RequirementReport analyze(DesignProject project) {
        List<String> functions = new ArrayList<>();
        if (project != null) {
            functions.addAll(project.getMainFunctions());
        }
        if (functions.isEmpty()) {
            functions.add("完成指定机械任务的主要动作");
            functions.add("提供稳定支承、传动和安装接口");
        }

        List<String> constraints = new ArrayList<>();
        constraints.add("满足机械强度和刚度要求");
        constraints.add("适合常规加工、装配和维护");
        constraints.add("关键运动副需要保留调整和润滑空间");

        List<String> objectives = new ArrayList<>();
        objectives.add("结构可靠");
        objectives.add("成本可控");
        objectives.add("制造路径清晰");
        objectives.add("可输出CAD、工程图、BOM和审核报告");
        return new RequirementReport(functions, constraints, objectives);
    }

    public record RequirementReport(List<String> functionalRequirements, List<String> constraints,
                                    List<String> designObjectives) {
    }
}
