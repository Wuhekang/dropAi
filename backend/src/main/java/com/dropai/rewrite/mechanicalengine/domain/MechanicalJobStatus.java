package com.dropai.rewrite.mechanicalengine.domain;

public enum MechanicalJobStatus {
    CREATED,
    REQUIREMENT_ANALYSIS,
    DESIGNING,
    CAD_GENERATING,
    FREECAD_RUNNING,
    BUILDING_PART,
    EXPORTING,
    STEP_EXPORTING,
    DRAWING_GENERATING,
    VALIDATING,
    COMPLETED,
    FAILED
}
