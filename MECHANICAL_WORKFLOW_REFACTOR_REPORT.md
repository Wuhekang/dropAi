# DropAI Mechanical Workflow Refactor Report

## Scope

This V4.5 increment changes the mechanical module from a result-heavy AI report into a gated engineering delivery workflow. No database schema or application database credentials were changed.

## Implemented

- Added workflow memory for wall-climbing robots and AGVs. Memory reuses architecture, parameter rules, assembly rules, and CAD strategy, never historical dimensions.
- Added an auditable plugin capability registry and a discovery agent. Missing tools are reported for operator approval; production code does not download or execute unknown plugins.
- Strengthened the delivery quality gate:
  - STEP must contain complete ISO-10303-21 boundaries.
  - STEP reopen validation and drawing validation reports must pass.
  - DXF must contain geometry and a complete EOF boundary.
  - SVG must be a complete SVG document.
  - PNG must decode through ImageIO.
  - Assembly constraints must reference real component IDs and non-empty mate targets.
- BOM canonicalization now merges repeated semantic names by canonical name and material.
- Final user ZIP contains only:
  - `01_Assembly.step`
  - `02_Drawings.zip`
  - `03_Design_Report.docx`
- Internal JSON, validation logs, and manifests remain available to the quality pipeline but are hidden from the user-facing download list.
- Refactored the result UI into a mechanical workstation with tabs for assembly, drawings, BOM, design report, and deliverables. Internal source and confidence fields are no longer shown.

## Engineering workflow distilled

The refactor follows component-first and relationship-first assembly practice: components own geometry and coordinate systems, while joints/mates define relative position and permitted motion. Part geometry is created in assembly context when interfaces drive dimensions, and standard parts remain bottom-up library components.

## Completion rule

A job can reach `COMPLETED` only when CAD export, STEP reopen, assembly constraints, drawing generation, drawing render validation, BOM mapping, document generation, and the final quality gate all pass. Otherwise the ZIP is not generated and the job ends in `FAILED`.

## Validation

Run:

```powershell
cd backend
mvn test
mvn clean package -DskipTests
cd ..\frontend
npm run build
```
