from __future__ import annotations

from copy import deepcopy
import json
import math
import os
import re
from typing import Any

from word_formatter.models.rules import CHINESE_FONT_SIZES, DocumentRules, font_size_name_for_points
from word_formatter.core.template_ai_pipeline import run_template_ai_pipeline


class DoubaoRuleParser:
    """Convert free-form requirements to validated DocumentRules with Doubao."""

    DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
    DEFAULT_MODEL = "doubao-seed-2-0-lite-260215"
    MAX_TEMPLATE_AI_WORKERS = 32
    TEMPLATE_RULES = {
        "normal_text": ("正文", "本文", "全文", "全篇", "基本格式"),
        "page_setup": ("页面", "纸张", "页边距", "页眉", "页脚", "A4", "版心"),
        "heading_1": ("一级", "一层", "章标题", "章名", "第一级", "1级", "标题"),
        "heading_2": ("二级", "二层", "节标题", "第二级", "2级", "标题"),
        "heading_3": ("三级", "三层", "第三级", "3级", "标题"),
        "heading_4": ("四级", "四层", "第四级", "4级", "标题"),
        "toc_title": ("目录", "目 录", "目　录", "TOC"),
        "toc_1": ("目录", "目 录", "目　录", "TOC"),
        "toc_2": ("目录", "目 录", "目　录", "TOC"),
        "toc_3": ("目录", "目 录", "目　录", "TOC"),
        "figure_caption": ("图名称", "图号", "图题", "图名", "图标题", "题注", "插图"),
        "table_caption": ("表名称", "表号", "表题", "表名", "表标题", "题注", "表格"),
        "reference": ("参考文献", "文献表", "参考资料"),
    }
    ENUMS = {
        "alignment": {"left", "center", "right", "justify"},
        "line_spacing_mode": {"single", "1.5", "double", "at_least", "fixed", "multiple"},
        "character_spacing_mode": {"standard", "expanded", "condensed"},
        "special_indent_mode": {"none", "first_line", "hanging"},
        "space_before_unit": {"line", "pt"},
        "space_after_unit": {"line", "pt"},
        "border_style": {"three_line", "grid", "none"},
        "vertical_alignment": {"top", "center", "bottom"},
        "direction": {"ltr", "rtl"},
    }

    def __init__(self, api_key: str | None = None, model: str | None = None) -> None:
        self.api_key = (
            api_key
            or os.getenv("ARK_API_KEY", "").strip()
            or os.getenv("DOUBAO_API_KEY", "").strip()
        )
        self.model = model or os.getenv("DOUBAO_MODEL", self.DEFAULT_MODEL).strip()
        base_url = os.getenv("DOUBAO_BASE_URL", "").strip()
        endpoint = os.getenv("DOUBAO_ENDPOINT", "").strip()
        if not base_url and endpoint:
            base_url = endpoint.rstrip("/")
            suffix = "/chat/completions"
            if base_url.endswith(suffix):
                base_url = base_url[: -len(suffix)]
        self.base_url = base_url or self.DEFAULT_BASE_URL
        self.last_template_analysis: dict[str, Any] = {}

    def parse(self, requirement: str, current: DocumentRules) -> tuple[DocumentRules, list[str]]:
        if not requirement.strip():
            raise ValueError("请先输入自然语言格式要求。")
        if not self.api_key:
            raise ValueError("未配置豆包 API Key，请先设置环境变量 ARK_API_KEY。")
        try:
            from openai import OpenAI
        except ImportError as exc:
            raise RuntimeError(
                "缺少 openai 依赖，请执行 pip install -r requirements-web.txt。"
            ) from exc

        schema = current.to_dict()
        prompt = (
            "你是 Word 论文格式规则解析器。把用户要求转换为给定 JSON 结构。"
            "必须只输出一个完整 JSON 对象，不要 Markdown，不要解释。"
            "没有明确提到的字段必须保持当前值，不要猜测。"
            "字号同时填写 font_size_name 和 font_size_pt；字号映射："
            "初号42、小初36、一号26、小一24、二号22、小二18、三号16、小三15、"
            "四号14、小四12、五号10.5、小五9、六号7.5、小六6.5、七号5.5、八号5。"
            "对齐仅用 left/center/right/justify；行距仅用 single/1.5/double/"
            "at_least/fixed/multiple；三线表 border_style 用 three_line。\n\n"
            f"当前规则 JSON：\n{json.dumps(schema, ensure_ascii=False)}\n\n"
            f"用户要求：\n{requirement}"
        )
        client = OpenAI(base_url=self.base_url, api_key=self.api_key, timeout=60.0)
        response = client.chat.completions.create(
            model=self.model,
            messages=[{"role": "user", "content": prompt}],
            temperature=0,
        )
        content = response.choices[0].message.content or ""
        proposed = self._extract_json(content)
        merged, changed = self._validated_merge(schema, proposed)
        if not changed:
            raise ValueError("豆包未返回可应用的格式变更，请补充更明确的要求。")
        return DocumentRules.from_dict(merged), [
            f"豆包模型：{self.model}",
            f"已校验并同步 {len(changed)} 个格式字段。",
            *[f"更新：{path}" for path in changed[:20]],
        ]

    def analyze_template(
        self, current: DocumentRules, evidence: list[str], context: dict[str, Any] | None = None
    ) -> tuple[DocumentRules, list[str]]:
        """Read the document's requirements before interpreting its sample formatting.

        Only independently validated, text-backed fields become recognized rules.
        Sample formatting remains available for review but never counts as an AI
        recognition simply because a request completed successfully.
        """
        self.last_template_analysis = {}
        if not self.api_key:
            raise ValueError("未配置豆包 API Key，无法执行模板 AI 分析。")
        try:
            from openai import OpenAI
        except ImportError as exc:
            raise RuntimeError("缺少 openai 依赖，无法执行模板 AI 分析。") from exc
        context = context if isinstance(context, dict) else {}
        blocks = self._text_blocks(context)
        if not blocks:
            raise ValueError("模板未提取到可读文字，不能仅依据显示格式完成 AI 分析。")
        baseline = current.to_dict()
        timeout = self._bounded_env("DOUBAO_FORMAT_AI_TIMEOUT_SECONDS", 45, 8, 60)
        workers = int(self._bounded_env("DOUBAO_FORMAT_AI_CONCURRENCY", 32, 1, 32))
        fast_mode = os.getenv("DOUBAO_FORMAT_AI_FAST_MODE", "true").strip().lower() not in {"0", "false", "off"}

        # The synchronous SDK uses a thread-safe HTTP connection pool. Reuse it
        # for the whole job instead of rebuilding TLS/proxy state per paragraph.
        client, create_completion, initialization_error = None, None, None
        try:
            client = OpenAI(base_url=self.base_url, api_key=self.api_key, timeout=timeout, max_retries=0)
            create_completion = client.chat.completions.create
        except Exception as exc:
            initialization_error = exc

        def request(prompt: str) -> dict[str, Any]:
            if initialization_error is not None:
                raise RuntimeError("无法初始化模板分析连接") from initialization_error
            options: dict[str, Any] = {"max_completion_tokens": 4096}
            if fast_mode:
                options.update(response_format={"type": "json_object"},
                               extra_body={"thinking": {"type": "disabled"}})
            response = create_completion(
                model=self.model, messages=[{"role": "user", "content": prompt}],
                temperature=0, **options,
            )
            return self._extract_json(response.choices[0].message.content or "")

        # Read short paragraphs in parallel; a failed branch cannot erase the
        # other evidence. The reducer can choose facts, never invent them.
        try:
            pipeline = run_template_ai_pipeline(
                request=request, blocks=blocks, baseline=baseline,
                rule_keys=self.TEMPLATE_RULES, enums=self.ENUMS,
                validate_field=self._valid_field, context=context, workers=workers,
                on_progress=getattr(self, "template_progress_callback", None),
            )
        finally:
            # The pipeline joins all map workers before returning or raising.
            # Never close a shared connection while another branch is using it.
            close = getattr(client, "close", None)
            if close:
                close()
        analysis = self._validated_front_decision(context, blocks, pipeline["semantic"])
        analysis["warnings"].extend(pipeline["warnings"])
        if analysis["frontMatterDecisionSource"] == "local_verified":
            analysis["warnings"].append("AI 未完整确认封面用途，已采用程序验证的封面/声明范围，请在确认页核对。")
        analysis["parallelAnalysis"] = {
            "mode": "paragraph_map_reduce", "workers": pipeline["workers"],
            "mapCount": pipeline["map_count"], "completedCount": pipeline["completed_count"],
            "successfulCount": pipeline["successful_count"], "reduceStatus": pipeline["reduce_status"],
            "textChars": pipeline["text_chars"], "mapErrors": pipeline["map_errors"],
            "mapTimingsMs": pipeline["map_timings_ms"], "reduceDurationMs": pipeline["reduce_duration_ms"],
            "reduceError": pipeline["reduce_error"],
        }
        self.last_template_analysis = analysis
        branch_results = pipeline["rule_results"]
        branch_errors = pipeline["rule_errors"]
        selected_blocks = pipeline["blocks"]

        merged = baseline
        changed: list[str] = []
        recognized = 0
        for key, tokens in self.TEMPLATE_RULES.items():
            proposed = branch_results.get(key, {})
            accepted, proofs, rejected = self._validated_rule_fields(
                baseline[key], proposed, selected_blocks, tokens
            )
            if accepted:
                recognized += 1
                accepted["enabled"] = True
                merged, branch_changed = self._validated_merge(merged, {key: accepted})
                changed.extend(branch_changed)
                analysis["ruleEvidence"][key] = {"status": "recognized", "evidence": proofs,
                                                  "fields": sorted(accepted.keys() - {"enabled"})}
            else:
                samples = self._fallback_evidence(key, baseline[key], evidence, blocks)
                status = "sample" if samples and baseline[key].get("enabled") else "unconfirmed"
                analysis["ruleEvidence"][key] = {"status": status, "evidence": samples[:3]}
                explanation = branch_errors.get(key, "没有返回有原文依据的有效字段")
                analysis["warnings"].append(f"{key} 未经 AI 确认：{explanation}；现有值仅供人工核对。")
            if rejected:
                analysis["warnings"].append(f"{key} 已忽略 {rejected} 个缺少证据或无效的字段。")
        supported_fallbacks = sum(item["status"] == "sample" for item in analysis["ruleEvidence"].values())
        analysis["aiStatus"] = "complete" if recognized == len(self.TEMPLATE_RULES) else "partial" if recognized else "unavailable"
        if not recognized and not supported_fallbacks:
            raise RuntimeError("豆包模板分析未识别到任何有原文依据的有效格式字段，请检查模板文字或稍后重试。")
        if not recognized:
            analysis["warnings"].insert(0, "本次 AI 未完成有效识别；已展示程序从规范文字或真实样例提取的格式，请逐项核对后继续。")
        notes = [
            f"模板文字用途分析：{analysis['documentKind']}；{analysis['reason']}",
            f"AI 已识别 {recognized}/{len(self.TEMPLATE_RULES)} 类有原文依据的格式，校正 {len(changed)} 个字段。",
            f"段落分析：{pipeline['completed_count']}/{pipeline['map_count']} 个请求完成，返回 {pipeline['successful_count']} 段结果；并发 {pipeline['workers']}，上限 32；总体整合：{pipeline['reduce_status']}。",
            *analysis["warnings"],
        ]
        return DocumentRules.from_dict(merged), notes

    @classmethod
    def _fallback_evidence(cls, key, rule, notes, blocks) -> list[str]:
        if not rule.get("enabled"):
            return []
        tokens = cls.TEMPLATE_RULES[key]
        # The deterministic written-spec parser explicitly reports successful
        # interpretation. This is useful even when the AI is unavailable, but
        # is always labelled for manual review rather than AI recognition.
        written = any("检测到撰写规范表" in note for note in notes)
        if written:
            matches = [block for block in blocks if any(token in block["text"] for token in tokens)]
            if matches:
                return [f"程序按规范文字提取：{cls._evidence_excerpt(block['text'], tokens)}" for block in matches[:3]]
        aliases = list(tokens)
        heading = re.fullmatch(r"heading_([1-4])", key)
        if heading:
            aliases.extend((f"{heading[1]} 级标题", f"{heading[1]}级标题"))
        return [line[:220] for line in notes
                if line.startswith("已从模板实际") and any(token in line for token in aliases)][:3]

    @staticmethod
    def _evidence_excerpt(text: str, tokens: tuple[str, ...]) -> str:
        for token in tokens:
            offset = text.find(token)
            if offset >= 0:
                start = max(0, offset - 20)
                return ("…" if start else "") + text[start:start + 260]
        return text[:260]

    @staticmethod
    def _bounded_env(name: str, default: float, minimum: float, maximum: float) -> float:
        try:
            value = float(os.getenv(name, str(default)))
            return max(minimum, min(maximum, value)) if math.isfinite(value) else default
        except (ValueError, TypeError):
            return default

    @staticmethod
    def _text_blocks(context: dict[str, Any]) -> list[dict[str, Any]]:
        blocks = []
        seen = set()
        for block in context.get("textBlocks", []):
            if not isinstance(block, dict):
                continue
            identity, text = block.get("id"), block.get("text")
            if not isinstance(identity, str) or not identity or identity in seen or not isinstance(text, str) or not text.strip():
                continue
            seen.add(identity)
            blocks.append({key: block[key] for key in ("id", "kind", "text", "paragraphStart", "paragraphEnd") if key in block})
        return blocks

    @staticmethod
    def _limit_blocks(blocks: list[dict[str, Any]], budget: int) -> list[dict[str, Any]]:
        selected = []
        for block in blocks:
            text = block["text"][:min(4000, budget)]
            if not text:
                break
            selected.append({**block, "text": text})
            budget -= len(text)
            if budget <= 0:
                break
        return selected

    @classmethod
    def _relevant_blocks(cls, blocks: list[dict[str, Any]], tokens: tuple[str, ...]) -> list[dict[str, Any]]:
        scores = [sum(token.casefold() in block["text"].casefold() for token in tokens)
                  + int(any(token in block["text"] for token in ("全文", "全篇", "统一要求"))) for block in blocks]
        for index, block in enumerate(blocks):
            if scores[index] and re.search(r"(?:字号|号字|宋体|黑体|磅|行距|段前|段后|居中|缩进)", block["text"]):
                scores[index] += 3
        indices = set()
        selected_size = 0
        for index in sorted(range(len(blocks)), key=lambda i: -scores[i]):
            if scores[index] > 0:
                neighbors = set(range(max(0, index - 1), min(len(blocks), index + 2))) - indices
                added_size = sum(min(len(blocks[item]["text"]), 4000) for item in neighbors)
                if selected_size + added_size <= 28000:
                    indices.update(neighbors)
                    selected_size += added_size
        # A short specification can place its column labels on a preceding row.
        # Retain document order and neighboring rows so the AI can resolve them.
        selected = [block for i, block in enumerate(blocks) if i in indices] if indices else blocks
        return cls._limit_blocks(selected, 28000)

    @staticmethod
    def _validated_front_decision(context: dict[str, Any], blocks: list[dict[str, Any]], proposed: dict[str, Any]) -> dict[str, Any]:
        by_id = {block["id"]: block for block in blocks}
        ids = proposed.get("evidenceIds", [])
        valid_ids = [identity for identity in ids if isinstance(identity, str) and identity in by_id] if isinstance(ids, list) else []
        kind = proposed.get("documentKind")
        semantic_valid = (kind in {"specification", "template", "mixed"}
                          and bool(valid_ids) and len(valid_ids) == len(ids)
                          and isinstance(proposed.get("copyFrontMatter"), bool))
        if not semantic_valid:
            kind = "unknown"
        hint = context.get("documentKindHint")
        if hint == "specification":
            kind = "specification"
        candidate = context.get("copyCandidate")
        candidate_valid = False
        if isinstance(candidate, dict):
            start, end = candidate.get("startParagraph"), candidate.get("endParagraph")
            evidence_ids = candidate.get("evidenceIds", [])
            last_paragraph = max((block.get("paragraphEnd", 0) for block in blocks
                                  if isinstance(block.get("paragraphEnd"), int)), default=0)
            candidate_valid = (isinstance(start, int) and not isinstance(start, bool) and start >= 1
                               and isinstance(end, int) and not isinstance(end, bool) and end >= start
                               and end <= last_paragraph
                               and isinstance(evidence_ids, list) and bool(evidence_ids)
                               and all(isinstance(identity, str) and identity in by_id
                                       and by_id[identity].get("kind") in {"paragraph", "table"}
                                       and isinstance(by_id[identity].get("paragraphStart"), int)
                                       and isinstance(by_id[identity].get("paragraphEnd"), int)
                                       and start <= by_id[identity]["paragraphStart"]
                                       <= by_id[identity]["paragraphEnd"] <= end
                                       for identity in evidence_ids))
        # A successful requirements paragraph does not decide what a separate
        # cover paragraph means when that paragraph's own request failed.
        if (candidate_valid and hint in {"template", "mixed"} and semantic_valid
                and not set(valid_ids).intersection(candidate["evidenceIds"])):
            semantic_valid = False
        local_fallback = not semantic_valid and candidate_valid and hint in {"template", "mixed"}
        copy_front = (semantic_valid and proposed.get("copyFrontMatter") is True
                      and kind in {"template", "mixed"} and candidate_valid
                      and bool(set(valid_ids) & set(candidate["evidenceIds"]))) or local_fallback
        if local_fallback:
            kind = hint
        reason = proposed.get("reason")
        reason = reason.strip()[:300] if isinstance(reason, str) else ""
        if local_fallback:
            reason = (f"AI 未提供完整的封面判定依据；采用程序已验证的第 {candidate['startParagraph']}–"
                      f"{candidate['endParagraph']} 段封面/声明范围，请核对后继续。")
        elif kind == "specification":
            reason = "该文档为撰写规范，只提取文字中的格式要求，保留论文原封面和声明。"
        elif not copy_front:
            reason = "未确认有可直接复制的封面/声明范围，保留论文原前置页。"
        elif not reason:
            reason = "原文和候选区域共同确认了可复制的封面/声明。"
        return {"documentKind": kind, "copyFrontMatter": copy_front, "reason": reason,
                "frontMatterDecisionSource": "local_verified" if local_fallback else "ai" if semantic_valid else "unconfirmed",
                "frontMatterRange": {"startParagraph": candidate["startParagraph"], "endParagraph": candidate["endParagraph"]} if copy_front else None,
                "ruleEvidence": {}, "warnings": []}

    @classmethod
    def _validated_rule_fields(
        cls, baseline: dict[str, Any], proposed: dict[str, Any], blocks: list[dict[str, Any]], tokens: tuple[str, ...] = ()
    ) -> tuple[dict[str, Any], list[str], int]:
        rule, evidence = proposed.get("rule"), proposed.get("fieldEvidence")
        if not isinstance(rule, dict) or not isinstance(evidence, dict):
            return {}, [], len(rule) if isinstance(rule, dict) else 0
        by_id = {block["id"]: block["text"] for block in blocks}
        accepted, proof_ids, rejected = {}, [], 0
        for key, value in rule.items():
            ids = evidence.get(key)
            valid_ids = [identity for identity in ids if isinstance(identity, str) and identity in by_id] if isinstance(ids, list) else []
            if key == "enabled" or key not in baseline or not valid_ids or len(valid_ids) != len(ids) or not cls._valid_field(key, value, baseline[key]):
                rejected += 1
                continue
            accepted[key] = value
            proof_ids.extend(valid_ids)
        cls._synchronize_font_size(accepted)
        proofs = [f"[{identity}] {cls._evidence_excerpt(by_id[identity], tokens)}" for identity in dict.fromkeys(proof_ids)][:8]
        return accepted, proofs, rejected

    @staticmethod
    def _extract_json(content: str) -> dict[str, Any]:
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip(), flags=re.I)
        try:
            value = json.loads(text)
        except json.JSONDecodeError:
            match = re.search(r"\{.*\}", text, re.S)
            if not match:
                raise ValueError("豆包返回内容中没有有效 JSON。")
            value = json.loads(match.group(0))
        if not isinstance(value, dict):
            raise ValueError("豆包返回的规则必须是 JSON 对象。")
        return value

    @classmethod
    def _valid_field(cls, key: str, value: Any, expected: Any) -> bool:
        if key in cls.ENUMS:
            return isinstance(value, str) and value in cls.ENUMS[key]
        if isinstance(expected, bool):
            return isinstance(value, bool)
        if isinstance(expected, (int, float)):
            if not isinstance(value, (int, float)) or isinstance(value, bool):
                return False
            try:
                if not math.isfinite(value):
                    return False
            except OverflowError:
                return False
            if key == "outline_level":
                return isinstance(value, int) and 0 <= value <= 9
            if key == "font_size_pt":
                return 5 <= value <= 72
            if key in {"width_mm", "height_mm"}:
                return 50 <= value <= 1000
            if key.startswith("margin_") and key.endswith("_mm"):
                return 0 <= value <= 150
            if key in {"fixed_line_spacing_pt", "minimum_line_spacing_pt"}:
                return 1 <= value <= 200
            if key == "multiple_line_spacing":
                return 0.5 <= value <= 10
            if key.endswith("_indent_cm"):
                return -20 <= value <= 20
            if key.endswith("_chars"):
                return -40 <= value <= 40
            if key.endswith("_lines"):
                return 0 <= value <= 20
            if key.endswith("_pt"):
                return 0 <= value <= 200
            if key.endswith("_mm"):
                return 0 <= value <= 1000
            return True
        if isinstance(expected, str):
            if not isinstance(value, str) or not value.strip() or len(value) > 120 or any(ord(char) < 32 for char in value):
                return False
            if key == "font_size_name":
                if value in CHINESE_FONT_SIZES:
                    return True
                match = re.fullmatch(r"(\d+(?:\.\d+)?)\s*(?:磅|pt)", value, re.I)
                return bool(match and 5 <= float(match.group(1)) <= 72)
            if key == "border_color":
                return bool(re.fullmatch(r"[0-9A-Fa-f]{6}", value))
            return True
        return isinstance(value, type(expected))

    @staticmethod
    def _synchronize_font_size(fields: dict[str, Any]) -> None:
        if "font_size_pt" in fields:
            fields["font_size_name"] = font_size_name_for_points(fields["font_size_pt"])
        elif "font_size_name" in fields:
            name = fields["font_size_name"]
            if name in CHINESE_FONT_SIZES:
                fields["font_size_pt"] = CHINESE_FONT_SIZES[name]
            else:
                fields["font_size_pt"] = float(re.match(r"\d+(?:\.\d+)?", name).group(0))

    @classmethod
    def _validated_merge(
        cls, baseline: dict[str, Any], proposed: dict[str, Any]
    ) -> tuple[dict[str, Any], list[str]]:
        result = deepcopy(baseline)
        changed: list[str] = []

        def merge(dst: dict[str, Any], src: dict[str, Any], ref: dict[str, Any], prefix: str) -> None:
            accepted: dict[str, Any] = {}
            for key, value in src.items():
                if key not in ref:
                    continue
                path = f"{prefix}.{key}" if prefix else key
                expected = ref[key]
                if isinstance(expected, dict):
                    if isinstance(value, dict):
                        merge(dst[key], value, expected, path)
                    continue
                if cls._valid_field(key, value, expected):
                    accepted[key] = value
            cls._synchronize_font_size(accepted)
            for key, value in accepted.items():
                if key in ref and value != ref[key]:
                    dst[key] = value
                    changed.append(f"{prefix}.{key}" if prefix else key)

        merge(result, proposed, baseline, "")
        return result, changed
