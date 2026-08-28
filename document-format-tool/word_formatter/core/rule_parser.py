from __future__ import annotations

import re

from word_formatter.models.rules import (
    CHINESE_FONT_SIZES,
    DocumentRules,
    ParagraphRule,
    font_size_name_for_points,
)


FONT_SIZE_NAMES = CHINESE_FONT_SIZES


class NaturalLanguageRuleParser:
    """可解释的本地解析器；只转换能明确判断的正文和页面要求。"""

    TARGET_LABELS = {
        "page": "页面",
        "normal": "正文",
        "heading_1": "一级标题",
        "heading_2": "二级标题",
        "heading_3": "三级标题",
        "heading_4": "四级标题",
        "table": "表格",
        "figure": "图名",
    }

    def apply(self, text: str, rules: DocumentRules) -> list[str]:
        notes: list[str] = []
        if not text.strip():
            return notes
        normal = rules.normal_text
        page = rules.page_setup
        table = rules.table
        groups, classification_warnings = self.classify(text)
        normal_text = "，".join(groups["normal"])
        table_text = "，".join(groups["table"])
        figure_text = "，".join(groups["figure"])
        heading_segments = {
            level: groups[f"heading_{level}"] for level in range(1, 5)
        }
        summary = "、".join(
            f"{self.TARGET_LABELS[key]} {len(items)} 条"
            for key, items in groups.items()
            if items
        )
        if summary:
            notes.append(f"分类结果：{summary}")
        notes.extend(classification_warnings)

        for name, size in FONT_SIZE_NAMES.items():
            if name in normal_text:
                normal.font_size_name = name
                normal.font_size_pt = size
                notes.append(f"识别正文字号：{name}（{size:g}磅）")
                break
        point_match = re.search(r"(?:字号|正文)[^\d]{0,6}(\d+(?:\.\d+)?)\s*(?:磅|pt)", normal_text, re.I)
        if point_match:
            normal.font_size_pt = float(point_match.group(1))
            normal.font_size_name = font_size_name_for_points(normal.font_size_pt)
            notes.append(f"识别正文字号：{normal.font_size_name}")

        known_fonts = ["宋体", "仿宋", "楷体", "黑体", "微软雅黑", "方正小标宋", "Times New Roman", "Arial"]
        for font in known_fonts:
            if font.casefold() in normal_text.casefold():
                if font in {"Times New Roman", "Arial"}:
                    normal.latin_font = font
                    normal.number_font = font
                    notes.append(f"识别英文/数字字体：{font}")
                else:
                    normal.chinese_font = font
                    notes.append(f"识别中文字体：{font}")

        indent_match = re.search(r"首行缩进\s*(\d+(?:\.\d+)?)\s*字符", normal_text)
        if indent_match:
            normal.first_line_indent_chars = float(indent_match.group(1))
            notes.append(f"识别首行缩进：{normal.first_line_indent_chars:g} 字符")
        if "两端对齐" in normal_text:
            normal.alignment = "justify"
        elif "居中" in normal_text:
            normal.alignment = "center"
        elif "右对齐" in normal_text:
            normal.alignment = "right"
        elif "左对齐" in normal_text:
            normal.alignment = "left"

        if "1.5倍" in normal_text or "一点五倍" in normal_text:
            normal.line_spacing_mode = "1.5"
        elif "2倍" in normal_text or "两倍" in normal_text:
            normal.line_spacing_mode = "double"
        elif "单倍" in normal_text:
            normal.line_spacing_mode = "single"
        self._apply_extended_line_spacing(normal_text, normal, "正文", notes)
        fixed = re.search(r"固定(?:值)?\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", normal_text, re.I)
        if fixed:
            normal.line_spacing_mode = "fixed"
            normal.fixed_line_spacing_pt = float(fixed.group(1))
            notes.append(f"识别正文固定行距：{normal.fixed_line_spacing_pt:g}磅")
        self._apply_wps_spacing(normal_text, normal, "正文", notes)

        margin_names = {"上": "margin_top_mm", "下": "margin_bottom_mm", "左": "margin_left_mm", "右": "margin_right_mm"}
        for label, field in margin_names.items():
            match = re.search(
                fr"{label}(?:页边距|边距)?\s*(\d+(?:\.\d+)?)\s*(毫米|mm|厘米|cm)",
                text,
                re.I,
            )
            if match:
                value = float(match.group(1))
                if match.group(2).lower() in {"厘米", "cm"}:
                    value *= 10
                setattr(page, field, value)
                notes.append(f"识别{label}页边距：{value:g} mm")
        if re.search(r"\bA4\b|A4纸", text, re.I):
            page.paper = "A4"
            page.width_mm, page.height_mm = 210.0, 297.0
            notes.append("识别纸张：A4")

        if table_text:
            table.enabled = True
            if "三线表" in table_text:
                table.border_style = "three_line"
                notes.append("识别表格边框：三线表")
            elif any(name in table_text for name in ("全框线", "全表格", "全表", "网格")):
                table.border_style = "grid"
                notes.append("识别表格边框：全框线")
            elif "无框线" in table_text:
                table.border_style = "none"
                notes.append("识别表格边框：无框线")
            for name, size in FONT_SIZE_NAMES.items():
                if name in table_text:
                    table.font_size_name = name
                    table.font_size_pt = size
                    notes.append(f"识别表内字号：{name}（{size:g}磅）")
                    break
            table_point = re.search(r"(?:表内|表格|字号)[^\d]{0,8}(\d+(?:\.\d+)?)\s*(?:磅|pt)", table_text, re.I)
            if table_point:
                table.font_size_pt = float(table_point.group(1))
                table.font_size_name = font_size_name_for_points(table.font_size_pt)
                notes.append(f"识别表内字号：{table.font_size_name}")
            for font in known_fonts:
                if font.casefold() in table_text.casefold():
                    if font in {"Times New Roman", "Arial"}:
                        table.latin_font = font
                        table.number_font = font
                        notes.append(f"识别表内英文/数字字体：{font}")
                    else:
                        table.chinese_font = font
                        notes.append(f"识别表内中文字体：{font}")
            if "1.5倍" in table_text or "一点五倍" in table_text:
                table.line_spacing_mode = "1.5"
            elif "2倍" in table_text or "两倍" in table_text:
                table.line_spacing_mode = "double"
            elif "单倍" in table_text:
                table.line_spacing_mode = "single"
            self._apply_extended_line_spacing(table_text, table, "表格", notes)
            table_fixed = re.search(r"固定(?:值)?\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", table_text, re.I)
            if table_fixed:
                table.line_spacing_mode = "fixed"
                table.fixed_line_spacing_pt = float(table_fixed.group(1))
            if "两端对齐" in table_text:
                table.alignment = "justify"
            elif "居中" in table_text:
                table.alignment = "center"
            elif "右对齐" in table_text:
                table.alignment = "right"
            elif "左对齐" in table_text:
                table.alignment = "left"
            row_height = re.search(r"行高\s*(\d+(?:\.\d+)?)\s*(?:毫米|mm)", table_text, re.I)
            if row_height:
                table.row_height_mm = float(row_height.group(1))
            column_width = re.search(r"列宽\s*(\d+(?:\.\d+)?)\s*(?:毫米|mm)", table_text, re.I)
            if column_width:
                table.column_width_mm = float(column_width.group(1))
            self._apply_wps_spacing(table_text, table, "表格", notes)
        for level, items in heading_segments.items():
            if items:
                self._apply_heading_text("，".join(items), getattr(rules, f"heading_{level}"), level, known_fonts, notes)
        if figure_text:
            self._apply_figure_text(figure_text, rules.figure_caption, known_fonts, notes)
        return notes

    @classmethod
    def classify(cls, text: str) -> tuple[dict[str, list[str]], list[str]]:
        """逐条分类；未写对象的后续要求继承最近一次明确对象。"""
        groups: dict[str, list[str]] = {key: [] for key in cls.TARGET_LABELS}
        warnings: list[str] = []
        clauses = [
            item.strip(" ：:")
            for item in re.split(r"[，,；;。\n]+", text)
            if item.strip(" ：:")
        ]
        current_targets: list[str] = []
        for clause in clauses:
            explicit = cls._targets_for_clause(clause)
            if explicit:
                current_targets = explicit
            targets = explicit or current_targets
            if not targets:
                targets = ["normal"]
                warnings.append(f"未指定作用对象，暂按正文分类：{clause}")
                current_targets = targets
            for target in targets:
                groups[target].append(clause)
        return groups, warnings

    @classmethod
    def _targets_for_clause(cls, clause: str) -> list[str]:
        targets: list[str] = []
        if re.search(r"页面|纸张|页边距|上边距|下边距|左边距|右边距|\bA[345]\b", clause, re.I):
            targets.append("page")
        if any(key in clause for key in ("表格外", "表外", "普通正文", "正文", "主体文字")):
            targets.append("normal")
        if any(key in clause for key in ("图名", "图题", "图注", "图片题注", "Figure题注")):
            targets.append("figure")
        if "表格外" not in clause and "表外" not in clause and any(
            key in clause for key in ("表格", "表内", "单元格", "三线表", "全框线", "全表")
        ):
            targets.append("table")

        for match in re.finditer(r"([一二三四1-4])\s*级(?:标题)?|标题\s*([一二三四1-4])", clause):
            token = match.group(1) or match.group(2)
            chinese_levels = {"一": 1, "二": 2, "三": 3, "四": 4}
            level = chinese_levels[token] if token in chinese_levels else int(token)
            if level:
                targets.append(f"heading_{level}")
        if any(key in clause for key in ("各级标题", "所有标题", "标题统一", "全部标题")):
            targets.extend(f"heading_{level}" for level in range(1, 5))
        elif (
            "标题" in clause
            and "figure" not in targets
            and "table" not in targets
            and not any(target.startswith("heading_") for target in targets)
        ):
            targets.extend(f"heading_{level}" for level in range(1, 5))
        # 保持顺序并去重；页面要求可与正文等对象同时存在。
        return list(dict.fromkeys(targets))

    @staticmethod
    def _heading_level_from_text(text: str) -> int | None:
        chinese = {"一": 1, "二": 2, "三": 3, "四": 4}
        match = re.search(r"([一二三四1-4])\s*级标题|标题\s*([1-4])", text)
        if not match:
            return None
        token = match.group(1) or match.group(2)
        return chinese[token] if token in chinese else int(token)

    @classmethod
    def _apply_heading_text(
        cls,
        text: str,
        rule: ParagraphRule,
        level: int,
        known_fonts: list[str],
        notes: list[str],
    ) -> None:
        rule.enabled = True
        for name, size in FONT_SIZE_NAMES.items():
            if name in text:
                rule.font_size_name = name
                rule.font_size_pt = size
                notes.append(f"识别 {level} 级标题字号：{name}（{size:g}磅）")
                break
        for font in known_fonts:
            if font.casefold() in text.casefold():
                if font in {"Times New Roman", "Arial"}:
                    rule.latin_font = font
                    rule.number_font = font
                else:
                    rule.chinese_font = font
        if "不加粗" in text:
            rule.bold = False
        elif "加粗" in text:
            rule.bold = True
        if "居中" in text:
            rule.alignment = "center"
        elif "右对齐" in text:
            rule.alignment = "right"
        elif "两端对齐" in text:
            rule.alignment = "justify"
        elif "左对齐" in text:
            rule.alignment = "left"
        if "1.5倍" in text or "一点五倍" in text:
            rule.line_spacing_mode = "1.5"
        elif "2倍" in text or "两倍" in text:
            rule.line_spacing_mode = "double"
        elif "单倍" in text:
            rule.line_spacing_mode = "single"
        cls._apply_extended_line_spacing(text, rule, f"{level}级标题", notes)
        fixed = re.search(r"固定(?:值)?\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", text, re.I)
        if fixed:
            rule.line_spacing_mode = "fixed"
            rule.fixed_line_spacing_pt = float(fixed.group(1))
        cls._apply_wps_spacing(text, rule, f"{level}级标题", notes)

    @classmethod
    def _apply_figure_text(
        cls, text: str, rule: ParagraphRule, known_fonts: list[str], notes: list[str]
    ) -> None:
        rule.enabled = True
        for name, size in FONT_SIZE_NAMES.items():
            if name in text:
                rule.font_size_name = name
                rule.font_size_pt = size
                notes.append(f"识别图名字号：{name}（{size:g}磅）")
                break
        for font in known_fonts:
            if font.casefold() in text.casefold():
                if font in {"Times New Roman", "Arial"}:
                    rule.latin_font = font
                    rule.number_font = font
                else:
                    rule.chinese_font = font
        if "居中" in text:
            rule.alignment = "center"
        elif "左对齐" in text:
            rule.alignment = "left"
        elif "右对齐" in text:
            rule.alignment = "right"
        cls._apply_extended_line_spacing(text, rule, "图名", notes)
        cls._apply_wps_spacing(text, rule, "图名", notes)

    @staticmethod
    def _apply_extended_line_spacing(
        text: str, rule: ParagraphRule, label: str, notes: list[str]
    ) -> None:
        multiple = re.search(r"(1\.15|1\.25)\s*倍", text)
        custom = re.search(r"多倍行距\s*(\d+(?:\.\d+)?)", text)
        minimum = re.search(r"最小值\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", text, re.I)
        if multiple or custom:
            rule.line_spacing_mode = "multiple"
            rule.multiple_line_spacing = float((multiple or custom).group(1))
            notes.append(f"识别{label}行距：{rule.multiple_line_spacing:g}倍")
        if minimum:
            rule.line_spacing_mode = "at_least"
            rule.minimum_line_spacing_pt = float(minimum.group(1))
            notes.append(f"识别{label}行距：最小值 {rule.minimum_line_spacing_pt:g}磅")

    @staticmethod
    def _apply_wps_spacing(
        text: str, rule: ParagraphRule, label: str, notes: list[str]
    ) -> None:
        character = re.search(
            r"(?:字符间距|字距)\s*(标准|加宽|扩展|紧缩)?\s*(\d+(?:\.\d+)?)?\s*(?:磅|pt)?",
            text,
            re.I,
        )
        if character:
            mode_text = character.group(1) or "标准"
            rule.character_spacing_mode = {
                "标准": "standard", "加宽": "expanded", "扩展": "expanded", "紧缩": "condensed"
            }[mode_text]
            rule.character_spacing_pt = float(character.group(2) or 0)
            notes.append(f"识别{label}字符间距：{mode_text} {rule.character_spacing_pt:g}磅")
        before = re.search(r"段前(?:间距)?\s*(\d+(?:\.\d+)?)\s*行", text)
        after = re.search(r"段后(?:间距)?\s*(\d+(?:\.\d+)?)\s*行", text)
        before_pt = re.search(r"段前(?:间距)?\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", text, re.I)
        after_pt = re.search(r"段后(?:间距)?\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", text, re.I)
        if before:
            rule.space_before_unit = "line"
            rule.space_before_lines = float(before.group(1))
            notes.append(f"识别{label}段前：{rule.space_before_lines:g}行")
        elif before_pt:
            rule.space_before_unit = "pt"
            rule.space_before_pt = float(before_pt.group(1))
            notes.append(f"识别{label}段前：{rule.space_before_pt:g}磅")
        if after:
            rule.space_after_unit = "line"
            rule.space_after_lines = float(after.group(1))
            notes.append(f"识别{label}段后：{rule.space_after_lines:g}行")
        elif after_pt:
            rule.space_after_unit = "pt"
            rule.space_after_pt = float(after_pt.group(1))
            notes.append(f"识别{label}段后：{rule.space_after_pt:g}磅")
        left = re.search(r"文本之前\s*(\d+(?:\.\d+)?)\s*(?:厘米|cm)", text, re.I)
        right = re.search(r"文本之后\s*(\d+(?:\.\d+)?)\s*(?:厘米|cm)", text, re.I)
        if left:
            rule.left_indent_cm = float(left.group(1))
        if right:
            rule.right_indent_cm = float(right.group(1))
        special = re.search(r"(首行缩进|悬挂缩进)\s*(\d+(?:\.\d+)?)\s*字符", text)
        if special:
            rule.special_indent_mode = "first_line" if special.group(1) == "首行缩进" else "hanging"
            rule.special_indent_chars = float(special.group(2))
