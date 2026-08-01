package com.dropai.rewrite.modules.mechanicalOptimization;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AlternativeDesignGenerator {
    public List<MechanicalDesignPlan.AlternativeDesign> generate(DesignProject project, MechanicalDesignPlan plan) {
        String evidence = evidence(project);
        if (hasAny(evidence, "机械臂", "robot arm", "抓取", "搬运")) {
            return List.of(
                    alternative("方案A-伺服关节机械臂", "伺服关节驱动", List.of("底座", "伺服关节", "大臂", "小臂", "夹爪"),
                            List.of("定位精度高", "控制成熟", "适合CAD模块化表达"), List.of("成本较高", "控制调试复杂"), 0.88, 0.78, 32),
                    alternative("方案B-液压机械臂", "液压驱动", List.of("液压站", "油缸", "连杆臂", "回转支座"),
                            List.of("输出力大", "抗冲击能力强"), List.of("维护复杂", "泄漏风险", "系统成本高"), 0.8, 0.62, 48),
                    alternative("方案C-气动机械臂", "气动驱动", List.of("气缸", "导向臂", "夹爪", "缓冲器"),
                            List.of("动作快", "结构简单", "成本低"), List.of("定位精度有限", "需要气源"), 0.76, 0.82, 24)
            );
        }
        if (hasAny(evidence, "agv", "小车", "移动", "底盘")) {
            return List.of(
                    alternative("方案A-轮式AGV", "差速轮式底盘", List.of("底盘", "驱动轮", "从动轮", "电池仓", "传感器支架"),
                            List.of("成本低", "控制简单", "维护方便"), List.of("越障能力一般"), 0.84, 0.86, 42),
                    alternative("方案B-履带AGV", "履带底盘", List.of("履带", "驱动轮", "张紧轮", "支重轮", "底盘"),
                            List.of("通过性好", "接地比压低"), List.of("重量高", "能耗高", "维护工作量大"), 0.82, 0.68, 68),
                    alternative("方案C-麦克纳姆轮AGV", "麦克纳姆轮全向底盘", List.of("麦克纳姆轮", "独立电机", "底盘", "悬挂支架"),
                            List.of("全向移动", "用户感知强", "适合复杂路径"), List.of("成本较高", "地面适应性较敏感"), 0.86, 0.78, 48)
            );
        }
        if (hasAny(evidence, "夹具", "夹紧", "fixture", "clamp")) {
            return List.of(
                    alternative("方案A-气缸夹具", "气缸夹紧", List.of("底板", "气缸", "夹爪", "导向杆", "限位块"),
                            List.of("动作快", "自动化程度高"), List.of("需要气源", "保压依赖气路"), 0.8, 0.76, 18),
                    alternative("方案B-丝杆夹具", "丝杆夹紧", List.of("底板", "丝杆", "夹爪", "导轨", "手轮/电机"),
                            List.of("夹紧稳定", "精度高", "维护简单"), List.of("速度较慢"), 0.88, 0.88, 22),
                    alternative("方案C-凸轮夹具", "凸轮快速夹紧", List.of("凸轮", "压板", "底座", "定位销"),
                            List.of("结构紧凑", "操作快"), List.of("行程和夹紧力调节范围有限"), 0.78, 0.84, 16)
            );
        }
        if (hasAny(evidence, "减速", "齿轮", "reducer", "gear")) {
            return List.of(
                    alternative("方案A-圆柱齿轮减速", "二级圆柱齿轮", List.of("输入轴", "中间轴", "输出轴", "齿轮副", "箱体"),
                            List.of("效率高", "寿命好", "计算链完整"), List.of("加工精度要求较高"), 0.88, 0.72, 36),
                    alternative("方案B-蜗轮蜗杆减速", "蜗轮蜗杆", List.of("蜗杆", "蜗轮", "箱体", "轴承"),
                            List.of("结构紧凑", "可具备自锁"), List.of("效率较低", "发热明显"), 0.78, 0.7, 34),
                    alternative("方案C-同步带减速", "同步带传动", List.of("同步带", "带轮", "张紧机构", "防护罩"),
                            List.of("噪声低", "成本低", "维护方便"), List.of("承载能力较低", "传动刚度一般"), 0.74, 0.86, 20)
            );
        }
        List<String> structures = plan == null || plan.getSubsystems().isEmpty()
                ? List.of("驱动模块", "执行模块", "支承模块", "防护模块")
                : plan.getSubsystems().stream().limit(5).map(MechanicalDesignPlan.SubsystemPlan::getName).toList();
        return List.of(
                alternative("方案A-标准模块化方案", "模块化机械执行机构", structures,
                        List.of("实现稳妥", "结构清晰"), List.of("创新性一般"), 0.82, 0.82, 30),
                alternative("方案B-轻量化方案", "轻量化支承执行机构", structures,
                        List.of("重量低", "响应快"), List.of("刚度余量较小"), 0.76, 0.78, 22),
                alternative("方案C-低成本方案", "简化传动执行机构", structures,
                        List.of("成本低", "加工简单"), List.of("性能余量较小"), 0.72, 0.84, 28)
        );
    }

    private MechanicalDesignPlan.AlternativeDesign alternative(String name, String mechanism, List<String> structure,
                                                               List<String> advantages, List<String> disadvantages,
                                                               double reliability, double maintainability,
                                                               double weightKg) {
        MechanicalDesignPlan.AlternativeDesign design = new MechanicalDesignPlan.AlternativeDesign();
        design.setName(name);
        design.setMechanism(mechanism);
        design.setStructure(new ArrayList<>(structure));
        design.setAdvantages(new ArrayList<>(advantages));
        design.setDisadvantages(new ArrayList<>(disadvantages));
        design.setReliability(reliability);
        design.setMaintainability(maintainability);
        design.setEstimatedWeightKg(weightKg);
        return design;
    }

    private String evidence(DesignProject project) {
        if (project == null) return "";
        return String.join(" ", project.getProjectTitle(), project.getEquipmentName(), project.getDesignType(),
                String.join(" ", project.getMainFunctions()), String.join(" ", project.getMainStructures())).toLowerCase(Locale.ROOT);
    }

    private boolean hasAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
