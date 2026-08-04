# Mechanical Design Skill Execution Upgrade Report

Date: 2026-08-05

## Objective

The Mechanical Design stage now produces validated engineering intent instead of ordinary mechanical prose or CAD geometry. The active flow is:

```text
User Requirement
  -> Mechanical Chief Engineer
  -> MechanicalDesignSpec
  -> MechanicalDesignQualityValidator
  -> MechanicalDesignCadConverter
  -> FeatureBasedCadSpec
  -> FeatureInterpreter
  -> FreeCAD PartDesign
```

## MechanicalDesignSpec

The new typed contract contains:

- product definition: type, name, purpose, environment, operating principle, core functions;
- requirement interpretation: functions, performance goals, operating conditions, engineering constraints;
- hierarchical function tree;
- selected mechanical architecture, load path, and motion path;
- modules with functions, interfaces, and installation methods;
- part plans with engineering purpose, owning module, material, manufacturing, and required CAD features;
- assembly intent expressed as relationships rather than design-stage coordinates;
- engineering parameters with value, unit, and engineering reason;
- explicit material and manufacturing decisions.

The JSON Schema at `mechanical-engine/schemas/MechanicalDesignSpec.json` was updated to match the runtime Java contract.

## Quality Rules

`MechanicalDesignQualityValidator` rejects:

- incomplete product definitions or empty function trees;
- duplicate modules, duplicate part numbers, or duplicate part names;
- modules without interfaces or installation methods;
- parts without a function, material, manufacturing process, module owner, or CAD feature intent;
- parts without an assembly relationship;
- engineering parameters without an engineering reason;
- BOX, CUBE, CYLINDER, SPHERE, or PRIMITIVE design intent.

The design stage does not invoke CAD APIs and does not output geometry primitives.

## CAD Handoff

`MechanicalDesignCadConverter` performs the explicit handoff to `FeatureBasedCadSpec`. It preserves part purpose, materials, ordered feature intent, and assembly relationships. Initial component placement seeds are created only at the CAD boundary; coordinates are not part of `MechanicalDesignSpec.assemblyIntent`.

`FeatureInterpreter` remains the second gate and rejects unsupported feature types or invalid feature order before FreeCAD executes.

## Automatic Clamp Case

Input:

```text
设计自动夹具
```

The resulting design includes:

- 底座模块;
- 夹持模块;
- 驱动模块;
- 导向模块;
- five purposeful, manufacturable parts;
- a load path from handle to machine table;
- a motion path from handle rotation to moving-jaw translation;
- Fixed, Coincident, Slider, and Concentric assembly intent;
- justified clamping force, workpiece width, base envelope, and safety factor;
- SKETCH, PAD, HOLE, FILLET, and CHAMFER CAD requirements.

The resulting `MechanicalDesignSpec` converts successfully to a `FeatureBasedCadSpec` accepted by `FeatureInterpreter`.

## API And UI

- `POST /mechanical/projects/design-spec` returns the validated design contract directly.
- Existing mechanical project responses include `designSpec` for frontend inspection.
- Execution stages now report product definition, functional decomposition, architecture, part planning, engineering parameters, assembly intent, Feature Spec, PartDesign, validation, and package generation.

## Verification

| Check | Result |
|---|---|
| Automatic clamp MechanicalDesignSpec | PASS |
| Required four clamp modules | PASS |
| Part purpose/material/manufacturing completeness | PASS |
| Engineering parameter reasons | PASS |
| Assembly intent completeness | PASS |
| MechanicalDesignSpec to FeatureBasedCadSpec | PASS |
| Primitive intent absent | PASS |
| Unsupported product fails instead of returning fake design | PASS |
| Mechanical design and CAD kernel tests | PASS: 7/7 |
| Frontend production build | PASS |

The frontend build retains the existing bundle-size warning; it does not fail compilation.

## Current Scope

Phase 1 has a fully specified automatic-clamp design family. Unknown product types fail explicitly with `UNSUPPORTED_MECHANICAL_PRODUCT` instead of receiving a generic or fabricated architecture. Additional product families must be added as dedicated engineering planners with the same schema and quality gates.
