"""Headless compatibility layer shared by the original ThesisDiagram v1.6 parsers.

The web worker deliberately contains no tkinter, dialogs, shell launch or Visio code.
"""
from __future__ import annotations

import json
import math
import os
import re
import subprocess
import sys
import traceback
from dataclasses import asdict, dataclass, field, is_dataclass
from pathlib import Path

from diagram_core.typography import *

FONT = FONT_FAMILY

@dataclass
class ParseIssue:
    line_number: int
    severity: str
    code: str
    message: str
    source_line: str = ""
    suggestion: str = ""

@dataclass(frozen=True)
class Point:
    x: float
    y: float

@dataclass(frozen=True)
class RectBounds:
    center_x: float
    center_y: float
    width: float
    height: float

def rectangle_boundary_point(bounds, toward):
    dx, dy = toward.x - bounds.center_x, toward.y - bounds.center_y
    if dx == 0 and dy == 0:
        return Point(bounds.center_x, bounds.center_y - bounds.height / 2)
    scale = min((bounds.width / 2) / abs(dx) if dx else math.inf,
                (bounds.height / 2) / abs(dy) if dy else math.inf)
    return Point(bounds.center_x + dx * scale, bounds.center_y + dy * scale)

def ellipse_boundary_point(bounds, toward):
    dx, dy = toward.x - bounds.center_x, toward.y - bounds.center_y
    if dx == 0 and dy == 0:
        return Point(bounds.center_x, bounds.center_y - bounds.height / 2)
    factor = 1 / math.sqrt(dx * dx / (bounds.width / 2) ** 2 + dy * dy / (bounds.height / 2) ** 2)
    return Point(bounds.center_x + dx * factor, bounds.center_y + dy * factor)

def read_text(path):
    raw = Path(path).read_bytes()
    for encoding in ("utf-8-sig", "utf-8", "gb18030", "gbk"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            pass
    raise ValueError("文件编码无法识别，请保存为 UTF-8。")

def safe_name(value):
    return re.sub(r'[\\/:*?"<>|]', "_", value).strip() or "diagram"

class OfflineDiagramApp:
    """Marker base retained so the authoritative legacy classes can be instantiated headlessly."""
    pass

class _TkCompat:
    NONE = "none"

tk = _TkCompat()
