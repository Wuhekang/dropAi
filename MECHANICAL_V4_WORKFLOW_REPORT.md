# DropAI Mechanical V4 Workflow Report

## Goal

Refactor the mechanical module from a text-driven generation page into a more truthful engineering delivery workflow.

## What Changed

- Added `MechanicalRealityReviewer` to check component count, assembly constraints, BOM mapping, dimensions, material presence, and three-view drawing readiness.
- Connected the reality review to the design pipeline.
- Extended the deliverable quality gate so a `FAILED_REVIEW` state blocks final package success.
- Changed BOM generation from one-row-per-component to merged rows by same name, material, and function.
- Updated the mechanical generation page from a loose progress UI to engineering workflow stages:
  - requirement parsing
  - parameter design
  - mechanism design
  - assembly design
  - CAD generation
  - drawing generation
  - quality review
  - final packaging
- Removed internal structure debug details from the visible structure tree.
- Added an engineering review status card.
- Added model controls for assembly view, exploded view, and full-screen preview.
- Added six V4 mechanical skills:
  - `mechanical-requirement-analysis`
  - `mechanical-product-designer`
  - `mechanical-assembly-engineer`
  - `mechanical-cad-engineer`
  - `mechanical-drawing-engineer`
  - `mechanical-reality-reviewer`

## Engineering References Used

- ISO 128 confirms that technical drawings should follow consistent presentation principles for orthographic views.
- ISO 129-1 covers general principles for dimensions and associated tolerances on technical drawings.
- DFMA guidance emphasizes reducing duplicate parts and validating assembly/manufacturing feasibility early.

Reference links:

- https://www.iso.org/standard/32462.html
- https://www.iso.org/standard/64007.html
- https://www.dfma.com/resources/what-is-dfma.asp

## Validation

Passed:

- `mvn "-Dtest=DesignPackageModuleTests,DesignPackageRegressionTests,MechanicalCadPipelineTests,MechanicalOptimizationEngineTests" test`
- `mvn test`
- `mvn clean package -DskipTests`
- `npm run build`

## Remaining Work

- Add a first-class `FunctionalRequirement` schema.
- Add a dedicated `EngineeringParameterGenerator`.
- Generate a persistent exploded-view artifact in the backend, not only frontend exploded visualization.
- Add screenshot regression once the Render deployment has the new frontend bundle.

## Database

No database schema change was made. No SQL migration is required.
