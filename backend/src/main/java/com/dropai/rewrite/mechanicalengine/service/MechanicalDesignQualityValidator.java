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
        validateGraduationRobot(spec, errors);
        if (!errors.isEmpty()) throw new IllegalArgumentException("MECHANICAL_DESIGN_INVALID: " + String.join("; ", errors));
    }

    private void validateGraduationRobot(MechanicalDesignSpec spec, List<String> errors) {
        if (!"wall_climbing_tank_inspection_robot".equals(spec.product().type())) return;
        if (spec.modules().size() < 5) errors.add("graduation robot requires at least five functional modules");
        if (spec.parts().size() < 30) errors.add("graduation robot requires at least thirty engineered parts");
        String designText = (spec.modules().stream().map(module -> module.name() + " " + module.function()).reduce("", (a,b) -> a + " " + b)
                + " " + spec.parts().stream().map(part -> part.name() + " " + part.function()).reduce("", (a,b) -> a + " " + b)).toLowerCase();
        requireAny(designText, errors, "crawler travel mechanism", "履带", "crawler", "track belt");
        requireAny(designText, errors, "permanent-magnet adhesion mechanism", "永磁", "磁吸", "permanent magnet");
        requireAny(designText, errors, "rotary cleaning mechanism", "清扫", "刷盘", "cleaning brush");
        requireAny(designText, errors, "inspection sensor adjustment mechanism", "检测", "传感器", "sensor");
        requireAny(designText, errors, "drive and transmission mechanism", "驱动轴", "减速电机", "transmission");
        requireAny(designText, errors, "protective enclosure", "防护", "密封上盖", "guard");
        requireAny(designText, errors, "track tensioning mechanism", "张紧", "tension");
    }

    private void requireAny(String text, List<String> errors, String label, String... terms) {
        for (String term : terms) if (text.contains(term.toLowerCase())) return;
        errors.add("task-book mechanism missing: " + label);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
