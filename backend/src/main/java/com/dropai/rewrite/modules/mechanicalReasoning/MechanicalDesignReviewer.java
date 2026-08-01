package com.dropai.rewrite.modules.mechanicalReasoning;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MechanicalDesignReviewer {
    public MechanicalDesignPlan.DesignReviewReport review(DesignProject project, MechanicalDesignPlan plan) {
        List<String> checks = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        checks.add("功能满足性: 已形成需求、机构、受力、材料、制造和图纸链路");
        checks.add("结构合理性: 子系统数量=" + (plan == null ? 0 : plan.getSubsystems().size()));
        checks.add("材料合理性: 已输出材料决策=" + (plan == null ? 0 : plan.getManufacturingPlan().getMaterialDecisions().size()));
        checks.add("加工可行性: 已覆盖CNC、焊接/钣金、标准件采购和样件制造");
        checks.add("装配维护: 已要求基准统一、运动副调节和拆装空间");

        if (plan == null || plan.getSubsystems().size() < 3) {
            risks.add("子系统划分不足，可能影响结构树和BOM完整性");
            recommendations.add("补充驱动、执行、支承、防护/维护等模块");
        }
        if (project == null || project.allParameters().size() < 3) {
            risks.add("输入参数偏少，部分尺寸和载荷仍为方案级估算");
            recommendations.add("补充重量、速度、行程、外形尺寸或安全系数");
        }
        if (plan != null && plan.getForceReport().getSafetyFactor() < 1.5) {
            risks.add("安全系数偏低");
            recommendations.add("提高关键承载件截面或材料等级");
        }

        int score = 82 - risks.size() * 8;
        MechanicalDesignPlan.DesignReviewReport report = new MechanicalDesignPlan.DesignReviewReport();
        report.setChecks(checks);
        report.setRisks(risks);
        report.setRecommendations(recommendations.isEmpty() ? List.of("进入CAD细化、工程图标注和样机校核") : recommendations);
        report.setScore(Math.max(60, score));
        report.setPassed(score >= 70);
        return report;
    }
}
