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
