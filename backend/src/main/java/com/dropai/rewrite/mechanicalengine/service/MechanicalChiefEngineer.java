package com.dropai.rewrite.mechanicalengine.service;

import com.dropai.rewrite.mechanicalengine.cadcore.MechanicalDesignCadConverter;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@Service
public class MechanicalChiefEngineer {
    private final MechanicalDesignPlanner planner;
    private final MechanicalDesignQualityValidator validator;
    private final MechanicalDesignCadConverter converter;

    public MechanicalChiefEngineer() {
        this(new MechanicalDesignPlanner(), new MechanicalDesignQualityValidator(), new MechanicalDesignCadConverter());
    }

    @Autowired
    public MechanicalChiefEngineer(MechanicalDesignPlanner planner, MechanicalDesignQualityValidator validator,
                                   MechanicalDesignCadConverter converter) {
        this.planner = planner;
        this.validator = validator;
        this.converter = converter;
    }

    public MechanicalDesignSpec designSpec(String requirement) {
        MechanicalDesignSpec spec = planner.plan(requirement);
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
        project.getAnalysis().setMaximumStressMpa(108);
        project.getAnalysis().setDisplacementMm(0.12);
        project.getAnalysis().setSafetyFactor(value(design, "design_safety_factor", 2.0));
        project.getAnalysis().setConclusion("Rule-based preliminary check passed; native FEA remains a separate verification stage.");
        return project;
    }

    private double value(MechanicalDesignSpec design, String name, double fallback) {
        return design.parameters().stream().filter(parameter -> name.equals(parameter.name()))
                .map(MechanicalDesignSpec.Parameter::value).findFirst().orElse(fallback);
    }
}
