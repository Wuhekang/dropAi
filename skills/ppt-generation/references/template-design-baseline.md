# Template design baseline

Read this file when creating, importing, registering, or auditing a Rendering V1 TemplatePack.

## Template identity

Every template must define an immutable identity and provenance:

- `templatePackId` and semantic version;
- source file hash and license/provenance note;
- resolved theme and font profile;
- semantic background mapping for cover, agenda, section, content, image, table, summary, and thanks;
- safe area and foreground contrast rules;
- canonical manifest and asset hashes.

No prompt may invent a template ID or point the Renderer at an arbitrary local path.

## Visual system

Use a single coherent palette with semantic tokens for canvas, surface, primary text, secondary text, accent, border, success, warning, and danger. Title text defaults to black/dark neutral unless the trusted template explicitly supplies another high-contrast title color. Blue is never an implicit fallback.

Define one spacing rhythm, one radius family, and one shadow family. Use the same title anchor, safe margin, page-number style, and recurring decoration throughout the deck. Decorative layers may vary by page role but must visibly belong to the same family.

Typography must expose at least cover title, section title, slide title, body, caption, source, and page-number roles. Adjacent hierarchy levels must be visually distinguishable by size, weight, placement, or color. Body and captions remain regular weight unless the source template intentionally emphasizes a short phrase.

## Page roles

- Cover: one focal title, restrained metadata, optional motif; long titles use deterministic multi-line layout.
- Agenda: 3-5 section items, fixed centered composition for DokiAI Academic, equal spacing, prominent numbering.
- Section divider: section number and title centered as a deliberate pause; no body paragraph.
- Narrative content: left-aligned body, at most four coherent points, supporting visual structure.
- Data/process: editable chart, table, timeline, or diagram plus one conclusion.
- Image evidence: one source image only, protected aspect ratio, meaningful title, 40-70 Chinese-character explanation, template background behind it.
- Summary: 3-5 conclusions or future directions, visually distinct but still in theme.
- Thanks: concise closing using the cover's visual language.

## Layout variety

Variety is constrained, not random. Choose among registered recipes compatible with the page type and measured content. Prefer alternating narrative, comparison, process, data, and visual-first compositions. Never choose variety at the cost of overflow, distorted evidence, unreadable text, or broken template identity.

## Acceptance checks

Reject a template when any of the following is true:

- title color falls back to blue without a theme token;
- image pages lose the template background;
- agenda or section divider is off-center;
- master placeholders remain visible;
- a foreground element enters the unsafe area;
- source images are cropped destructively or stretched;
- fonts are unresolved;
- contrast is insufficient at presentation distance;
- two semantically different page roles map to an unusable single background;
- the template cannot be reproduced from its manifest and assets.
