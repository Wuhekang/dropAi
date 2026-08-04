package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.cadcore.MechanicalDesignCadConverter;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import com.dropai.rewrite.mechanicalengine.productplanner.AgvProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.ConveyorProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.MechanicalProductFamilyResolver;
import com.dropai.rewrite.mechanicalengine.productplanner.MechanismProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.ProductFamily;
import com.dropai.rewrite.mechanicalengine.productplanner.ProductPlanner;
import com.dropai.rewrite.mechanicalengine.productplanner.RobotProductPlanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MechanicalChiefEngineer {
    private final MechanicalProductFamilyResolver familyResolver;
    private final List<ProductPlanner> planners;
    private final MechanicalDesignQualityValidator validator;
    private final MechanicalDesignCadConverter converter;

    public MechanicalChiefEngineer() {
        this(new MechanicalProductFamilyResolver(), defaultPlanners(), new MechanicalDesignQualityValidator(), new MechanicalDesignCadConverter());
    }

    public MechanicalChiefEngineer(MechanicalDesignPlanner ignored, MechanicalDesignQualityValidator validator,
                                   MechanicalDesignCadConverter converter) {
        this(new MechanicalProductFamilyResolver(), defaultPlanners(), validator, converter);
    }

    @Autowired
    public MechanicalChiefEngineer(MechanicalProductFamilyResolver familyResolver, List<ProductPlanner> planners,
                                   MechanicalDesignQualityValidator validator, MechanicalDesignCadConverter converter) {
        this.familyResolver = familyResolver;
        this.planners = List.copyOf(planners);
        this.validator = validator;
        this.converter = converter;
    }

    public MechanicalDesignSpec designSpec(String requirement) {
        ProductFamily family = familyResolver.resolve(requirement);
        ProductPlanner planner = planners.stream().filter(candidate -> candidate.family() == family).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNSUPPORTED_MECHANICAL_PRODUCT: no planner for " + family));
        MechanicalDesignSpec spec = planner.plan(family == ProductFamily.FIXTURE ? "automatic clamp" : requirement);
        validator.validate(spec);
        return spec;
    }

    public MechanicalProject design(String requirement) {
        MechanicalDesignSpec design = designSpec(requirement);
        MechanicalProject project = new MechanicalProject();
        project.setProjectId("mech_" + UUID.randomUUID().toString().replace("-", ""));
        project.setDesignSpec(design);
        project.setProductName(design.product().name());
        project.setScenario(design.product().environment());
        project.getRequirement().setFunctions(design.requirements().functions());
        project.getRequirement().setConstraints(design.requirements().engineeringConstraints());
        project.getConcept().setSelectedConcept(design.architecture().selectedConcept());
        project.getConcept().setSelectionReason(design.architecture().selectionReason());
        project.getConcept().setModules(design.modules().stream().map(MechanicalDesignSpec.Module::name).toList());
        project.setParameters(design.parameters().stream().map(parameter -> new MechanicalProject.EngineeringParameter(
                parameter.name(), parameter.value(), parameter.unit(), parameter.engineeringReason())).toList());
        project.setParts(design.parts().stream().map(part -> new MechanicalProject.CADModelSpec(
                part.partNumber(), part.name(), part.function(), part.material(), part.manufacturing(),
                part.cadRequirements().stream().map(feature -> new MechanicalProject.CADFeature(
                        feature.order(), feature.type(), feature.intent(), feature.parameters())).toList())).toList());
        var cad = converter.convert(project.getProjectId(), design);
        project.getAssembly().setRoot(design.product().name() + " Assembly");
        project.getAssembly().setComponents(cad.assembly().components());
        project.getAssembly().setConstraints(cad.assembly().constraints());
        project.getAnalysis().setSafetyFactor(value(design, "design_safety_factor", 2.0));
        project.getAnalysis().setConclusion("Engineering estimate completed; native FEA remains a separate verification stage.");
        return project;
    }

    private double value(MechanicalDesignSpec design, String name, double fallback) {
        return design.parameters().stream().filter(parameter -> name.equals(parameter.name()))
                .map(MechanicalDesignSpec.Parameter::value).findFirst().orElse(fallback);
    }

    private static List<ProductPlanner> defaultPlanners() {
        return List.of(new MechanicalDesignPlanner(), new RobotProductPlanner(), new AgvProductPlanner(),
                new ConveyorProductPlanner(), new MechanismProductPlanner());
    }
}
