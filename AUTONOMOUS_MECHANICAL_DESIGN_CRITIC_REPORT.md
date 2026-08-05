# Autonomous Mechanical Design Critic Report

## Production flow

The production chain now performs:

`MechanicalDesignSpec -> MechanicalDesignCritic -> ArchitectureReviewValidator -> FeatureBasedCadSpec`

The critic combines deterministic, non-bypassable integrity checks with an independent AI chief-engineer review.

## Review output

`DesignReviewReport` contains:

- score from 0 to 100
- categorized issues with CRITICAL, MAJOR, or MINOR severity
- recommendations
- approval decision
- reviewer source

The report is attached to `MechanicalProject` for API and frontend visibility.

## Blocking behavior

- Missing critic model: `AI_REASONING_UNAVAILABLE`
- Invalid critic JSON: `INVALID_DESIGN_REVIEW`
- Unapproved or critically flawed design: `DESIGN_REVIEW_REJECTED`

CAD generation cannot start after a rejected review. Deterministic critical findings override an incorrect AI approval.

## Review coverage

- requirement and required-system coverage
- mechanical-system completeness
- mechanism and load suitability through AI semantic review
- part purpose, uniqueness, material, and manufacturing definition
- assembly connectivity
- maintenance and safety recommendations

## Defect injection verification

Automated tests deliberately inject and reject:

1. a wall-climbing robot without its required adhesion system
2. an AGV with a disconnected assembly plan
3. a mechanical arm without a required joint braking system
4. a fixture with a duplicated part

These checks verify that obvious defects cannot pass merely because an AI critic returns `approval: true`.
