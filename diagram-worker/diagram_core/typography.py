"""Shared Chinese typography and measurement rules for ThesisDiagram renderers."""
from __future__ import annotations

import unicodedata

FONT_FAMILY = "Microsoft YaHei, PingFang SC, SimSun, Noto Sans CJK SC, sans-serif"
DIAGRAM_TITLE_FONT = 26
PRIMARY_FONT = 24
SECONDARY_FONT = 22
NODE_FONT = 20
MEDIUM_FONT = 19
SMALL_FONT = 17
EDGE_LABEL_FONT = 17
MIN_NODE_FONT = 17


def character_units(char: str) -> float:
    """Return display units: CJK/full-width chars are 1, ASCII is 0.5."""
    if char == "\t":
        return 2.0
    return 1.0 if unicodedata.east_asian_width(char) in ("W", "F", "A") else 0.5


def text_units(value: str) -> float:
    return sum(character_units(char) for char in value)


def wrap_text(value: str, max_units: float = 8) -> list[str]:
    """Wrap without truncation, while preserving explicit newlines."""
    result: list[str] = []
    for source_line in str(value or "").split("\n"):
        if source_line == "":
            result.append("")
            continue
        line = ""
        units = 0.0
        for char in source_line:
            width = character_units(char)
            if line and units + width > max_units:
                result.append(line)
                line, units = char, width
            else:
                line += char
                units += width
        result.append(line)
    return result or [""]


def line_height(font_size: int) -> int:
    return max(font_size + 5, round(font_size * 1.35))


def text_block_height(value: str, font_size: int = NODE_FONT, max_units: float = 8) -> int:
    return len(wrap_text(value, max_units)) * line_height(font_size)


def node_height(value: str, font_size: int = NODE_FONT, max_units: float = 8,
                minimum: int = 64, vertical_padding: int = 26) -> int:
    return max(minimum, text_block_height(value, font_size, max_units) + vertical_padding)


def node_width(value: str, font_size: int = NODE_FONT, max_units: float = 8,
               minimum: int = 180, horizontal_padding: int = 42) -> int:
    widest = max((text_units(line) for line in wrap_text(value, max_units)), default=1)
    return max(minimum, round(widest * font_size + horizontal_padding))
