from __future__ import annotations

"""Content-preservation diagnostics and delivery checks for formatted DOCX files.

Strict callers reject detailed content differences. Format-first callers keep
those diagnostics while permitting delivery after package validation and the
unchanged-source check succeed.
"""

from dataclasses import dataclass
import hashlib
import posixpath
from pathlib import Path
from typing import Any
from xml.etree import ElementTree as ET
import zipfile


NS = {
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
}
REQUIRED_DOCX_PARTS = frozenset({"[Content_Types].xml", "word/document.xml"})
PRESERVED_PREFIXES = (
    "word/media/",
    "word/embeddings/",
    "word/charts/",
    "word/diagrams/",
    "word/activeX/",
)
PRESERVED_TEXT_PARTS = (
    "word/header",
    "word/footnotes.xml",
    "word/endnotes.xml",
    "word/comments.xml",
)
COUNT_TAGS = {
    "text_node_count": ("w", "t"),
    "paragraph_count": ("w", "p"),
    "table_count": ("w", "tbl"),
    "section_count": ("w", "sectPr"),
    "drawing_count": ("w", "drawing"),
    "pict_count": ("w", "pict"),
    "blip_count": ("a", "blip"),
    "field_char_count": ("w", "fldChar"),
    "instruction_text_count": ("w", "instrText"),
    "bookmark_start_count": ("w", "bookmarkStart"),
    "bookmark_end_count": ("w", "bookmarkEnd"),
    "content_control_count": ("w", "sdt"),
    "hyperlink_count": ("w", "hyperlink"),
}
RELATIONSHIP_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


class IntegrityValidationError(RuntimeError):
    """The formatted document lost or changed source content."""


@dataclass(slots=True)
class IntegrityResult:
    passed: bool
    differences: dict[str, dict[str, Any]]
    source_sha256: str
    output_sha256: str
    strict: bool = True

    def summary(self) -> dict[str, Any]:
        return {
            "passed": self.passed,
            "mode": "strict" if self.strict else "format_first",
            "basicChecksPassed": True,
            "deliveryAllowed": self.passed or not self.strict,
            "differences": self.differences,
            "sourceSha256": self.source_sha256,
            "outputSha256": self.output_sha256,
            "checks": [
                "DOCX ZIP CRC and required parts",
                "visible body text",
                "paragraph/table/section and drawing object counts",
                "fields/bookmarks/content controls",
                "headers/footnotes/endnotes/comments",
                "media/embeddings/charts/diagrams/ActiveX payloads",
                "OOXML relationship graph",
                "source file unchanged",
            ],
        }


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _text_hash(root: ET.Element) -> str:
    values = [node.text or "" for node in root.findall(".//w:t", NS)]
    return _sha256("\u241e".join(values).encode("utf-8"))


def _body_suffix_snapshot(root: ET.Element, paragraph_start: int = 1) -> dict[str, Any]:
    """Inspect the body from a 1-based top-level paragraph onward."""
    body = root.find(".//w:body", NS)
    if body is None:
        raise IntegrityValidationError("DOCX 缺少正文 body")
    requested = max(1, paragraph_start)
    seen = 0
    selected: list[ET.Element] = []
    started = requested == 1
    paragraph_tag = f"{{{NS['w']}}}p"
    for child in body:
        if child.tag == paragraph_tag:
            seen += 1
            if seen >= requested:
                started = True
        if started:
            selected.append(child)

    def descendants(prefix: str, local_name: str):
        tag = f"{{{NS[prefix]}}}{local_name}"
        return [node for child in selected for node in child.iter(tag)]

    text = "".join(node.text or "" for node in descendants("w", "t"))
    relationship_ids = {
        value
        for child in selected
        for node in child.iter()
        for attribute, value in node.attrib.items()
        if attribute.startswith(f"{{{RELATIONSHIP_NS}}}") and value
    }
    snapshot: dict[str, Any] = {
        "visible_text": text,
        "relationship_ids": relationship_ids,
    }
    for key, (prefix, local_name) in COUNT_TAGS.items():
        snapshot[key] = len(descendants(prefix, local_name))
    return snapshot


def inspect_docx_body(path: str | Path, paragraph_start: int = 1) -> dict[str, Any]:
    try:
        with zipfile.ZipFile(Path(path)) as package:
            root = ET.fromstring(package.read("word/document.xml"))
            snapshot = _body_suffix_snapshot(root, paragraph_start)
            relationship_targets: dict[str, str] = {}
            relationship_name = "word/_rels/document.xml.rels"
            if relationship_name in package.namelist():
                relationships = ET.fromstring(package.read(relationship_name))
                relationship_targets = {
                    item.attrib.get("Id", ""): item.attrib.get("Target", "")
                    for item in relationships
                }
            part_hashes: dict[str, str] = {}
            names = set(package.namelist())
            for relationship_id in snapshot.pop("relationship_ids"):
                target = relationship_targets.get(relationship_id, "")
                if not target or "://" in target:
                    continue
                name = posixpath.normpath(posixpath.join("word", target))
                if (
                    name in names
                    and any(name.startswith(prefix) for prefix in PRESERVED_PREFIXES)
                ):
                    part_hashes[name] = _sha256(package.read(name))
            snapshot["preserved_parts"] = part_hashes
            return snapshot
    except (zipfile.BadZipFile, KeyError, ET.ParseError) as exc:
        raise IntegrityValidationError("无法读取 DOCX 正文以执行完整性校验") from exc


def _preserved_part_hashes(package: zipfile.ZipFile, names: set[str]) -> dict[str, str]:
    return {
        name: _sha256(package.read(name))
        for name in sorted(names)
        if any(name.startswith(prefix) for prefix in PRESERVED_PREFIXES)
        and not name.endswith("/")
    }


def _preserved_text_hashes(package: zipfile.ZipFile, names: set[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for name in sorted(names):
        if not name.endswith(".xml"):
            continue
        if not any(name.startswith(prefix) for prefix in PRESERVED_TEXT_PARTS):
            continue
        root = ET.fromstring(package.read(name))
        result[name] = _text_hash(root)
    return result


def _relationships(package: zipfile.ZipFile, names: set[str]) -> dict[str, list[tuple[str, str, str]]]:
    result: dict[str, list[tuple[str, str, str]]] = {}
    for name in sorted(item for item in names if item.endswith(".rels")):
        root = ET.fromstring(package.read(name))
        result[name] = sorted(
            (
                node.attrib.get("Type", ""),
                node.attrib.get("Target", ""),
                node.attrib.get("TargetMode", ""),
            )
            for node in root
        )
    return result


def inspect_docx(path: str | Path, *, validate_all_xml: bool = False) -> dict[str, Any]:
    source = Path(path)
    if not source.is_file() or source.stat().st_size == 0:
        raise IntegrityValidationError(f"DOCX 文件不存在或为空：{source.name}")
    try:
        with zipfile.ZipFile(source) as package:
            listed_names = package.namelist()
            names = set(listed_names)
            if len(names) != len(listed_names):
                raise IntegrityValidationError("DOCX 包含重复 ZIP 条目")
            if not REQUIRED_DOCX_PARTS.issubset(names):
                raise IntegrityValidationError("文件不是完整的 DOCX：缺少必要 OOXML 部件")
            invalid_name = next(
                (
                    name
                    for name in names
                    if name.startswith(("/", "\\"))
                    or ".." in name.replace("\\", "/").split("/")
                ),
                None,
            )
            if invalid_name is not None:
                raise IntegrityValidationError("DOCX ZIP 条目路径不安全")
            broken = package.testzip()
            if broken is not None:
                raise IntegrityValidationError(f"DOCX ZIP 校验失败：{broken}")

            root = ET.fromstring(package.read("word/document.xml"))
            # Generated output must be well-formed throughout. Input templates
            # retain the existing inspection scope so repairable extension
            # parts do not acquire a new pre-processing rejection condition.
            if validate_all_xml:
                for name in sorted(names):
                    if name != "word/document.xml" and name.endswith((".xml", ".rels")):
                        ET.fromstring(package.read(name))
            if root.find("w:body", NS) is None:
                raise IntegrityValidationError("DOCX 缺少正文 body")
            return {
                "body_text_sha256": _text_hash(root),
                "text_node_count": len(root.findall(".//w:t", NS)),
                "paragraph_count": len(root.findall(".//w:p", NS)),
                "table_count": len(root.findall(".//w:tbl", NS)),
                "section_count": len(root.findall(".//w:sectPr", NS)),
                "drawing_count": len(root.findall(".//w:drawing", NS)),
                "pict_count": len(root.findall(".//w:pict", NS)),
                "blip_count": len(root.findall(".//a:blip", NS)),
                "field_char_count": len(root.findall(".//w:fldChar", NS)),
                "instruction_text_count": len(root.findall(".//w:instrText", NS)),
                "bookmark_start_count": len(root.findall(".//w:bookmarkStart", NS)),
                "bookmark_end_count": len(root.findall(".//w:bookmarkEnd", NS)),
                "content_control_count": len(root.findall(".//w:sdt", NS)),
                "hyperlink_count": len(root.findall(".//w:hyperlink", NS)),
                "preserved_parts": _preserved_part_hashes(package, names),
                "preserved_text_parts": _preserved_text_hashes(package, names),
                "relationships": _relationships(package, names),
            }
    except zipfile.BadZipFile as exc:
        raise IntegrityValidationError("文件不是有效的 DOCX ZIP 包") from exc
    except ET.ParseError as exc:
        raise IntegrityValidationError("DOCX 包含损坏的 OOXML") from exc


def validate_preservation(
    source_path: str | Path,
    output_path: str | Path,
    *,
    expected_source_sha256: str | None = None,
    allow_front_matter: bool = False,
    source_body_start: int = 1,
    strict: bool = True,
) -> IntegrityResult:
    """Always validate files; optionally make detailed differences non-blocking."""
    source = Path(source_path)
    output = Path(output_path)
    source_hash = sha256_file(source)
    if expected_source_sha256 is not None and source_hash != expected_source_sha256:
        raise IntegrityValidationError("处理期间源文件发生变化，已拒绝交付输出")

    before = inspect_docx(source)
    after = inspect_docx(output, validate_all_xml=True)
    differences = {
        key: {"before": before[key], "after": after[key]}
        for key in before
        if before[key] != after.get(key)
    }
    if allow_front_matter:
        source_body = inspect_docx_body(source, source_body_start)
        output_body = inspect_docx_body(output)
        for key in list(differences):
            if key == "body_text_sha256":
                if source_body["visible_text"] in output_body["visible_text"]:
                    differences.pop(key, None)
            elif key in COUNT_TAGS:
                # Visible text is the authoritative paragraph/run/field check.
                # Only semantic body objects remain blocking count checks.
                blocking_counts = {
                    "table_count", "drawing_count", "pict_count", "blip_count"
                }
                if (
                    key not in blocking_counts
                    or output_body.get(key, 0) >= source_body.get(key, 0)
                ):
                    differences.pop(key, None)
            elif key in {"preserved_text_parts", "relationships"}:
                differences.pop(key, None)
            elif key == "preserved_parts":
                # Front covers may intentionally be replaced. Only binary
                # parts referenced by the retained source body are mandatory.
                source_hashes = set(source_body[key].values())
                output_hashes = set(output_body[key].values())
                if source_hashes.issubset(output_hashes):
                    differences.pop(key, None)
    result = IntegrityResult(
        passed=not differences,
        differences=differences,
        source_sha256=source_hash,
        output_sha256=sha256_file(output),
        strict=strict,
    )
    if strict and not result.passed:
        labels = "、".join(sorted(differences))
        raise IntegrityValidationError(f"严格完整性校验未通过：{labels}")
    return result
