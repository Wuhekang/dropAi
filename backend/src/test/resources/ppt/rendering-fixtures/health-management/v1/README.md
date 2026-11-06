# Health Management Rendering Fixture V1

This directory freezes the sole end-to-end input fixture for DokiAI Academic PPT Rendering V1.

The fixture starts at the validated, asset-mapped presentation tree. Tests must load the checked-in JSON and bundled resources directly. They must not run `DocumentParser`, `ContentPlanner`, `ContentSanitizer`, `OutlinePlanner`, `OutlineValidator`, or `AssetMapper` to rebuild it.

## Frozen scope

- 40 presentation pages with stable `sourcePageId` values and exact ordering.
- One cover, one agenda, 35 content/image/table pages, two summary pages, and one thanks page.
- 25 mandatory source figures copied byte-for-byte from the thesis DOCX package.
- Two editable structured tables: database entity purposes and representative system test results.
- The exact page sequence and the RenderPlan structural invariants available before the V1 theme and compiler exist.
- User-visible forbidden-text rules for later package and preview quality gates.

## Provenance

The source thesis is “基于Spring Boot的个人健康管理系统的设计与实现” by 高瑞康. Its source DOCX SHA-256 is `bd782bf1b36fd5400740e383b44ce1f2009743fc6459c4e91f0d3a42a1081a82`.

The package contains 29 unique media files. Front-matter media `word/media/image1` through `image4` are intentionally excluded. The 25 body figures `image5` through `image29` are mapped by their following captions to figures 2-1 through 2-4, 3-1 through 3-3, and 4-1 through 4-18. Their original bytes and true file extensions are retained; no image is recompressed or re-encoded.

The previously generated server PPTX and the generic 15-page template test deck are not golden outputs and are not sources for this fixture.

## Determinism

JSON is UTF-8 without BOM, uses LF line endings, orders object keys lexicographically, preserves array order, and ends with one newline. The presentation tree hash is computed from the canonical compact JSON form and recorded in `fixture-manifest.json`.

Commit 3 does not freeze layout IDs, geometry, styles, theme hashes, font profiles, a full RenderPlan, PPTX output, or preview images. Those belong to later commits in the frozen rendering sequence.
