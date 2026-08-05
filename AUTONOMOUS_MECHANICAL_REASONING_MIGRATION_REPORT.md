# Autonomous Mechanical Reasoning Migration Report

## Production migration

The production mechanical entry point now executes:

`Requirement -> MechanicalRequirementReasoner -> MechanicalRequirementAnalysis -> ProductFamilyKnowledgeRepository -> AutonomousMechanicalChiefEngineer -> ArchitectureReviewValidator -> MechanicalDesignSpec -> FeatureBasedCadSpec -> FreeCAD`

Spring production wiring no longer routes requests through `ProductFamilyResolver` or a fixed `ProductPlanner`. Existing planners remain available only through explicit non-Spring constructors used by Golden Benchmark and native CAD regression tests.

## Strict failure behavior

- Missing model configuration: `AI_REASONING_UNAVAILABLE`
- Invalid requirement JSON: `INVALID_REQUIREMENT_ANALYSIS`
- Invalid design JSON: `INVALID_MECHANICAL_DESIGN`
- Missing systems or incomplete architecture: `ARCHITECTURE_REVIEW_FAILED`

No default robot, five-part product, or generic structure fallback is used.

## Knowledge layer

External product-family knowledge exists for robot, fixture, AGV, conveyor, mechanism, machine, and custom products. Entries contain engineering experience, candidate modules, design rules, parameter rules, and part patterns. They contain no fixed product structure or fixed part list.

## Traceability

`MechanicalDesignSpec` records reasoning source, knowledge references, and architecture decisions. Production designs must report `reasoningSource: AI`. `MechanicalProject` exposes the requirement analysis used to create the design.

## Verification scope

Automated tests cover distinct architectures for a wall-climbing robot, automatic fixture, AGV, conveyor, and mechanical arm benchmark; explicit AI unavailability; malformed analysis; and architecture rejection before CAD. Golden Benchmarks measure expected design quality but are not production generators.
