package com.dropai.rewrite;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlanner;
import com.dropai.rewrite.modules.model.DesignProject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicalDesignPlannerTests {
    private final MechanicalDesignPlanner planner = new MechanicalDesignPlanner();

    @Test
    void plannerBuildsDifferentPlansForRequiredMechanicalTasks() {
        DesignProject robot = planner.plan(project("油罐检测爬壁机器人结构设计",
                List.of("油罐壁面爬行", "永磁吸附", "检测云台安装"),
                List.of("履带机构", "永磁吸附模块", "驱动电机")));
        DesignProject chamber = planner.plan(project("重力沉降室设计",
                List.of("含尘气体沉降", "排灰", "检修维护"),
                List.of("箱体", "灰斗", "进出口法兰")));
        DesignProject conveyor = planner.plan(project("带式输送机结构设计",
                List.of("连续输送物料", "驱动滚筒传动", "张紧调节"),
                List.of("输送带", "主动滚筒", "托辊", "机架")));

        MechanicalDesignPlan robotPlan = robot.getMechanicalDesignPlan();
        MechanicalDesignPlan chamberPlan = chamber.getMechanicalDesignPlan();
        MechanicalDesignPlan conveyorPlan = conveyor.getMechanicalDesignPlan();

        assertEquals("永磁吸附履带移动机构", robotPlan.getMechanismType());
        assertEquals("重力沉降分离箱体", chamberPlan.getMechanismType());
        assertEquals("带式连续输送机构", conveyorPlan.getMechanismType());
        assertNotEquals(subsystemNames(robotPlan), subsystemNames(chamberPlan));
        assertNotEquals(subsystemNames(chamberPlan), subsystemNames(conveyorPlan));
        assertTrue(subsystemNames(robotPlan).contains("吸附机构"));
        assertTrue(subsystemNames(chamberPlan).contains("沉降与排灰组件"));
        assertTrue(subsystemNames(conveyorPlan).contains("张紧组件"));
    }

    @Test
    void sparseTaskReceivesTrackedAiParameterCompletion() {
        DesignProject conveyor = new DesignProject();
        conveyor.setProjectTitle("设计一种输送机");

        DesignProject result = planner.plan(conveyor);

        assertEquals("带式连续输送机构", result.getMechanicalDesignPlan().getMechanismType());
        assertFalse(result.getSuggestedParameters().isEmpty());
        assertTrue(result.getSuggestedParameters().stream()
                .allMatch(parameter -> parameter.getSource() != null && parameter.getSource().contains("generatedByAI=true")));
        assertTrue(result.getMechanicalDesignPlan().getCompletedRequirements().stream()
                .anyMatch(item -> item.contains("输送长度")));
        assertTrue(result.getMechanicalDesignPlan().getConfidence() > 0.4);
    }

    @Test
    void plannerDoesNotCreateSingleUniversalSubsystemTemplate() {
        DesignProject robot = planner.plan(project("油罐检测爬壁机器人",
                List.of("爬壁", "检测"), List.of("履带", "磁吸附")));
        DesignProject chamber = planner.plan(project("重力沉降室",
                List.of("沉降", "除尘"), List.of("箱体", "灰斗")));

        Set<String> robotNames = subsystemNames(robot.getMechanicalDesignPlan());
        Set<String> chamberNames = subsystemNames(chamber.getMechanicalDesignPlan());

        assertTrue(robotNames.size() >= 5);
        assertTrue(chamberNames.size() >= 5);
        assertFalse(robotNames.contains("箱体组件"));
        assertFalse(chamberNames.contains("移动机构"));
    }

    private DesignProject project(String title, List<String> functions, List<String> structures) {
        DesignProject project = new DesignProject();
        project.setProjectTitle(title);
        project.setEquipmentName(title);
        project.setMainFunctions(functions);
        project.setMainStructures(structures);
        return project;
    }

    private Set<String> subsystemNames(MechanicalDesignPlan plan) {
        return plan.getSubsystems().stream().map(MechanicalDesignPlan.SubsystemPlan::getName).collect(Collectors.toSet());
    }
}
