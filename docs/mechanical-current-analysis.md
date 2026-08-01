# DropAI Mechanical Current Analysis

## Existing Capability

DropAI already has a task-driven mechanical design pipeline instead of a single prompt-only generator. The current backend flow is:

`DesignPackageController -> DocumentParser -> DesignAnalyzer -> TaskDrivenDesignPipeline -> ProjectAnalyzer -> MechanicalDesignPlanner -> StructureTreeBuilder -> PartGeneratorAgent -> CADFeatureGenerator -> AssemblyBuilder -> AssemblyPlannerAgent -> BOMGenerator -> CalculationEngine -> DrawingPlannerAgent -> DrawingPlanBuilder -> DesignPackageService -> DrawingEngine/ExportEngine`

Reusable modules found in the current project:

- `ProjectAnalyzer`: normalizes project title, equipment name, functions, requirements, and deliverables.
- `MechanicalDesignPlanner`: creates a design plan from the task context.
- `StructureTreeBuilder`: builds mechanical structure hierarchy.
- `PartGeneratorAgent`: resolves standard and non-standard parts.
- `CADFeatureGenerator`: maps parts to a feature-tree-like CAD representation.
- `AssemblyBuilder`: builds assembly tree and part relations.
- `BOMGenerator`: creates BOM rows from components.
- `CalculationEngine`: generates mechanical calculation items.
- `DrawingEngine`: emits assembly, CAD preview, part drawings, SVG/PNG/DXF artifacts.
- `ExportEngine` and `DesignDeliverableQualityGate`: package and validate final files.

## Missing Capability

- The system has local rules and mock/fallback providers, but it lacks a curated engineering knowledge base for materials, standard parts, drawing standards, and manufacturing checks.
- Standard part lookup still has mock/fallback states, so outputs must continue to mark unverified recommendations honestly.
- CAD feature trees exist, but the current schema is still lightweight and should be reviewed against manufacturability, dimension completeness, and assembly feasibility.
- Design quality checks are mostly artifact-presence and geometry-count checks; they need a higher-level engineering reviewer layer.
- Skills/prompts are not separated into chief engineer, CAD engineer, and calculation engineer roles, making future prompt maintenance harder.

## Reusable Modules

- Keep the existing `TaskDrivenDesignPipeline` orchestration.
- Keep `DrawingEngine`, `AssemblyBuilder`, `CADFeatureGenerator`, `ExportEngine`, and CAD worker integration.
- Add knowledge and reviewer layers around the existing modules instead of replacing them.

## Upgrade Scope Implemented

- Added project-level mechanical engineering knowledge JSON files under `knowledge/mechanical/`.
- Added project-level skills under `skills/`.
- Added `MechanicalDesignResult` as a structured backend schema.
- Added `MechanicalQualityReviewer` for engineering-level completeness checks.
- Added tests for the reviewer and schema.

## Public Sources Used

- FreeCAD documentation: https://wiki.freecad.org/PartDesign_Workbench
- Open CASCADE documentation: https://dev.opencascade.org/doc/overview/html/
- SKF rolling bearing guidance: https://www.skf.com/group/products/rolling-bearings
- MISUMI technical resources: https://us.misumi-ec.com/
- ISO standards overview pages: https://www.iso.org/
- MatWeb material data pages: https://www.matweb.com/
