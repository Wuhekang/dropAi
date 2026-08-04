# DropAI Mechanical CAD Kernel Rewrite V2 Report

Date: 2026-08-04

## Result

The product CAD path has been replaced with a feature-based FreeCAD PartDesign pipeline. The backend and frontend builds pass, primitive generation has been removed from the product source path, and completion now depends on a machine-readable CAD reality report.

Native FreeCAD execution was not available on this workstation because `FreeCADCmd` is not installed. The generated automatic-clamp FCStd/STEP artifacts must therefore be validated in the updated Render image before this rewrite can be declared production-proven.

## Removed

- Hard-coded `FreeCadJobGenerator` and all part-name geometry branches.
- Product use of `Part.makeBox`, `Part.makeCylinder`, and `Part.makeSphere`.
- Legacy CadQuery `cad_worker.py`, its virtual-environment setup, and Render worker configuration.
- Legacy `ParametricDxfService` and `DesignWorkflowService`.
- Legacy engineering-writing workflow and direct primitive DXF endpoints.
- Legacy `cad-engineer` skill.

## Added

- `FeatureBasedCadSpec`: typed parts, ordered body features, components, and constraints.
- `FeatureInterpreter`: rejects unsupported primitives and invalid feature order before FreeCAD runs.
- `PartDesignJobGenerator`: generic Feature Spec execution without part-name branching.
- Real FreeCAD document objects:
  - `PartDesign::Body`
  - `Sketcher::SketchObject`
  - `PartDesign::Pad`
  - `PartDesign::Pocket`
  - `PartDesign::Hole`
  - `PartDesign::Fillet`
  - `PartDesign::Chamfer`
- Constrained rectangle and circle sketch generation.
- Per-feature execution log with success/failure result.
- FCStd assembly constraint objects and a constraint solve step for Fixed, Coincident, Slider, and Concentric relations used by the clamp design.
- `CADRealityValidator` with primitive-only, Body, Sketch, Pad, feature-history, solid, feature-log, and assembly-solve gates.
- A clearer frontend workspace showing Feature Tree, assembly constraints, actual generation stages, model preview, drawings, and artifacts.
- Six focused mechanical/CAD skills.

## Feature Execution

The automatic-clamp design emits five parts. Every part starts with a constrained `SKETCH` and creates its first solid with `PAD`. The design also includes `HOLE`, `FILLET`, and `CHAMFER` operations. The interpreter rejects `BOX`, `CYLINDER`, `SPHERE`, unknown features, and subtractive/edge features without an existing solid.

The generated FreeCAD script dispatches features by `featureType`; it does not inspect a fixed part name. Each executed feature is recorded in `02_STEP/cad-reality-report.json`.

## Assembly

Assembly constraints are persisted as `App::FeaturePython` objects inside the FCStd document, with typed component/reference properties and `SolveStatus`. The solve step derives component placements by constraint type and fails unresolved component references. The validator requires one `SOLVED` receipt per design constraint.

This is an internal deterministic constraint engine stored in the FreeCAD document, not an Assembly3/Assembly4 add-on dependency.

## Drawing And Export

- Per-part and assembly STEP are exported from final PartDesign Body tips.
- STL remains a derived browser-preview format only.
- SVG and DXF are generated from final BRep geometry.
- PDF generation continues from actual projection-line data.
- The project package remains limited to the five user-facing engineering folders.

## Validation Results

| Check | Result |
|---|---|
| Product-source primitive scan | PASS: no primitive generation calls remain |
| Fixed part-name generation branches | PASS: removed |
| Feature Spec validation tests | PASS |
| Primitive rejection test | PASS |
| Missing reality report rejection | PASS |
| Mechanical kernel tests | PASS: 4/4 |
| Full Maven test suite | PASS |
| Frontend production build | PASS |
| Native automatic-clamp FreeCAD run | NOT RUN: local `FreeCADCmd` unavailable |
| Render native model validation | PENDING DEPLOYMENT |

The frontend build reports the existing Vite bundle-size warning; it does not fail the build.

## Deployment Change

The Render Docker image now installs FreeCAD and exposes a normalized `/usr/local/bin/freecadcmd` command through `FREECAD_CMD`. CadQuery and the old Python worker are no longer installed or configured.

Production completion requires the image build to confirm FreeCAD package availability and then execute an automatic-clamp request. The request must produce:

- `01_Model/Assembly.FCStd`
- per-part BRep files
- `02_STEP/Assembly.STEP`
- per-part STEP files
- `03_Drawing/Assembly.svg`
- `03_Drawing/Assembly.dxf`
- `03_Drawing/Assembly_Drawing.pdf`
- `02_STEP/cad-reality-report.json`

The reality report must contain non-empty PartDesign bodies, Sketch and Pad history, successful Hole/Fillet execution where specified, and solved assembly constraints. Any missing item keeps the project in `DESIGN_FAILED`.
