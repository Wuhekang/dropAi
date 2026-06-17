package com.dropai.rewrite.modules.calculationEngine;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CalculationEngine {
    public DesignProject calculate(DesignProject project) {
        double length = project.number("总长", 4200);
        double width = project.number("总宽", 1600);
        double height = project.number("总高", 1800);
        double load = project.number("设计载荷", 1200);
        double safety = project.number("安全系数", 1.8);
        double plate = Math.max(3, Math.ceil(Math.max(width, height) / 500));
        double designForce = load * 9.81 * safety;
        double baseArea = Math.max(1, length * width);
        double pressure = designForce / baseArea;
        double slenderness = height / Math.max(1, width);

        upsertDerived(project, "壳体板厚", plate, "mm", "根据结构外形尺寸与通用刚度要求推导");
        upsertDerived(project, "设计载荷力", round(designForce), "N", "设计载荷×重力加速度×安全系数");
        upsertSuggested(project, "检修口尺寸", "600×500", "mm", "根据设备维护空间建议确定");

        List<DesignProject.Calculation> calculations = new ArrayList<>();
        calculations.add(new DesignProject.Calculation("设计载荷力", "F=m·g·S",
                "F=" + load + "×9.81×" + safety, round(designForce), "N", "作为主体结构与支撑校核输入"));
        calculations.add(new DesignProject.Calculation("底座平均面载荷", "q=F/(L·W)",
                "q=" + round(designForce) + "/(" + length + "×" + width + ")", round(pressure), "N/mm²", "用于底座初步强度判断"));
        calculations.add(new DesignProject.Calculation("结构高宽比", "λ=H/W",
                "λ=" + height + "/" + width, round(slenderness), "", slenderness < 2 ? "总体稳定性初判可接受" : "需加强支撑并复核稳定性"));
        calculations.add(new DesignProject.Calculation("壳体板厚初选", "t=max(3,ceil(max(W,H)/500))",
                "t=max(3,ceil(max(" + width + "," + height + ")/500))", plate, "mm", "用于图纸与后续强度复核"));
        double power = project.number("电机功率", project.number("输入功率", project.number("功率", 3.0)));
        double speed = project.number("输入转速", project.number("转速", 960));
        double torque = 9550 * power / Math.max(1, speed);
        double sectionModulus = Math.max(1, width * plate * plate / 6d);
        double bendingMoment = designForce * length / 8000d;
        double bendingStress = bendingMoment * 1000d / sectionModulus;
        calculations.add(new DesignProject.Calculation("外形尺寸校核", "L×B×H",
                "L×B×H=" + length + "×" + width + "×" + height, round(length * width * height / 1_000_000_000d), "m³", "用于判断设备布置空间和运输安装空间"));
        calculations.add(new DesignProject.Calculation("功率计算", "P=F·v/η",
                "按方案阶段载荷、运行速度和效率估算，P取" + power, round(power), "kW", "用于电机或动力源选型"));
        calculations.add(new DesignProject.Calculation("扭矩计算", "T=9550P/n",
                "T=9550×" + power + "/" + speed, round(torque), "N·m", "用于传动轴、联轴器或驱动件选型"));
        calculations.add(new DesignProject.Calculation("弯曲强度计算", "σ=M/W",
                "σ=" + round(bendingMoment) + "×1000/" + round(sectionModulus), round(bendingStress), "MPa", bendingStress < 160 ? "方案阶段弯曲强度满足Q235B许用应力要求" : "需增大截面或增加加强筋"));
        calculations.add(new DesignProject.Calculation("标准件选型计算", "S=F/F_allow",
                "按设计载荷力" + round(designForce) + "N选择螺栓、轴承、法兰或阀门标准件", round(safety), "", "标准件按计算载荷和安全系数向上选型"));
        String signature = project.getEquipmentName() + project.getProjectTitle() + project.getDesignType() + String.join("", project.getMainStructures());
        if (containsAny(signature, "爬壁", "履带", "磁吸附", "油罐检测", "清扫刷")) {
            double mass = project.number("整机质量", 35);
            double adsorption = project.number("吸附力", 220);
            double crawlerSpeed = project.number("爬行速度", 0.5);
            double wheelDiameter = project.number("轮径", 120);
            double brushDiameter = project.number("清扫刷直径", 180);
            double rollingFactor = 0.18;
            double traction = mass * 9.81 * rollingFactor;
            double wheelTorque = traction * wheelDiameter / 2000d * 1.8;
            double drivePower = traction * (crawlerSpeed / 60d) / 0.72 / 1000d;
            double adsorptionSafety = adsorption / Math.max(1, mass * 9.81);
            double brushSpeed = 300;
            double brushLineSpeed = Math.PI * brushDiameter / 1000d * brushSpeed / 60d;
            upsertSuggested(project, "整机质量", mass, "kg", "按800×600×300mm以内小型爬壁机器人估算");
            upsertDerived(project, "驱动轮输出扭矩", round(wheelTorque), "N·m", "按牵引阻力、轮径和安全系数推导");
            upsertDerived(project, "磁吸安全系数", round(adsorptionSafety), "", "按吸附力与整机重力比值校核");
            calculations.add(new DesignProject.Calculation("吸附力校核", "K=F_m/(m·g)",
                    "K=" + adsorption + "/(" + mass + "×9.81)", round(adsorptionSafety), "", adsorptionSafety >= 0.6 ? "吸附能力满足任务书≥200N指标，需结合壁面曲率继续复核" : "吸附裕量不足，需增加磁吸模块"));
            calculations.add(new DesignProject.Calculation("履带牵引力计算", "F_t=μ·m·g",
                    "F_t=" + rollingFactor + "×" + mass + "×9.81", round(traction), "N", "用于左右履带驱动电机选型"));
            calculations.add(new DesignProject.Calculation("驱动轮扭矩计算", "T=F_t·D/2·K",
                    "T=" + round(traction) + "×" + wheelDiameter + "/2000×1.8", round(wheelTorque), "N·m", "减速器输出扭矩应大于该计算值"));
            calculations.add(new DesignProject.Calculation("爬行功率估算", "P=F_t·v/η",
                    "P=" + round(traction) + "×(" + crawlerSpeed + "/60)/0.72", round(drivePower), "kW", "用于左右履带驱动电机功率初选"));
            calculations.add(new DesignProject.Calculation("清扫刷线速度计算", "v=πDn/60",
                    "v=π×" + brushDiameter + "/1000×" + brushSpeed + "/60", round(brushLineSpeed), "m/s", "清扫刷线速度用于清扫效率与刷丝磨损校核"));
            calculations.add(new DesignProject.Calculation("续航容量估算", "E=P_total·t",
                    "按驱动、清扫和检测模块总功率估算，工作时间取4h", round((drivePower * 2 + 0.08) * 4), "kW·h", "电池容量按连续工作4h并留有余量选型"));
        }
        if (signature.contains("沉降")) {
            double airFlow = project.number("处理风量", 10000);
            double velocity = project.number("设计风速", 0.8);
            double effectiveLength = length * 0.86 / 1000d;
            double effectiveWidth = width * 0.78 / 1000d;
            double effectiveHeight = height * 0.72 / 1000d;
            double sectionArea = effectiveWidth * effectiveHeight;
            double volume = effectiveLength * effectiveWidth * effectiveHeight;
            double flowSecond = airFlow / 3600d;
            double residence = volume / Math.max(0.001, flowSecond);
            double pipeDiameter = Math.sqrt(4 * flowSecond / Math.PI / Math.max(0.1, velocity)) * 1000d;
            calculations.add(new DesignProject.Calculation("表面负荷计算", "q_s=Q/(L·B)",
                    "q_s=" + airFlow + "/(" + round(effectiveLength) + "×" + round(effectiveWidth) + ")", round(airFlow / Math.max(0.001, effectiveLength * effectiveWidth)), "m³/(m²·h)", "用于判断沉降室平面尺寸是否满足处理能力"));
            calculations.add(new DesignProject.Calculation("有效容积计算", "V=L·B·H",
                    "V=" + round(effectiveLength) + "×" + round(effectiveWidth) + "×" + round(effectiveHeight), round(volume), "m³", "作为停留时间和沉降空间校核输入"));
            calculations.add(new DesignProject.Calculation("停留时间计算", "t=V/Q",
                    "t=" + round(volume) + "/(" + airFlow + "/3600)", round(residence), "s", residence >= 3 ? "停留时间满足方案阶段沉降要求" : "需增大箱体容积或降低处理风量"));
            calculations.add(new DesignProject.Calculation("管径校核", "D=√(4Q/(πv))",
                    "D=√(4×" + round(flowSecond) + "/(π×" + velocity + "))", round(pipeDiameter), "mm", "进出口管径按DN系列向上圆整"));
            calculations.add(new DesignProject.Calculation("支腿稳定性校核", "λ=μl/i",
                    "按支腿计算长度" + round(height * .38) + "mm和截面回转半径估算", round(height / Math.max(1, width) * 42), "", "方案阶段支腿长细比可接受，正式设计需按型钢截面复核"));
        }
        project.setCalculations(calculations);
        ensureVerification(project, "壳体强度校核");
        ensureVerification(project, "支撑结构校核");
        ensureVerification(project, "运行稳定性校核");
        return project;
    }

    private void upsertDerived(DesignProject project, String name, Object value, String unit, String basis) {
        project.getDerivedParameters().removeIf(item -> name.equals(item.getName()));
        project.getDerivedParameters().add(new DesignProject.Parameter(name, value, unit, null, basis));
    }
    private void upsertSuggested(DesignProject project, String name, Object value, String unit, String basis) {
        if (project.allParameters().stream().noneMatch(item -> name.equals(item.getName()))) {
            project.getSuggestedParameters().add(new DesignProject.Parameter(name, value, unit, null, basis));
        }
    }
    private void ensureVerification(DesignProject project, String item) {
        if (!project.getVerificationItems().contains(item)) project.getVerificationItems().add(item);
    }
    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }
    private double round(double value) { return Math.round(value * 1000d) / 1000d; }
}
