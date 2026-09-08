from __future__ import annotations

"""Final-delivery cleanup for reviewer-only Word markup."""

import os
from pathlib import Path
import re
import tempfile
import zipfile

from lxml import etree

from word_formatter.core.indent_guard import IndentGuard


W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types"
NS = {"w": W_NS}


def is_red_font_value(value: str | None) -> bool:
    if not value:
        return False
    normalized = value.strip().lstrip("#").upper()
    if normalized == "RED":
        return True
    if not re.fullmatch(r"[0-9A-F]{6}", normalized):
        return False
    red, green, blue = (int(normalized[index:index + 2], 16) for index in (0, 2, 4))
    return red >= 160 and red >= green * 1.5 and red >= blue * 1.5


def _xml_bytes(root: etree._Element) -> bytes:
    return etree.tostring(root, xml_declaration=True, encoding="UTF-8", standalone="yes")


def _lock_unresolved_references(root: etree._Element, bookmarks: set[str]) -> int:
    """Keep cached labels for dangling REF fields; valid references stay live.

    Word's Fields.Update respects fldLock. Locking only an invalid reference
    preserves its cached runs and nested fields without deleting author text.
    """
    locked = 0
    stack: list[dict] = []

    def protect(instruction: str, marker, cached: str) -> None:
        nonlocal locked
        match = re.match(r'\s*REF\s+(?:"([^"]+)"|([^\s\\]+))', instruction, re.I)
        if match and (match.group(1) or match.group(2)) not in bookmarks and cached.strip():
            if marker.get(f"{{{W_NS}}}fldLock") not in {"1", "true", "on"}:
                marker.set(f"{{{W_NS}}}fldLock", "true")
                marker.set(f"{{{W_NS}}}dirty", "false")
                locked += 1

    for simple in root.xpath(".//w:fldSimple", namespaces=NS):
        protect(simple.get(f"{{{W_NS}}}instr", ""), simple, "".join(simple.xpath(".//w:t/text()", namespaces=NS)))
    for node in root.iter():
        if node.tag == f"{{{W_NS}}}fldChar":
            kind = node.get(f"{{{W_NS}}}fldCharType")
            if kind == "begin":
                stack.append({"marker": node, "instruction": [], "cached": [], "separated": False})
            elif kind == "separate" and stack:
                stack[-1]["separated"] = True
            elif kind == "end" and stack:
                field = stack.pop()
                protect("".join(field["instruction"]), field["marker"], "".join(field["cached"]))
        elif node.tag == f"{{{W_NS}}}instrText" and stack and not stack[-1]["separated"]:
            stack[-1]["instruction"].append(node.text or "")
        elif node.tag == f"{{{W_NS}}}t":
            for field in stack:
                if field["separated"]:
                    field["cached"].append(node.text or "")
    return locked


def finalize_docx(path: str | Path) -> dict[str, int]:
    """Enforce package-wide delivery policies, including the hard indent cap."""

    target = Path(path)
    stats = {"comment_markup_removed": 0, "comment_parts_removed": 0, "red_fonts_blackened": 0, "unresolved_references_locked": 0, "unsafe_indents_reset": 0}
    with zipfile.ZipFile(target, "r") as source:
        names = set(source.namelist())
        def optional_xml(name):
            try:
                return etree.fromstring(source.read(name)) if name in names else None
            except etree.XMLSyntaxError:
                return None
        indent_guard = IndentGuard(optional_xml("word/styles.xml"), optional_xml("word/numbering.xml"))
        overrides: dict[str, bytes] = {}
        removed_parts = {
            name
            for name in names
            if re.fullmatch(r"word/comments[^/]*\.xml", name, re.I)
            or re.fullmatch(r"word/_rels/comments[^/]*\.xml\.rels", name, re.I)
            or name.lower() == "word/people.xml"
        }
        stats["comment_parts_removed"] = len(removed_parts)
        bookmarks = set()
        for name in names:
            if name.startswith("word/") and name.endswith(".xml") and name not in removed_parts:
                try:
                    bookmarks.update(etree.fromstring(source.read(name)).xpath(".//w:bookmarkStart/@w:name", namespaces=NS))
                except etree.XMLSyntaxError:
                    pass

        for name in names:
            if not name.startswith("word/") or not name.endswith(".xml") or name in removed_parts:
                continue
            try:
                root = etree.fromstring(source.read(name))
            except etree.XMLSyntaxError:
                continue
            changed = False
            reference_count = _lock_unresolved_references(root, bookmarks)
            stats["unresolved_references_locked"] += reference_count
            changed = reference_count > 0
            indent_count = indent_guard.apply(root)
            stats["unsafe_indents_reset"] += indent_count
            changed = changed or indent_count > 0
            for tag in ("commentRangeStart", "commentRangeEnd", "commentReference"):
                for element in reversed(root.xpath(f".//w:{tag}", namespaces=NS)):
                    parent = element.getparent()
                    if parent is not None:
                        parent.remove(element)
                        stats["comment_markup_removed"] += 1
                        changed = True
            for color in root.xpath(".//w:color", namespaces=NS):
                attribute = f"{{{W_NS}}}val"
                if is_red_font_value(color.get(attribute)):
                    color.set(attribute, "000000")
                    stats["red_fonts_blackened"] += 1
                    changed = True
            if changed:
                overrides[name] = _xml_bytes(root)

        for name in [item for item in names if item.endswith(".rels")]:
            try:
                root = etree.fromstring(source.read(name))
            except etree.XMLSyntaxError:
                continue
            changed = False
            for relationship in list(root.findall(f"{{{REL_NS}}}Relationship")):
                rel_type = (relationship.get("Type") or "").lower()
                target_name = (relationship.get("Target") or "").lower()
                if "comment" in rel_type or "person" in rel_type or "comment" in target_name or target_name.endswith("people.xml"):
                    root.remove(relationship)
                    changed = True
            if changed:
                overrides[name] = _xml_bytes(root)

        content_types = "[Content_Types].xml"
        root = etree.fromstring(source.read(content_types))
        changed = False
        for override in list(root.findall(f"{{{CT_NS}}}Override")):
            part_name = (override.get("PartName") or "").lower()
            if "comment" in part_name or part_name.endswith("/people.xml"):
                root.remove(override)
                changed = True
        if changed:
            overrides[content_types] = _xml_bytes(root)

        handle, temporary_name = tempfile.mkstemp(
            prefix=f".{target.stem}.", suffix=".finalizing.docx", dir=target.parent
        )
        os.close(handle)
        temporary = Path(temporary_name)
        with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as output:
            for info in source.infolist():
                if info.filename in removed_parts:
                    continue
                output.writestr(info, overrides.get(info.filename, source.read(info.filename)))
    try:
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return stats
