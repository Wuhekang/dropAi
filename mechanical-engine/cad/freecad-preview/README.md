# FreeCAD Preview Validation

The command registered by `FREECAD_VALIDATION_COMMAND` must reopen `02_STEP/Assembly.STEP`, render `02_STEP/freecad-preview.png`, and write `02_STEP/freecad-validation.json` containing `{ "passed": true }`. Any missing or invalid output fails the project.
