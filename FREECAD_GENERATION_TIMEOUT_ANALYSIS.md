# DropAI FreeCAD Generation Timeout Analysis

## 1. Purpose

This document summarizes the current DropAI mechanical CAD generation failure for external technical review. The immediate goal is to determine why FreeCAD stalls during a multi-part model export and how the generation pipeline should be redesigned for reliable production use.

## 2. Environment

- Server: Windows Server
- Backend: Java Spring Boot
- Reverse proxy: Nginx
- CAD runtime: FreeCAD 1.1.3, headless `FreeCADCmd.exe`
- CAD method: FreeCAD PartDesign feature workflow
- Browser entry: DropAI Mechanical CAD page
- Current Java process timeout for FreeCAD: 10 minutes
- Current Nginx API read/send timeout after adjustment: 600 seconds

## 3. Current Pipeline

```text
User requirement
  -> MechanicalDesignSpec
  -> FeatureBasedCadSpec
  -> Java generates build_partdesign.py
  -> FreeCADCmd executes the script
  -> PartDesign bodies and features
  -> Per-part BREP and STEP
  -> Assembly.FCStd and Assembly.STEP
  -> Assembly STL preview
  -> Drawing artifacts
  -> CAD reality report
```

The HTTP request currently remains open while Java waits synchronously for the FreeCAD process to finish.

## 4. Problems Already Fixed

### 4.1 JSON passed as a FreeCAD command-line argument

FreeCAD previously attempted to import `feature-based-cad-spec.json` as a mesh. The spec and workspace are now passed through environment variables.

### 4.2 Obsolete Drawing module usage

The script no longer depends on the obsolete FreeCAD `Drawing` module. Drawing projection data is produced from BRep edges.

### 4.3 Generated Python syntax failure

Java text-block escaping previously generated an invalid Python line for DXF output:

```text
unterminated string literal (detected at line 132)
```

The Java source now escapes the newline correctly. The generated `build_partdesign.py` passes `python -m py_compile` locally, and the targeted Java mechanical tests pass.

## 5. Current Failure

After the syntax fix, the browser eventually receives HTTP 504 or a generation failure. Initially this appeared to be a general performance problem, but inspection of the server workspace identified a deterministic stall.

### 5.1 Process evidence

- `FreeCADCmd.exe` started at approximately 09:58.
- After roughly 10 minutes, accumulated CPU time was only about 1.05 seconds.
- Working memory remained around 68 MB.
- Workspace files stopped changing at approximately 09:58:06.
- Java terminated the FreeCAD process when the 10-minute process timeout expired.

This indicates an internal blocking or stalled export operation, not ten minutes of active CAD computation.

### 5.2 Generated artifacts

The latest job workspace contained:

```text
build_partdesign.py
feature-based-cad-spec.json
01_Model/Parts/P001.brep
01_Model/Parts/P002.brep
02_STEP/P001.step
```

It did not contain:

```text
02_STEP/P002.step
01_Model/Assembly.FCStd
02_STEP/Assembly.STEP
02_STEP/Assembly.stl
02_STEP/cad-reality-report.json
03_Drawing/projection-lines.json
03_Drawing/Assembly.svg
03_Drawing/Assembly.dxf
```

### 5.3 Exact suspected blocking point

The generated script performs these operations for every part:

```python
body.Tip.Shape.exportBrep(part_brep_path)
Part.export([body], part_step_path)
```

For P002, BREP export completed but STEP did not appear. Therefore the current evidence places the stall at:

```python
Part.export([body], P002_STEP_PATH)
```

P002 is the robot `Drive carrier`, generated as a rectangular PartDesign body with Sketch, Pad, Hole, and Fillet features.

## 6. HTTP Timeout Relationship

Originally:

- Java allowed FreeCAD to run for 10 minutes.
- Nginx allowed `/api/` to wait for only 60 seconds.

This caused Nginx to return 504 while Java and FreeCAD continued running. Nginx was changed to:

```nginx
proxy_connect_timeout 30s;
proxy_send_timeout 600s;
proxy_read_timeout 600s;
```

This aligns the proxy timeout with Java, but it does not solve the underlying FreeCAD stall. Increasing both limits further would only conceal a blocked export.

## 7. Proposed Immediate Fix

Replace document-object export:

```python
Part.export([body], output_path)
```

with direct final-shape export:

```python
body.Tip.Shape.exportStep(output_path)
```

The rationale is to export only the final valid BRep shape and avoid traversal of the complete `PartDesign::Body` feature history during per-part STEP export.

This change still requires validation on the Windows Server FreeCAD 1.1.3 runtime before it is committed.

## 8. Recommended Production Redesign

### 8.1 Asynchronous generation job

The browser should not hold one HTTP connection for the entire CAD job.

Recommended interface:

```text
POST /api/mechanical/projects/{projectId}/generate
  -> returns jobId immediately

GET /api/mechanical/jobs/{jobId}
  -> returns stage, progress, current part, timestamps and error
```

The frontend can poll every two seconds and resume status display after a page refresh.

### 8.2 Stage and part timeouts

Use bounded timeouts instead of relying only on one global timeout:

```text
Single-part feature build: 120 seconds
Single-part STEP export: 60 seconds
Assembly generation: 180 seconds
Assembly STL export: 120 seconds
Drawing generation: 180 seconds
Overall job: 20 minutes
```

A blocked operation should produce an explicit error such as:

```text
P002_STEP_EXPORT_TIMEOUT
```

### 8.3 Real progress reporting

FreeCAD should write or emit progress after every feature and artifact:

```json
{
  "stage": "PART_STEP_EXPORT",
  "part": "P002",
  "completedParts": 1,
  "totalParts": 5,
  "progress": 34
}
```

Output must be flushed immediately so Java can persist and expose it to the frontend.

### 8.4 Split preview from full delivery

The first interactive result should prioritize:

- Assembly FCStd
- Assembly STL preview
- Assembly STEP

Per-part STEP files, drawings, PDF documentation and the final package can be generated in a subsequent background stage.

### 8.5 Process isolation

Consider generating each part in an isolated FreeCAD subprocess. Benefits:

- A blocked part can be terminated independently.
- Completed parts remain reusable.
- Retries can target only the failed part.
- Independent parts may later be generated with controlled parallelism.

FreeCAD document operations should not be made multithreaded inside one process.

## 9. Questions for External Review

1. Is `Part.export([PartDesign::Body], path)` known to block in FreeCAD 1.1.x for some feature histories, and is `Shape.exportStep(path)` the preferred reliable alternative?
2. Should per-part STEP export use `body.Tip.Shape`, a temporary `Part::Feature`, or a dedicated export document?
3. Could a successful BREP export followed by a blocked STEP export indicate invalid topology that should first be checked with `shape.check()` or `shape.isValid()`?
4. Which FreeCAD or OpenCascade diagnostic settings can expose the exact STEP writer stall?
5. Should each part be generated in a separate FreeCAD process for production isolation?
6. What is the safest way to interrupt a blocked STEP export on Windows without losing already completed artifacts?
7. Is an asynchronous job architecture with polling sufficient, or would server-sent events be preferable for progress reporting?
8. Which artifacts should be generated in the fast preview stage and which should be deferred to the full engineering package stage?

## 10. Acceptance Criteria

The problem should be considered resolved only when all of the following are demonstrated on the server:

- Every configured PartDesign part has a non-empty BREP or FCStd artifact.
- Every required part STEP is non-empty and exported within its stage timeout.
- `Assembly.FCStd`, `Assembly.STEP`, and `Assembly.stl` are generated.
- Drawing JSON, SVG, and DXF artifacts are generated.
- The CAD reality report passes.
- The frontend reports real progress rather than remaining at 0%.
- Refreshing the page restores the active job state.
- A blocked part produces a specific error without leaving FreeCAD running indefinitely.
- A typical five-part preview completes in approximately one to three minutes.
- A full engineering package normally completes within three to eight minutes.

## 11. Current Conclusion

The immediate failure is not simply that the model needs more than ten minutes. The inspected run stopped making progress after approximately six seconds and stalled while exporting P002 from a `PartDesign::Body` to STEP. The correct next step is to make STEP export reliable and observable, then replace the synchronous HTTP workflow with a resumable asynchronous generation job.
