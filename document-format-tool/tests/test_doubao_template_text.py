from __future__ import annotations

import json
import os
import sys
import threading
import time
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
        self.pipeline_calls: list[dict] = []

    def run_analysis(self, rules=None, responses=None, semantic=None, context=None, evidence=None):
        responses = responses or {}
        semantic = semantic if semantic is not None else {
            "documentKind": "specification", "copyFrontMatter": False,
            "reason": "这是格式规范说明", "evidenceIds": ["b1"],
        }

        def fake_pipeline(**kwargs):
            self.pipeline_calls.append(kwargs)
            errors = {key: type(value).__name__ for key, value in responses.items() if isinstance(value, Exception)}
            semantic_failed = isinstance(semantic, Exception)
            map_count = len(kwargs["blocks"])
            return {
                "semantic": {} if semantic_failed else semantic,
                "rule_results": {key: value for key, value in responses.items() if not isinstance(value, Exception)},
                "rule_errors": errors, "map_errors": {"0": type(semantic).__name__} if semantic_failed else {},
                "warnings": [f"段落 AI 分析未完成（{type(semantic).__name__}）"] if semantic_failed else [],
                "map_count": map_count, "completed_count": map_count,
                "successful_count": 0 if errors else map_count, "reduce_status": "failed" if semantic_failed else "complete",
                "workers": min(kwargs["workers"], map_count), "blocks": kwargs["blocks"],
                "text_chars": sum(len(block["text"]) for block in kwargs["blocks"]),
                "map_timings_ms": {}, "reduce_duration_ms": 0, "reduce_error": type(semantic).__name__ if semantic_failed else None,
            }

        parser = DoubaoRuleParser(api_key="fake-test-key")
        with patch.dict(sys.modules, {"openai": SimpleNamespace(OpenAI=lambda **kwargs: SimpleNamespace(
                chat=SimpleNamespace(completions=SimpleNamespace(create=lambda **options: self.fail("unit test must use mocked pipeline")))))}), \
                patch("word_formatter.core.doubao_parser.run_template_ai_pipeline", side_effect=fake_pipeline):
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
        self.assertEqual(len(self.pipeline_calls), 1)
        self.assertIn("一级标题使用黑体小二号", self.pipeline_calls[0]["blocks"][0]["text"])
        self.assertEqual(result.heading_1.font_size_pt, 18)
        self.assertEqual(result.heading_1.font_size_name, "小二")
        self.assertEqual(result.heading_1.chinese_font, "黑体")
        self.assertTrue(result.heading_1.enabled)
        self.assertEqual(result.normal_text.font_size_name, "小四")
        self.assertEqual(result.normal_text.line_spacing_mode, "fixed")
        self.assertEqual(result.normal_text.fixed_line_spacing_pt, 22)
        self.assertEqual(analysis["ruleEvidence"]["normal_text"]["status"], "recognized")
        self.assertEqual(analysis["parallelAnalysis"]["mapCount"], 3)

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

    def test_semantic_timeout_keeps_locally_verified_cover(self):
        context = {**self.context, "documentKindHint": "mixed",
                   "copyCandidate": {"startParagraph": 3, "endParagraph": 3, "evidenceIds": ["b3"]}}
        _, _, analysis = self.run_analysis(
            semantic=TimeoutError(), context=context,
            responses={key: TimeoutError() for key in DoubaoRuleParser.TEMPLATE_RULES},
            evidence=['检测到撰写规范表：已直接提取正文规则。'])
        self.assertTrue(analysis["copyFrontMatter"])
        self.assertEqual(analysis["frontMatterRange"], {"startParagraph": 3, "endParagraph": 3})
        self.assertEqual(analysis["frontMatterDecisionSource"], "local_verified")
        self.assertEqual(analysis["aiStatus"], "unavailable")
        self.assertNotIn("未确认有可直接复制", analysis["reason"])
        self.assertTrue(any("已采用程序验证" in item for item in analysis["warnings"]))

    def test_explicit_evidenced_no_copy_is_not_overridden(self):
        context = {**self.context, "documentKindHint": "mixed",
                   "copyCandidate": {"startParagraph": 3, "endParagraph": 3, "evidenceIds": ["b3"]}}
        decision = DoubaoRuleParser._validated_front_decision(context, context["textBlocks"], {
            "documentKind": "mixed", "copyFrontMatter": False, "evidenceIds": ["b3"], "reason": "封面仅为示意"})
        self.assertFalse(decision["copyFrontMatter"])

    def test_non_cover_specification_evidence_cannot_cancel_verified_cover(self):
        context = {**self.context, "documentKindHint": "mixed",
                   "copyCandidate": {"startParagraph": 3, "endParagraph": 3, "evidenceIds": ["b3"]}}
        decision = DoubaoRuleParser._validated_front_decision(context, context["textBlocks"], {
            "documentKind": "specification", "copyFrontMatter": False,
            "evidenceIds": ["b1"], "reason": "这一段是在说明一级标题要求"})
        self.assertTrue(decision["copyFrontMatter"])
        self.assertEqual(decision["frontMatterRange"], {"startParagraph": 3, "endParagraph": 3})
        self.assertEqual(decision["frontMatterDecisionSource"], "local_verified")

    def test_local_cover_fallback_rejects_unbounded_and_annotation_candidates(self):
        for candidate in (
            {"startParagraph": 1, "endParagraph": 999, "evidenceIds": ["b3"]},
            {"startParagraph": 1, "endParagraph": 2, "evidenceIds": ["b3"]},
            {"startParagraph": 1, "endParagraph": 3, "evidenceIds": ["missing"]},
        ):
            context = {**self.context, "documentKindHint": "template", "copyCandidate": candidate}
            self.assertFalse(DoubaoRuleParser._validated_front_decision(context, context["textBlocks"], {})["copyFrontMatter"])
        context = {**self.context, "documentKindHint": "template",
                   "copyCandidate": {"startParagraph": 3, "endParagraph": 3, "evidenceIds": ["b3"]}}
        blocks = [*context["textBlocks"][:2], {**context["textBlocks"][2], "kind": "comment"}]
        self.assertFalse(DoubaoRuleParser._validated_front_decision(context, blocks, {})["copyFrontMatter"])

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

    def exercise_real_pipeline_with_fake_openai(self, fast_mode=True, block_count=40, fail_map=False, fail_reduce=False, conflicting=True):
        requests, constructors, progress = [], [], []
        active, peak, closed = 0, 0, 0
        lock = threading.Lock()
        context = {"documentKindHint": "specification", "copyCandidate": None, "textBlocks": [
            {"id": f"b{index}", "kind": "paragraph", "paragraphStart": index + 1, "paragraphEnd": index + 1,
             "text": "一级标题使用黑体小二号。" if index == 0 else
                     "正文采用宋体四号。" if conflicting and 8 <= index < 12 else "正文采用宋体小四号。"}
            for index in range(block_count)
        ]}

        def create(**kwargs):
            nonlocal active, peak
            prompt = kwargs["messages"][0]["content"]
            with lock:
                self.assertEqual(closed, 0, "shared client was closed too early")
                requests.append(kwargs)
                active += 1
                peak = max(peak, active)
            try:
                if "任务：template_paragraph_reduce" in prompt:
                    if fail_reduce:
                        raise TimeoutError("simulated reducer timeout")
                    facts = json.loads(prompt.split("\n已验证事实：", 1)[1])["facts"]
                    chosen = {}
                    for fact in facts:
                        chosen.setdefault((fact["rule"], fact["field"]), fact["id"])
                    response = {"factIds": list(chosen.values())}
                else:
                    data = json.loads(prompt.split("\n输入：", 1)[1])
                    if fail_map and data["batchIndex"] == 1:
                        raise TimeoutError("simulated branch timeout")
                    response = {"rules": {}}
                    for block in data["textBlocks"]:
                        key = "heading_1" if block["id"] == "b0" else "normal_text"
                        value = 18 if key == "heading_1" else (14 if "正文采用宋体四号" in block["text"] else 12)
                        response["rules"][key] = {
                            "rule": {"font_size_pt": value},
                            "fieldEvidence": {"font_size_pt": [block["id"]]},
                        }
                    time.sleep(0.003)
                return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=json.dumps(response)))])
            finally:
                with lock:
                    active -= 1

        def close():
            nonlocal closed
            with lock:
                self.assertEqual(active, 0, "client cannot close during an active request")
                closed += 1

        def client(**kwargs):
            with lock:
                constructors.append(kwargs)
            return SimpleNamespace(chat=SimpleNamespace(completions=SimpleNamespace(create=create)), close=close)

        parser = DoubaoRuleParser(api_key="fake-test-key")
        parser.template_progress_callback = progress.append
        with patch.dict(sys.modules, {"openai": SimpleNamespace(OpenAI=client)}), patch.dict(os.environ, {
            "DOUBAO_FORMAT_AI_CONCURRENCY": "999", "DOUBAO_FORMAT_AI_FAST_MODE": "true" if fast_mode else "false",
        }):
            rules, _ = parser.analyze_template(DocumentRules(), [], context)
        return rules, parser.last_template_analysis, requests, constructors, progress, peak, closed

    def test_real_pipeline_maps_then_reduces_with_progress_and_fast_request_options(self):
        rules, analysis, requests, constructors, progress, peak, closed = self.exercise_real_pipeline_with_fake_openai()
        self.assertEqual(rules.heading_1.font_size_pt, 18)
        self.assertEqual(rules.heading_1.font_size_name, "小二")
        self.assertEqual(rules.normal_text.font_size_pt, 12)
        self.assertLess(len(requests), 41)
        self.assertGreater(len(requests), 2)
        self.assertTrue(all("template_paragraph_map" in call["messages"][0]["content"] for call in requests[:-1]))
        self.assertIn("template_paragraph_reduce", requests[-1]["messages"][0]["content"])
        self.assertNotIn("正文采用宋体", requests[-1]["messages"][0]["content"])
        self.assertGreater(peak, 1)
        self.assertLessEqual(peak, 32)
        self.assertEqual(closed, len(constructors))
        self.assertEqual(closed, 1)
        for request in requests:
            self.assertEqual(request["max_completion_tokens"], 4096)
            self.assertEqual(request["response_format"], {"type": "json_object"})
            self.assertEqual(request["extra_body"], {"thinking": {"type": "disabled"}})
        self.assertTrue(all(options["max_retries"] == 0 for options in constructors))
        stats = analysis["parallelAnalysis"]
        self.assertEqual(stats["mapCount"], len(requests) - 1)
        self.assertEqual(stats["completedCount"], stats["mapCount"])
        self.assertEqual(stats["workers"], min(32, stats["mapCount"]))
        self.assertEqual(stats["reduceStatus"], "complete")
        self.assertEqual([event["completed"] for event in progress if event["stage"] == "map"], list(range(stats["mapCount"] + 1)))
        self.assertEqual(progress[-1]["stage"], "reduce")

    def test_fast_request_options_can_be_disabled_without_disabling_output_budget(self):
        _, _, requests, _, _, _, closed = self.exercise_real_pipeline_with_fake_openai(fast_mode=False, block_count=1)
        self.assertEqual(len(requests), 1)
        self.assertEqual(closed, 1)
        for request in requests:
            self.assertNotIn("response_format", request)
            self.assertNotIn("extra_body", request)
            self.assertEqual(request["max_completion_tokens"], 4096)

    def test_consistent_rules_finish_without_an_extra_ai_request(self):
        rules, analysis, requests, constructors, progress, _, closed = self.exercise_real_pipeline_with_fake_openai(conflicting=False)
        stats = analysis["parallelAnalysis"]
        self.assertEqual(stats["reduceStatus"], "local_complete")
        self.assertEqual(len(requests), stats["mapCount"])
        self.assertTrue(all("template_paragraph_map" in call["messages"][0]["content"] for call in requests))
        self.assertEqual(len(constructors), 1)
        self.assertEqual(closed, 1)
        self.assertEqual(progress[-1]["stage"], "reduce")
        self.assertEqual(rules.heading_1.font_size_pt, 18)
        self.assertEqual(rules.normal_text.font_size_pt, 12)

    def test_shared_client_closes_once_when_pipeline_raises(self):
        closed = []
        client = SimpleNamespace(close=lambda: closed.append(True))
        with patch.dict(sys.modules, {"openai": SimpleNamespace(OpenAI=lambda **kwargs: client)}), \
                patch("word_formatter.core.doubao_parser.run_template_ai_pipeline", side_effect=RuntimeError("test")):
            with self.assertRaisesRegex(RuntimeError, "test"):
                DoubaoRuleParser(api_key="test-key").analyze_template(DocumentRules(), [], self.context)
        self.assertEqual(closed, [True])

    def test_client_initialization_failure_still_allows_local_rule_fallback(self):
        def fail_client(**kwargs):
            raise ValueError("simulated client configuration failure")
        with patch.dict(sys.modules, {"openai": SimpleNamespace(OpenAI=fail_client)}):
            parser = DoubaoRuleParser(api_key="test-key")
            _, _ = parser.analyze_template(DocumentRules(), ["检测到撰写规范表：正文宋体小四"], self.context)
        self.assertEqual(parser.last_template_analysis["aiStatus"], "unavailable")
        self.assertEqual(parser.last_template_analysis["ruleEvidence"]["normal_text"]["status"], "sample")
        self.assertTrue(parser.last_template_analysis["parallelAnalysis"]["mapErrors"])

    def test_partial_timeouts_do_not_close_the_shared_client_early(self):
        for fail_reduce in (False, True):
            with self.subTest(fail_reduce=fail_reduce):
                rules, analysis, requests, constructors, _, _, closed = self.exercise_real_pipeline_with_fake_openai(
                    fail_map=True, fail_reduce=fail_reduce)
                self.assertEqual(len(constructors), 1)
                self.assertEqual(closed, 1)
                self.assertEqual(rules.heading_1.font_size_pt, 18)
                self.assertEqual(rules.normal_text.font_size_pt, 12)
                self.assertTrue(analysis["parallelAnalysis"]["mapErrors"])
                self.assertIn("template_paragraph_reduce", requests[-1]["messages"][0]["content"])
                self.assertEqual(analysis["parallelAnalysis"]["reduceStatus"], "failed" if fail_reduce else "complete")


if __name__ == "__main__":
    unittest.main()
