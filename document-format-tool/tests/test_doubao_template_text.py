from __future__ import annotations

import json
import re
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from word_formatter.core.doubao_parser import DoubaoRuleParser
from word_formatter.models.rules import DocumentRules


class TemplateTextAnalysisTests(unittest.TestCase):
    def setUp(self) -> None:
        self.context = {
            "documentKindHint": "specification",
            "copyCandidate": None,
            "textBlocks": [
                {"id": "b1", "kind": "paragraph", "text": "一级标题使用黑体小二号，居中。",
                 "paragraphStart": 1, "paragraphEnd": 1},
                {"id": "b2", "kind": "paragraph", "text": "正文使用宋体小四号，固定行距22磅。",
                 "paragraphStart": 2, "paragraphEnd": 2},
                {"id": "b3", "kind": "paragraph", "text": "封面示例：学生姓名、专业、指导教师。",
                 "paragraphStart": 3, "paragraphEnd": 3},
            ],
        }
        self.prompts: list[str] = []
        self.calls: list[str] = []

    def run_analysis(self, rules=None, responses=None, semantic=None, context=None, evidence=None):
        responses = responses or {}
        semantic = semantic if semantic is not None else {
            "documentKind": "specification", "copyFrontMatter": False,
            "reason": "这是格式规范说明", "evidenceIds": ["b1"],
        }

        def create(**kwargs):
            prompt = kwargs["messages"][0]["content"]
            self.prompts.append(prompt)
            if "任务：template_semantics。" in prompt:
                self.calls.append("semantic")
                result = semantic
            else:
                key = re.search(r"任务：template_rule:([a-z_0-9]+)", prompt).group(1)
                self.calls.append(key)
                result = responses.get(key, {"rule": {}, "fieldEvidence": {}})
            if isinstance(result, Exception):
                raise result
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=json.dumps(result, ensure_ascii=False)))])

        def fake_client(**kwargs):
            return SimpleNamespace(chat=SimpleNamespace(completions=SimpleNamespace(create=create)))

        parser = DoubaoRuleParser(api_key="fake-test-key")
        with patch.dict(sys.modules, {"openai": SimpleNamespace(OpenAI=fake_client)}):
            result, notes = parser.analyze_template(
                rules or DocumentRules(),
                evidence if evidence is not None else ["已从模板实际1 级标题提取：宋体12磅。"],
                context if context is not None else self.context,
            )
        return result, notes, parser.last_template_analysis

    def test_reads_text_first_and_overrides_appearance_including_body(self):
        rules = DocumentRules()
        rules.heading_1.font_size_pt = 12
        rules.heading_1.chinese_font = "宋体"
        result, _, analysis = self.run_analysis(rules, {
            "heading_1": {"rule": {"font_size_pt": 18, "chinese_font": "黑体", "alignment": "center"},
                          "fieldEvidence": {"font_size_pt": ["b1"], "chinese_font": ["b1"], "alignment": ["b1"]}},
            "normal_text": {"rule": {"font_size_pt": 12, "line_spacing_mode": "fixed", "fixed_line_spacing_pt": 22},
                            "fieldEvidence": {"font_size_pt": ["b2"], "line_spacing_mode": ["b2"], "fixed_line_spacing_pt": ["b2"]}},
        })
        self.assertEqual(self.calls[0], "semantic")
        self.assertEqual(len(self.calls), 14)
        self.assertEqual(result.heading_1.font_size_pt, 18)
        self.assertEqual(result.heading_1.font_size_name, "小二")
        self.assertEqual(result.heading_1.chinese_font, "黑体")
        self.assertTrue(result.heading_1.enabled)
        self.assertEqual(result.normal_text.font_size_name, "小四")
        self.assertEqual(result.normal_text.line_spacing_mode, "fixed")
        self.assertEqual(result.normal_text.fixed_line_spacing_pt, 22)
        self.assertEqual(analysis["ruleEvidence"]["normal_text"]["status"], "recognized")
        heading_prompt = next(prompt for prompt in self.prompts if "template_rule:heading_1" in prompt)
        self.assertIn("一级标题使用黑体小二号", heading_prompt)
        self.assertIn("书面要求优先于显示格式", heading_prompt)

    def test_unchanged_supported_value_enables_rule(self):
        rules = DocumentRules()
        rules.heading_1.font_size_pt = 18
        rules.heading_1.font_size_name = "小二"
        rules.heading_1.enabled = False
        result, _, analysis = self.run_analysis(rules, {
            "heading_1": {"rule": {"font_size_name": "小二"}, "fieldEvidence": {"font_size_name": ["b1"]}},
        })
        self.assertTrue(result.heading_1.enabled)
        self.assertEqual(analysis["ruleEvidence"]["heading_1"]["status"], "recognized")

    def test_empty_response_is_not_reported_as_success(self):
        with self.assertRaisesRegex(RuntimeError, "未识别到任何"):
            self.run_analysis(responses={key: {} for key in DoubaoRuleParser.TEMPLATE_RULES})

    def test_ai_timeout_preserves_real_style_samples_for_manual_confirmation(self):
        rules = DocumentRules()
        rules.heading_1.enabled = True
        rules.heading_1.font_size_pt = 18
        rules.heading_1.font_size_name = '小二'
        result, _, analysis = self.run_analysis(
            rules=rules, semantic=TimeoutError(),
            responses={key: TimeoutError() for key in DoubaoRuleParser.TEMPLATE_RULES},
            evidence=['已从模板实际1 级标题段落提取黑体小二。'])
        self.assertEqual(result.heading_1.font_size_pt, 18)
        self.assertEqual(analysis['aiStatus'], 'unavailable')
        self.assertEqual(analysis['ruleEvidence']['heading_1']['status'], 'sample')
        self.assertFalse(analysis['copyFrontMatter'])
        self.assertIn('请逐项核对', analysis['warnings'][0])

    def test_ai_timeout_preserves_explicit_written_rules_without_claiming_ai_success(self):
        rules = DocumentRules()
        rules.normal_text.line_spacing_mode = 'fixed'
        rules.normal_text.fixed_line_spacing_pt = 22
        result, _, analysis = self.run_analysis(
            rules=rules, semantic=TimeoutError(),
            responses={key: TimeoutError() for key in DoubaoRuleParser.TEMPLATE_RULES},
            evidence=['检测到撰写规范表：已直接提取正文规则。'])
        self.assertEqual(result.normal_text.fixed_line_spacing_pt, 22)
        self.assertEqual(analysis['ruleEvidence']['normal_text']['status'], 'sample')
        self.assertEqual(analysis['aiStatus'], 'unavailable')
        self.assertTrue(any('固定行距22磅' in item for item in analysis['ruleEvidence']['normal_text']['evidence']))

    def test_unknown_evidence_and_invalid_numeric_fields_are_discarded(self):
        result, _, analysis = self.run_analysis(responses={
            "heading_1": {"rule": {"font_size_pt": 18}, "fieldEvidence": {"font_size_pt": ["invented"]}},
            "normal_text": {"rule": {"font_size_pt": -12, "fixed_line_spacing_pt": float("nan"),
                                     "multiple_line_spacing": 999, "alignment": ["center"], "chinese_font": "宋体"},
                            "fieldEvidence": {key: ["b2"] for key in ("font_size_pt", "fixed_line_spacing_pt", "multiple_line_spacing", "alignment", "chinese_font")}},
        })
        self.assertEqual(result.heading_1.font_size_pt, 16)
        self.assertEqual(analysis["ruleEvidence"]["heading_1"]["status"], "unconfirmed")
        self.assertEqual(result.normal_text.font_size_pt, 12)
        self.assertEqual(result.normal_text.fixed_line_spacing_pt, 20)
        self.assertEqual(result.normal_text.multiple_line_spacing, 1.25)
        self.assertEqual(result.normal_text.alignment, "justify")
        self.assertTrue(any("忽略 4" in warning for warning in analysis["warnings"]))

    def test_specification_never_copies_front_even_when_ai_requests_it(self):
        context = {**self.context, "copyCandidate": {"startParagraph": 3, "endParagraph": 3, "evidenceIds": ["b3"]}}
        _, _, analysis = self.run_analysis(
            responses={"normal_text": {"rule": {"font_size_pt": 12}, "fieldEvidence": {"font_size_pt": ["b2"]}}},
            semantic={"documentKind": "template", "copyFrontMatter": True, "reason": "可复制", "evidenceIds": ["b3"]},
            context=context,
        )
        self.assertEqual(analysis["documentKind"], "specification")
        self.assertFalse(analysis["copyFrontMatter"])
        self.assertIsNone(analysis["frontMatterRange"])

    def test_copy_requires_local_candidate_and_matching_evidence(self):
        context = {**self.context, "documentKindHint": "template"}
        semantic = {"documentKind": "template", "copyFrontMatter": True, "reason": "存在填写式封面", "evidenceIds": ["b3"]}
        responses = {"normal_text": {"rule": {"font_size_pt": 12}, "fieldEvidence": {"font_size_pt": ["b2"]}}}
        _, _, missing = self.run_analysis(responses=responses, semantic=semantic, context=context)
        self.assertFalse(missing["copyFrontMatter"])
        context["copyCandidate"] = {"startParagraph": 3, "endParagraph": 3, "evidenceIds": ["b3"]}
        _, _, found = self.run_analysis(responses=responses, semantic=semantic, context=context)
        self.assertTrue(found["copyFrontMatter"])
        self.assertEqual(found["frontMatterRange"], {"startParagraph": 3, "endParagraph": 3})

    def test_semantic_failure_preserves_front_and_keeps_valid_rules(self):
        result, _, analysis = self.run_analysis(
            responses={"normal_text": {"rule": {"font_size_pt": 12}, "fieldEvidence": {"font_size_pt": ["b2"]}}},
            semantic=TimeoutError("test timeout"),
        )
        self.assertEqual(result.normal_text.font_size_pt, 12)
        self.assertFalse(analysis["copyFrontMatter"])
        self.assertTrue(any("TimeoutError" in message for message in analysis["warnings"]))

    def test_no_text_rejects_notes_only_analysis(self):
        with self.assertRaisesRegex(ValueError, "不能仅依据显示格式"):
            self.run_analysis(context={})

    def test_general_merge_rejects_nonfinite_and_keeps_size_fields_in_sync(self):
        baseline = DocumentRules().to_dict()
        merged, _ = DoubaoRuleParser._validated_merge(baseline, {
            "normal_text": {"font_size_pt": 18, "font_size_name": "小四", "alignment": [], "multiple_line_spacing": float("inf")},
            "page_setup": {"width_mm": -10},
        })
        self.assertEqual(merged["normal_text"]["font_size_name"], "小二")
        self.assertEqual(merged["normal_text"]["alignment"], "justify")
        self.assertEqual(merged["normal_text"]["multiple_line_spacing"], 1.25)
        self.assertEqual(merged["page_setup"]["width_mm"], 210)


if __name__ == "__main__":
    unittest.main()
