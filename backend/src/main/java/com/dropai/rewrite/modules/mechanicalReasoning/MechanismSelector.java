package com.dropai.rewrite.modules.mechanicalReasoning;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MechanismSelector {
    public MechanismDecision select(DesignProject project) {
        String evidence = evidence(project);
        if (containsAny(evidence, "夹具", "夹紧", "fixture", "clamp")) {
            return new MechanismDecision(
                    List.of("丝杆夹紧机构: 精度高、保压可靠、速度中等", "气缸夹紧机构: 速度快、需要气源", "连杆增力机构: 夹紧力大、结构调试复杂"),
                    "丝杆夹紧机构",
                    List.of("更适合毕业设计中的力学计算和尺寸校核", "维护简单，便于表达装配关系", "无需额外气源系统"));
        }
        if (containsAny(evidence, "agv", "小车", "移动", "行走")) {
            return new MechanismDecision(
                    List.of("差速轮式底盘: 控制简单、转弯半径小", "麦克纳姆轮底盘: 机动性高、成本较高", "履带底盘: 越障能力强、能耗较高"),
                    "差速轮式底盘",
                    List.of("适合室内AGV小车", "零部件标准化程度高", "结构、控制和维护成本平衡"));
        }
        if (containsAny(evidence, "减速", "齿轮", "传动", "reducer", "gear")) {
            return new MechanismDecision(
                    List.of("圆柱齿轮减速机构: 效率高、加工成熟", "蜗轮蜗杆机构: 结构紧凑、有自锁可能", "带传动: 成本低、传动精度较低"),
                    "圆柱齿轮减速机构",
                    List.of("便于传动比、齿宽和轴系计算", "效率和寿命更适合连续工作", "工程图表达清晰"));
        }
        if (containsAny(evidence, "机械臂", "robot arm", "抓取", "搬运")) {
            return new MechanismDecision(
                    List.of("关节式机械臂: 工作空间大、控制复杂", "直角坐标机械臂: 定位清晰、占地较大", "连杆机械臂: 结构轻、轨迹受限"),
                    "关节式机械臂",
                    List.of("适合抓取和搬运任务", "模块化关节便于CAD装配", "能形成完整运动链分析"));
        }
        if (containsAny(evidence, "输送", "conveyor", "belt")) {
            return new MechanismDecision(
                    List.of("带传动输送机构: 连续输送、结构成熟", "链传动输送机构: 承载高、噪声较大", "辊筒输送机构: 维护方便、适合规则物料"),
                    "带传动输送机构",
                    List.of("符合连续输送需求", "可直接建立驱动、张紧和支承子系统", "易于生成BOM和工程图"));
        }
        return new MechanismDecision(
                List.of("连杆机构: 适合往复运动", "齿轮机构: 适合定比传动", "丝杆机构: 适合直线定位", "带传动: 适合连续输送"),
                "模块化机械执行机构",
                List.of("任务信息不足时优先保证结构完整性", "保留驱动、执行、支承和防护模块", "后续可按参数继续细化"));
    }

    private String evidence(DesignProject project) {
        if (project == null) return "";
        return String.join(" ", project.getProjectTitle(), project.getEquipmentName(), project.getDesignType(),
                String.join(" ", project.getMainFunctions()), String.join(" ", project.getMainStructures())).toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public record MechanismDecision(List<String> candidates, String recommendedMechanism, List<String> reasons) {
    }
}
