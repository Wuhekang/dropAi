from __future__ import annotations

"""Bound paragraph indents throughout a Word package, without changing text.

Direct formatting alone is insufficient: Word can restore indent properties
from styles, numbering levels, headers and field-generated paragraphs.
"""

import math
from lxml import etree

from word_formatter.models.rules import MAX_PARAGRAPH_INDENT_CM, MAX_PARAGRAPH_INDENT_CHARS

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
NS = {"w": W}
MAX_TWIPS = round(MAX_PARAGRAPH_INDENT_CM * 1440 / 2.54)
GROUPS = (
    ("left", "start", "leftChars", "startChars"),
    ("right", "end", "rightChars", "endChars"),
    ("firstLine", "firstLineChars"),
    ("hanging", "hangingChars"),
)


def q(name):
    return f"{{{W}}}{name}"


def number(value):
    try:
        parsed = float(value)
        return parsed if math.isfinite(parsed) else None
    except (TypeError, ValueError, OverflowError):
        return None


def properties(parent, path="w:ind"):
    ind = parent.find(path, NS) if parent is not None else None
    return dict(ind.attrib) if ind is not None else {}


def font_size(parent, fallback):
    if parent is None:
        return fallback
    values = [number(node.get(q("val"))) for name in ("sz", "szCs")
              for node in parent.findall(f"w:rPr/w:{name}", NS)]
    sizes = [value / 2 for value in values if value and value > 0]
    return max(sizes) if sizes else fallback


class IndentGuard:
    def __init__(self, styles=None, numbering=None):
        self.styles = {}
        self.style_cache = {}
        self.numbering = {}
        self.default_size = 12.0
        self.default_indents = {}
        self.default_style = None
        if styles is not None:
            self.default_size = font_size(styles.find("w:docDefaults/w:rPrDefault", NS), 12.0)
            self.default_indents = properties(styles, "w:docDefaults/w:pPrDefault/w:pPr/w:ind")
            for style in styles.findall("w:style", NS):
                identity = style.get(q("styleId"))
                self.styles[identity] = style
                if style.get(q("type")) == "paragraph" and style.get(q("default")) in {"1", "true", "on"}:
                    self.default_style = identity
        if numbering is not None:
            abstracts = {a.get(q("abstractNumId")): a for a in numbering.findall("w:abstractNum", NS)}
            nums = {n.get(q("numId")): n for n in numbering.findall("w:num", NS)}

            def resolve_levels(identity, seen):
                if identity in self.numbering:
                    return self.numbering[identity]
                num = nums.get(identity)
                if num is None or identity in seen:
                    return {}
                ref = num.find("w:abstractNumId", NS)
                abstract = abstracts.get(ref.get(q("val"))) if ref is not None else None
                levels = {}
                if abstract is not None:
                    link = abstract.find("w:numStyleLink", NS)
                    if link is not None:
                        linked_id = self.style_values(link.get(q("val")))[2].get("numId")
                        levels.update(resolve_levels(linked_id, seen | {identity}))
                    levels.update({lvl.get(q("ilvl")): lvl for lvl in abstract.findall("w:lvl", NS)})
                for override in num.findall("w:lvlOverride", NS):
                    level = override.find("w:lvl", NS)
                    if level is not None:
                        levels[override.get(q("ilvl"))] = level
                self.numbering[identity] = levels
                return levels

            for identity in nums:
                resolve_levels(identity, set())

    def style_values(self, identity, seen=None):
        if identity in self.style_cache:
            return self.style_cache[identity]
        style = self.styles.get(identity)
        seen = set() if seen is None else seen
        if style is None or identity in seen:
            return self.default_size, dict(self.default_indents), {}
        seen = seen | {identity}
        base = style.find("w:basedOn", NS)
        size, indents, num_pr = self.style_values(base.get(q("val")), seen) if base is not None else (self.default_size, dict(self.default_indents), {})
        size = font_size(style, size)
        indents = {**indents, **properties(style, "w:pPr/w:ind")}
        num_pr = {**num_pr, **self.num_properties(style.find("w:pPr", NS))}
        self.style_cache[identity] = (size, indents, num_pr)
        return size, indents, num_pr

    @staticmethod
    def num_properties(p_pr):
        num_pr = p_pr.find("w:numPr", NS) if p_pr is not None else None
        return {} if num_pr is None else {etree.QName(child).localname: child.get(q("val")) for child in num_pr}

    def paragraph_values(self, p):
        style = p.find("w:pPr/w:pStyle", NS)
        size, inherited, num_pr = self.style_values(style.get(q("val")) if style is not None else self.default_style)
        p_pr = p.find("w:pPr", NS)
        size = font_size(p_pr, size)
        # Use the largest actually styled run, not just the paragraph mark.
        sizes = [size]
        for run in p.iter(q("r")):
            # Fields/SDTs can wrap runs; nested textbox paragraphs own theirs.
            if next(run.iterancestors(q("p")), None) is not p:
                continue
            run_style = run.find("w:rPr/w:rStyle", NS)
            run_size = self.style_values(run_style.get(q("val")))[0] if run_style is not None else size
            sizes.append(font_size(run, run_size))
        num_pr = {**num_pr, **self.num_properties(p_pr)}
        level = self.numbering.get(num_pr.get("numId"), {}).get(num_pr.get("ilvl", "0"))
        # A direct zero must defeat an inherited character-unit indent too.
        values = {**inherited, **properties(level, "w:pPr/w:ind"), **properties(p_pr)}
        return max(sizes), values

    @staticmethod
    def unsafe_groups(values, size):
        chars_limit = min(MAX_PARAGRAPH_INDENT_CHARS * 100, MAX_TWIPS / (size * 20) * 100)
        return [group for group in GROUPS if any(
            q(name) in values and (number(values[q(name)]) is None or
                                  not 0 <= number(values[q(name)]) <= (chars_limit if name.endswith("Chars") else MAX_TWIPS))
            for name in group)]

    @staticmethod
    def reset(ind, groups):
        for group in groups:
            # Explicit zeros (not deletion) prevent inheritance from returning.
            for name in group:
                ind.set(q(name), "0")

    @staticmethod
    def ensure_ind(container):
        p_pr = container.find("w:pPr", NS)
        if p_pr is None:
            p_pr = etree.Element(q("pPr"))
            if container.tag == q("p"):
                container.insert(0, p_pr)
            else:
                before = next((node for node in container if node.tag in {q("rPr"), q("tblPr"), q("trPr"), q("tcPr"), q("tblStylePr")}), None)
                container.append(p_pr) if before is None else before.addprevious(p_pr)
        ind = p_pr.find("w:ind", NS)
        if ind is None:
            ind = etree.Element(q("ind"))
            following = {q(n) for n in ("contextualSpacing", "mirrorIndents", "suppressOverlap", "jc", "textDirection", "textAlignment", "textboxTightWrap", "outlineLvl", "divId", "cnfStyle", "rPr", "sectPr", "pPrChange")}
            before = next((node for node in p_pr if node.tag in following), None)
            p_pr.append(ind) if before is None else before.addprevious(ind)
        return ind

    def apply(self, root):
        changed = 0
        # First materialize protection on actual paragraphs; this also handles
        # a small base-style char indent inherited by a much larger-font style.
        for p in root.iter(q("p")):
            size, values = self.paragraph_values(p)
            groups = self.unsafe_groups(values, size)
            if not groups:
                continue
            self.reset(self.ensure_ind(p), groups)
            changed += 1
        for style in root.iter(q("style")):
            if style.get(q("type")) not in {"paragraph", "numbering", "table"}:
                continue
            size, values, _ = self.style_values(style.get(q("styleId")))
            groups = self.unsafe_groups(values, size)
            if groups:
                # Future field-generated paragraphs must inherit a safe style,
                # even when that derived style has no existing w:ind node.
                self.reset(self.ensure_ind(style), groups)
                changed += 1
        # All definitions, including styles, numbering, footnotes and textbox
        # stories, must also be safe when Word regenerates a field later.
        for ind in root.iter(q("ind")):
            size = self.default_size
            for ancestor in ind.iterancestors():
                if ancestor.tag == q("p"):
                    size = self.paragraph_values(ancestor)[0]
                    break
                if ancestor.tag == q("style"):
                    size = self.style_values(ancestor.get(q("styleId")))[0]
                    break
                if ancestor.tag == q("lvl"):
                    size = font_size(ancestor, size)
                    break
            groups = self.unsafe_groups(ind.attrib, size)
            if groups:
                self.reset(ind, groups)
                changed += 1
        for ind in root.iter(q("tblInd")):
            value = number(ind.get(q("w")))
            kind = ind.get(q("type"), "dxa")
            if value is None or (kind == "dxa" and not 0 <= value <= MAX_TWIPS) or (kind not in {"dxa", "nil"} and value != 0):
                ind.set(q("w"), "0")
                ind.set(q("type"), "dxa")
                changed += 1
        return changed
