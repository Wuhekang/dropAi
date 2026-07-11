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

### Phase 5: Real STEP Export And CAD Worker

- Environment detection on 2026-07-11:
  - Python: available, `Python 3.10.8`.
  - FreeCAD / FreeCADCmd: not available on PATH.
  - CadQuery / OCP: installed with user-site packages and verified in non-sandbox execution.
- Added Python CAD worker:
  `backend/cad_worker/cad_worker.py`.
- Replaced pseudo STEP export in `StepExportEngine`:
  - no longer writes metadata-only ISO-10303 text as a `.step` file;
  - calls the Python CAD worker;
  - fails clearly when the CAD worker fails;
  - does not fall back to fake STEP.
- CAD worker behavior:
  - reads `DesignProject` / `AssemblyModel` JSON;
  - creates CadQuery/OCP B-Rep solids;
  - exports `assembly.step` and `part_01.step` to `part_05.step`;
  - reopens exported STEP through OpenCascade;
  - validates solid count, volume, bounding box, positioned parts and non-origin distribution;
  - writes `assembly-validation.json`.
- `DesignPackageService` now includes `assembly-validation.json` in the STEP artifact group.
- `DesignDeliverableQualityGate` now requires `assembly-validation.json` and rejects packages when `"passed": true` is missing.

### Phase 6: Drawing Quality Gate Hardening

- Existing drawing output remains:
  - `assembly.dxf`;
  - `cad_preview.svg`;
  - `cad_preview.png`;
  - `part_01.dxf` to `part_05.dxf`.
- Added drawing validation in `DesignDeliverableQualityGate`:
  - assembly DXF must contain enough geometry entities;
  - each part DXF must contain enough geometry entities;
  - five part DXF drawings must have different SHA-256 hashes;
  - copied or empty DXF files cannot pass the package gate.

### Phase 7: Wanliang Real Request Adapter

- `WanliangImageProvider` now performs a real HTTP image request when enabled.
- It reuses existing text provider configuration when dedicated image config is absent:
  - API key fallback: `WANLIANG_API_KEY -> MATRIX_API_KEY`;
  - base URL fallback: `WANLIANG_BASE_URL -> MATRIX_BASE_URL`;
  - endpoint fallback: `{baseUrl}/images/generations`;
  - model fallback: `WANLIANG_IMAGE_MODEL -> MATRIX_IMAGE_MODEL -> gpt-image-1`.
- It supports URL, `b64_json`, `base64`, raw `image`, and async `taskId` style responses.
- It saves generated images under `data/generated-images`.
- It writes `image-generation-audit.json`.
- Added backend test endpoint:
  `POST /api/engineering-writing/image/test`.
- Actual endpoint probe on 2026-07-11:
  - endpoint: `MATRIX_BASE_URL + /images/generations` with API key redacted;
  - result: HTTP `403`;
  - no image was returned;
  - this is recorded as a real provider-side blocking result, not treated as success.

## Test Commands

- Backend compile: `mvn.cmd -DskipTests compile`
  - Result: passed on 2026-07-11.
- Backend package: `mvn.cmd "-Dmaven.test.skip=true" package`
  - Result: passed on 2026-07-11.
- Frontend build: `npm.cmd run build`
  - Result: passed on 2026-07-11 with existing Vite chunk-size warning.
- Targeted test attempt: `mvn.cmd -Dtest=DesignPackageServiceTests test`
  - Previous sandbox result: failed during `testCompile` because the sandboxed Java compiler could not resolve directory classpath entries.
  - Non-sandbox verification result: passed on 2026-07-11.
- Targeted CAD test: `mvn.cmd -Dtest=MechanicalCadPipelineTests test`
  - Result: passed on 2026-07-11.
  - Covered: standard part selection, CAD features, `AssemblyBuilder`, Java-to-Python CAD worker, real STEP output and `assembly-validation.json`.
- Targeted package gate test: `mvn.cmd -Dtest=DesignPackageServiceTests test`
  - Result: passed on 2026-07-11.
  - Observed artifacts in test log:
    `assembly.step` size about 819 KB,
    five part STEP files,
    `assembly-validation.json`,
    `assembly.dxf`,
    `cad_preview.svg`,
    `cad_preview.png`,
    five different part DXF files,
    `paper.docx`,
    `manifest.json`,
    `project_package.zip`.
- Three-project end-to-end regression: `mvn.cmd -Dtest=DesignPackageRegressionTests test`
  - Result: passed on 2026-07-11.
  - All three projects generated `project_package.zip`.
  - All three projects generated `assembly.step`, five part STEP files, `assembly-validation.json`, assembly DXF/SVG/PNG preview, five part DXF drawings, `paper.docx` and `manifest.json`.
  - The regression asserts that the three final mechanical design plans have different subsystem sets.
  - Oil-tank wall-climbing robot:
    - recognized category: wall-climbing inspection robot / crawler and magnetic adsorption mechanism;
    - assembly STEP size observed in test log: about 672 KB;
    - assembly validation artifact: generated and accepted by quality gate;
    - part drawing count: 5;
    - ZIP size observed in test log: about 235 KB;
    - final status: success.
  - Gravity settling chamber:
    - recognized category: gravity settling separation chamber;
    - assembly STEP size observed in test log: about 984 KB;
    - assembly validation artifact: generated and accepted by quality gate;
    - part drawing count: 5;
    - ZIP size observed in test log: about 379 KB;
    - final status: success.
  - Belt conveyor:
    - recognized category: belt conveyor / continuous conveying mechanism;
    - assembly STEP size observed in test log: about 819 KB;
    - assembly validation artifact: generated and accepted by quality gate;
    - part drawing count: 5;
    - ZIP size observed in test log: about 260 KB;
    - final status: success.
- CAD worker self-test:
  - Result: passed on 2026-07-11.
  - `assembly.step` reopened successfully by OpenCascade.
  - `solidCount`: 6.
  - `volume`: greater than 0.
  - `uniquePositionCount`: 6.
  - validation status: passed.

## Remaining Issues

- FreeCAD / FreeCADCmd is not installed on the machine; current real STEP route uses CadQuery/OCP.
- Drawing generation still uses the final `AssemblyModel` / component geometry data and drawing plan; a future phase should project directly from reopened STEP topology for full hidden-line CAD drafting.
- Wanliang image endpoint returned HTTP 403 for the tested OpenAI-compatible image endpoint. The provider now records this as an explicit blocked request in `image-generation-audit.json` when called through `/image/test`.
- Persistent resumable stage state is not yet stored as an independent table; current phase status is represented through artifacts and audit JSON.
- Existing text in several Java/Vue files contains mojibake from older encoding damage; this task avoided broad re-encoding to reduce risk.

## Phase 8: Runtime 50 Percent Stall Audit

- Branch created for this round: `mechanical-complete-pipeline`.
- Starting commit: `e19f290`.
- `git status` before edits showed only untracked local/user files such as screenshots, `data/`, edge crops and SQL helper files. No user changes were reverted.
- Project size snapshot:
  - `backend/src`: 1.13 MB, source, keep.
  - `backend/target`: 55.09 MB, rebuildable build output.
  - `backend/storage`: 68.65 MB, user/generated data, keep.
  - `backend/work`: empty, keep.
  - `backend/data`: 0.05 MB, app data, keep.
  - `frontend/src`: 0.23 MB, source, keep.
  - `frontend/node_modules`: 139.37 MB, dependency cache, keep.
  - `frontend/dist`: 2.04 MB, rebuildable build output.
  - root `work`: 6.4 MB, generated work data, keep until individually classified.
- Real UI stall root cause:
  - `frontend/src/views/NewProject/index.vue` set `currentStep = 4` after analysis and `currentStep = 5` before calling the long synchronous `POST /api/design-packages/generate`.
  - The request had no persisted `jobId`, no heartbeat, no backend stage polling and no refresh recovery.
  - While the synchronous request was running, failed, timed out or returned late, the page could only display the local 50 percent state.
- Wrong model source:
  - `frontend/src/components/ParametricModelBuilder.js` generated production fallback models when backend assembly data was absent.
  - `frontend/src/components/ModelQualityGate.js` classified equipment by project title keywords and enforced fixed crawler/settling core part rules and mesh thresholds.
  - `frontend/src/components/ModelRepairAgent.js` replaced failed previews with fixed settling-chamber or generic repair assemblies.
  - Therefore the page could show a conveyor-like or unrelated block/cylinder assembly even when the backend final CAD pipeline had not produced a final model.
- Fix direction selected:
  - Frontend preview quality gate only checks renderability and backend assembly/constraint presence.
  - Frontend no longer fabricates topic-specific fallback equipment in production.
  - Backend design package generation gains persisted async job endpoints and real stage/heartbeat polling while keeping the old synchronous endpoint for compatibility.

### Phase 8 Implementation

- Added backend async design package job support:
  - `POST /api/design-packages/jobs`;
  - `GET /api/design-packages/jobs/{jobId}`.
- Reused `document_job` instead of adding a new table:
  - `job_id`: async package job id, prefix `dp_`;
  - `status`: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`;
  - `processed_paragraphs`: backend progress percent;
  - `paragraphs_json`: persisted job state JSON with `stage`, `message`, `errorCode`, `result` and `finishedAt`;
  - `updated_at`: heartbeat time;
  - `cost_points` / `points_charged`: success-only charging state.
- Added backend stages:
  `PARSING`, `ANALYZING`, `PLANNING`, `STRUCTURE`, `PART_GENERATION`, `ASSEMBLY`, `STEP_EXPORT`, `STEP_VALIDATION`, `DRAWING`, `PAPER`, `QUALITY_GATE`, `PACKAGING`, `COMPLETED`, `FAILED`.
- `DesignPackageService` now reports stage callbacks at real generation points while the old synchronous `/generate` endpoint remains available.
- `DesignPackageJobService` now:
  - checks points at job creation without charging;
  - runs generation on `TaskExecutor`;
  - sets `AuthContext` inside the background thread and clears it in `finally`;
  - updates heartbeat on every stage;
  - marks stale RUNNING jobs as failed when heartbeat is older than 30 minutes;
  - charges `DESIGN_GENERATE` exactly once after successful package generation.
- Frontend `NewProject/index.vue` now:
  - creates a job through `/design-packages/jobs`;
  - polls the backend job endpoint every 2 seconds;
  - maps backend progress to the existing visual step list;
  - stores the active job id in `localStorage` and resumes polling after refresh;
  - stops using long synchronous `/generate` for the page action;
  - shows backend `stage | errorCode | message` on failure.
- Frontend model preview fix:
  - `ModelQualityGate.js` no longer classifies equipment from title keywords or requires fixed oil-tank robot / settling chamber / conveyor parts.
  - `ModelRepairAgent.js` no longer inserts fixed repair assemblies into production preview.
  - `ParametricModelBuilder.js` no longer draws crawler, settling chamber or generic fallback equipment when backend assembly data is absent.
  - `ModelViewer3D.vue` no longer tells users to lower generation requirements; it shows that AI mechanical plan and CAD assembly are being generated, or displays the real backend failure stage.
- Additional build repair:
  - `NewProject/index.vue` had pre-existing broken mojibake tags and unterminated strings that prevented Vite from compiling. Only malformed tags and user-facing strings on that page were repaired.

### Phase 8 Verification

- Backend compile: `mvn.cmd -DskipTests compile`
  - Result: passed on 2026-07-11.
- Frontend build: `npm.cmd run build`
  - Result: passed on 2026-07-11.
- CAD pipeline test: `mvn.cmd -Dtest=MechanicalCadPipelineTests test`
  - Result: passed on 2026-07-11.
- Package service test: `mvn.cmd -Dtest=DesignPackageServiceTests test`
  - Result: passed on 2026-07-11.
  - Oil-tank wall-climbing robot generated:
    `MechanicalDesignPlan.json`, `mechanical-pipeline-audit.json`, `assembly-model.json`, `model_3d.json`, `assembly.step`, five part STEP files, `assembly-validation.json`, `assembly.dxf`, preview SVG/PNG, five part DXF drawings, `paper.docx`, `manifest.json`, `project_package.zip`.
- Three-project regression: `mvn.cmd -Dtest=DesignPackageRegressionTests test`
  - Result: passed on 2026-07-11.
  - Oil-tank robot, gravity settling chamber and belt conveyor each generated a final ZIP and real STEP deliverables through the package service.
- Full backend test suite: `mvn.cmd test`
  - Result: passed on 2026-07-11.
  - Summary: 27 tests, 0 failures, 0 errors.
- Backend package: `mvn.cmd "-Dmaven.test.skip=true" package`
  - Result: passed on 2026-07-11.

## Final Artifact Paths

- Files are persisted as `DocumentJobRecord` rows and exposed through `/api/documents/{jobId}/download`.
- Final ZIP artifact name: `project_package.zip`.
- New manifest artifact name: `manifest.json`.
