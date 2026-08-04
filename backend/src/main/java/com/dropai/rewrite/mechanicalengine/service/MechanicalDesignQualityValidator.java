package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MechanicalDesignQualityValidator {
    private static final Set<String> FORBIDDEN = Set.of("BOX", "CUBE", "CYLINDER", "SPHERE", "PRIMITIVE");

    public void validate(MechanicalDesignSpec spec) {
        List<String> errors = new ArrayList<>();
        if (spec.product() == null || blank(spec.product().name()) || blank(spec.product().purpose())) errors.add("product definition is incomplete");
        if (spec.functions() == null || spec.functions().isEmpty()) errors.add("function tree is empty");
        if (spec.modules() == null || spec.modules().isEmpty()) errors.add("mechanical architecture has no modules");
        Set<String> moduleIds = new HashSet<>();
        for (MechanicalDesignSpec.Module module : spec.modules()) {
            if (!moduleIds.add(module.id())) errors.add("duplicate module " + module.id());
            if (blank(module.function()) || module.interfaces().isEmpty() || blank(module.installation())) errors.add(module.name() + " has incomplete engineering intent");
        }
        Set<String> partNumbers = new HashSet<>();
        Set<String> partNames = new HashSet<>();
        for (MechanicalDesignSpec.PartPlan part : spec.parts()) {
            if (!partNumbers.add(part.partNumber()) || !partNames.add(part.name())) errors.add("duplicate part " + part.name());
            if (!moduleIds.contains(part.moduleId())) errors.add(part.name() + " references an unknown module");
            if (blank(part.function()) || blank(part.material()) || blank(part.manufacturing())) errors.add(part.name() + " is not manufacturable");
            if (part.cadRequirements().isEmpty()) errors.add(part.name() + " has no CAD feature intent");
            part.cadRequirements().forEach(feature -> {
                if (FORBIDDEN.contains(feature.type().toUpperCase())) errors.add(part.name() + " uses forbidden primitive intent " + feature.type());
                if (blank(feature.intent())) errors.add(part.name() + " has a feature without engineering intent");
            });
        }
        for (String partNumber : partNumbers) {
            boolean connected = spec.assemblyIntent().stream().anyMatch(intent -> partNumber.equals(intent.componentA()) || partNumber.equals(intent.componentB()));
            if (!connected) errors.add(partNumber + " has no assembly relationship");
        }
        if (spec.parameters().stream().anyMatch(parameter -> blank(parameter.engineeringReason()))) errors.add("engineering parameter without reason");
        if (!errors.isEmpty()) throw new IllegalArgumentException("MECHANICAL_DESIGN_INVALID: " + String.join("; ", errors));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
