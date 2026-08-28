from __future__ import annotations

from dataclasses import asdict, dataclass, field, fields
import json
from pathlib import Path
from typing import Any, Literal


Alignment = Literal["left", "center", "right", "justify"]
LineSpacingMode = Literal["single", "1.5", "double", "at_least", "fixed", "multiple"]
CharacterSpacingMode = Literal["standard", "expanded", "condensed"]
SpecialIndentMode = Literal["none", "first_line", "hanging"]
SpacingUnit = Literal["line", "pt"]
TableBorderStyle = Literal["three_line", "grid", "none"]
VerticalAlignment = Literal["top", "center", "bottom"]

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
    latin_font: str = "Times New Roman"
    number_font: str = "Times New Roman"
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
    table_caption: ParagraphRule = field(default_factory=lambda: ParagraphRule(enabled=False))
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
            "normal_text", "heading_1", "heading_2", "heading_3", "heading_4",
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
