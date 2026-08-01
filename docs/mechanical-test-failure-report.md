# Mechanical Test Failure Report

## Scope

This report explains the failures observed after the mechanical engineering knowledge/skill/reviewer upgrade.

The upgrade did not change database tables, indexes, seed data, payment data, or SQL migration files. No SQL update file is required for this change.

## Commands Run

- `mvn -Dtest=MechanicalQualityReviewerTests test`
- `mvn clean package -DskipTests`
- `mvn test`

## Passing Checks

- `MechanicalQualityReviewerTests`: passed.
- Backend compilation and packaging with skipped tests: passed.
- JSON knowledge files under `knowledge/mechanical/`: parsed successfully.
- `mechanical-ai/schemas/MechanicalDesignResult.json`: parsed successfully.

## Full Test Failures

### 1. CAD worker dependency failure

Affected tests:

- `MechanicalCadPipelineTests.localStandardPartDatabaseFeedsCadFeaturesAssemblyAndStepExport`
- `DesignPackageServiceTests.successfulArtifactsHaveRealDownloadMetadata`
- `DesignPackageRegressionTests.threeMechanicalProjectsGenerateDifferentValidatedPackages`

Observed root error:

```text
ImportError: cannot import name 'DelimitedList' from 'pyparsing'
```

Failure path:

```text
StepExportEngine.export
-> backend/cad_worker/cad_worker.py
-> import cadquery
-> cadquery imports pyparsing.DelimitedList
-> local Python loads an incompatible pyparsing package from C:\python\lib\site-packages
```

Why this fails:

CadQuery expects a newer compatible `pyparsing` API that provides `DelimitedList`. The current local Python environment resolves `pyparsing` from `C:\python\lib\site-packages`, where that symbol is unavailable. Because STEP generation depends on CadQuery, STEP export fails, and package-generation tests become `failed`.

Why this is not caused by the current code change:

The mechanical upgrade added a Java quality reviewer, schema, knowledge JSON, and skills. It did not change `cad_worker.py`, Python dependencies, `StepExportEngine`, or CAD worker configuration. The failing stack trace occurs before generated STEP content is returned.

Recommended fix:

Use an isolated CAD worker Python environment and ensure CadQuery and pyparsing are compatible. On Windows, prefer a virtual environment dedicated to DropAI:

```powershell
cd C:\Users\Administrator\Documents\dropAi\backend
python -m venv .venv-cad
.\.venv-cad\Scripts\python.exe -m pip install --upgrade pip
.\.venv-cad\Scripts\python.exe -m pip install --upgrade "pyparsing>=3.1" cadquery
```

Then point DropAI to that Python:

```powershell
$env:CAD_WORKER_PYTHON="C:\Users\Administrator\Documents\dropAi\backend\.venv-cad\Scripts\python.exe"
mvn test
```

If a project `.env` is used:

```text
CAD_WORKER_PYTHON=C:\Users\Administrator\Documents\dropAi\backend\.venv-cad\Scripts\python.exe
```

### 2. Existing drawing text assertion failure

Affected test:

- `DesignPackageModuleTests.partDrawingEngineProducesMajorEngineeringPartDrawings`

Observed assertion:

```text
expected: <true> but was: <false>
```

The failing assertion checks whether generated part drawing content contains expected Chinese drawing markers such as:

```text
结构特征
未注尺寸公差
基准A
```

Why this fails:

The generated drawing artifact content does not contain at least one expected marker string. This is a drawing-output content mismatch, not a compile error. It likely comes from an existing difference between the test's expected drawing text and the current `DrawingEngine` output.

Why this is not caused by the current code change:

The mechanical upgrade did not modify `DrawingEngine`, `PartDrawingEngine`, or the test expectations. This assertion was already observed as a pre-existing failure during prior mechanical-vision verification.

Recommended fix:

Inspect the generated part drawing content and decide whether:

1. `DrawingEngine` should restore the expected labels, or
2. the test should be updated to match the current drawing terminology.

Do this as a separate targeted drawing-engine fix, not as part of the mechanical knowledge/reviewer upgrade.

## Summary

The new mechanical reviewer/schema change is healthy. The remaining full-suite failures are environment/dependency and existing drawing-output issues:

- CAD worker fails because local CadQuery loads an incompatible `pyparsing`.
- Drawing assertion fails because current generated drawing text does not match the expected marker strings.

No SQL migration is required.
