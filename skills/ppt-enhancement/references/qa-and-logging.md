# Quality assurance and enhancement logging

The enhanced deck must remain a faithful copy of the source content. Visual polish is not permission to rewrite research.

## Baseline record

Capture before editing:

- source path, SHA-256, file size, slide count, and page dimensions;
- a rendered image of every slide;
- visible text, speaker notes, citations, hyperlinks, names, dates, and data-bearing numbers per slide;
- text in hidden shapes, hidden slides, charts, SmartArt, alternative text, comments, and embedded objects where exposed;
- master and layout identifiers, template-page mapping, and byte hashes for theme, master, layout, page-size, and related template parts;
- hidden-slide state, transitions, animations, sections, document properties, custom XML, OLE objects, embedded files, and other opaque package-part hashes;
- page archetype and existing overflow, overlap, clipping, low contrast, and intentional edge bleed;
- paths to any source analysis, asset catalog, generation log, and validation report.

## Enhancement plan contract

Keep a machine-readable plan with one record per slide. A minimal record is:

```json
{
  "slide": 4,
  "sourceSlide": 4,
  "archetype": "quadrant",
  "focalEnhancement": "strengthen four-part hierarchy",
  "microDetails": ["01-04 labels", "template-tone strokes"],
  "inheritedEdits": ["adjust existing card fills and icon contrast"],
  "additions": [
    {
      "type": "shape",
      "purpose": "sequence marker",
      "boundedZone": "inside existing card header",
      "mustNotOverlapInherited": true
    }
  ]
}
```

Save the plan as `<output-stem>-enhancement-plan.json`. Under `textPolicy: locked`, the `additions` list must not contain a text-bearing object and the visible and hidden text inventories must match exactly by slide URI, shape or object ID, and paragraph/run path; a deck-level aggregate text match is insufficient.

If the project already has a frame map or addition-allowlist contract, declare every new object there before rendering. Unplanned top-layer masks and large opaque shapes are prohibited.

## Required verification

1. Re-import the output without repair warnings.
2. Compare slide count and order with the source.
3. Render all slides and inspect each one at full-slide scale.
4. Diff all visible and hidden text. Under `locked`, require an exact match and permit no added text objects. Under `preserve-original`, permit only planned auxiliary labels such as page numbers, chapter tags, or sequence markers.
5. Confirm notes, citations, hyperlinks, names, dates, numerical values, claims, chart labels, units, sources, comments, and alternative text are unchanged.
6. Confirm no placeholder, debug label, empty chart, missing image, or broken relation remains.
7. Check safe areas, clipping, accidental overlap, line breaks, icon contrast, decorative interference, and projector readability.
8. Check that images retain aspect ratio and that tables and charts preserve meaning.
9. Allow edge bleed only where the baseline showed the same intentional template behavior; no new object may create it accidentally.
10. In `polish` mode, require byte-identical hashes for `ppt/theme/*`, `ppt/slideMasters/*`, `ppt/slideLayouts/*`, their relationship parts, and the original `p:sldSz` fragment in `ppt/presentation.xml`. Any difference fails validation; do not normalize away a write to a protected part.
11. Compare hidden-slide state, transitions, animations, sections, document properties, custom XML, OLE objects, embedded files, and other opaque package parts. Any unexplained removal or mutation fails validation.
12. Record the renderer name, version, viewport or resolution, per-slide render path and hash, and a per-slide inspection result.
13. Rerun the full check after an auto-fix. Do not mark the deck passed based on a pre-fix render.

## Output policy

- Default PPTX: `<source-stem>_精美增强版.pptx`.
- Default log: `<output-stem>-enhancement-log.json`.
- Default plan: `<output-stem>-enhancement-plan.json`.
- Preserve the source PPTX and existing `-generation-log.json`.
- If an output, plan, or log name already exists, use a shared `_v2`, `_v3`, and so on suffix; never replace a prior enhancement artifact silently.
- Keep temporary renders and debug artifacts in a separate working directory unless the user asks to receive them.

## Enhancement log schema

Write UTF-8 JSON with at least these fields:

```json
{
  "skillName": "ppt-enhancement",
  "skillVersion": "1.0.0",
  "source": {"path": "", "sha256": "", "slideCount": 0},
  "outputPptx": "",
  "request": {"mode": "polish", "profile": "balanced", "textPolicy": "preserve-original", "template": null},
  "contentPolicy": "preserve-source-content",
  "designSystem": {"palette": [], "fonts": [], "chapterAccents": []},
  "slides": [{"page": 1, "pageType": "cover", "changes": [], "originalContentChanged": false}],
  "preservation": {
    "slideCountMatch": true,
    "slideOrderMatch": true,
    "originalTextPreserved": true,
    "notesPreserved": true,
    "citationsPreserved": true,
    "hyperlinksPreserved": true,
    "numericalValuesPreserved": true,
    "hiddenContentPreserved": true,
    "opaquePackagePartsPreserved": true,
    "protectedTemplatePartsByteIdentical": true
  },
  "validation": {"status": "PASSED", "renderedPages": 0, "renderer": {"name": "", "version": "", "resolution": ""}, "pageRenders": [], "checks": [], "issues": [], "autoFixes": [], "warnings": []},
  "generationLog": null
}
```

Do not claim `PASSED` when a required comparison was skipped. Record the skipped check and the reason as a warning.
