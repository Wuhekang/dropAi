package com.dropai.rewrite.modules.mechanicalOptimization;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DesignScoreEngine {
    public List<MechanicalDesignPlan.ScoreCard> score(List<MechanicalDesignPlan.AlternativeDesign> alternatives) {
        double maxCost = alternatives.stream().mapToDouble(d -> d.getCostEstimate().getTotalCost()).max().orElse(1);
        double minCost = alternatives.stream().mapToDouble(d -> d.getCostEstimate().getTotalCost()).min().orElse(0);
        double maxWeight = alternatives.stream().mapToDouble(MechanicalDesignPlan.AlternativeDesign::getEstimatedWeightKg).max().orElse(1);
        double minWeight = alternatives.stream().mapToDouble(MechanicalDesignPlan.AlternativeDesign::getEstimatedWeightKg).min().orElse(0);
        return alternatives.stream().map(design -> score(design, minCost, maxCost, minWeight, maxWeight))
                .sorted(Comparator.comparingDouble(MechanicalDesignPlan.ScoreCard::getTotalScore).reversed())
                .toList();
    }

    private MechanicalDesignPlan.ScoreCard score(MechanicalDesignPlan.AlternativeDesign design,
                                                 double minCost, double maxCost, double minWeight, double maxWeight) {
        double function = clamp(72 + design.getAdvantages().size() * 6 - design.getDisadvantages().size() * 2);
        double reliability = clamp(design.getReliability() * 100);
        double cost = inverse(design.getCostEstimate().getTotalCost(), minCost, maxCost);
        double weight = inverse(design.getEstimatedWeightKg(), minWeight, maxWeight);
        double maintenance = clamp(design.getMaintainability() * 100);
        double total = function * 0.30 + reliability * 0.20 + cost * 0.20 + weight * 0.15 + maintenance * 0.15;

        MechanicalDesignPlan.ScoreCard card = new MechanicalDesignPlan.ScoreCard();
        card.setDesignName(design.getName());
        card.setFunctionScore(round(function));
        card.setReliabilityScore(round(reliability));
        card.setCostScore(round(cost));
        card.setWeightScore(round(weight));
        card.setMaintenanceScore(round(maintenance));
        card.setTotalScore(round(total));
        return card;
    }

    private double inverse(double value, double min, double max) {
        if (max <= min) return 85;
        return clamp(60 + (max - value) / (max - min) * 35);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
