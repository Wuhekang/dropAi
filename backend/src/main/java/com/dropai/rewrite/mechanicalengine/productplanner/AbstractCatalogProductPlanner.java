package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;

import java.util.List;
import java.util.Map;

abstract class AbstractCatalogProductPlanner implements ProductPlanner {
    protected MechanicalDesignSpec build(String type, String name, String purpose, String principle,
                                         List<MechanicalDesignSpec.Module> modules,
                                         List<PartSeed> seeds,
                                         List<MechanicalDesignSpec.Parameter> parameters,
                                         List<String> loadPath, List<String> motionPath) {
        List<MechanicalDesignSpec.PartPlan> parts = seeds.stream().map(seed -> part(seed, modules)).toList();
        List<MechanicalDesignSpec.AssemblyIntent> intents = new java.util.ArrayList<>();
        intents.add(intent("FIXED", seeds.get(0).number(), "mounting datum", "ASSEMBLY", "world datum", "establish assembly datum"));
        for (int index = 1; index < seeds.size(); index++) {
            PartSeed seed = seeds.get(index);
            intents.add(intent(seed.relation(), seed.number(), seed.reference(), seeds.get(seed.parentIndex()).number(), seed.parentReference(), seed.assemblyPurpose()));
        }
        List<MechanicalDesignSpec.FunctionNode> functions = modules.stream().map(module ->
                new MechanicalDesignSpec.FunctionNode(module.name(), module.function(), List.of())).toList();
        return new MechanicalDesignSpec(
                new MechanicalDesignSpec.Product(type, name, purpose, "indoor industrial environment", principle,
                        modules.stream().map(MechanicalDesignSpec.Module::function).toList()),
                new MechanicalDesignSpec.Requirements(modules.stream().map(MechanicalDesignSpec.Module::function).toList(),
                        List.of("stable operation", "maintainable modular structure"),
                        List.of("industrial duty cycle", "dust and vibration"),
                        List.of("dimensions are generated from engineering parameters", "prototype verification required before production")),
                List.of(new MechanicalDesignSpec.FunctionNode("Product Function", purpose, functions)),
                new MechanicalDesignSpec.Architecture(name + " modular architecture",
                        "Selected for manufacturability, serviceability, and a direct load path", loadPath, motionPath),
                modules, parts, intents, parameters,
                parts.stream().map(part -> new MechanicalDesignSpec.MaterialDecision(part.partNumber(), part.material(), "matched to load, wear, mass, and cost")).toList(),
                parts.stream().map(part -> new MechanicalDesignSpec.ManufacturingDecision(part.partNumber(), part.manufacturing(), "matched to geometry and production volume")).toList(),
                new MechanicalDesignSpec.DesignProvenance("GOLDEN_BENCHMARK", List.of(getClass().getSimpleName()),
                        List.of("Deterministic benchmark retained for regression acceptance only")));
    }

    private MechanicalDesignSpec.PartPlan part(PartSeed seed, List<MechanicalDesignSpec.Module> modules) {
        String profile = seed.circular() ? "circle" : "rectangle";
        Map<String, Object> sketch = seed.circular()
                ? Map.of("profile", profile, "diameter", seed.width())
                : Map.of("profile", profile, "length", seed.length(), "width", seed.width());
        return new MechanicalDesignSpec.PartPlan(seed.number(), seed.name(), seed.moduleId(), seed.function(), seed.material(), seed.process(), List.of(
                feature(1, "SKETCH", "create a fully constrained primary profile", sketch),
                feature(2, "PAD", "create the load-bearing primary solid", Map.of("length", seed.height())),
                feature(3, "HOLE", "create the assembly interface", Map.of("diameter", seed.holeDiameter(), "depth", seed.height())),
                feature(4, "FILLET", "reduce edge stress concentration", Map.of("radius", seed.fillet()))));
    }

    protected MechanicalDesignSpec.Module module(String id, String name, String function, List<String> interfaces, String installation) {
        return new MechanicalDesignSpec.Module(id, name, function, interfaces, installation);
    }
    protected MechanicalDesignSpec.Parameter parameter(String name, double value, String unit, String reason) {
        return new MechanicalDesignSpec.Parameter(name, value, unit, reason);
    }
    protected MechanicalDesignSpec.AssemblyIntent intent(String type, String a, String ar, String b, String br, String purpose) {
        return new MechanicalDesignSpec.AssemblyIntent(type, a, ar, b, br, purpose);
    }
    protected MechanicalDesignSpec.FeatureRequirement feature(int order, String type, String intent, Map<String, Object> parameters) {
        return new MechanicalDesignSpec.FeatureRequirement(order, type, intent, parameters);
    }
    protected record PartSeed(String number, String name, String moduleId, String function, String material, String process,
                              double length, double width, double height, boolean circular, double holeDiameter, double fillet,
                              String relation, int parentIndex, String reference, String parentReference, String assemblyPurpose) {}
}
