---
name: ppt-generation
description: Generate and validate Dokiai Academic defense PPTX files from uploaded DOCX, PDF, PPTX, thesis, graduation-design report, task brief, or project report. Use for computer, mechanical, environmental, landscape, interior, visual communication, UI, and digital-media presentation generation, including document parsing, source-derived chapter planning, image extraction, slide planning, template selection, rendering, and QA.
---

# Dokiai PPT Generation

Build defense presentations with one invariant: source structure determines content, templates determine visuals, and AI only compresses expression.

## Required pipeline

Run every stage in order and record its result:

1. `Document Analyzer`: extract identity fields, source headings, body blocks, tables, and images.
2. `Chapter Planner`: derive the PPT directory from valid source chapters only.
3. `Asset Extractor`: build an asset library and classify every usable image.
4. `Slide Planner`: create section, content, image, data, outlook, and thanks pages.
5. `Template Engine`: map each planned page to an allowed template page.
6. `PPT Renderer`: generate PPTX, render every page, validate, and auto-reflow layout faults.

Do not invent chapters, restructure the thesis, copy long paragraphs, create unrelated images, shrink fonts to hide overflow, or expose references/acknowledgements as directory chapters.

## Source contract

Accept DOCX, PDF, and PPTX. Extract title, English title, author, major, student number, and advisor for the cover.

Recognize numbered source chapters such as `第1章` / `第一章` / `1`. Exclude abstract, keywords, references, acknowledgements, appendices, and raw table-of-contents labels. Preserve source order and wording after removing duplicated numbering.

If reliable chapter headings cannot be extracted, stop with an explicit diagnostic rather than inventing a directory.

## Asset contract

Create one asset record per extracted image:

```json
{
  "id": "",
  "chapter": "",
  "page": 0,
  "type": "",
  "description": "",
  "path": ""
}
```

Read [professional-rules.md](references/professional-rules.md) to classify and prioritize assets for the detected major. Preserve aspect ratio. Treat a core source image as its own image-led slide by default; do not pack unrelated core images together.

## Slide planning contract

Always plan exactly one cover, one catalog, one future-outlook page, and one thanks page. Add one section page per valid source chapter. Allocate one or two content pages per chapter, then add image-led pages for core assets.

Use these semantic layouts:

- `COVER`: centered title, English title, author, major, advisor.
- `CATALOG`: source chapters only, numbered `01` onward.
- `SECTION`: large number, chapter title, English subtitle; centered.
- `TEXT_CARDS`: title, up to three short idea cards, one conclusion.
- `IMAGE_TEXT`: title, text and one undistorted source image.
- `BIG_IMAGE`: title, image occupying 60–70%, one explanation.
- `DATA`: title, table/chart, explicit conclusion.
- `FUTURE`: three source-grounded directions.
- `THANKS`: final `THANK YOU / 谢谢大家` page.

Keep every text box at 20 Chinese characters or fewer. Split into another slide when needed; never solve overflow by shrinking below the template hierarchy.

## Template contract

Apply template priority in this order:

1. Explicit user selection.
2. User template library.
3. `小熊熊` series inside that library.
4. System template.

When automatic recommendation ranks the user library, rank `小熊熊` ahead of other user templates.

Map cover only to `cover`, catalog only to `catalog`, section starts only to `section`, images to `image_content`, text to `text_content`, data to `chart/table`, and the final page only to `thanks`. Never use cover, catalog, or thanks in the body pool.

Preserve `background_asset`; replace only `content_asset`. Respect each template safe area, visual center, and image slot. Center no-image section/title pages.

## Validation gate

Validate before delivery:

- Directory equals the ordered valid source chapters.
- Exactly one cover, catalog, future-outlook, and thanks page exist.
- The first page is cover, second catalog, and last thanks.
- Every section has a section page.
- Every used image exists, opens, keeps aspect ratio, and has title/description.
- References and acknowledgements do not appear as chapters.
- No text box exceeds 20 characters.
- No title/text/image leaves the safe area or unintentionally overlaps.
- The PPTX opens successfully.

Render every page and inspect each page at full size. Auto-reflow safe-area, alignment, and overflow faults, then regenerate and revalidate. Do not declare success from a unit test alone.

## Required logs

Persist a generation log containing:

- `skillName` and `skillVersion`
- document-analysis result
- accepted and excluded chapter headings
- extracted asset records and classifications
- planned page type for every output page
- selected template and priority reason
- source-template page mapping
- validation results and automatic fixes

Use [log-schema.md](references/log-schema.md) for the stable structure.
