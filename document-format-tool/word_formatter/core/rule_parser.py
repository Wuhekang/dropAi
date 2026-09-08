from __future__ import annotations

import re

from word_formatter.models.rules import (
    CHINESE_FONT_SIZES,
    DocumentRules,
    ParagraphRule,
    font_size_name_for_points,
)


FONT_SIZE_NAMES = CHINESE_FONT_SIZES


def extract_explicit_template_fields(blocks: list[dict]) -> dict[str, dict]:
    """Parse only labelled written requirements, not the formatting of prose.

    Values retain field-level evidence and scope. Conflicting same-scope values
    are omitted for review; a broad default never erases a role-specific value.
    """
    candidates: dict[tuple[str, str], list[tuple[object, int, str]]] = {}
    size_pattern = "|".join(re.escape(name) for name, _ in NaturalLanguageRuleParser._font_sizes_longest_first())
    fonts = ("方正小标宋", "微软雅黑", "仿宋", "楷体", "黑体", "宋体")
    labels = re.compile(
        r"目录[一二三四1-4]级(?:标题|条目)?|(?:正文)?[一二三四1-4]级标题|各级标题|目录(?:标题|内容|条目)?[:：]|"
        r"(?:图序和图题|图名称|图名|图题|表序和表题|表名称|表名|表题|表内|表格内容|单元格)[:：]|"
        r"(?:摘要正文|正文|本文|全文)(?=\s*[:：\n（(]|使用|采用|应|为|宋体|黑体|小|字号|首行)|参考文献(?:正文|内容)[:：]"
    )

    def properties(text: str) -> dict:
        values: dict = {}
        text = re.sub(r"\s+", "", text).replace("断后", "段后")
        match = re.search(size_pattern, text)
        if match:
            values.update(font_size_name=match.group(), font_size_pt=FONT_SIZE_NAMES[match.group()])
        point = re.search(r"字号(?:为|采用|使用)?(\d+(?:\.\d+)?)(?:磅|pt)", text, re.I)
        if point:
            values.update(font_size_pt=float(point[1]), font_size_name=font_size_name_for_points(float(point[1])))
        for font in fonts:
            if font in text:
                values["chinese_font"] = font
                break
        if re.search(r"TimesNewRoman", text, re.I):
            values.update(latin_font="Times New Roman", number_font="Times New Roman")
        if "不加粗" in text or "非加粗" in text:
            values["bold"] = False
        elif "加粗" in text:
            values["bold"] = True
        for token, alignment in (("居中", "center"), ("两端对齐", "justify"), ("右对齐", "right"),
                                 ("左对齐", "left"), ("居左", "left"), ("顶格", "left")):
            if token in text:
                values["alignment"] = alignment
                break
        fixed = re.search(r"固定(?:值|行距|行间距)?(\d+(?:\.\d+)?)(?:磅|pt)", text, re.I)
        multiple = re.search(r"(\d+(?:\.\d+)?)倍行距|行间距(\d+(?:\.\d+)?)(?:行)?", text)
        if fixed:
            values.update(line_spacing_mode="fixed", fixed_line_spacing_pt=float(fixed[1]))
        elif multiple:
            value = float(multiple[1] or multiple[2])
            if value in {1, 1.5, 2}:
                values["line_spacing_mode"] = {1: "single", 1.5: "1.5", 2: "double"}[value]
            else:
                values.update(line_spacing_mode="multiple", multiple_line_spacing=value)
        elif "单倍" in text:
            values["line_spacing_mode"] = "single"
        for side in ("before", "after"):
            label = "段前" if side == "before" else "段后"
            spacing = re.search(label + r"(?:间距|各)?(\d+(?:\.\d+)?)(行|磅|pt)", text, re.I)
            both = re.search(r"段前段后(?:各)?(\d+(?:\.\d+)?)(行|磅|pt)", text, re.I)
            spacing = both or spacing
            if spacing:
                unit = "line" if spacing[2] == "行" else "pt"
                values[f"space_{side}_unit"] = unit
                values[f"space_{side}_{'lines' if unit == 'line' else 'pt'}"] = float(spacing[1])
        indent = re.search(r"首行缩进(\d+(?:\.\d+)?)字符", text)
        if indent:
            value = float(indent[1])
            values.update(first_line_indent_chars=value, special_indent_chars=value,
                          special_indent_mode="first_line" if value else "none")
        if "三线表" in text:
            values["border_style"] = "three_line"
        return values

    def add(role: str, text: str, identity: str, priority: int = 2) -> None:
        for field, value in properties(text).items():
            if field == "border_style" and role != "table":
                continue
            if priority == 1 and role != "normal_text" and field not in {"chinese_font", "latin_font", "number_font"}:
                # A generic "全文" default is not an instruction to turn every
                # heading into a small, indented, bold body paragraph.
                continue
            candidates.setdefault((role, field), []).append((value, priority, identity))

    previous_role = None
    for block in blocks:
        text, identity = block.get("text", ""), block.get("id", "")
        if not isinstance(text, str) or not text.strip() or not identity:
            continue
        region = block.get("semanticRegion", "main")
        compact = re.sub(r"\s+", "", text)
        if region == "toc" and any(re.fullmatch(r"1\.5倍行距[。；;]?", re.sub(r"\s+", "", line))
                                   for line in text.splitlines()):
            # An independent instruction on a verified contents page applies
            # to its entries. A level-specific instruction still outranks it;
            # the same unlabelled text outside this region is not propagated.
            for level in range(1, 4):
                candidates.setdefault((f"toc_{level}", "line_spacing_mode"), []).append(("1.5", 1, identity))
        if "页面设置" in compact or "页边距" in compact:
            start = compact.find("页面设置") if "页面设置" in compact else compact.find("页边距")
            page_text = compact[start:start + 250]
            for chinese, field in (("上", "margin_top_mm"), ("下", "margin_bottom_mm"),
                                   ("左", "margin_left_mm"), ("右", "margin_right_mm")):
                margin = re.search(chinese + r"(?:页边距|边距)?[:：]?(\d+(?:\.\d+)?)(cm|mm|厘米|毫米)", page_text, re.I)
                if margin:
                    value = float(margin[1]) * (10 if margin[2].lower() in {"cm", "厘米"} else 1)
                    candidates.setdefault(("page_setup", field), []).append((value, 2, identity))
            if "A4" in compact.upper():
                for field, value in (("paper", "A4"), ("width_mm", 210.0), ("height_mm", 297.0)):
                    candidates.setdefault(("page_setup", field), []).append((value, 2, identity))
        # Inline explanations on actual samples are unambiguous even when the
        # sample's own Word font disagrees with the written size.
        annotated = re.fullmatch(r"(.{0,100}?)[（(]([^（）()]{1,100})[）)]", compact)
        if annotated and re.search(size_pattern + r"|宋体|黑体|居中", annotated[2]):
            prefix, note = annotated[1], annotated[2]
            role = None
            numeric = re.match(r"^(\d+(?:[.．]\d+){0,3})(?:[^\d.]|$)", prefix)
            if prefix in {"目录", "Contents"}:
                role = "toc_title"
            elif prefix in {"前言", "引言", "绪论", "摘要", "Abstract", "ABSTRACT", "结论", "致谢", "参考文献"}:
                role = "heading_1"
            elif numeric:
                role = f"{'toc' if region == 'toc' else 'heading'}_{min(4, numeric[1].count('.') + numeric[1].count('．') + 1)}"
            elif re.fullmatch(r"[XxＸｘ…]*", prefix):
                role = "reference" if previous_role == "reference" else "normal_text"
            if role:
                add(role, note, identity)
                previous_role = "reference" if prefix == "参考文献" else role
                continue
        # Keep PDF wrapped labels intact, while retaining newlines for a bare
        # "正文" label. Chinese-to-Chinese wraps cannot change the rule's scope.
        normalized = text
        matches = list(labels.finditer(normalized))
        local_region = region
        for index, match in enumerate(matches):
            label = match.group()
            if label.startswith("目录"):
                local_region = "toc"
            elif label.startswith("正文") and region != "reference":
                local_region = "main"
            end = matches[index + 1].start() if index + 1 < len(matches) else len(normalized)
            fragment = normalized[match.end():end]
            # A requirement annotation is short; don't absorb unrelated sample
            # paragraphs later on the page. The next role label is a hard stop.
            fragment = re.split(r"正文页脚|页脚|页眉|奇数页|对总项|绪论[:：]|插图位于", fragment, maxsplit=1)[0][:500]
            roles, priority = [], 2
            level = re.search(r"([一二三四1-4])级(?:标题|条目)?", label)
            if level:
                token = level[1]
                number = "一二三四".index(token) + 1 if token in "一二三四" else int(token)
                roles = [f"{'toc' if local_region == 'toc' else 'heading'}_{number}"]
            elif label == "各级标题":
                roles = [f"heading_{n}" for n in range(1, 5)]
            elif label.startswith("目录"):
                roles = ["toc_title"] if "内容" not in label and "条目" not in label else [f"toc_{n}" for n in range(1, 4)]
            elif label.startswith(("表内", "表格内容", "单元格")):
                roles = ["table"]
            elif label.startswith("表"):
                roles = ["table_caption"]
            elif label.startswith("图"):
                roles = ["figure_caption"]
            elif label.startswith("参考文献") or region == "reference":
                roles = ["reference"]
            elif label.startswith("全文"):
                roles = ["normal_text", *[f"heading_{n}" for n in range(1, 5)]]
                priority = 1
            elif not label.startswith("摘要") and region != "abstract":
                roles = ["normal_text"]
            for role in roles:
                add(role, fragment, identity, priority)
    result: dict[str, dict] = {}
    for (role, field), items in candidates.items():
        priority = max(item[1] for item in items)
        preferred = [item for item in items if item[1] == priority]
        if len({str(item[0]) for item in preferred}) != 1:
            continue
        rule = result.setdefault(role, {"rule": {}, "fieldEvidence": {}})
        rule["rule"][field] = preferred[0][0]
        rule["fieldEvidence"][field] = list(dict.fromkeys(item[2] for item in preferred))
    return result


def apply_explicit_template_fields(rules: DocumentRules, evidence: dict[str, dict]) -> list[str]:
    notes = []
    for role, item in evidence.items():
        rule = getattr(rules, role, None)
        if not isinstance(rule, ParagraphRule) and role != "page_setup":
            continue
        for field, value in item["rule"].items():
            if hasattr(rule, field):
                setattr(rule, field, value)
        rule.enabled = True
        notes.append(f"已按模板明确文字提取 {role}：" + "、".join(item["rule"]))
    return notes


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

        for name, size in self._font_sizes_longest_first():
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
            for name, size in self._font_sizes_longest_first():
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
            and "五级标题" not in clause
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
        for name, size in cls._font_sizes_longest_first():
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
        elif "左对齐" in text or "居左" in text or "顶格" in text:
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
        for name, size in cls._font_sizes_longest_first():
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
    def _font_sizes_longest_first():
        # “小四号”同时包含“小四”和“四号”；同长度时必须优先“小X”。
        return sorted(
            FONT_SIZE_NAMES.items(),
            key=lambda item: (len(item[0]), item[0].startswith("小")),
            reverse=True,
        )

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
        both_pt = re.search(r"段前段后\s*(\d+(?:\.\d+)?)\s*(?:磅|pt)", text, re.I)
        if before:
            rule.space_before_unit = "line"
            rule.space_before_lines = float(before.group(1))
            notes.append(f"识别{label}段前：{rule.space_before_lines:g}行")
        elif before_pt or both_pt:
            rule.space_before_unit = "pt"
            rule.space_before_pt = float((before_pt or both_pt).group(1))
            notes.append(f"识别{label}段前：{rule.space_before_pt:g}磅")
        if after:
            rule.space_after_unit = "line"
            rule.space_after_lines = float(after.group(1))
            notes.append(f"识别{label}段后：{rule.space_after_lines:g}行")
        elif after_pt or both_pt:
            rule.space_after_unit = "pt"
            rule.space_after_pt = float((after_pt or both_pt).group(1))
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
