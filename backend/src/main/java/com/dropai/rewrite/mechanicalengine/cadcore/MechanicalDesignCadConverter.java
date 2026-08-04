package com.dropai.rewrite.mechanicalengine.cadcore;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import com.dropai.rewrite.mechanicalengine.domain.MechanicalProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MechanicalDesignCadConverter {
    public FeatureBasedCadSpec convert(String projectId, MechanicalDesignSpec design) {
        List<FeatureBasedCadSpec.Part> parts = design.parts().stream().map(part -> new FeatureBasedCadSpec.Part(
                part.partNumber(), part.name(), part.material(),
                part.cadRequirements().stream().map(feature -> new FeatureBasedCadSpec.Feature(
                        feature.order(), feature.type().toUpperCase(), feature.intent(), feature.parameters())).toList())).toList();

        List<MechanicalProject.AssemblyComponent> components = new ArrayList<>();
        for (int index = 0; index < design.parts().size(); index++) {
            MechanicalDesignSpec.PartPlan part = design.parts().get(index);
            components.add(new MechanicalProject.AssemblyComponent(part.partNumber(), part.name(), "ASSEMBLY",
                    new MechanicalProject.Pose(index * 35.0, 0, 0), new MechanicalProject.Pose(0, 0, 0)));
        }
        List<MechanicalProject.Constraint> constraints = design.assemblyIntent().stream().map(intent ->
                new MechanicalProject.Constraint(intent.type(), intent.componentA(), intent.referenceA(),
                        intent.componentB(), intent.referenceB())).toList();
        return new FeatureBasedCadSpec(projectId, parts, new FeatureBasedCadSpec.Assembly(components, constraints));
    }
}
