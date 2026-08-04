# DropAI Mechanical CAD Engine Rewrite v1.0 Plan

## Remove

- SolidWorks-specific generator, executor, agents, skills, registry entries, and UI copy from the previous rewrite.
- Any remaining primitive, fake assembly, fake drawing, hard-coded completion, or legacy mechanical compatibility path.

## Add

- Intent-first `MechanicalDesignSpec`, `CADModelSpec`, `AssemblySpec`, `DrawingSpec`, and `AnalysisSpec`.
- CAD DSL and OpenCascade BRep execution through configured `FreeCADCmd`.
- Parameterized automatic-clamp demo with meaningful parts, manufacturing decisions, features, placements, and constraints.
- STEP and browser STL export, BRep volume/solid validation, engineering drawings, phase-1 analysis cloud, and design report.
- Mechanical Workspace with input, real process state, browser 3D viewer, design information, and model/explosion/drawing/analysis/document tabs.
- Mechanical agents, skills, plugin registry, and materials/fasteners/bearings/manufacturing/mechanisms knowledge.

## Flow

`Requirement -> Alternatives -> Parameters -> Architecture -> CAD DSL -> OpenCascade BRep -> Assembly -> STEP/STL -> Drawing -> Analysis -> Document -> Validation -> Package`

Any missing FreeCAD worker, empty solid, trivial feature plan, incomplete assembly, invalid STEP, missing drawing, missing analysis, or missing document returns `DESIGN_FAILED`.

## Boundaries

- No database schema, account, password, payment, authentication, or writing-module change.
- Task-book dimensions are not copied into CAD.
- Mesh is generated only as a browser preview derived from validated BRep, never as the design source.
