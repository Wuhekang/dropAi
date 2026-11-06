from __future__ import annotations

"""Final-delivery cleanup for reviewer-only Word markup."""

import os
from copy import deepcopy
from pathlib import Path
import re
import tempfile
import zipfile

from lxml import etree

from word_formatter.core.indent_guard import IndentGuard
from word_formatter.core.xml_utils import xpath


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

    for simple in xpath(root, ".//w:fldSimple"):
        protect(simple.get(f"{{{W_NS}}}instr", ""), simple, "".join(xpath(simple, ".//w:t/text()")))
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


def _cleanup_error(errors: list[dict[str, str]] | None, item: str, exc: Exception) -> None:
    # Exception messages can contain document text, paths or credentials.
    # Filesystem/package corruption and resource exhaustion are never partial success.
    if errors is None or isinstance(exc, (OSError, zipfile.BadZipFile, MemoryError)):
        raise exc
    errors.append({
        "item": f"最终清理：{item}",
        "reason": f"{type(exc).__name__}：该项处理失败，已保留原有内容",
    })


def _attempt_cleanup(root, operation, item, errors):
    """Commit one XML action only after both mutation and serialization succeed."""
    try:
        candidate = deepcopy(root)
        count = operation(candidate)
        _xml_bytes(candidate)
    except Exception as exc:
        _cleanup_error(errors, item, exc)
        return root, 0, False
    return candidate, count, True


def _remove_comment_markup(root: etree._Element) -> int:
    removed = 0
    for tag in ("commentRangeStart", "commentRangeEnd", "commentReference"):
        for element in reversed(xpath(root, f".//w:{tag}")):
            parent = element.getparent()
            if parent is not None:
                parent.remove(element)
                removed += 1
    return removed


def _blacken_red_fonts(root: etree._Element) -> int:
    changed = 0
    for color in xpath(root, ".//w:color"):
        attribute = f"{{{W_NS}}}val"
        if is_red_font_value(color.get(attribute)):
            color.set(attribute, "000000")
            changed += 1
    return changed


def _remove_comment_relationships(root: etree._Element) -> int:
    removed = 0
    for relationship in list(root.findall(f"{{{REL_NS}}}Relationship")):
        rel_type = (relationship.get("Type") or "").lower()
        target_name = (relationship.get("Target") or "").lower()
        if "comment" in rel_type or "person" in rel_type or "comment" in target_name or target_name.endswith("people.xml"):
            root.remove(relationship)
            removed += 1
    return removed


def _remove_comment_content_types(root: etree._Element) -> int:
    removed = 0
    for override in list(root.findall(f"{{{CT_NS}}}Override")):
        part_name = (override.get("PartName") or "").lower()
        if "comment" in part_name or part_name.endswith("/people.xml"):
            root.remove(override)
            removed += 1
    return removed


def finalize_docx(path: str | Path, errors: list[dict[str, str]] | None = None) -> dict[str, int]:
    """Enforce delivery policies; an error list opts into per-action recovery.

    The return value remains the legacy integer statistics dictionary. Without
    an error list, action failures retain the original fail-fast behavior.
    """

    target = Path(path)
    stats = {"comment_markup_removed": 0, "comment_parts_removed": 0, "red_fonts_blackened": 0, "unresolved_references_locked": 0, "unsafe_indents_reset": 0}
    with zipfile.ZipFile(target, "r") as source:
        names = set(source.namelist())
        # These are mandatory DOCX parts, not recoverable formatting actions.
        required_roots = {
            "word/document.xml": f"{{{W_NS}}}document",
            "[Content_Types].xml": f"{{{CT_NS}}}Types",
            "_rels/.rels": f"{{{REL_NS}}}Relationships",
        }
        for name, expected_tag in required_roots.items():
            if etree.fromstring(source.read(name)).tag != expected_tag:
                raise ValueError("Invalid required DOCX XML part")

        def optional_xml(name):
            try:
                return etree.fromstring(source.read(name)) if name in names else None
            except etree.XMLSyntaxError as exc:
                if errors is not None:
                    _cleanup_error(errors, "XML 部件读取", exc)
                return None

        styles, numbering = optional_xml("word/styles.xml"), optional_xml("word/numbering.xml")
        try:
            indent_guard = IndentGuard(styles, numbering)
        except Exception as exc:
            _cleanup_error(errors, "缩进保护", exc)
            indent_guard = None
        overrides: dict[str, bytes] = {}
        removed_parts = {
            name
            for name in names
            if re.fullmatch(r"word/comments[^/]*\.xml", name, re.I)
            or re.fullmatch(r"word/_rels/comments[^/]*\.xml\.rels", name, re.I)
            or name.lower() == "word/people.xml"
        }
        comments_complete = True
        bookmarks = set()
        for name in sorted(names):
            if name.startswith("word/") and name.endswith(".xml") and name not in removed_parts:
                try:
                    root = etree.fromstring(source.read(name))
                    bookmarks.update(node.get(f"{{{W_NS}}}name") for node in root.iter(f"{{{W_NS}}}bookmarkStart"))
                except etree.XMLSyntaxError:
                    # Keep comment definitions if an unreadable story might
                    # still refer to them. The main document was checked above.
                    comments_complete = False

        for name in sorted(names):
            if not name.startswith("word/") or not name.endswith(".xml") or name in removed_parts:
                continue
            try:
                root = etree.fromstring(source.read(name))
            except etree.XMLSyntaxError as exc:
                if errors is not None:
                    _cleanup_error(errors, "XML 部件读取", exc)
                continue
            changed = False
            actions = [
                ("引用保护", "unresolved_references_locked", lambda candidate: _lock_unresolved_references(candidate, bookmarks)),
                ("缩进保护", "unsafe_indents_reset", indent_guard.apply if indent_guard is not None else None),
                ("批注清理", "comment_markup_removed", _remove_comment_markup),
                ("红色字体清理", "red_fonts_blackened", _blacken_red_fonts),
            ]
            for item, stat, operation in actions:
                if operation is None:
                    continue
                root, count, succeeded = _attempt_cleanup(root, operation, item, errors)
                stats[stat] += count
                changed = changed or count > 0
                if stat == "comment_markup_removed" and not succeeded:
                    comments_complete = False
            if changed:
                overrides[name] = _xml_bytes(root)

        # Removing comment definitions, relationships and content types is a
        # package-level transaction. A failed marker/metadata action keeps all
        # definitions and links; successful marker removals remain valid.
        metadata_overrides: dict[str, bytes] = {}
        for name in sorted(item for item in names if item.endswith(".rels") and item not in removed_parts):
            try:
                root = etree.fromstring(source.read(name))
            except etree.XMLSyntaxError as exc:
                if name == "word/_rels/document.xml.rels":
                    raise
                if errors is not None:
                    _cleanup_error(errors, "批注关联清理", exc)
                comments_complete = False
                continue
            if comments_complete:
                root, count, succeeded = _attempt_cleanup(root, _remove_comment_relationships, "批注关联清理", errors)
                comments_complete = comments_complete and succeeded
                if count:
                    metadata_overrides[name] = _xml_bytes(root)

        content_types = "[Content_Types].xml"
        root = etree.fromstring(source.read(content_types))
        if comments_complete:
            root, count, succeeded = _attempt_cleanup(root, _remove_comment_content_types, "批注类型清理", errors)
            comments_complete = comments_complete and succeeded
            if count:
                metadata_overrides[content_types] = _xml_bytes(root)
        if comments_complete:
            overrides.update(metadata_overrides)
            stats["comment_parts_removed"] = len(removed_parts)
        else:
            removed_parts = set()

        handle, temporary_name = tempfile.mkstemp(
            prefix=f".{target.stem}.", suffix=".finalizing.docx", dir=target.parent
        )
        os.close(handle)
        temporary = Path(temporary_name)
        try:
            with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as output:
                for info in source.infolist():
                    if info.filename in removed_parts:
                        continue
                    output.writestr(info, overrides.get(info.filename, source.read(info.filename)))
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    try:
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    return stats
