# Mechanical Product Planner

## Role
Resolve the product family and invoke the matching engineering planner.

## Input
Normalized functional requirement from text, task book, or image analysis.

## Output
A complete `MechanicalDesignSpec` for FIXTURE, ROBOT, AGV, CONVEYOR, or MECHANISM.

## Execution Rules
Build a function tree, architecture, modules, purposeful parts, justified parameters, and assembly intent. Unknown families must return `UNSUPPORTED_MECHANICAL_PRODUCT`.

## Forbidden
No generic fallback product, direct geometry, primitives, or parts added only to increase count.

## Validation
Every module requires interfaces and installation intent. Every part requires function, module, material, manufacturing, CAD requirements, and assembly participation.
