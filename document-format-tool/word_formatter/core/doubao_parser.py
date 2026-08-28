from __future__ import annotations

from copy import deepcopy
import json
import os
import re
from typing import Any

from word_formatter.models.rules import DocumentRules


class DoubaoRuleParser:
    """Convert free-form requirements to validated DocumentRules with Doubao."""

    DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
    DEFAULT_MODEL = "doubao-seed-2-0-lite-260215"
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
    def _validated_merge(
        cls, baseline: dict[str, Any], proposed: dict[str, Any]
    ) -> tuple[dict[str, Any], list[str]]:
        result = deepcopy(baseline)
        changed: list[str] = []

        def merge(dst: dict[str, Any], src: dict[str, Any], ref: dict[str, Any], prefix: str) -> None:
            for key, value in src.items():
                if key not in ref:
                    continue
                path = f"{prefix}.{key}" if prefix else key
                expected = ref[key]
                if isinstance(expected, dict):
                    if isinstance(value, dict):
                        merge(dst[key], value, expected, path)
                    continue
                if key in cls.ENUMS and value not in cls.ENUMS[key]:
                    continue
                if isinstance(expected, bool):
                    valid = isinstance(value, bool)
                elif isinstance(expected, (int, float)) and not isinstance(expected, bool):
                    valid = isinstance(value, (int, float)) and not isinstance(value, bool)
                else:
                    valid = isinstance(value, type(expected))
                if valid and value != expected:
                    dst[key] = value
                    changed.append(path)

        merge(result, proposed, baseline, "")
        return result, changed
