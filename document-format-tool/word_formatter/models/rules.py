from __future__ import annotations

from dataclasses import asdict, dataclass, field, fields
import json
import math
from pathlib import Path
from typing import Any, Literal


Alignment = Literal["left", "center", "right", "justify"]
LineSpacingMode = Literal["single", "1.5", "double", "at_least", "fixed", "multiple"]
CharacterSpacingMode = Literal["standard", "expanded", "condensed"]
SpecialIndentMode = Literal["none", "first_line", "hanging"]
SpacingUnit = Literal["line", "pt"]
TableBorderStyle = Literal["three_line", "grid", "none"]
VerticalAlignment = Literal["top", "center", "bottom"]
DEFAULT_LATIN_FONT = "Times New Roman"
MAX_PARAGRAPH_INDENT_CM = 2.0
MAX_PARAGRAPH_INDENT_CHARS = 4.0

# 中国大陆 WPS“字号”下拉框使用的标准字号与磅值映射。
CHINESE_FONT_SIZES: dict[str, float] = {
    "初号": 42.0,
    "小初": 36.0,
    "一号": 26.0,
    "小一": 24.0,
    "二号": 22.0,
    "小二": 18.0,
    "三号": 16.0,
    "小三": 15.0,
    "四号": 14.0,
    "小四": 12.0,
    "五号": 10.5,
    "小五": 9.0,
    "六号": 7.5,
    "小六": 6.5,
    "七号": 5.5,
    "八号": 5.0,
}


def font_size_name_for_points(points: float) -> str:
    for name, value in CHINESE_FONT_SIZES.items():
        if abs(value - points) < 0.01:
            return name
    return f"{points:g}磅"


@dataclass(slots=True)
class RuleBase:
    """所有格式规则都可单独启用或关闭。"""

    enabled: bool = True


@dataclass(slots=True)
class PageSetupRule(RuleBase):
    paper: str = "A4"
    width_mm: float = 210.0
    height_mm: float = 297.0
    margin_top_mm: float = 25.4
    margin_bottom_mm: float = 25.4
    margin_left_mm: float = 31.8
    margin_right_mm: float = 31.8


@dataclass(slots=True)
class ParagraphRule(RuleBase):
    chinese_font: str = "宋体"
    latin_font: str = DEFAULT_LATIN_FONT
    number_font: str = DEFAULT_LATIN_FONT
    font_size_name: str = "小四"
    font_size_pt: float = 12.0
    bold: bool = False
    italic: bool = False
    underline: bool = False
    character_spacing_mode: CharacterSpacingMode = "standard"
    character_spacing_pt: float = 0.0
    direction: Literal["ltr", "rtl"] = "ltr"
    outline_level: int = 9
    left_indent_cm: float = 0.0
    right_indent_cm: float = 0.0
    special_indent_mode: SpecialIndentMode = "first_line"
    special_indent_chars: float = 2.0
    auto_adjust_right_indent: bool = True
    first_line_indent_chars: float = 2.0
    left_indent_chars: float = 0.0
    right_indent_chars: float = 0.0
    line_spacing_mode: LineSpacingMode = "1.5"
    fixed_line_spacing_pt: float = 20.0
    minimum_line_spacing_pt: float = 12.0
    multiple_line_spacing: float = 1.25
    space_before_unit: SpacingUnit = "line"
    space_after_unit: SpacingUnit = "line"
    space_before_lines: float = 0.0
    space_after_lines: float = 0.0
    # 保留旧字段用于加载 0.1.x 格式方案；新版本统一写入 WPS 的“行”。
    space_before_pt: float = 0.0
    space_after_pt: float = 0.0
    alignment: Alignment = "justify"
    snap_to_grid: bool = False
    widow_control: bool = True
    keep_with_next: bool = False
    keep_lines_together: bool = False
    page_break_before: bool = False


@dataclass(slots=True)
class TableRule(ParagraphRule):
    """表格拥有独立于正文的字体、段落和边框规则。"""

    first_line_indent_chars: float = 0.0
    special_indent_mode: SpecialIndentMode = "none"
    special_indent_chars: float = 0.0
    line_spacing_mode: LineSpacingMode = "single"
    alignment: Alignment = "center"
    border_style: TableBorderStyle = "three_line"
    border_color: str = "000000"
    outer_border_width_pt: float = 1.5
    inner_border_width_pt: float = 0.75
    vertical_alignment: VerticalAlignment = "center"
    row_height_mm: float = 0.0
    column_width_mm: float = 0.0
    repeat_header_row: bool = True
    header_row_bold: bool = False


def _heading_rule(level: int) -> ParagraphRule:
    settings = {
        1: ("三号", 16.0),
        2: ("小三", 15.0),
        3: ("四号", 14.0),
        4: ("小四", 12.0),
    }
    size_name, size_pt = settings[level]
    return ParagraphRule(
        enabled=False,
        chinese_font="黑体",
        font_size_name=size_name,
        font_size_pt=size_pt,
        bold=True,
        first_line_indent_chars=0.0,
        special_indent_mode="none",
        special_indent_chars=0.0,
        line_spacing_mode="single",
        alignment="left",
        outline_level=level - 1,
        keep_with_next=True,
    )


@dataclass(slots=True)
class GenericRule(RuleBase):
    """后续模块的稳定扩展入口。"""

    settings: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class DocumentRules:
    schema_version: str = "1.2"
    name: str = "默认格式方案"
    page_setup: PageSetupRule = field(default_factory=PageSetupRule)
    normal_text: ParagraphRule = field(default_factory=ParagraphRule)
    heading_1: ParagraphRule = field(default_factory=lambda: _heading_rule(1))
    heading_2: ParagraphRule = field(default_factory=lambda: _heading_rule(2))
    heading_3: ParagraphRule = field(default_factory=lambda: _heading_rule(3))
    heading_4: ParagraphRule = field(default_factory=lambda: _heading_rule(4))
    toc_title: ParagraphRule = field(default_factory=lambda: _heading_rule(1))
    toc_1: ParagraphRule = field(default_factory=lambda: _heading_rule(1))
    toc_2: ParagraphRule = field(default_factory=lambda: _heading_rule(2))
    toc_3: ParagraphRule = field(default_factory=lambda: _heading_rule(3))
    table: TableRule = field(default_factory=TableRule)
    figure_caption: ParagraphRule = field(
        default_factory=lambda: ParagraphRule(
            enabled=False,
            chinese_font="宋体",
            font_size_name="五号",
            font_size_pt=10.5,
            first_line_indent_chars=0.0,
            special_indent_mode="none",
            special_indent_chars=0.0,
            line_spacing_mode="single",
            alignment="center",
        )
    )
    table_caption: ParagraphRule = field(default_factory=lambda: ParagraphRule(
        enabled=False, font_size_name="五号", font_size_pt=10.5,
        first_line_indent_chars=0, special_indent_mode="none", special_indent_chars=0,
        alignment="center", line_spacing_mode="single"))
    header: GenericRule = field(default_factory=lambda: GenericRule(enabled=False))
    footer: GenericRule = field(default_factory=lambda: GenericRule(enabled=False))
    page_number: GenericRule = field(default_factory=lambda: GenericRule(enabled=False))
    reference: ParagraphRule = field(default_factory=lambda: ParagraphRule(enabled=False))
    formula: GenericRule = field(default_factory=lambda: GenericRule(enabled=False))

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def save(self, path: str | Path) -> None:
        Path(path).write_text(
            json.dumps(self.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8"
        )

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "DocumentRules":
        paragraph_keys = {
            "normal_text", "heading_1", "heading_2", "heading_3", "heading_4", "toc_title", "toc_1", "toc_2", "toc_3",
            "figure_caption", "table_caption", "reference",
        }
        generic_keys = {"header", "footer", "page_number", "formula"}
        values = dict(data)
        values["page_setup"] = PageSetupRule(**values.get("page_setup", {}))
        for key in paragraph_keys:
            paragraph_data = dict(values.get(key, {"enabled": False}))
            cls._upgrade_paragraph_data(paragraph_data)
            values[key] = ParagraphRule(**paragraph_data)
        table_data = values.get("table", {})
        # 兼容 0.1.0 使用 GenericRule 保存的旧格式方案。
        if "settings" in table_data:
            table_data = {"enabled": table_data.get("enabled", False), **table_data.get("settings", {})}
        cls._upgrade_paragraph_data(table_data)
        allowed_table_fields = {item.name for item in fields(TableRule)}
        table_data = {key: value for key, value in table_data.items() if key in allowed_table_fields}
        values["table"] = TableRule(**table_data)
        for key in generic_keys:
            values[key] = GenericRule(**values.get(key, {"enabled": False}))
        return cls(**values)

    @staticmethod
    def _upgrade_paragraph_data(data: dict[str, Any]) -> None:
        size = float(data.get("font_size_pt", 12.0))
        data.setdefault("font_size_name", font_size_name_for_points(size))
        spacing = float(data.get("character_spacing_pt", 0.0))
        if "character_spacing_mode" not in data:
            data["character_spacing_mode"] = "expanded" if spacing > 0 else "condensed" if spacing < 0 else "standard"
            data["character_spacing_pt"] = abs(spacing)
        if "space_before_lines" not in data:
            data["space_before_lines"] = float(data.get("space_before_pt", 0.0)) / size if size else 0.0
        if "space_after_lines" not in data:
            data["space_after_lines"] = float(data.get("space_after_pt", 0.0)) / size if size else 0.0
        if "special_indent_mode" not in data:
            chars = float(data.get("first_line_indent_chars", 0.0))
            data["special_indent_mode"] = "first_line" if chars > 0 else "none"
            data["special_indent_chars"] = chars

    @classmethod
    def load(cls, path: str | Path) -> "DocumentRules":
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise ValueError("格式方案必须是 JSON 对象")
        return cls.from_dict(data)


LOCKED_TABLE_POLICY_NOTE = (
    "正文数据表采用系统固定规范：黑色三线表（外框 1.5 磅、表头线 0.75 磅），"
    "表格及单元格内容居中，段落零缩进且全部不加粗；"
    "字体字号按模板识别并允许确认修改，以上结构设置不可覆盖。"
)


def apply_default_latin_fonts(rules: DocumentRules) -> DocumentRules:
    """Normalize inferred defaults before applying the customer's explicit edits.

    A school's Chinese font declaration or a Word run's ASCII fallback must
    not become the English default. This is intentionally separate from the
    locked policies so a later latinFont confirmation remains editable.
    """
    for item in fields(rules):
        rule = getattr(rules, item.name)
        if isinstance(rule, ParagraphRule):
            rule.latin_font = DEFAULT_LATIN_FONT
            rule.number_font = DEFAULT_LATIN_FONT
    return rules


def enforce_locked_table_policy(rules: DocumentRules) -> DocumentRules:
    """Apply the non-overridable formatting contract for body data tables.

    Template extraction and both instruction parsers intentionally remain able
    to describe arbitrary table examples. This final, idempotent pass is the
    policy boundary that prevents those inputs from changing the product's
    fixed table contract.
    """

    table = rules.table
    table.enabled = True
    table.bold = False
    table.alignment = "center"
    table.left_indent_cm = 0.0
    table.right_indent_cm = 0.0
    table.left_indent_chars = 0.0
    table.right_indent_chars = 0.0
    table.first_line_indent_chars = 0.0
    table.special_indent_mode = "none"
    table.special_indent_chars = 0.0
    table.border_style = "three_line"
    table.border_color = "000000"
    table.outer_border_width_pt = 1.5
    table.inner_border_width_pt = 0.75
    table.vertical_alignment = "center"
    table.header_row_bold = False
    return rules


def normalize_inferred_layout(rules: DocumentRules, explicit_fields: dict | None = None) -> list[str]:
    """Discard incidental sample layout before, never after, customer edits.

    A margin-like indent on one annotation or cover run is not a body rule.
    Explicit prose is stronger evidence; font sizes and line spacing remain
    independently inferred and are deliberately not reset here.
    """
    explicit_fields = explicit_fields or {}
    changed = []
    for name in ("normal_text", "heading_1", "heading_2", "heading_3", "heading_4",
                 "toc_title", "toc_1", "toc_2", "toc_3", "figure_caption",
                 "table_caption", "reference", "table"):
        rule = getattr(rules, name)
        entry = explicit_fields.get(name, {})
        declared = entry.get("rule", {}) if isinstance(entry, dict) else {}
        defaults = {"left_indent_cm": 0.0, "right_indent_cm": 0.0,
                    "left_indent_chars": 0.0, "right_indent_chars": 0.0,
                    "direction": "ltr", "character_spacing_mode": "standard",
                    "character_spacing_pt": 0.0}
        if name == "normal_text":
            defaults.update(bold=False, alignment="justify")
        if name.startswith("heading_") or name in {"toc_title", "figure_caption", "table_caption"}:
            defaults.update(special_indent_mode="none", special_indent_chars=0.0,
                            first_line_indent_chars=0.0)
        if name in {"figure_caption", "table_caption"}:
            defaults["alignment"] = "center"
        for field_name, default in defaults.items():
            expected = declared.get(field_name, default)
            if getattr(rule, field_name) != expected:
                setattr(rule, field_name, expected)
                changed.append(f"{name}.{field_name}")
    return (["已清理样例局部缩进、加粗或排版方向，未将其推广到全文；请在确认页核对："
             + "、".join(changed)] if changed else [])


LOCKED_DOCUMENT_POLICY_NOTES = (
    "正文段落默认首行缩进 2 字符；模板和客户确认值不能覆盖。",
    "全文左右段落缩进仅允许 0～2 厘米；异常值自动归零，字符缩进最多 4 字符且不超过 2 厘米，模板、AI 和确认值均不能绕过。",
    "正文数据表固定为黑色三线表，内容水平/垂直居中且不加粗。",
    "图片所在段落固定居中、单倍行距。",
    "清除段落级“与下段同页、段中不分页、段前分页、孤行控制”，保留文档中的分页符和分节符。",
    "先读取模板文字要求并判断用途；仅复制确认存在的独立封面、诚信声明页，纯规范说明不复制，保留原稿前置内容。",
)


def enforce_locked_document_policy(rules: DocumentRules) -> DocumentRules:
    """Apply product rules which neither template AI nor customer may change."""
    enforce_locked_table_policy(rules)
    enforce_safe_indentation(rules)
    body = rules.normal_text
    body.enabled = True
    body.special_indent_mode = "first_line"
    body.special_indent_chars = 2.0
    body.first_line_indent_chars = 2.0
    for rule in (
        body,
        rules.heading_1,
        rules.heading_2,
        rules.heading_3,
        rules.heading_4,
        rules.toc_title, rules.toc_1, rules.toc_2, rules.toc_3,
        rules.figure_caption,
        rules.table_caption,
        rules.reference,
        rules.table,
    ):
        rule.widow_control = False
        rule.keep_with_next = False
        rule.keep_lines_together = False
        rule.page_break_before = False
    return rules


def enforce_safe_indentation(rules: DocumentRules) -> int:
    """Hard output boundary, including restored plans and direct library calls.

    Reject inference artifacts by resetting them, not by clamping a 8 cm indent
    to an unexplained 2 cm indent. Small, intentional TOC/reference indents stay.
    """
    changed = 0
    for item in fields(rules):
        rule = getattr(rules, item.name)
        if not isinstance(rule, ParagraphRule):
            continue
        size = rule.font_size_pt
        if not isinstance(size, (float, int)) or not math.isfinite(size) or size <= 0:
            size = 12.0
        chars_limit = min(MAX_PARAGRAPH_INDENT_CHARS,
                          MAX_PARAGRAPH_INDENT_CM * 72 / (2.54 * size))
        for name in ("left_indent_cm", "right_indent_cm", "left_indent_chars",
                     "right_indent_chars", "first_line_indent_chars", "special_indent_chars"):
            value = getattr(rule, name)
            limit = MAX_PARAGRAPH_INDENT_CM if name.endswith("_cm") else chars_limit
            if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value) or not 0 <= value <= limit:
                setattr(rule, name, 0.0)
                changed += 1
    return changed
