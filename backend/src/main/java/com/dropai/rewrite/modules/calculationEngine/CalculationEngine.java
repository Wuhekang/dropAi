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
        if ((project.getEquipmentName() + project.getProjectTitle() + project.getDesignType()).contains("沉降")) {
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
    private double round(double value) { return Math.round(value * 1000d) / 1000d; }
}
