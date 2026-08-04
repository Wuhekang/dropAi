# DropAI Mechanical Module Complete Rewrite Plan

## Decision

The existing mechanical implementation will be removed instead of adapted. The replacement uses a new `mechanical-engine` domain and a new backend package `com.dropai.rewrite.mechanicalengine`. No old mechanical DTO, pipeline, renderer, CAD worker, or package format is retained.

## Delete

Backend mechanical implementation:

- `backend/src/main/java/com/dropai/rewrite/modules/{assembly*,bomGenerator,cadFeatureGenerator,calculationEngine,designAnalyzer,designEnhancementEngine,designPipeline,designReferenceAgent,drawing*,exportEngine,mechanical*,model,modelQualityGate,nonStandardPartGenerator,paperEngine,parameterEngine,parametricStandardPartGeometryGenerator,part*,pluginDiscoveryAgent,projectAnalyzer,projectSessionReset,requirementCompleter,standardPart*,stepExportEngine,structure*,swMacroEngine,unknownPartResolver}`
- `backend/src/main/java/com/dropai/rewrite/controller/DesignPackageController.java`
- `backend/src/main/java/com/dropai/rewrite/service/DesignPackageService.java`
- `backend/src/main/java/com/dropai/rewrite/service/DesignPackageJobService.java`
- old design package VO classes and old mechanical/design-package tests

Frontend mechanical implementation:

- `frontend/src/views/NewProject/`
- primitive/parametric model builders, fake assembly visualizers, model repair/quality animation, and the old Three.js mechanical viewer
- old `/design-packages/*` API functions

Repository knowledge:

- old `mechanical-ai/` and `skills/mechanical-*` content
- obsolete V1-V4 mechanical reports

## Add

- `mechanical-engine/agents/`
- `mechanical-engine/cad/solidworks-script-generator/`
- `mechanical-engine/cad/solidworks-execution/`
- `mechanical-engine/cad/step-export/`
- `mechanical-engine/cad/freecad-preview/`
- `mechanical-engine/skills/`
- `mechanical-engine/schemas/`
- `mechanical-engine/mechanical-tool-registry.json`
- backend `mechanicalengine` domain, orchestrator, SolidWorks script generator/executor, artifact validator, plugin manager, package builder, controller
- a new `Mechanical Workspace` frontend using only real backend stages and real artifact availability

## New flow

`REQUIREMENT_UNDERSTANDING -> MECHANICAL_DESIGN -> PARAMETER_GENERATION -> ASSEMBLY_ARCHITECTURE -> PART_DESIGN -> SOLIDWORKS_SCRIPT -> SOLIDWORKS_EXECUTION -> DRAWING_EXPORT -> STEP_VALIDATION -> FREECAD_PREVIEW_VALIDATION -> PACKAGE -> COMPLETED`

Any unavailable SolidWorks/FreeCAD worker or invalid artifact ends as `DESIGN_FAILED`. No placeholder CAD, drawing, or completion status is permitted.

## Preserved boundaries

- Authentication, payment, points, document storage, and general writing features remain untouched.
- No database schema or database credential change.
- Existing document download endpoint is reused for validated artifacts only.
