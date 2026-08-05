# FreeCAD Runtime Stability Report

## Scope

This upgrade is limited to the FreeCAD worker runtime, export path, job status, artifact success checks, and diagnostics. It does not change `MechanicalDesignSpec`, `FeatureBasedCadSpec`, the mechanical skill, product planning, or CAD design intent.

## Root cause addressed

The inspected production job completed `P002.brep` but stalled before `P002.step` appeared. The previous worker then waited for a single ten-minute global timeout and returned only `FREECAD_TIMEOUT`. This made a blocked STEP writer indistinguishable from a slow build and left the preview without a real STL/BRep artifact.

## Implemented changes

### 1. Final-shape export

- Per-part BRep and STEP exports use `body.Tip.Shape`.
- STEP is exported with `shape.exportStep(path)` instead of traversing a `PartDesign::Body` through `Part.export`.
- STL export uses a temporary `Part::Feature` holding a copy of the final shape, avoiding export traversal of the complete PartDesign history.
- Assembly STEP is exported from a validated placed-shape compound rather than `Part.export(objects, ...)`.

### 2. Topology validation before export

Every part now checks:

- body tip exists;
- shape is not null;
- `shape.isValid()` is true;
- solid count is greater than zero;
- volume is greater than zero;
- PartDesign feature history is present.

The assembly compound receives the same null, validity, solid-count, and volume checks. Failures raise `INVALID_BREP` with part and feature context.

### 3. Feature-level observable logging

The generated FreeCAD Python emits flushed JSON lines for every feature:

```text
DROP_AI_FEATURE|{"stage":"FEATURE_EXECUTION","part":"P002","feature":"FILLET","status":"START"}
DROP_AI_FEATURE|{"stage":"FEATURE_EXECUTION","part":"P002","feature":"FILLET","status":"SUCCESS","time":0.23}
```

A failure emits `status=FAILED` and the original error. The script raises a contextual error in the form:

```text
FEATURE_FAILED:P002:FILLET:<original error>
```

All progress and feature messages use immediate flushing.

### 4. Timeout and process cleanup

- Runtime timeout is configurable through `FREECAD_TIMEOUT_SECONDS` and defaults to 600 seconds.
- On timeout, the worker terminates the FreeCAD process and its descendants.
- stdout and stderr are captured separately.
- The last progress event is retained so a timeout can identify the affected part and stage.
- Instead of a reason-free `FREECAD_TIMEOUT`, the worker returns a contextual code such as:

```text
P002_PART_STEP_EXPORTING_TIMEOUT
```

### 5. Automatic runtime report

Every completed, failed, incomplete, or timed-out FreeCAD run writes:

```text
freecad-runtime-report.json
```

The report contains:

- command;
- runtime and configured timeout;
- last part, feature/message, and stage;
- stdout;
- stderr;
- exit code;
- files present in the workspace;
- failure reason.

The report is exposed as a live job artifact and persisted with partial artifacts when generation fails.

### 6. Real completion conditions

The worker no longer accepts process exit code zero alone. Required outputs must exist and be non-empty:

- `Assembly.FCStd`;
- `Assembly.STEP`;
- `Assembly.stl`;
- `cad-reality-report.json`;
- projection JSON;
- assembly SVG;
- assembly DXF.

The later `MechanicalArtifactValidator` remains responsible for the full CAD reality validation. Therefore `FEATURE_EXECUTION 100%` is not a success condition; only validated artifacts allow the job to reach `COMPLETED`.

### 7. Asynchronous job states

The existing API already creates an asynchronous job and returns a job ID rather than holding the HTTP request through FreeCAD execution. Status polling and resumable workspace support were already present.

Runtime state mapping was refined with:

- `CREATED`;
- `FREECAD_RUNNING`;
- `BUILDING_PART`;
- `EXPORTING`;
- `VALIDATING`;
- `COMPLETED`;
- `FAILED`.

## Simplified clamp V1 policy

The runtime is ready to validate the existing five-part automatic clamp baseline:

1. base;
2. fixed jaw;
3. moving jaw;
4. guide;
5. simple screw.

The first server verification must avoid real trapezoidal threads, complex sweeps, large Boolean chains, and decorative high-complexity fillets/chamfers. Those features should be restored one at a time after the basic FCStd/STEP/STL/SVG/DXF chain is stable.

No design-layer code was changed to enforce this policy because the upgrade was explicitly restricted to the runtime layer.

## Test results

Executed locally:

```text
mvn -q -Dtest=MechanicalEngineRewriteTests,MechanicalPlatformV3Tests test
```

Result: PASS.

The tests verify that the generated job contains PartDesign history, feature events, topology checks, direct final-shape STEP export, and explicit `FEATURE_FAILED` reporting. `git diff --check` reports no whitespace errors.

## Native FreeCAD validation status

Native end-to-end execution is now verified locally with FreeCAD 1.0.2 (`1.0.2R39319`). The official Windows portable bundle is located at:

```text
D:\FreeCADPortable\FreeCAD_1.0.2-conda-Windows-x86_64-py311
```

The current user's persistent environment is configured as:

```text
FREECAD_CMD=D:\FreeCADPortable\FreeCAD_1.0.2-conda-Windows-x86_64-py311\bin\freecadcmd.exe
FREECAD_TIMEOUT_SECONDS=120
```

The original downloaded NSIS installer could not complete because it repeatedly reported `Extract: error writing to file`; the official portable archive was therefore used to obtain a complete runtime with the required `Mod`, PartDesign, Sketcher, Part, and Mesh modules.

### Local kernel smoke test

The runtime created and reloaded a valid solid and exported all basic native formats:

```text
Runtime: 5.266 seconds
Solid volume: 12000.0
smoke.FCStd: 2216 bytes
smoke.brep: 2766 bytes
smoke.step: 6854 bytes
smoke.stl: 684 bytes
```

### Five-part clamp V1 result

The native integration test executed all five parts, including Sketch, Pad, Hole, Fillet/Chamfer, topology validation, BRep export, direct final-shape STEP export, STL preview export, assembly, and drawings.

```text
Result: PASS
FreeCAD runtime: 2.481 seconds
P002 STEP export: PASS (10782 bytes)
Assembly.FCStd: 67641 bytes
Assembly.STEP: 37746 bytes
Assembly.stl: 924084 bytes
Assembly.svg: 67897 bytes
Assembly.dxf: 104715 bytes
```

The generated assembly was then reopened in FreeCAD. Verification result:

```text
FreeCAD exit code: 0
FCStd PartDesign bodies: 5
STEP solids: 5
STEP total volume: 1313745.1516363781
STL bytes: 924084
```

The complete native test workspace is:

```text
C:\Users\Administrator\Documents\dropAi\backend\target\freecad-native-clamp-v1-1785906865545
```

Remaining server parity tests:

1. Repeat the locally passing test on the production Windows Server FreeCAD 1.1.3 runtime.
2. Confirm the deployed service account can read the configured FreeCAD path and write its job workspace.
3. Fillet and Chamfer are restored and tested separately.
4. An intentionally invalid feature returns `FEATURE_FAILED:<part>:<feature>` and produces `freecad-runtime-report.json`.
5. A forced export stall returns a part/stage-specific timeout and leaves no orphaned FreeCAD process.
6. The browser loads `Assembly.stl` only after validation and shows `FAILED` instead of an endless BRep placeholder when generation fails.

## Remaining production hardening

Per-part FreeCADCmd process isolation and retry are not included in this patch. The current checkpoint preserves completed parts, and timeout diagnostics identify the failed part, but the generated job still uses one FreeCAD process for the complete assembly. Process-per-part isolation should be the next runtime-only increment after this direct-shape export fix is verified on the server; implementing it before validating the known P002 export fix would add orchestration risk without proving the original stall is resolved.

## Acceptance decision

Code-level runtime stability upgrade: completed and unit-tested.

Local native FreeCAD stability: passed. The simplified five-part clamp produced valid, non-empty, readable FCStd/STEP/STL/SVG/DXF outputs in 2.481 seconds, including the previously blocked P002 STEP export.

Production deployment parity: pending one repeat on the Windows Server FreeCAD 1.1.3 service account and browser preview verification.

## Acceptance audit addendum

The native test previously used an environment condition that could report Maven `BUILD SUCCESS` while silently skipping the only FreeCAD test. The test now supports a required acceptance mode:

```text
mvn "-Dfreecad.required=true" "-Dtest=FreeCadNativeRuntimeTests,MechanicalEngineRewriteTests,MechanicalPlatformV3Tests" test
```

In required mode, a missing `FREECAD_CMD` fails the build instead of producing a misleading green result.

The strengthened native test also launches FreeCAD a second time and reopens both `Assembly.FCStd` and `Assembly.STEP`. It asserts:

- 5 editable PartDesign bodies in FCStd;
- 5 solids in the reopened STEP;
- non-zero total STEP volume.

Audited local result on 2026-08-05:

```text
Tests run: 8
Failures: 0
Errors: 0
Skipped: 0
Native runtime: 1.937 seconds
```

The current shell did not inherit the user-level `FREECAD_CMD`; it had to be set explicitly for the acceptance command. Production validation must therefore run from the same account and start-script environment used by the Spring Boot service.

The runtime is now proven locally at the CAD-kernel boundary. Full production acceptance still requires the deployed asynchronous API, artifact download authorization, browser incremental preview, timeout cleanup, and resume workflow to be exercised against the server's FreeCAD 1.1.3 installation.
