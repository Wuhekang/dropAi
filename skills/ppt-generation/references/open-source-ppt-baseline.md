# Open-source PPT skill baseline

## Selected reference

DokiAI opening-proposal mode uses the public `siril9/presentation-skill` as its composition-grammar and QA reference:

- repository: `https://github.com/siril9/presentation-skill`
- license: MIT
- upstream files reviewed: `SKILL.md`, `DESIGN.md`, `references/outline_schema.md`, and `references/composition_grammar_catalog.md`

The upstream repository is never downloaded dynamically in production. Its bounded semantic grammar is packaged into DokiAI's trusted opening-proposal adapter, while the frozen `SlideRenderPlan -> PureRenderer` boundary remains authoritative. The adapter must produce the same required opening-proposal structures on localhost and on the server.

## Adopted principles

1. Classify each slide into one dominant semantic type before layout selection.
2. Treat cover, agenda, section divider, content, image showcase, summary, and closing as distinct visual problems.
3. Use an explicit theme contract rather than scattered colors and fonts.
4. Preserve strong typography hierarchy and safe margins.
5. Select a content subtype before choosing a layout: narrative, mixed media, data, comparison, process, table, or image showcase.
6. Avoid consecutive slides with identical composition when another compatible registered recipe exists.
7. Require visual QA after export: text extraction, placeholder scan, package inspection, slide previews, and a fix-and-verify loop.

## Opening-proposal mandatory grammar

Every document detected as an opening proposal must include all of the following before RenderPlan compilation:

1. one centered section-divider page immediately before every first-level section;
2. one dedicated `国内外文献对比环形图` page;
3. one dedicated `四象限研究价值卡` page;
4. one dedicated `研究逻辑时间轴` page;
5. one dedicated `指标与进度路径图` page.

The four semantic figures are mandatory assets and may not be removed by page limits. They summarize only source-supported proposal structure and must not fabricate literature counts, effect sizes, patient numbers, dates, or research results.

## DokiAI-specific corrections

The upstream reference is general-purpose. DokiAI makes the following academic constraints stricter:

- Chinese production fonts are resolved and fingerprinted; the Renderer may not silently fall back.
- A user-selected trusted TemplatePack overrides generic palette suggestions.
- Thesis facts, source images, tables, citations, and metadata remain source-grounded.
- A valid source image receives its own evidence slide; it is not treated as decoration.
- Body text is never centered merely for visual symmetry.
- Page limits may remove low-value text candidates but may not remove mandatory evidence pages.
- PureRenderer executes a frozen plan and never performs auto-fit, rewriting, page invention, or layout improvisation.

## Non-adopted mechanics

Do not copy the upstream one-JavaScript-file-per-slide build model into DokiAI. Do not introduce online dependencies, global npm installs, uncontrolled fonts, or model-generated coordinates. DokiAI retains Java services, registered TemplatePacks, deterministic layout recipes, bundle fingerprints, and its existing quality gate.
