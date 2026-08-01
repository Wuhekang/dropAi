package com.dropai.rewrite;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlanner;
import com.dropai.rewrite.modules.model.DesignProject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalOptimizationEngineTests {
    private final MechanicalDesignPlanner planner = new MechanicalDesignPlanner();

    @Test
    void mechanicalV3GeneratesAlternativesScoreCardsAndOptimizationReport() {
        for (DesignProject project : List.of(robotArm(), agv(), reducer(), fixture())) {
            MechanicalDesignPlan plan = planner.plan(project).getMechanicalDesignPlan();

            assertTrue(plan.getAlternativeDesigns().size() >= 3);
            assertEquals(plan.getAlternativeDesigns().size(), plan.getScoreCards().size());
            assertFalse(plan.getOptimizationReport().getSelectedDesign().isBlank());
            assertFalse(plan.getOptimizationReport().getSelectedMechanism().isBlank());
            assertFalse(plan.getOptimizationReport().getComparisonSummary().isEmpty());
            assertFalse(plan.getOptimizationReport().getSelectionReasons().isEmpty());
            assertTrue(plan.getScoreCards().stream().allMatch(card -> card.getTotalScore() > 0));
            assertTrue(plan.getAlternativeDesigns().stream()
                    .allMatch(design -> design.getCostEstimate().getTotalCost() > 0));
        }
    }

    @Test
    void fixtureOptimizationComparesCylinderScrewAndCamInsteadOfFirstOnly() {
        DesignProject result = planner.plan(fixture());
        MechanicalDesignPlan plan = result.getMechanicalDesignPlan();
        String names = String.join(" ", plan.getAlternativeDesigns().stream().map(MechanicalDesignPlan.AlternativeDesign::getMechanism).toList());

        assertTrue(names.contains("气缸"));
        assertTrue(names.contains("丝杆"));
        assertTrue(names.contains("凸轮"));
        assertTrue(plan.getScoreCards().get(0).getTotalScore() >= plan.getScoreCards().get(1).getTotalScore());
        assertTrue(result.getVerificationItems().contains("AlternativeDesign"));
        assertTrue(result.getVerificationItems().contains("ScoreCard"));
        assertTrue(result.getVerificationItems().contains("OptimizationReport"));
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
