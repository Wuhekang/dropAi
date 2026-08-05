package com.dropai.rewrite.mechanicalengine.validation;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalRequirementAnalysis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ArchitectureReviewValidator {
    public void validate(MechanicalRequirementAnalysis analysis, MechanicalDesignSpec design) {
        List<String> errors = new ArrayList<>();
        if (design.modules() == null || design.modules().size() < 2) errors.add("insufficient mechanical modules");
        if (design.parts() == null || design.parts().isEmpty()) errors.add("part plan is empty");
        if (design.assemblyIntent() == null || design.assemblyIntent().isEmpty()) errors.add("assembly intent is empty");
        if (design.parameters() == null || design.parameters().isEmpty()) errors.add("engineering parameters are empty");
        String architecture = ((design.product() == null ? "" : design.product().name() + " " + design.product().purpose()) + " "
                + design.modules().stream().map(module -> module.name() + " " + module.function()).reduce("", (a,b) -> a + " " + b)
                + " " + design.functions().stream().map(node -> node.name() + " " + node.purpose()).reduce("", (a,b) -> a + " " + b))
                .toLowerCase(Locale.ROOT);
        for (String system : analysis.requiredSystems()) {
            String normalized = system.toLowerCase(Locale.ROOT).trim();
            if (!normalized.isBlank() && !architecture.contains(normalized)) errors.add("required system not covered: " + system);
        }
        if (design.parts() != null) design.parts().forEach(part -> {
            if (blank(part.function())) errors.add("part without function: " + part.name());
        });
        if (!errors.isEmpty()) throw new IllegalArgumentException("ARCHITECTURE_REVIEW_FAILED: " + String.join("; ", errors));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
