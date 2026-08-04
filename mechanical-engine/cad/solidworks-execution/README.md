# SolidWorks Worker Contract

Set `SOLIDWORKS_AUTOMATION_COMMAND` to an executable Windows worker. DropAI invokes it with two arguments: generated script path and workspace path. The worker must run SolidWorks API automation and return exit code 0 only after creating the complete artifact tree.

Set `FREECAD_VALIDATION_COMMAND` to the FreeCAD validation executable used by the worker to reopen `02_STEP/Assembly.STEP`, render `02_STEP/freecad-preview.png`, and write `02_STEP/freecad-validation.json` with `{ "passed": true }`.

The worker must also create one native `SLDPRT` and one DWG part drawing per `CADSpecification`, plus `Assembly.SLDASM`, `Assembly.STEP`, `Assembly.DWG`, and `Design_Report.pdf`. Returning exit code zero without these artifacts is treated as a failed design.
