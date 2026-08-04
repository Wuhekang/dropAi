package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalAnalysisReport;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MechanicalAnalysisEngine {
    public MechanicalAnalysisReport analyze(MechanicalDesignSpec design) {
        double load = governingLoad(design);
        double safetyTarget = parameter(design, "design_safety_factor", 2.0);
        double characteristicArea = Math.max(80.0, design.parts().size() * 35.0);
        double stress = load / characteristicArea * 1.8;
        double yield = minimumYield(design);
        double safety = Math.max(0.1, yield / Math.max(stress, 0.1));
        double displacement = Math.min(3.0, load / Math.max(yield * characteristicArea, 1.0) * 120.0);
        List<MechanicalAnalysisReport.CloudPoint> cloud = java.util.stream.IntStream.rangeClosed(0, 10)
                .mapToObj(index -> new MechanicalAnalysisReport.CloudPoint(index / 10.0,
                        stress * (0.35 + 0.65 * Math.sin(Math.PI * index / 10.0)))).toList();
        String conclusion = safety >= safetyTarget
                ? "Preliminary engineering estimate satisfies the target safety factor."
                : "Preliminary estimate is below the target safety factor; redesign is required.";
        return new MechanicalAnalysisReport("ENGINEERING_ESTIMATE", load, stress, displacement, safety,
                design.architecture().loadPath(),
                design.materials().stream().map(item -> item.partNumber() + ": " + item.material() + " - " + item.reason()).toList(),
                cloud, conclusion, true);
    }

    private double governingLoad(MechanicalDesignSpec design) {
        for (String key : List.of("design_clamping_force", "output_force", "payload", "distributed_load")) {
            var match = design.parameters().stream().filter(item -> key.equals(item.name())).findFirst();
            if (match.isPresent()) {
                double value = match.get().value();
                return match.get().unit().toLowerCase().contains("kg") ? value * 9.81 : value;
            }
        }
        return 1000.0;
    }
    private double parameter(MechanicalDesignSpec design, String name, double fallback) {
        return design.parameters().stream().filter(item -> name.equals(item.name())).map(MechanicalDesignSpec.Parameter::value).findFirst().orElse(fallback);
    }
    private double minimumYield(MechanicalDesignSpec design) {
        return design.parts().stream().mapToDouble(part -> yieldStrength(part.material())).min().orElse(235.0);
    }
    private double yieldStrength(String material) {
        String value = material.toLowerCase();
        if (value.contains("6061")) return 240;
        if (value.contains("5052")) return 190;
        if (value.contains("40cr")) return 785;
        if (value.contains("45")) return 355;
        if (value.contains("q345")) return 345;
        return 235;
    }
}
