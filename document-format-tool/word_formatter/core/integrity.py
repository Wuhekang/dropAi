from __future__ import annotations

"""Strict, content-preservation checks for formatted DOCX files.

Formatting is allowed to change document/style/settings/footer XML, but it must
not change the source's visible body text, semantic object counts, media,
embedded objects, charts, or relationship graph.
"""

from dataclasses import dataclass
import hashlib
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


class IntegrityValidationError(RuntimeError):
    """The formatted document lost or changed source content."""


@dataclass(slots=True)
class IntegrityResult:
    passed: bool
    differences: dict[str, dict[str, Any]]
    source_sha256: str
    output_sha256: str

    def summary(self) -> dict[str, Any]:
        return {
            "passed": self.passed,
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


def inspect_docx(path: str | Path) -> dict[str, Any]:
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
) -> IntegrityResult:
    source = Path(source_path)
    output = Path(output_path)
    source_hash = sha256_file(source)
    if expected_source_sha256 is not None and source_hash != expected_source_sha256:
        raise IntegrityValidationError("处理期间源文件发生变化，已拒绝交付输出")

    before = inspect_docx(source)
    after = inspect_docx(output)
    differences = {
        key: {"before": before[key], "after": after[key]}
        for key in before
        if before[key] != after.get(key)
    }
    result = IntegrityResult(
        passed=not differences,
        differences=differences,
        source_sha256=source_hash,
        output_sha256=sha256_file(output),
    )
    if not result.passed:
        labels = "、".join(sorted(differences))
        raise IntegrityValidationError(f"严格完整性校验未通过：{labels}")
    return result
