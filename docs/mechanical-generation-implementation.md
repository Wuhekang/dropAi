# DropAI Mechanical Generation Implementation Log

## Current Architecture

- Entry points: `DesignPackageController`, `EngineeringWritingController`, `NewProject/index.vue`.
- Main generation chain:
  `TaskDrivenDesignPipeline -> MechanicalDesignPlanner -> StructureTreeBuilder -> MechanicalDesignAgent -> PartGeneratorAgent -> CADFeatureGenerator -> AssemblyBuilder -> BOMGenerator -> CalculationEngine -> DrawingPlannerAgent -> DrawingPlanBuilder -> DesignPackageService`.
- Deliverable chain:
  `StepExportEngine -> DrawingEngine -> PaperEngine -> ExportEngine -> DocumentJobRecord persistence -> project_package.zip`.
- Shared data objects already present:
  `DesignProject`, `MechanicalDesignPlan`, `AssemblyModel`, `DrawingPlan`, BOM, calculations, resolved parts and assembly constraints.

## Problems Found

- `DesignPackageService` previously generated the ZIP from every successful artifact even when required CAD, STEP, part drawing or paper artifacts failed.
- Final status could become `partial_success`, which made the UI look close to complete while the mechanical deliverable was not complete.
- The engineering page advanced progress by fixed frontend delays before the backend actually finished CAD, paper and ZIP work.
- No manifest existed to list file size, SHA-256, source stage and validation state.
- GPT Image/Wanliang image generation had no isolated backend provider layer; image work must not be mixed into text chat or CAD/STEP generation.

## Phase Changes

### Phase 1: Audit And Blocking Fix

- Added `DesignDeliverableQualityGate`.
- Required artifacts are validated before ZIP creation:
  `MechanicalDesignPlan.json`, `mechanical-pipeline-audit.json`, `assembly-model.json`, `model-generation-report.json`, `model_3d.json`, `assembly.step`, five part STEP files, `assembly.dxf`, preview files, five part DXF files and `paper.docx`.
- ZIP is only generated after validation passes.
- Any validation failure marks the whole package result as `failed`; the ZIP artifact is returned as failed instead of an empty or misleading success file.

### Phase 2: Manifest

- Added `manifest.json`.
- Manifest records:
  file name, media type, size, SHA-256, source stage and validation status.
- Manifest is included in the final ZIP inputs after validation passes.

### Phase 3: Frontend Progress

- Removed fixed 280 ms fake progress advancement from `NewProject/index.vue`.
- Progress reaches 100% only when a successful ZIP artifact exists and no artifact failed.
- Failed validation now keeps the process below 100% and shows the backend message.

### Phase 4: Wanliang Image Provider Layer

- Added isolated image provider classes:
  `ImageGenerationProvider`, `ImageGenerationRequest`, `ImageGenerationResult`, `WanliangImageProvider`.
- Added backend health endpoint:
  `GET /api/engineering-writing/image/status`.
- Supported environment variables:
  `WANLIANG_BASE_URL`, `WANLIANG_API_KEY`, `WANLIANG_IMAGE_MODEL`, `WANLIANG_IMAGE_ENDPOINT`, `WANLIANG_IMAGE_TIMEOUT`, `WANLIANG_IMAGE_ENABLED`.
- The provider keeps API keys server-side and does not block CAD deliverables when disabled.

## Test Commands

- Backend compile: `mvn.cmd -DskipTests compile`
  - Result: passed on 2026-07-11.
- Backend package: `mvn.cmd "-Dmaven.test.skip=true" package`
  - Result: passed on 2026-07-11.
- Frontend build: `npm.cmd run build`
  - Result: passed on 2026-07-11 with existing Vite chunk-size warning.
- Targeted test attempt: `mvn.cmd -Dtest=DesignPackageServiceTests test`
  - Result: failed during `testCompile` because the current Maven test compilation could not resolve main-source packages such as `com.dropai.rewrite.modules.*`; this is an existing environment/test-classpath issue, not a runtime compile failure. Main compile and package both passed.

## Remaining Issues

- Current `StepExportEngine` emits STEP metadata in ISO-10303 format, but it is not yet a real FreeCAD/CadQuery B-Rep solid export.
- Wanliang image generation is guarded by a provider layer, but the exact image request contract still needs provider documentation before real generation is enabled.
- Persistent resumable stage state is not yet stored as an independent table; current phase status is represented through artifacts and audit JSON.
- Existing text in several Java/Vue files contains mojibake from older encoding damage; this task avoided broad re-encoding to reduce risk.

## Final Artifact Paths

- Files are persisted as `DocumentJobRecord` rows and exposed through `/api/documents/{jobId}/download`.
- Final ZIP artifact name: `project_package.zip`.
- New manifest artifact name: `manifest.json`.
