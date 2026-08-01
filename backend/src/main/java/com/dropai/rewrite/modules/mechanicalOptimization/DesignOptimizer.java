package com.dropai.rewrite.modules.mechanicalOptimization;

import com.dropai.rewrite.modules.mechanicalDesignPlanner.MechanicalDesignPlan;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DesignOptimizer {
    public MechanicalDesignPlan.OptimizationReport optimize(List<MechanicalDesignPlan.AlternativeDesign> alternatives,
                                                            List<MechanicalDesignPlan.ScoreCard> scoreCards) {
        MechanicalDesignPlan.ScoreCard bestCard = scoreCards.stream()
                .max(Comparator.comparingDouble(MechanicalDesignPlan.ScoreCard::getTotalScore))
                .orElse(new MechanicalDesignPlan.ScoreCard());
        MechanicalDesignPlan.AlternativeDesign best = alternatives.stream()
                .filter(item -> item.getName().equals(bestCard.getDesignName()))
                .findFirst()
                .orElse(alternatives.isEmpty() ? new MechanicalDesignPlan.AlternativeDesign() : alternatives.get(0));

        MechanicalDesignPlan.OptimizationReport report = new MechanicalDesignPlan.OptimizationReport();
        report.setSelectedDesign(best.getName());
        report.setSelectedMechanism(best.getMechanism());
        report.setComparisonSummary(scoreCards.stream()
                .map(card -> card.getDesignName() + " totalScore=" + card.getTotalScore())
                .toList());
        report.setOptimizationActions(List.of(
                "优先保留最高评分方案的机构链和装配基准",
                "对低分维度执行减重、降成本或维护性优化",
                "将最优方案继续传递给CAD、工程图和BOM生成链路"
        ));
        report.setSelectionReasons(List.of(
                "综合评分最高: " + bestCard.getTotalScore(),
                "可靠性评分: " + bestCard.getReliabilityScore(),
                "成本评分: " + bestCard.getCostScore(),
                "维护评分: " + bestCard.getMaintenanceScore()
        ));
        return report;
    }
}
