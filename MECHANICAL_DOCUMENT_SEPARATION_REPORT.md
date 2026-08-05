# Mechanical Result and Document Pipeline Separation Report

## Scope

The mechanical CAD kernel and FeatureBasedCadSpec interpretation remain unchanged. This upgrade changes service boundaries, job execution, result models, validation rules, packaging and frontend workflow.

## Removed Coupling

- Mechanical generation no longer invokes `MechanicalDocumentAgent`.
- Mechanical validation no longer requires `04_Document/Design_Report.pdf`.
- Mechanical result packages no longer include the document directory.
- The mechanical page no longer waits synchronously for the complete FreeCAD request.
- Document state is no longer represented as a mechanical CAD stage or tab.

## New Models

- `MechanicalJobStatus`
- `MechanicalJobSnapshot`
- `MechanicalDesignResult`
- `DocumentGenerationResult`

`MechanicalDesignResult` contains design data, assembly, parts, model files, STEP files, drawings, BOM and validation. It contains no DOCX or design-report PDF fields.

## New Services

- `MechanicalJobService`: starts mechanical work on the application task executor and exposes job status.
- `DocumentGenerationService`: accepts an existing mechanical result and independently generates PDF and DOCX artifacts.

The document service reads the completed mechanical project. It does not rerun CAD, change features or invent new engineering parameters.

## API Changes

```text
POST /api/mechanical/projects/{projectId}/generate
GET  /api/mechanical/jobs/{jobId}

POST /api/documents/generate
GET  /api/documents/jobs/{jobId}
```

The legacy synchronous `/api/mechanical/projects/execute` endpoint remains temporarily available for compatibility, but the mechanical frontend now uses the asynchronous endpoints.

## Frontend Changes

- Mechanical generation returns immediately with a job ID.
- The page polls the mechanical job every two seconds.
- Current stage and progress are displayed beside the controls.
- Mechanical completion loads the STL preview.
- A separate `Generate design document` command becomes available only after a mechanical result exists.
- Document generation runs as its own job and appends its downloads only after completion.

## Package Boundary

`Mechanical_Result.zip` contains mechanical outputs only:

```text
01_Model
02_STEP
03_Drawing
05_Analysis
```

Document PDF and DOCX files are persisted as independent document artifacts and are not prerequisites for CAD validation.

## Database

No database schema was changed. Existing `document_job` storage is reused for downloadable binary artifacts. No SQL migration is required.

## Validation

- Backend package compilation completed successfully after the service split.
- Mechanical package tests now assert that document outputs are absent.
- Frontend uses short HTTP requests for job creation and polling, eliminating the long synchronous browser/Nginx wait.

## Remaining Production Work

The initial job registry is process-local. A production follow-up should persist job snapshots and mechanical-result metadata so active jobs can be recovered after a backend restart. FreeCAD worker isolation and per-stage timeout handling remain separate CAD-runtime reliability work and are not part of this boundary-only refactor.
