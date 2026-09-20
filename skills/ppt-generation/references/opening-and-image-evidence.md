# Opening proposals and image evidence

## Opening-proposal mode

An upload can be treated as an opening proposal when it explicitly says `开题报告`, `开题答辩`, `开题汇报`, or when several structural markers co-occur: research purpose/background, literature status, research content, method/technical route, feasibility, schedule, expected outcome, or key problems.

The document does not need a completed thesis chapter structure. Build a conservative narrative from only the supplied material:

1. selection basis and value;
2. literature status or research gap;
3. research content, questions, and methods;
4. feasibility, difficulties, and responses when present;
5. schedule and expected outcome;
6. summary/expected contribution.

Every opening-proposal upload deterministically receives four semantic explanatory assets:

- `国内外文献对比环形图`;
- `四象限研究价值卡`;
- `研究逻辑时间轴`;
- `指标与进度路径图`.

The visual labels prefer the document's own literature, purpose, method and schedule statements. Conservative structural labels may fill a missing slot, but the diagrams never claim numeric literature shares, effect sizes, completed experiments or research results. Each generated figure is a mandatory dedicated page. In addition, every first-level section receives a centered section-divider immediately before its first content page.

## One valid source image, one dedicated slide

For `N` valid source images, the candidate tree and final content tree must contain exactly `N` distinct `IMAGE_EVIDENCE` pages before adding any deterministic opening-proposal diagrams.

Each image page must have:

- one non-empty, unique `figureId`;
- `mandatoryAsset=true`;
- exactly one source image binding;
- a semantic title rather than a figure number or placeholder;
- an explicit question answered by the image;
- a complete 40-70 Chinese-character explanation;
- source chapter and source position traceability;
- a protected aspect ratio and a registered image layout.

The explanation evidence priority is:

1. nearest valid paragraph immediately before the image;
2. formal caption;
3. subsection title and supporting sentence;
4. conservative neutral description.

Do not use text from the following feature or following subsection to explain the current image.

## Pollution filter

The one-image-one-slide rule applies after deterministic filtering. Exclude only evidence-backed pollution classes: cover logos, seals, signatures, QR codes, page ornaments, headers/footers, exact duplicate binaries, unreadable files, and images below the configured content-size threshold. Record every exclusion with a reason. Do not call a difficult-to-interpret research figure “decoration” merely to reduce page count.

## Invariants

- Planner: every valid asset ID appears once as an `IMAGE_EVIDENCE` candidate.
- Outline: all mandatory image candidates survive scoring and page limits.
- Validator: duplicate figure IDs, missing figure IDs, or non-mandatory image pages fail.
- AssetMapper: the figure ID resolves to exactly one real file and fingerprint.
- Layout: the image and caption fit without distortion, truncation, or illegal overlap.
- Renderer: no combining, cropping, rewriting, substituting, or dropping the image.
- QualityGate: missing mandatory assets and aspect-ratio distortion block delivery.
