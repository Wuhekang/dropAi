# Render CAD Worker Environment

Date: 2026-07-12

## Root Cause

- Render can find and start `/app/cad_worker/cad_worker.py`.
- The confirmed runtime failure is `ModuleNotFoundError: No module named 'cadquery'`.
- This means the worker script path problem is mostly fixed, but the Python interpreter used by Java does not contain CadQuery/OCP.
- The failure chain is:
  - CadQuery missing;
  - CAD Worker startup fails;
  - `assembly.step` fails;
  - `part_01.step` through `part_05.step` fail;
  - STEP validation fails;
  - DXF, paper, manifest and ZIP are blocked by the CAD model gate.

## Repository Audit

- Project root: `C:\Users\Administrator\Documents\dropAi`
- Backend: `backend`
- Backend Maven file: `backend/pom.xml`
- Maven wrapper: not present at `backend/mvnw`; local builds use `mvn.cmd`.
- CAD Worker development script: `backend/cad_worker/cad_worker.py`
- CAD Worker packaged script: `backend/src/main/resources/cad-worker/cad_worker.py`
- CAD Worker requirements:
  - `backend/cad_worker/requirements.txt`
  - `backend/src/main/resources/cad-worker/requirements.txt`
- Docker deployment file: `Dockerfile.render`
- Render blueprint: `render.yaml`
- Docker ignore file: `.dockerignore`
- Git ignore file: `.gitignore`
- Spring config: `backend/src/main/resources/application.yml`

## Render Deployment Configuration

- `render.yaml` uses Docker deployment:
  - `runtime: docker`
  - `dockerfilePath: ./Dockerfile.render`
- Runtime container contains:
  - Java 17 runtime image;
  - Python 3;
  - pip;
  - Python virtual environment at `/opt/dropai-cad-venv`;
  - CAD Worker copied to `/app/cad_worker`;
  - Spring Boot JAR copied to `/app/app.jar`.
- CAD environment variables:
  - `CAD_WORKER_ENABLED=true`
  - `CAD_WORKER_PYTHON=/opt/dropai-cad-venv/bin/python`
  - `CAD_WORKER_SCRIPT=/app/cad_worker/cad_worker.py`
  - `CAD_WORKER_WORK_DIR=/tmp/dropai-cad-worker`
  - `CAD_WORKER_TIMEOUT_SECONDS=300`
- Spring Boot port now reads `PORT` with local fallback:
  - `server.port=${PORT:8080}`

## Dockerfile Changes

- Runtime stage installs:
  - `python3`
  - `python3-pip`
  - `python3-venv`
  - `libgl1`
  - `libglib2.0-0`
  - `libxrender1`
  - `libxext6`
- Runtime stage creates `/opt/dropai-cad-venv`.
- Runtime stage installs `/app/cad_worker/requirements.txt` into `/opt/dropai-cad-venv`.
- Dependency installation is mandatory. There is no `|| true` fallback.
- Build runs `scripts/render-cad-preflight.sh`.
- If CadQuery import, Worker health, STEP export or STEP re-open fails, Docker build fails.

## Requirements

Current CAD Worker imports:

- `cadquery`
- `OCP.STEPControl`
- `OCP.IFSelect`
- `OCP.TopAbs`
- `OCP.TopExp`

Pinned requirement:

```text
cadquery==2.5.2
```

No unused heavy Python dependency was added.

## Java CAD Worker Invocation

- `StepExportEngine` uses `CadWorkerProperties`, `CadWorkerLocator` and configured values.
- Java `ProcessBuilder` uses:
  - configured Python executable;
  - absolute worker script path;
  - absolute input JSON path;
  - absolute output directory path.
- Java no longer relies only on `user.dir` relative paths.
- Worker resolution order:
  1. configured `CAD_WORKER_SCRIPT`;
  2. development `cad_worker/cad_worker.py`;
  3. development `backend/cad_worker/cad_worker.py`;
  4. packaged classpath resource `cad-worker/cad_worker.py`.
- Runtime health endpoint:
  - `GET /api/mechanical/cad-worker/health`
- Startup log:
  - `CAD_WORKER_HEALTH=UP`
  - or `CAD_WORKER_HEALTH=DOWN ERROR_CODE=...`

## Health Check Behavior

`cad_worker.py --health` now performs:

1. Python executable detection;
2. CadQuery import;
3. OCP import;
4. box-with-hole creation;
5. temporary STEP export;
6. STEP re-open;
7. solid count validation;
8. temporary file cleanup.

If CadQuery is missing, the worker returns structured JSON with:

```text
status=DOWN
errorCode=CADQUERY_MODULE_NOT_FOUND
message=CAD Worker Python environment does not contain cadquery
```

## Local Environment Evidence

- Java command resolved by `java -version`:
  - Java 22 was first on the Windows PATH.
  - Maven compile still targets Java 17 release.
- Python command:
  - `C:\python\python.exe`
- Python version:
  - `3.10.8`
- pip:
  - `pip 26.1.1 from C:\python\lib\site-packages\pip`
- Local CadQuery:
  - `2.7.0`
- Local Worker health:
  - command: `python backend\src\main\resources\cad-worker\cad_worker.py --health`
  - result: `status=UP`
  - `pythonExecutable=C:\python\python.exe`
  - `cadqueryVersion=2.7.0`
  - `stepExportAvailable=true`
  - `stepImportAvailable=true`
  - `solidCount=1`
  - `boundingBox=100 x 60 x 10`

## Minimal CAD Test Evidence

Command:

```powershell
python backend\src\main\resources\cad-worker\cad_worker.py input.json out
```

Input:

- five components;
- five constraints;
- multiple distinct positions.

Output:

- `assembly.step`: 155969 bytes
- `part_01.step`: 50888 bytes
- `part_02.step`: 5758 bytes
- `part_03.step`: 50866 bytes
- `part_04.step`: 5754 bytes
- `part_05.step`: 34453 bytes
- `assembly-validation.json`: 1905 bytes

Validation:

- `opened=true`
- `solidCount=5`
- `volume=418249.4204660883`
- `boundingBox=310 x 170 x 68`
- `uniquePositionCount=5`
- `passed=true`

## Backend Test Evidence

Command:

```powershell
mvn.cmd "-Dtest=DesignPackageServiceTests,MechanicalCadPipelineTests" test
```

Result:

- passed on 2026-07-12;
- tests run: 2;
- failures: 0;
- errors: 0.

Generated evidence from the oil-tank wall-climbing robot test:

- `assembly.step` success, 421405 bytes;
- `part_01.step` success;
- `part_02.step` success;
- `part_03.step` success;
- `part_04.step` success;
- `part_05.step` success;
- `assembly-validation.json` success;
- DXF, preview image, paper, manifest and ZIP generated after STEP success.

## Local Docker Status

- Docker CLI is installed:
  - `Docker version 29.4.2`
- Docker daemon is not running on this Windows machine:
  - cannot connect to `npipe:////./pipe/dockerDesktopLinuxEngine`;
  - system cannot find the Docker Desktop Linux engine pipe.
- Because the daemon is unavailable, local `docker build` and local container health verification could not be completed in this environment.

## Render Verification Status

Pending after push.

Required Render evidence:

- Docker build logs show CadQuery import success;
- build logs show `render-cad-preflight` OK;
- startup logs show `CAD_WORKER_HEALTH=UP`;
- `GET /api/mechanical/cad-worker/health` returns `status=UP`;
- online mechanical task generates `assembly.step`;
- online task no longer shows `ModuleNotFoundError: No module named 'cadquery'`.

## Failure Items and Remaining Work

- Local Docker build is blocked by local Docker daemon state, not by project code.
- Render online verification still requires the next deployment logs.
- GLB export remains unavailable in the current worker and is not part of this CAD environment fix.
- If Render cannot install `cadquery==2.5.2`, the build must fail and the actual pip error must be used to choose a compatible version. No fake STEP fallback is allowed.

## 2026-07-12 Python 3.14 Compatibility Failure

### Render Build Root Cause

- Render build logs showed that the final runtime image installed Python `3.14.3`.
- The project pins `cadquery==2.5.2`.
- `cadquery==2.5.2` requires `cadquery-ocp>=7.7.0,<7.8`.
- In the Python 3.14 environment, pip could only resolve newer `cadquery-ocp` builds such as `7.9.3.1.1`.
- Pip therefore failed with:

```text
No matching distribution found for cadquery-ocp<7.8,>=7.7.0
```

This was not a memory issue and not a CAD Worker path issue. It was a Python ABI / wheel compatibility issue caused by relying on the base image distribution default Python.

### Fix

- Final runtime image changed from Java runtime image plus `apt install python3` to explicit `python:3.11-slim-bookworm`.
- Java 17 is installed inside the Python 3.11 final image with `openjdk-17-jre-headless`.
- The CAD virtual environment is created from Python 3.11:

```text
/opt/dropai-cad-venv/bin/python
```

- Docker build now asserts:

```text
sys.version_info[:2] == (3, 11)
```

- `CAD_WORKER_PYTHON` remains:

```text
/opt/dropai-cad-venv/bin/python
```

- `cadquery==2.5.2` is still pinned. It was not upgraded in this emergency fix.
- `pip install`, `pip check`, `pip show cadquery`, CadQuery import, OCP import and `render-cad-preflight.sh` are blocking build steps.
- There is no `|| true` dependency-install fallback.

### Expected Render Build Evidence

The next Render build should show:

```text
Python 3.11.x
PYTHON= 3.11.x
EXECUTABLE= /opt/dropai-cad-venv/bin/python
CADQUERY= 2.5.2
OCP_OK
[render-cad-preflight] OK
```

If Render still cannot install `cadquery==2.5.2` under Python 3.11, the build must fail and the pip error must be used as the next root cause.

### Local Verification After Dockerfile Change

- `python backend\src\main\resources\cad-worker\cad_worker.py --health`
  - Result: `status=UP`
  - Local Windows Python: `3.10.8`
  - Local CadQuery: `2.7.0`
  - STEP export: true
  - STEP import: true
  - solid count: 1
- `mvn.cmd -DskipTests compile`
  - Result: passed.
- `mvn.cmd "-Dtest=DesignPackageServiceTests,MechanicalCadPipelineTests" test`
  - Result: passed.
  - Oil-tank wall-climbing robot generated `assembly.step`, five part STEP files, `assembly-validation.json`, drawings, paper, manifest and ZIP.

### Local Docker Status

- Docker CLI is installed, but Docker daemon is still unavailable on this Windows machine.
- Local `docker build -f Dockerfile.render -t dropai-render-cad-test .` could not be executed until Docker Desktop / Linux engine is started.
- The Render build remains the authoritative Docker validation for this environment.
