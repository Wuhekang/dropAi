package com.dropai.rewrite.modules.mechanicalDesignPlanner;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MechanicalDesignPlanner {
    public DesignProject plan(DesignProject project) {
        if (project == null) return new DesignProject();
        MechanicalDesignPlan plan = new MechanicalDesignPlan();
        plan.setProjectName(firstNonBlank(project.getProjectTitle(), project.getEquipmentName(), "机械毕业设计项目"));

        String evidence = evidence(project);
        if (containsAny(evidence, "油罐", "爬壁", "吸附", "履带", "wall", "tank", "climbing")) {
            planWallClimbingRobot(project, plan);
        } else if (containsAny(evidence, "沉降", "除尘", "灰斗", "气流", "settling", "chamber")) {
            planSettlingChamber(project, plan);
        } else if (containsAny(evidence, "输送", "传送", "皮带", "带式", "conveyor", "belt")) {
            planBeltConveyor(project, plan);
        } else {
            planGenericMachine(project, plan);
        }

        copyExplicitParameters(project, plan);
        normalizeProjectFromPlan(project, plan);
        plan.setConfidence(confidence(project, plan));
        project.setMechanicalDesignPlan(plan);
        project.getEnhancementNotes().add("MechanicalDesignPlanner已生成机械设计方案，后续结构树、零件、装配、图纸和论文均基于该方案继续生成。");
        return project;
    }

    private void planWallClimbingRobot(DesignProject project, MechanicalDesignPlan plan) {
        plan.setDesignPurpose("完成油罐壁面检测、稳定吸附移动和检测模块安装。");
        plan.setWorkingPrinciple("永磁吸附模块提供壁面附着力，履带移动机构沿罐壁行走，检测云台或传感器支架完成表面检测。");
        plan.setMechanismType("永磁吸附履带移动机构");
        plan.setCalculationBasis("按整机重量、吸附安全系数、履带接触长度和电机牵引力进行方案级估算。");
        addParameter(project, plan, "整机长度", 900, "mm", "油罐检测爬壁机器人毕业设计常用整机尺度", true);
        addParameter(project, plan, "整机宽度", 520, "mm", "双履带吸附移动机构布置宽度", true);
        addParameter(project, plan, "整机高度", 260, "mm", "检测云台与电控箱安装空间", true);
        addParameter(project, plan, "履带宽度", 80, "mm", "保证接触面积和转弯空间", true);
        addParameter(project, plan, "吸附安全系数", 2.0, "", "壁面移动设备需考虑滑移和振动余量", true);
        addParameter(project, plan, "驱动电机功率", 120, "W", "低速爬壁移动和检测负载估算", true);
        material(plan, "机架", "6061铝合金");
        material(plan, "履带", "耐磨橡胶");
        material(plan, "吸附模块", "钕铁硼永磁体");
        subsystem(plan, "移动机构", "完成罐壁表面行走", List.of("左履带组件", "右履带组件", "主动轮", "从动轮", "支重轮"), "橡胶/45钢", "task: wall-climbing robot", 0.9);
        subsystem(plan, "吸附机构", "提供壁面附着力并防止滑落", List.of("永磁吸附块", "磁钢安装座", "调距支架"), "钕铁硼/铝合金", "task: magnetic adhesion", 0.88);
        subsystem(plan, "驱动机构", "将电机转矩传递至履带主动轮", List.of("直流减速电机", "联轴器", "传动轴", "轴承座"), "45钢/Q235", "task: drive chain", 0.86);
        subsystem(plan, "检测机构", "安装摄像头或检测传感器", List.of("检测云台", "传感器支架", "防护罩"), "铝合金/工程塑料", "task: inspection payload", 0.82);
        subsystem(plan, "控制与电源机构", "布置控制板、电池和线束", List.of("电控箱", "电池仓", "线束固定座"), "ABS/铝合金", "task: control box", 0.8);
    }

    private void planSettlingChamber(DesignProject project, MechanicalDesignPlan plan) {
        plan.setDesignPurpose("利用重力沉降完成含尘气流分离，并保证箱体、灰斗和检修结构满足毕业设计图纸表达。");
        plan.setWorkingPrinciple("含尘气流进入箱体后流速降低，颗粒在重力作用下沉降至灰斗，净化后的气体从出口排出。");
        plan.setMechanismType("重力沉降分离箱体");
        plan.setCalculationBasis("按处理风量、气流速度、停留时间、箱体容积和灰斗角度进行方案级估算。");
        addParameter(project, plan, "箱体长度", 2500, "mm", "重力沉降室停留时间与布置空间估算", true);
        addParameter(project, plan, "箱体宽度", 1200, "mm", "保证气流截面积", true);
        addParameter(project, plan, "箱体高度", 1800, "mm", "沉降空间和灰斗安装高度", true);
        addParameter(project, plan, "设计风量", 3000, "m3/h", "毕业设计方案级处理风量", true);
        addParameter(project, plan, "箱体板厚", 4, "mm", "Q235焊接箱体常用板厚", true);
        addParameter(project, plan, "灰斗角度", 55, "deg", "颗粒下滑和排灰要求", true);
        material(plan, "箱体", "Q235钢板");
        material(plan, "检修门", "Q235钢板+密封条");
        material(plan, "支撑架", "Q235角钢");
        subsystem(plan, "箱体组件", "形成沉降空间和气流通道", List.of("上箱体", "侧板", "顶板", "加强筋"), "Q235钢板", "task: settling chamber shell", 0.9);
        subsystem(plan, "进出口组件", "完成含尘气体导入和净化气体排出", List.of("进口法兰", "出口接管", "导流板"), "Q235钢板", "task: inlet outlet", 0.88);
        subsystem(plan, "沉降与排灰组件", "收集沉降颗粒并排出灰尘", List.of("灰斗", "排灰口", "密封盖板"), "Q235钢板", "task: dust hopper", 0.86);
        subsystem(plan, "支撑组件", "承载箱体重量并便于安装", List.of("支腿", "底座", "横向拉杆"), "Q235型钢", "task: support frame", 0.84);
        subsystem(plan, "检修组件", "方便内部清理和维护", List.of("检修门", "铰链", "压紧手柄"), "Q235钢板/标准件", "task: maintenance", 0.8);
    }

    private void planBeltConveyor(DesignProject project, MechanicalDesignPlan plan) {
        plan.setDesignPurpose("完成物料连续输送，并满足驱动、张紧、支撑和安全防护要求。");
        plan.setWorkingPrinciple("电机经减速机带动主动滚筒转动，输送带依靠摩擦力运行，托辊支撑物料并通过张紧机构保持带面稳定。");
        plan.setMechanismType("带式连续输送机构");
        plan.setCalculationBasis("按输送长度、带宽、输送速度、载荷和滚筒直径进行方案级估算。");
        addParameter(project, plan, "输送长度", 6000, "mm", "本科带式输送机方案常用长度", true);
        addParameter(project, plan, "带宽", 500, "mm", "中小型物料输送带宽", true);
        addParameter(project, plan, "输送速度", 1.0, "m/s", "普通水平输送速度", true);
        addParameter(project, plan, "驱动电机功率", 2.2, "kW", "带式输送机方案级功率估算", true);
        addParameter(project, plan, "滚筒直径", 159, "mm", "中小带宽滚筒规格", true);
        material(plan, "机架", "Q235型钢");
        material(plan, "输送带", "橡胶帆布带");
        material(plan, "滚筒", "45钢/Q235焊接件");
        subsystem(plan, "机架组件", "支撑整机并安装滚筒与托辊", List.of("纵梁", "横梁", "支腿", "底脚板"), "Q235型钢", "task: conveyor frame", 0.88);
        subsystem(plan, "输送带组件", "承载并连续输送物料", List.of("输送带", "上托辊", "下托辊", "挡边"), "橡胶/45钢", "task: belt carrying", 0.9);
        subsystem(plan, "驱动组件", "提供输送带运行动力", List.of("电机", "减速机", "联轴器", "主动滚筒"), "标准件/45钢", "task: drive station", 0.88);
        subsystem(plan, "张紧组件", "调整输送带初张力并补偿伸长", List.of("螺旋张紧座", "改向滚筒", "滑座"), "Q235/45钢", "task: tension station", 0.84);
        subsystem(plan, "防护与清扫组件", "减少跑偏和粘料", List.of("防护罩", "清扫器", "跑偏挡轮"), "Q235/聚氨酯", "task: safety cleanup", 0.8);
    }

    private void planGenericMachine(DesignProject project, MechanicalDesignPlan plan) {
        plan.setDesignPurpose("根据任务书完成机械结构方案、参数、材料和后续图纸生成准备。");
        plan.setWorkingPrinciple(firstNonBlank(project.getWorkingPrinciple(), "由机架、执行机构、驱动机构和控制安装结构共同完成设计任务。"));
        plan.setMechanismType(firstNonBlank(project.getDesignType(), "通用机械装置"));
        plan.setCalculationBasis("按任务书给定参数和毕业设计常用机械结构进行方案级估算。");
        addParameter(project, plan, "总体长度", 1200, "mm", "任务书信息不足时的方案级补全", true);
        addParameter(project, plan, "总体宽度", 800, "mm", "任务书信息不足时的方案级补全", true);
        addParameter(project, plan, "总体高度", 900, "mm", "任务书信息不足时的方案级补全", true);
        material(plan, "机架", "Q235型钢");
        material(plan, "安装板", "Q235钢板");
        List<String> structures = project.getMainStructures().isEmpty()
                ? List.of("机架组件", "执行组件", "驱动组件", "连接组件", "防护组件")
                : project.getMainStructures().stream().limit(8).toList();
        for (String structure : structures) {
            subsystem(plan, structure, "承担" + structure + "相关功能", List.of(structure + "主体", structure + "安装件"), "Q235/45钢", "task: inferred structure", 0.72);
        }
    }

    private void copyExplicitParameters(DesignProject project, MechanicalDesignPlan plan) {
        for (DesignProject.Parameter parameter : project.allParameters()) {
            if (parameter.getName() == null || parameter.getName().isBlank()) continue;
            plan.getDesignParameters().putIfAbsent(parameter.getName(), parameterValue(parameter.getValue(),
                    parameter.getUnit(), firstNonBlank(parameter.getSource(), "task"), parameter.getBasis(), false));
        }
    }

    private void normalizeProjectFromPlan(DesignProject project, MechanicalDesignPlan plan) {
        if (blank(project.getProjectTitle())) project.setProjectTitle(plan.getProjectName());
        if (blank(project.getEquipmentName())) project.setEquipmentName(plan.getProjectName());
        if (blank(project.getWorkingPrinciple())) project.setWorkingPrinciple(plan.getWorkingPrinciple());
        if (blank(project.getDesignType())) project.setDesignType(plan.getMechanismType());
        List<String> subsystemNames = plan.getSubsystems().stream().map(MechanicalDesignPlan.SubsystemPlan::getName).toList();
        if (project.getMainStructures().isEmpty() || project.getMainStructures().size() < 3) {
            project.setMainStructures(subsystemNames);
        }
        for (MechanicalDesignPlan.SubsystemPlan subsystem : plan.getSubsystems()) {
            for (String component : subsystem.getComponents()) {
                if (!project.getStandardParts().contains(component) && isLikelyStandardPart(component)) {
                    project.getStandardParts().add(component);
                }
            }
        }
    }

    private void addParameter(DesignProject project, MechanicalDesignPlan plan, String name, Object value, String unit, String basis, boolean generatedByAi) {
        Object existing = project.allParameters().stream()
                .filter(parameter -> name.equals(parameter.getName()))
                .map(DesignProject.Parameter::getValue)
                .findFirst()
                .orElse(value);
        boolean fromProject = project.allParameters().stream().anyMatch(parameter -> name.equals(parameter.getName()));
        plan.getDesignParameters().put(name, parameterValue(existing, unit, fromProject ? "task" : "MechanicalDesignPlanner", basis, generatedByAi && !fromProject));
        if (!fromProject) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, "MechanicalDesignPlanner generatedByAI=true", basis));
            plan.getCompletedRequirements().add(name + "由MechanicalDesignPlanner按任务类型补全");
        }
    }

    private Map<String, Object> parameterValue(Object value, String unit, String source, String basis, boolean generatedByAi) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("unit", unit == null ? "" : unit);
        item.put("source", source == null ? "" : source);
        item.put("basis", basis == null ? "" : basis);
        item.put("generatedByAI", generatedByAi);
        return item;
    }

    private void material(MechanicalDesignPlan plan, String key, String value) {
        plan.getMaterialSelection().put(key, value);
    }

    private void subsystem(MechanicalDesignPlan plan, String name, String function, List<String> components,
                           String material, String source, double confidence) {
        MechanicalDesignPlan.SubsystemPlan subsystem = new MechanicalDesignPlan.SubsystemPlan(name, function, components, material, source, confidence);
        subsystem.getParameters().put("componentCount", components.size());
        plan.getSubsystems().add(subsystem);
    }

    private double confidence(DesignProject project, MechanicalDesignPlan plan) {
        double score = 0.52;
        if (!blank(project.getProjectTitle())) score += 0.08;
        if (!blank(project.getEquipmentName())) score += 0.08;
        score += Math.min(0.12, project.allParameters().size() * 0.02);
        score += Math.min(0.12, plan.getSubsystems().size() * 0.02);
        if (!plan.getCompletedRequirements().isEmpty()) score -= 0.04;
        return Math.max(0.35, Math.min(0.95, score));
    }

    private String evidence(DesignProject project) {
        String text = String.join(" ",
                nullToEmpty(project.getProjectTitle()),
                nullToEmpty(project.getEquipmentName()),
                nullToEmpty(project.getDesignType()),
                nullToEmpty(project.getProjectCategory()),
                nullToEmpty(project.getWorkingPrinciple()),
                String.join(" ", project.getMainFunctions()),
                String.join(" ", project.getMainStructures()),
                String.join(" ", project.getDetailFeatures()));
        return text.toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isLikelyStandardPart(String name) {
        return containsAny(name, "电机", "减速机", "联轴器", "轴承", "螺栓", "螺母", "垫片", "托辊", "滚筒", "铰链", "手柄");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!blank(value)) return value;
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
