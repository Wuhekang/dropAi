package com.dropai.rewrite.modules.mechanicalOptimization;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import org.springframework.stereotype.Component;

@Component
public class CostEstimationEngine {
    public MechanicalDesignPlan.CostEstimate estimate(MechanicalDesignPlan.AlternativeDesign design, MechanicalDesignPlan plan) {
        double complexity = Math.max(1, design.getStructure().size());
        double material = Math.max(120, design.getEstimatedWeightKg() * materialRate(design));
        double machining = 180 * complexity * processFactor(design);
        double assembly = 90 * complexity * (1.0 + Math.max(0, design.getDisadvantages().size() - 1) * 0.08);
        MechanicalDesignPlan.CostEstimate estimate = new MechanicalDesignPlan.CostEstimate();
        estimate.setMaterialCost(round(material));
        estimate.setMachiningCost(round(machining));
        estimate.setAssemblyCost(round(assembly));
        estimate.setTotalCost(round(material + machining + assembly));
        estimate.setCurrency("CNY");
        return estimate;
    }

    private double materialRate(MechanicalDesignPlan.AlternativeDesign design) {
        String mechanism = design.getMechanism().toLowerCase();
        if (mechanism.contains("液压")) return 42;
        if (mechanism.contains("伺服") || mechanism.contains("麦克纳姆")) return 50;
        if (mechanism.contains("气动")) return 34;
        if (mechanism.contains("丝杆") || mechanism.contains("齿轮")) return 38;
        return 32;
    }

    private double processFactor(MechanicalDesignPlan.AlternativeDesign design) {
        String mechanism = design.getMechanism().toLowerCase();
        if (mechanism.contains("液压") || mechanism.contains("伺服")) return 1.45;
        if (mechanism.contains("齿轮") || mechanism.contains("麦克纳姆")) return 1.35;
        if (mechanism.contains("气动") || mechanism.contains("同步带")) return 1.05;
        return 1.15;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
