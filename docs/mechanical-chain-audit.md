# DropAI Mechanical Chain Audit

Date: 2026-07-11
Branch: `main`

## Current Runtime Chain

Frontend:

`NewProject/index.vue`
-> `POST /api/design-packages/analyze`
-> `POST /api/design-packages/jobs`
-> `GET /api/design-packages/jobs/{jobId}`
-> `ModelViewer3D`
-> `ParametricModelBuilder`
-> `AssemblyConstraintVisualizer`

Backend:

`DesignPackageController`
-> `DesignPackageJobService`
-> `DesignPackageService.generateForJob`
-> `TaskDrivenDesignPipeline`
-> `ProjectAnalyzer`
-> `MechanicalDesignPlanner`
-> `StructureTreeBuilder`
-> `MechanicalDesignAgent`
-> `PartGeneratorAgent`
-> `CADFeatureGenerator`
-> `AssemblyBuilder`
-> `AssemblyPlannerAgent`
-> `BOMGenerator`
-> `CalculationEngine`
-> `DrawingPlannerAgent`
-> `DrawingPlanBuilder`
-> `StepExportEngine`
-> `DrawingEngine`
-> `PaperEngine`
-> `DesignDeliverableQualityGate`
-> `ExportEngine.zip`

## Root Causes Found

1. The page did not have a real backend job before the last fix. It displayed local progress and could remain at 50 percent while a synchronous generation request was still running or had failed.

2. The 3D preview still had a second architecture problem: `AssemblyConstraintVisualizer.js` was reinterpreting backend assembly data with keyword-based layout rules. Even when `AssemblyModel` existed, the frontend could still resize and place parts according to terms such as track, motor, reducer, frame, settling chamber or conveyor.

3. The frontend preview path was not a reliable representation of final CAD. It could render primitive geometry derived from local heuristics rather than from backend assembly positions and constraints.

4. Encoding was not forced at the Spring Boot response layer, and the MySQL fallback URL used `utf8` instead of `utf8mb4`. This can create new mojibake even though some existing Java/Vue source literals are already damaged from earlier encoding corruption.

5. Drawing and paper generation are invoked after `AssemblyBuilder` in the backend package path. The user-facing symptom made it look as if they were not entered because the frontend remained on intermediate local progress and preview state.

## Fixes Applied In This Round

1. `AssemblyConstraintVisualizer.js` was rewritten to use only backend `assemblyModel.components` and `assemblyModel.constraints`.

2. Frontend assembly preview now refuses to build when backend components or constraints are missing. It no longer falls back to topic-specific geometry or keyword layouts.

3. Component size conversion now respects the backend coordinate convention:
   - backend `x`: length -> Three.js `x`;
   - backend `y`: width -> Three.js `z`;
   - backend `z`: height -> Three.js `y`.

4. Component position conversion follows the same coordinate mapping. The frontend no longer places parts from Chinese or English name keywords.

5. `application.yml` now forces UTF-8 servlet encoding and uses JDBC `characterEncoding=UTF-8` with `utf8mb4_unicode_ci` collation in the fallback MySQL URL.

6. Axios now sends and accepts JSON with `charset=UTF-8`.

## Follow-up Implementation Added

1. Added first export-stage `MechanicalDesignContext` so each package now includes one inspectable JSON object tying together project info, plan, structure tree, components, standard/non-standard parts, assembly model, constraints, BOM, calculations, drawings and paper context.

2. `MechanicalDesignContext.json` is now generated before `MechanicalDesignPlan.json` and is covered by `DesignPackageServiceTests`.

3. Three-project regression was rerun after the preview data-source fix. Oil-tank wall-climbing robot, gravity settling chamber and belt conveyor each generated STEP, validation, drawing, paper, manifest and ZIP artifacts.

## Remaining Architecture Work

1. Migrate downstream generation modules to consume `MechanicalDesignContext` directly rather than reading scattered `DesignProject` fields.

2. Remove existing mojibake Java/Vue literals by restoring source files from UTF-8 originals or rewriting affected user-facing strings.

3. Upgrade `DrawingEngine` from drawing-plan geometry to projection from reopened STEP topology for true hidden-line engineering drawings.

4. Replace test-only or mock online standard part providers in production quality gates.

5. Add an endpoint that exposes `assembly-validation.json` and STEP validation evidence directly to the frontend without requiring ZIP inspection.
