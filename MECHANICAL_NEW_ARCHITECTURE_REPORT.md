# DropAI Mechanical New Architecture Report

## Result

The old mechanical subsystem was removed. The replacement is a new SolidWorks-first engineering system with no primitive CAD, mock assembly, fake drawing, compatibility adapter, or legacy design-package API.

## Architecture

- `MechanicalChiefEngineer` understands function/scenario, chooses a concept, generates justified parameters, defines modules, assembly components, poses, mates, and part feature plans.
- `SolidWorksScriptGenerator` creates a Windows SolidWorks API automation job.
- `SolidWorksExecutor` delegates execution to the registered Windows worker through `SOLIDWORKS_AUTOMATION_COMMAND`.
- `MechanicalArtifactValidator` rejects non-native SLDASM/SLDPRT files, missing per-part DWG drawings, incomplete STEP, invalid DWG/PDF, missing mates, missing FreeCAD preview PNG, and an unsuccessful FreeCAD reopen receipt.
- `MechanicalPackageBuilder` exposes only `01_Model`, `02_STEP`, `03_Drawing`, and `04_Document` in `Mechanical_Project.zip`.
- `EngineeringPluginManager` reports SolidWorks, FreeCAD, STEP, and DWG capabilities without silently installing executable plugins.
- The new Mechanical Workspace accepts DOCX/PDF/TXT task books, shows real stages, and exposes validated files only.

## Runtime truth

Render cannot run desktop SolidWorks. Without a registered Windows SolidWorks Worker and FreeCAD validator, execution intentionally returns `DESIGN_FAILED` with `SOLIDWORKS_WORKER_UNAVAILABLE` or `ARTIFACT_VALIDATION_FAILED`. This is required behavior and prevents fake completion.

## Required worker configuration

- `SOLIDWORKS_AUTOMATION_COMMAND`: executable Windows worker path.
- `SOLIDWORKS_PART_TEMPLATE`: real `.prtdot` template used by SolidWorks.
- `FREECAD_VALIDATION_COMMAND`: FreeCAD CLI validator that reopens the exported STEP and produces `freecad-preview.png` plus a passing `freecad-validation.json` receipt.

## Database

No database schema, account, password, or migration was changed. Validated ZIP files reuse the existing document storage table and download endpoint.

## Verification

- Backend unit/integration tests include feature-plan/mate assertions, placeholder-artifact rejection, package-content isolation, and Spring context startup.
- Frontend production build verifies the new workspace and removal of old viewer imports.
