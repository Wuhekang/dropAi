from __future__ import annotations

"""CLI boundary between the Dokiai Java backend and the verified formatter.

The process writes only newline-delimited UTF-8 JSON progress events to stdout.
The complete success/failure payload is always written atomically to
``--result-json`` after argument parsing succeeds.
"""

import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import sys
import time
from typing import Any
import uuid

from word_formatter import __version__
from word_formatter.core.analyzer import DocumentAnalyzer, DocumentInfo
from word_formatter.core.doubao_parser import DoubaoRuleParser
from word_formatter.core.integrity import (
    IntegrityValidationError,
    inspect_docx,
    sha256_file,
    validate_preservation,
)
from word_formatter.core.processor import DocumentProcessor
from word_formatter.core.rule_parser import NaturalLanguageRuleParser
from word_formatter.core.template_extractor import TemplateRuleExtractor
from word_formatter.core.word_converter import WordConversionError, WordDocumentConverter
from word_formatter.models.rules import (
    LOCKED_TABLE_POLICY_NOTE,
    DocumentRules,
    ParagraphRule,
    TableRule,
    enforce_locked_table_policy,
)


MAX_SOURCE_BYTES = 100 * 1024 * 1024
MAX_TEMPLATE_BYTES = 30 * 1024 * 1024
MAX_INSTRUCTIONS_BYTES = 64 * 1024
SUPPORTED_TEMPLATE_SUFFIXES = frozenset({".doc", ".docx", ".dotx"})


class CliInputError(ValueError):
    """The caller supplied an invalid or unsafe job input."""


def _configure_stdout() -> None:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="strict", line_buffering=True)
    except (AttributeError, ValueError):
        pass


def emit_progress(progress: int, stage: str, message: str) -> None:
    event = {
        "type": "progress",
        "progress": max(0, min(100, int(progress))),
        "stage": stage,
        "message": message,
    }
    print(json.dumps(event, ensure_ascii=False, separators=(",", ":")), flush=True)


def _write_json_atomic(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{uuid.uuid4().hex}.tmp")
    try:
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def _required_file(path: Path, *, label: str, max_bytes: int) -> None:
    if not path.is_file():
        raise CliInputError(f"{label}不存在或不是文件")
    size = path.stat().st_size
    if size <= 0:
        raise CliInputError(f"{label}不能为空")
    if size > max_bytes:
        raise CliInputError(f"{label}不能超过 {max_bytes // 1024 // 1024} MB")


def validate_runtime_support(template: Path, platform_name: str | None = None) -> None:
    """Fail clearly instead of attempting Word COM on Linux/macOS."""

    platform_name = platform_name or os.name
    if template.suffix.lower() in {".doc", ".dotx"} and platform_name != "nt":
        raise WordConversionError(
            "Linux/macOS 环境不支持旧版 .doc/.dotx 模板；请先在 Microsoft Word 中另存为 .docx 后上传"
        )


def _validate_paths(
    source: Path,
    template: Path,
    output: Path,
    result_json: Path,
    instructions_file: Path | None,
) -> None:
    role_paths = [source, template, output, result_json]
    if instructions_file is not None:
        role_paths.append(instructions_file)
    if len(set(role_paths)) != len(role_paths):
        raise CliInputError("原稿、模板、输出、结果 JSON 和指令文件路径必须互不相同")

    _required_file(source, label="论文原稿", max_bytes=MAX_SOURCE_BYTES)
    _required_file(template, label="格式模板", max_bytes=MAX_TEMPLATE_BYTES)
    if source.suffix.lower() != ".docx":
        raise CliInputError("论文原稿仅支持 .docx 文件")
    if template.suffix.lower() not in SUPPORTED_TEMPLATE_SUFFIXES:
        raise CliInputError("格式模板仅支持 .doc、.docx 或 .dotx 文件")
    validate_runtime_support(template)

    if output.suffix.lower() != ".docx":
        raise CliInputError("输出文件必须使用 .docx 扩展名")
    if output.exists():
        raise FileExistsError("输出文件已存在，格式工具拒绝覆盖")
    if instructions_file is not None:
        _required_file(
            instructions_file,
            label="自然语言指令文件",
            max_bytes=MAX_INSTRUCTIONS_BYTES,
        )

    # Validate the source package before python-docx allocates document objects.
    inspect_docx(source)
    if template.suffix.lower() in {".docx", ".dotx"}:
        inspect_docx(template)


def _read_instructions(path: Path | None) -> str:
    if path is None:
        return ""
    try:
        return path.read_text(encoding="utf-8").strip()
    except UnicodeDecodeError as exc:
        raise CliInputError("自然语言指令文件必须是 UTF-8 文本") from exc


def _paragraph_rule_summary(rule: ParagraphRule) -> dict[str, Any]:
    return {
        "enabled": rule.enabled,
        "chineseFont": rule.chinese_font,
        "latinFont": rule.latin_font,
        "fontSizeName": rule.font_size_name,
        "fontSizePt": rule.font_size_pt,
        "bold": rule.bold,
        "alignment": rule.alignment,
        "lineSpacingMode": rule.line_spacing_mode,
        "firstLineIndentChars": rule.special_indent_chars
        if rule.special_indent_mode == "first_line"
        else 0.0,
        "spaceBefore": {
            "unit": rule.space_before_unit,
            "value": rule.space_before_lines
            if rule.space_before_unit == "line"
            else rule.space_before_pt,
        },
        "spaceAfter": {
            "unit": rule.space_after_unit,
            "value": rule.space_after_lines
            if rule.space_after_unit == "line"
            else rule.space_after_pt,
        },
    }


def _rule_summary(rules: DocumentRules) -> dict[str, Any]:
    table = _paragraph_rule_summary(rules.table)
    table.update(
        {
            "borderStyle": rules.table.border_style,
            "outerBorderWidthPt": rules.table.outer_border_width_pt,
            "innerBorderWidthPt": rules.table.inner_border_width_pt,
            "verticalAlignment": rules.table.vertical_alignment,
            "repeatHeaderRow": rules.table.repeat_header_row,
            "headerRowBold": rules.table.header_row_bold,
        }
    )
    return {
        "schemaVersion": rules.schema_version,
        "name": rules.name,
        "pageSetup": asdict(rules.page_setup),
        "normalText": _paragraph_rule_summary(rules.normal_text),
        "heading1": _paragraph_rule_summary(rules.heading_1),
        "heading2": _paragraph_rule_summary(rules.heading_2),
        "heading3": _paragraph_rule_summary(rules.heading_3),
        "heading4": _paragraph_rule_summary(rules.heading_4),
        "figureCaption": _paragraph_rule_summary(rules.figure_caption),
        "tableCaption": _paragraph_rule_summary(rules.table_caption),
        "reference": _paragraph_rule_summary(rules.reference),
        "table": table,
        "pageNumber": {
            "enabled": rules.page_number.enabled,
            **rules.page_number.settings,
        },
    }


def _analysis_summary(info: DocumentInfo) -> dict[str, Any]:
    levels: dict[str, int] = {}
    for _, level, _ in info.headings:
        key = str(level or "special")
        levels[key] = levels.get(key, 0) + 1
    return {
        "paragraphCount": info.paragraph_count,
        "nonEmptyParagraphCount": info.non_empty_paragraph_count,
        "tableCount": info.table_count,
        "imageCount": info.image_count,
        "sectionCount": info.section_count,
        "headingCount": len(info.headings),
        "headingLevels": levels,
        "uncertainHeadingCount": len(info.uncertain_headings),
        "figureCaptionCount": len(info.figure_captions),
        "tableCaptionCount": len(info.table_captions),
    }


def _error_code(exc: BaseException, template: Path | None = None) -> str:
    if isinstance(exc, IntegrityValidationError):
        return "INTEGRITY_CHECK_FAILED"
    if isinstance(exc, FileExistsError):
        return "OUTPUT_EXISTS"
    if isinstance(exc, WordConversionError):
        if template is not None and template.suffix.lower() in {".doc", ".dotx"} and os.name != "nt":
            return "LEGACY_TEMPLATE_UNSUPPORTED"
        return "TEMPLATE_CONVERSION_FAILED"
    if isinstance(exc, (CliInputError, FileNotFoundError, ValueError)):
        return "INVALID_INPUT"
    return "FORMAT_PROCESS_FAILED"


def _safe_error_message(exc: BaseException) -> str:
    text = " ".join(str(exc).split()) or exc.__class__.__name__
    return text[:1000]


def _publish_without_overwrite(staging: Path, output: Path) -> None:
    """Atomically create the final name without replacing an existing file."""

    linked = False
    try:
        os.link(staging, output)
        linked = True
        staging.unlink()
    except FileExistsError:
        raise FileExistsError("输出文件已存在，格式工具拒绝覆盖") from None
    except OSError as exc:
        if linked:
            try:
                output.unlink(missing_ok=True)
            except OSError:
                pass
        # Hard links are supported on NTFS/ext4 and keep publication atomic. A
        # backend must keep staging/output on the same filesystem.
        raise RuntimeError("无法原子发布输出文件，请确保任务目录位于同一文件系统") from exc


def _safe_unlink(path: Path | None) -> None:
    if path is None:
        return
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass


def run_job(args: argparse.Namespace) -> dict[str, Any]:
    started = time.perf_counter()
    source = Path(args.source).expanduser().resolve()
    template = Path(args.template).expanduser().resolve()
    output = Path(args.output).expanduser().resolve()
    result_json = Path(args.result_json).expanduser().resolve()
    instructions_file = (
        Path(args.instructions_file).expanduser().resolve()
        if args.instructions_file
        else None
    )
    staging: Path | None = None
    staging_log: Path | None = None
    published_output = False
    published_log: Path | None = None
    delivery_committed = False
    template_notes: list[str] = []
    instruction_notes: list[str] = []
    warnings: list[str] = []
    analysis: dict[str, Any] = {}
    rule_summary: dict[str, Any] = {}
    current_progress = 0

    try:
        current_progress = 3
        emit_progress(current_progress, "validating", "正在检查论文原稿、模板和输出路径")
        _validate_paths(source, template, output, result_json, instructions_file)
        source_hash_before = sha256_file(source)
        template_hash_before = sha256_file(template)
        instructions = _read_instructions(instructions_file)
        if args.use_doubao and not instructions:
            raise CliInputError("启用豆包解析时必须提供非空的自然语言指令文件")

        current_progress = 15
        emit_progress(current_progress, "extracting_template", "正在从学校模板提取页面、标题、题注和表格规则")
        extracted = TemplateRuleExtractor().extract(template)
        rules = extracted.rules
        template_notes = list(extracted.notes)

        current_progress = 30
        if instructions:
            if args.use_doubao:
                emit_progress(current_progress, "applying_rules", "正在使用豆包解析附加格式要求")
                api_key = (
                    os.getenv("ARK_API_KEY", "").strip()
                    or os.getenv("DOUBAO_API_KEY", "").strip()
                )
                model = os.getenv("DOUBAO_MODEL", "").strip() or None
                rules, instruction_notes = DoubaoRuleParser(
                    api_key=api_key or None, model=model
                ).parse(instructions, rules)
            else:
                emit_progress(current_progress, "applying_rules", "正在本地解析附加格式要求")
                instruction_notes = NaturalLanguageRuleParser().apply(instructions, rules)
        else:
            emit_progress(current_progress, "applying_rules", "未提供附加要求，直接采用模板识别规则")
        enforce_locked_table_policy(rules)
        instruction_notes.append(LOCKED_TABLE_POLICY_NOTE)
        rule_summary = _rule_summary(rules)

        current_progress = 42
        emit_progress(current_progress, "analyzing_source", "正在识别论文正文、标题、图表题注和参考文献")
        source_info = DocumentAnalyzer().analyze(source)
        analysis = _analysis_summary(source_info)

        current_progress = 56
        emit_progress(current_progress, "processing", "正在把模板规则安全应用到论文副本")
        output.parent.mkdir(parents=True, exist_ok=True)
        staging = output.with_name(
            f".{output.stem}.{uuid.uuid4().hex}.working.docx"
        )
        processor_result = DocumentProcessor().process(source, rules, staging)
        staging_log = staging.with_suffix(".log.json")
        warnings = list(processor_result.warnings)

        current_progress = 88
        emit_progress(current_progress, "integrity_check", "正在校验文字、图片、表格、域、书签和关系部件完整性")
        integrity = validate_preservation(
            source, staging, expected_source_sha256=source_hash_before
        )
        if sha256_file(template) != template_hash_before:
            raise IntegrityValidationError("处理期间格式模板发生变化，已拒绝交付输出")

        _publish_without_overwrite(staging, output)
        published_output = True
        staging = None
        if staging_log is not None and staging_log.exists():
            final_log = output.with_suffix(".log.json")
            if final_log.exists():
                warnings.append("处理日志已存在，未覆盖旧日志。")
                _safe_unlink(staging_log)
            else:
                _publish_without_overwrite(staging_log, final_log)
                published_log = final_log
            staging_log = None

        payload = {
            "success": True,
            "engineVersion": __version__,
            "changedCount": processor_result.changed_count,
            "warnings": warnings,
            "templateNotes": template_notes,
            "instructionNotes": instruction_notes,
            "analysis": analysis,
            "ruleSummary": rule_summary,
            "integrity": integrity.summary(),
            "output": {
                "fileName": output.name,
                "sizeBytes": output.stat().st_size,
                "sha256": sha256_file(output),
            },
            "durationMs": round((time.perf_counter() - started) * 1000),
            "error": None,
        }
        _write_json_atomic(result_json, payload)
        delivery_committed = True
        try:
            emit_progress(100, "completed", "格式处理和完整性校验已完成")
        except BrokenPipeError:
            pass
        return payload
    except Exception as exc:
        _safe_unlink(staging)
        _safe_unlink(staging_log)
        if not delivery_committed:
            if published_log is not None:
                _safe_unlink(published_log)
            if published_output:
                _safe_unlink(output)
        message = _safe_error_message(exc)
        payload = {
            "success": False,
            "engineVersion": __version__,
            "changedCount": 0,
            "warnings": warnings,
            "templateNotes": template_notes,
            "instructionNotes": instruction_notes,
            "analysis": analysis,
            "ruleSummary": rule_summary,
            "integrity": {"passed": False, "differences": {}},
            "durationMs": round((time.perf_counter() - started) * 1000),
            "error": message,
            "errorCode": _error_code(exc, template),
            "errorType": exc.__class__.__name__,
        }
        # An invalid --result-json may deliberately alias an input/output.
        # Never write a failure report when doing so could overwrite user data.
        unsafe_result_targets = {source, template, output}
        if instructions_file is not None:
            unsafe_result_targets.add(instructions_file)
        if result_json not in unsafe_result_targets:
            try:
                _write_json_atomic(result_json, payload)
            except Exception:
                # Preserve the formatting error as the process failure even when
                # the filesystem also refuses the diagnostic report.
                pass
        try:
            emit_progress(current_progress, "failed", message)
        except BrokenPipeError:
            pass
        raise


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Dokiai Word 模板格式处理 CLI",
    )
    parser.add_argument("--source", required=True, help="论文原稿 .docx")
    parser.add_argument("--template", required=True, help="格式模板 .doc/.docx/.dotx")
    parser.add_argument("--output", required=True, help="新建的输出 .docx（禁止覆盖）")
    parser.add_argument("--result-json", required=True, help="完整任务结果 JSON")
    parser.add_argument("--instructions-file", help="可选 UTF-8 自然语言格式要求")
    parser.add_argument(
        "--use-doubao",
        action="store_true",
        help="使用环境变量中的豆包 Key/模型解析格式要求",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    _configure_stdout()
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        run_job(args)
        return 0
    except Exception:
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
