# DropAI Mechanical AI Engineering Platform V3 Report

## 1. Upgrade Scope

V3 replaces the single-product planning entry with a product-family planning layer while preserving the existing engineering chain:

`User Requirement -> Mechanical Chief Engineer -> MechanicalDesignSpec -> FeatureBasedCadSpec -> FreeCAD PartDesign -> STEP -> Drawing -> Document`

No database schema or SQL migration is required by this upgrade.

## 2. Product Family Planning

The new `MechanicalProductFamilyResolver` recognizes these supported families:

- Fixture / automatic clamp
- AGV
- Mobile or industrial robot
- Conveyor
- General mechanism

Unsupported requirements fail explicitly with `UNSUPPORTED_MECHANICAL_PRODUCT`; they are not routed to a generic primitive model.

Each family has a dedicated `ProductPlanner`. Every planner produces product definition, functional decomposition, architecture, modules, purposeful parts, engineering parameters, materials, manufacturing methods, and assembly intent.

## 3. CAD Contract

All V3 planned parts use the shared feature contract:

- `SKETCH`
- `PAD`
- `HOLE` or `POCKET`
- `FILLET` or `CHAMFER`

Assembly intent uses engineering relationships such as `FIXED`, `COINCIDENT`, `CONCENTRIC`, `DISTANCE`, and `ANGLE`. Product planners do not emit box, cylinder, sphere, or mesh primitives.

The existing `MechanicalDesignCadConverter`, `PartDesignJobGenerator`, and `CADRealityValidator` remain the only production route from design intent to PartDesign execution and STEP validation.

## 4. Engineering Analysis

`MechanicalAnalysisEngine` now produces a preliminary engineering report containing:

- Governing load and load path
- Estimated stress and displacement
- Material yield-strength reference
- Safety factor
- Stress-cloud data for the web result view
- Explicit CalculiX-ready status

These calculations are preliminary engineering estimates. They do not claim to be a native FEA solution.

## 5. Drawing And Documentation

Drawing artifacts now carry provenance metadata derived from the project CAD model, including orthographic views, drawing standard, tolerances, materials, and technical requirements. Artifact validation rejects incomplete drawing metadata.

`MechanicalDocumentAgent` generates the design PDF from the current product, architecture, modules, parameters, analysis, assembly constraints, and manufacturing information. It no longer emits an automatic-clamp-only static report for every product family.

## 6. Frontend

The project result page now presents these engineering stages independently:

- Mechanical design
- Feature CAD
- Assembly
- Drawing
- Analysis
- Document
- Delivery package

The design and analysis views show structured engineering results instead of raw JSON or debug logs.

## 7. Skills

V3 adds narrowly scoped instructions for:

- Mechanical product planning
- Engineering drawing generation
- Mechanical analysis
- DFMA review
- Mechanical validation

Each skill defines its role, inputs, outputs, engineering rules, forbidden shortcuts, and validation requirements.

## 8. Verification

Automated V3 tests cover automatic clamp, AGV, mobile robot, and conveyor requirements. They verify:

- Product family resolution
- `MechanicalDesignSpec` generation
- Conversion to `FeatureBasedCadSpec`
- Required feature history for every part
- Non-empty assembly constraints
- PartDesign job generation without primitive APIs
- Analysis report generation
- Dynamic engineering PDF generation
- Explicit rejection of unsupported product types

Commands verified:

- Targeted mechanical regression tests: passed
- Frontend production build: passed
- Full backend test suite: passed

## 9. Native CAD Validation Boundary

The Java-to-FreeCAD PartDesign job contract and primitive prohibition are covered by automated tests. Native `PartDesign::Body`, STEP, and drawing execution still requires `FreeCADCmd` in the runtime environment. A machine without FreeCAD cannot honestly certify native BRep output; production validation remains enforced by the existing CAD reality checks after execution.

## 10. Result

V3 establishes a product-aware mechanical engineering platform instead of a clamp-specific generator. New product families share one strict, feature-based CAD pipeline, while analysis, drawings, documents, and the frontend are generated from the actual selected product design.
