# DropAI Mechanical Pipeline Audit

## Scope

This audit records the current mechanical generation data flow after the MechanicalDesignPlanner work.
The goal is to make the design chain explicit:

TaskDrivenDesignPipeline -> MechanicalDesignPlan -> StructureTree -> AssemblyModel -> 3D/CAD outputs.

## Current Pipeline

1. `ParameterEngine.normalize`
   - Input: `DesignProject`
   - Output: normalized explicit/derived/suggested parameters.

2. `ProjectAnalyzer.analyze`
   - Input: task title, equipment name, functions, structures, parameters.
   - Output: project category, equipment type, functions, inferred missing data.

3. `DesignReferenceAgent` and `RequirementCompleter`
   - Input: incomplete graduation-design task.
   - Output: suggested parameters and reference drawing plan when task book is sparse.

4. `MechanicalDesignPlanner.plan`
   - Input: analyzed `DesignProject`.
   - Output: `MechanicalDesignPlan`.
   - Important fields: `mechanismType`, `subsystems`, `designParameters`, `materialSelection`, `completedRequirements`.

5. `StructureTreeBuilder.build`
   - Input: `MechanicalDesignPlan` first, task structures second.
   - Output: normalized `StructureTree` with 8-15 major nodes.

6. `PartGeneratorAgent.generate`
   - Input: structure tree leaf nodes.
   - Output: `resolvedParts`.
   - Standard parts go to `StandardPartSelector`.
   - Non-standard parts go to `NonStandardPartGenerator`.

7. `CADFeatureGenerator.generate`
   - Input: `resolvedParts`.
   - Output: `DesignPart.cadFeatures`.

8. `AssemblyBuilder.build`
   - Input: `resolvedParts`, `cadFeatures`, structure context.
   - Output:
     - legacy `components`
     - legacy `assemblyConstraints`
     - legacy `assemblyTree`
     - new `assemblyModel`

9. `AssemblyPlannerAgent.plan`
   - Input: assembly constraints.
   - Output: enriched mounting face, contact face, axis, symmetry and hole pattern metadata.

10. `ExportEngine.model3d`
    - Input: complete `DesignProject`.
    - Output: `model_3d.json` containing `assemblyModel`, legacy components and constraints.

11. Frontend `ParametricModelBuilder`
    - Input: `project.assemblyModel` first.
    - Output: Three.js model with transform data from `AssemblyModel.components.position/rotation/size`.

## Previous Gap

`MechanicalDesignPlan` was created, but downstream 3D rendering still depended on loose `components` and fallback placement rules.
This caused several visible issues:

- Progress appeared stuck around the structure/model stage.
- 3D displayed only a small number of loose primitive parts.
- Components did not share a single assembly coordinate source.
- Model generation could fall back to demo geometry even when backend design data existed.

## Phase 2.5 Fix

- Added `AssemblyModel` as the explicit assembly data source.
- `AssemblyBuilder` now maps generated components and constraints into `AssemblyModel`.
- `ExportEngine.model3d` now includes `assemblyModel`.
- Package output includes:
  - `assembly-model.json`
  - `model-generation-report.json`
- Frontend model generation now reads `assemblyModel.components` before legacy fields.
- 3D generation rejects `assemblyModel.components > 0` with `constraints = 0` as `assembly incomplete`.

## Remaining Work

- Replace layout heuristics in `AssemblyConstraintEngine` with mechanism-specific assembly rules.
- Make `AssemblyPlannerAgent` update `assemblyModel.constraints` after enrichment.
- Let CAD and drawing generators read `AssemblyModel` directly, not legacy `components`.
- Add a real FreeCAD/CadQuery executor behind `StepExportEngine`.
