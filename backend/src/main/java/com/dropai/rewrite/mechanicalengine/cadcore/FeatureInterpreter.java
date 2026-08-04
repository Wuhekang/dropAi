package com.dropai.rewrite.mechanicalengine.cadcore;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class FeatureInterpreter {
    private static final Set<String> SUPPORTED = Set.of("SKETCH", "PAD", "POCKET", "HOLE", "FILLET", "CHAMFER");
    private static final Set<String> ASSEMBLY_CONSTRAINTS = Set.of("FIXED", "COINCIDENT", "CONCENTRIC", "DISTANCE", "ANGLE", "SLIDER");

    public void validate(FeatureBasedCadSpec spec) {
        List<String> errors = new ArrayList<>();
        if (spec.parts().isEmpty()) errors.add("CAD spec contains no parts");
        for (FeatureBasedCadSpec.Part part : spec.parts()) {
            boolean sketch = false;
            boolean solid = false;
            for (FeatureBasedCadSpec.Feature feature : part.body()) {
                if (!SUPPORTED.contains(feature.featureType())) {
                    errors.add(part.partNumber() + " uses unsupported feature " + feature.featureType());
                    continue;
                }
                if (feature.featureType().equals("SKETCH")) sketch = true;
                if (feature.featureType().equals("PAD")) {
                    if (!sketch) errors.add(part.partNumber() + " PAD requires a preceding SKETCH");
                    solid = true;
                }
                if (Set.of("POCKET", "HOLE", "FILLET", "CHAMFER").contains(feature.featureType()) && !solid) {
                    errors.add(part.partNumber() + " " + feature.featureType() + " requires an existing solid");
                }
            }
            if (!sketch || !solid) errors.add(part.partNumber() + " must contain SKETCH and PAD");
        }
        if (spec.assembly().constraints().isEmpty()) errors.add("Assembly has no constraints");
        spec.assembly().constraints().forEach(constraint -> {
            if (!ASSEMBLY_CONSTRAINTS.contains(constraint.type().toUpperCase())) {
                errors.add("Assembly uses unsupported constraint " + constraint.type());
            }
        });
        if (!errors.isEmpty()) throw new IllegalArgumentException("FEATURE_SPEC_INVALID: " + String.join("; ", errors));
    }
}
