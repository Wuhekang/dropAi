# DropAI Mechanical CAD Engine Rewrite v1.0 Report

## Delivered architecture

- Replaced the SolidWorks-first execution path with CAD DSL and a FreeCAD/OpenCascade BRep worker contract.
- Added intent-first mechanical design, alternative comparison, justified parameters, part purpose/material/manufacturing, ordered feature plans, assembly placements, and constraints.
- Added OpenCascade BRep, FCStd, per-part STEP, assembly STEP, and STL browser-preview generation.
- Added model-derived SVG/DXF/PDF drawing outputs, phase-1 rule-analysis cloud, PDF design report, artifact persistence, and isolated project package.
- Added a Three.js STL viewer that displays only a validated BRep-derived artifact.
- Added the requested agents, skills, schemas, plugin registry, and mechanism knowledge.

## Automatic clamp demo

The built-in first demo designs a five-part trapezoidal-lead-screw clamp:

1. Q235B machined base with mounting-hole pattern.
2. 45-steel fixed jaw with relief feature.
3. 45-steel moving jaw with screw bore.
4. 40Cr lead screw with hub and cross hole.
5. Q235 cross handle with rounded ends.

The design contains five assembly constraints, justified load/envelope/safety parameters, BRep construction operations, STEP/STL export, assembly/part drawings, stress trend, and a design report.

## Validation policy

Completion requires positive BRep volume and solid count for every part, multiple meaningful CAD feature types, complete ISO-10303-21 STEP, non-empty STL, assembly constraints, valid SVG/DXF/PDF drawings, analysis cloud, and design report. Internal CAD DSL and worker scripts are excluded from the user package.

## Runtime requirement

Set `FREECAD_CMD` to a real `FreeCADCmd` executable. The current development machine does not have FreeCAD installed, so a native clamp artifact was not fabricated during this run. Until the worker is configured, execution intentionally returns `FREECAD_WORKER_UNAVAILABLE` and never reports false completion.

Optional future phase: set `CALCULIX_CMD` for formal FEA. The current cloud is explicitly labeled rule-based phase-1 engineering analysis and is not presented as certified FEA.

## Database

No database schema, database account, password, or migration was changed. Existing document storage is reused for validated artifacts.
