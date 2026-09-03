# Visual recipes

Use this reference only after inspecting the source deck. The source template remains the design authority.

## Extract design tokens

Record a compact design system before editing:

- background and surface colors;
- three to five accent colors, including chapter accents where present;
- title, subtitle, body, caption, and numeral typography;
- icon family, line weight, corner radius, shadow softness, and recurring ornaments;
- content grid, page margins, title baseline, footer zone, and intentional bleed zones.

Reuse these tokens consistently. Do not introduce a second visual language.

## Decoration budget by profile

### subtle

- Lines: about 0.6–1.0 pt.
- Shadows: none or barely visible.
- New objects: only when needed for alignment or navigation.
- Emphasis: typography, spacing, and contrast first.

### balanced

- Lines: about 0.7–1.2 pt.
- Shadows: low-opacity and short-distance.
- New objects: one navigation or section motif plus limited labels or surface refinements.
- Emphasis: clearer cards, chapter accents, page rhythm, and focal conclusions.

### showcase

- Large section numerals may use roughly 5–15% visual strength behind or beside content.
- Progress devices should normally use three to five nodes or one slim rail.
- Alternate pale and deep template tones; emphasize the current or final node.
- Combine at most one large focal device with one family of micro-details on a slide.
- Keep body content calmer than the navigation and section devices.
- When `textPolicy` is `locked`, render all numerals, labels, and progress cues as text-free geometry or restyle existing text-bearing objects without changing their content.

## Slide recipes

### Cover

- Strengthen the title, subject, and identity hierarchy.
- Reuse one template-native pill, line, numeral, or dot cluster.
- Keep optional supporting text to one short source-grounded line.
- Never add unsupported school, adviser, date, slogan, or contact information.

### Catalog

- Use a slim rail, chapter markers, or stable chapter colors.
- Make current hierarchy obvious through weight and spacing.
- Repair contrast before adding decoration.
- Do not wrap every entry in an independent card.

### Section divider

- Use a large chapter numeral, a short progress rail, or both when the page is sparse.
- Retain inherited labels and template ornaments.
- Keep it intentionally sparse; it is a transition, not a content page.

### Literature or research summary

- Keep author, year, source, and citation text fully readable.
- Use subtle finding surfaces or one highlighted conclusion band.
- If the center is empty, one restrained thematic label can anchor the composition.
- Do not convert prose into claims that the source does not make.

### Quadrant or framework

- Use consistent `01–04` labels and alternating template tones.
- Prefer inherited surfaces and native icons.
- Check icon-to-surface contrast after changing card fills.

### Process or timeline

- Strengthen the existing rail or arrow, stage numbers, and tonal progression.
- Emphasize the current or final stage with template colors.
- Preserve stage order and wording.
- Never add percentages, durations, milestones, or dependencies that are not in the source.

### Data or chart

- Visualize only real source data.
- Preserve axes, units, legend, precision, and source attribution.
- Add at most one conclusion band or callout that restates an existing source conclusion.
- Never fabricate a chart to fill whitespace.

### Image-led slide

- Preserve image aspect ratio and crop only when the subject remains intact.
- Reuse the template image slot where available.
- Captions must be source-grounded and should not obscure the image.

### Outlook and closing

- Use source-grounded conclusions or outlook items only.
- Echo the cover's design language for closure.
- Do not add “Q&A”, contact details, logos, or promises unless they already exist or the user requests them.

## Contrast and projection checks

- Test every icon, label, and line against its immediate background, not the page background.
- Use stronger contrast for small text and thin strokes.
- If a new filled surface causes inherited corner art to cut through it, first restyle or reposition an existing local object when the plan permits; otherwise remove the fill.
- Do not hide template decoration with an unplanned opaque mask.
- Treat projector legibility as the final criterion: pale-on-pale combinations that look acceptable on a monitor still fail.
