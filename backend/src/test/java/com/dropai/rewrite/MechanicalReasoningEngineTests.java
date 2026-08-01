package com.dropai.rewrite;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlanner;
import com.dropai.rewrite.modules.model.DesignProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalReasoningEngineTests {
    private final MechanicalDesignPlanner planner = new MechanicalDesignPlanner();

    @Test
    void complexMechanicalCasesGenerateReasoningReviewAndDeliverySignals() {
        for (DesignProject project : List.of(robotArm(), agv(), reducer(), fixture())) {
            DesignProject result = planner.plan(project);
            MechanicalDesignPlan plan = result.getMechanicalDesignPlan();

            assertFalse(plan.getEngineeringDecisionLog().getFunctionalRequirements().isEmpty());
            assertFalse(plan.getEngineeringDecisionLog().getMechanismCandidates().isEmpty());
            assertFalse(plan.getEngineeringDecisionLog().getRecommendedMechanism().isBlank());
            assertFalse(plan.getForceReport().getForcePath().isEmpty());
            assertTrue(plan.getForceReport().getEstimatedLoadN() > 0);
            assertFalse(plan.getManufacturingPlan().getProcesses().isEmpty());
            assertFalse(plan.getManufacturingPlan().getMaterialDecisions().isEmpty());
            assertTrue(plan.getDesignReviewReport().getScore() >= 60);
            assertFalse(plan.getDesignReviewReport().getChecks().isEmpty());
            assertTrue(result.getVerificationItems().contains("DecisionLog"));
            assertTrue(result.getVerificationItems().contains("ForceReport"));
            assertTrue(result.getVerificationItems().contains("ManufacturingPlan"));
            assertTrue(result.getVerificationItems().contains("DesignReviewReport"));
            assertFalse(plan.getSubsystems().isEmpty());
            assertFalse(plan.getCalculationBasis().isBlank());
        }
    }

    private DesignProject robotArm() {
        DesignProject project = project("四自由度机械臂", "机械臂抓取搬运");
        project.setMainFunctions(List.of("抓取", "搬运", "定位"));
        project.setMainStructures(List.of("底座", "大臂", "小臂", "夹爪", "关节伺服"));
        return project;
    }

    private DesignProject agv() {
        DesignProject project = project("AGV小车结构设计", "AGV差速移动平台");
        project.setMainFunctions(List.of("移动", "承载", "转向"));
        project.setMainStructures(List.of("底盘", "驱动轮", "从动轮", "电池仓", "传感器支架"));
        return project;
    }

    private DesignProject reducer() {
        DesignProject project = project("二级齿轮减速机构", "齿轮减速传动");
        project.setMainFunctions(List.of("减速", "增矩", "传动"));
        project.setMainStructures(List.of("输入轴", "齿轮副", "输出轴", "箱体", "轴承"));
        return project;
    }

    private DesignProject fixture() {
        DesignProject project = project("自动夹具", "丝杆夹紧夹具");
        project.setMainFunctions(List.of("夹紧", "定位", "释放"));
        project.setMainStructures(List.of("底板", "丝杆", "夹爪", "导轨", "手轮"));
        project.getExplicitParameters().add(new DesignProject.Parameter("工件重量", 5, "kg", "task", "fixture test load"));
        project.getExplicitParameters().add(new DesignProject.Parameter("安全系数", 2.0, "", "task", "fixture safety factor"));
        return project;
    }

    private DesignProject project(String title, String type) {
        DesignProject project = new DesignProject();
        project.setProjectTitle(title);
        project.setEquipmentName(title);
        project.setDesignType(type);
        return project;
    }
}
