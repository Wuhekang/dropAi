package com.dropai.rewrite.modules.mechanicalReasoning;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ForceAnalysisReasoner {
    public MechanicalDesignPlan.ForceReport analyze(DesignProject project, MechanicalDesignPlan plan) {
        double massKg = project == null ? 20 : project.number("工件重量", project.number("整机重量", 20));
        double loadN = Math.max(50, massKg * 9.8);
        MechanicalDesignPlan.ForceReport report = new MechanicalDesignPlan.ForceReport();
        report.setEstimatedLoadN(loadN);
        report.setSafetyFactor(project == null ? 2.0 : project.number("安全系数", 2.0));
        report.setLoadModel("按重力载荷、传动载荷和安装约束建立方案级载荷模型");

        List<String> path = new ArrayList<>();
        path.add("外部载荷/工件载荷");
        if (plan != null && !plan.getSubsystems().isEmpty()) {
            path.addAll(plan.getSubsystems().stream().limit(4).map(MechanicalDesignPlan.SubsystemPlan::getName).toList());
        } else {
            path.add("执行机构");
            path.add("支承结构");
            path.add("机架/底座");
        }
        report.setForcePath(path);
        report.setCriticalParts(path.stream().skip(1).limit(3).toList());
        return report;
    }
}
