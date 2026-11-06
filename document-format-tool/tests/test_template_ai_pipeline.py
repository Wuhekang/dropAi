from __future__ import annotations

import json
import threading
import time
import unittest

from word_formatter.core.doubao_parser import DoubaoRuleParser
from word_formatter.core.template_ai_pipeline import prepare_text_batches, run_template_ai_pipeline
from word_formatter.models.rules import DocumentRules


class TemplateAiPipelineTests(unittest.TestCase):
    def setUp(self):
        self.baseline = DocumentRules().to_dict()
        self.keys = DoubaoRuleParser.TEMPLATE_RULES
        self.blocks = [
            {"id": "b1", "kind": "paragraph", "text": "一级标题黑体小二。" + "其他说明文字。" * 24, "paragraphStart": 1, "paragraphEnd": 1},
            {"id": "b2", "kind": "paragraph", "text": "正文宋体小四，固定行距22磅。" + "其他说明文字。" * 24, "paragraphStart": 2, "paragraphEnd": 2},
        ]

    def run_pipeline(self, request, **kwargs):
        return run_template_ai_pipeline(request=request, blocks=kwargs.pop("blocks", self.blocks),
            baseline=self.baseline, rule_keys=self.keys, enums=DoubaoRuleParser.ENUMS,
            validate_field=DoubaoRuleParser._valid_field, **kwargs)

    @staticmethod
    def map_input(prompt):
        return json.loads(prompt.split("\n输入：", 1)[1])

    @staticmethod
    def fact(rule, field, value, identity, scope="specific"):
        return {"rules": {rule: {"rule": {field: value}, "fieldEvidence": {field: [identity]}, "fieldScope": {field: scope}}}}

    def test_consistent_paragraphs_merge_locally_with_overall_progress(self):
        calls, progress, caller = [], [], threading.get_ident()

        def request(prompt):
            calls.append(prompt)
            self.assertNotIn("template_paragraph_reduce", prompt)
            block = self.map_input(prompt)["textBlocks"][0]
            return self.fact("heading_1" if block["id"] == "b1" else "normal_text", "font_size_pt", 18 if block["id"] == "b1" else 12, block["id"])

        def on_progress(event):
            self.assertEqual(threading.get_ident(), caller)
            progress.append(event)

        result = self.run_pipeline(request, on_progress=on_progress)
        self.assertEqual(len(calls), 2)
        self.assertEqual(result["map_count"], 2)
        self.assertEqual(result["completed_count"], 2)
        self.assertEqual(result["rule_results"]["heading_1"]["rule"]["font_size_pt"], 18)
        self.assertEqual(result["rule_results"]["normal_text"]["fieldEvidence"]["font_size_pt"], ["b2"])
        self.assertEqual(result["reduce_status"], "local_complete")
        self.assertEqual([event["completed"] for event in progress if event["stage"] == "map"], [0, 1, 2])
        self.assertEqual(progress[-1]["stage"], "reduce")
        self.assertIn("本地整合", progress[-1]["message"])

    def test_long_table_splitting_preserves_all_text_offsets_and_ids(self):
        text = ("列名 | 一级标题 | 黑体小二\n" * 300) + "尾行"
        original, batches, truncated = prepare_text_batches([{"id": "table1", "kind": "table", "text": text}])
        parts = [part for batch in batches for part in batch]
        self.assertFalse(truncated)
        self.assertGreater(len(parts), 1)
        self.assertEqual("".join(part["text"] for part in parts), text)
        self.assertTrue(all(part["id"] == "table1" and len(part["text"]) <= 1200 for part in parts))
        self.assertEqual(parts[0]["startOffset"], 0)
        self.assertEqual(parts[-1]["endOffset"], len(text))
        self.assertEqual(original[0]["text"], text)

    def test_many_short_paragraphs_have_64_request_cap_and_no_loss(self):
        blocks = [{"id": f"b{index}", "text": f"段落{index}"} for index in range(500)]
        original, batches, truncated = prepare_text_batches(blocks)
        self.assertEqual(len(batches), 64)
        self.assertFalse(truncated)
        self.assertEqual([part["id"] for batch in batches for part in batch], [block["id"] for block in blocks])
        self.assertEqual(len(original), 500)

    def test_short_neighboring_blocks_share_small_batches_without_crossing_front_boundary(self):
        blocks = [{"id": f"b{index}", "kind": "paragraph", "text": f"第{index}项：请按规范填写。",
                   "paragraphStart": index + 1, "paragraphEnd": index + 1} for index in range(45)]
        # A cover ends in the middle of a potential four-paragraph batch.
        context = {"copyCandidate": {"startParagraph": 1, "endParagraph": 7, "evidenceIds": ["b0"]}}
        original, batches, truncated = prepare_text_batches(blocks, context=context)
        self.assertFalse(truncated)
        self.assertEqual(len(batches), 12)
        self.assertEqual([part["id"] for batch in batches for part in batch], [block["id"] for block in original])
        for batch in batches:
            self.assertLessEqual(len(batch), 4)
            self.assertLessEqual(sum(len(part["text"]) for part in batch), 160)
            self.assertEqual(len({part["region"] for part in batch}), 1)
            self.assertTrue(all(part["startOffset"] == 0 and part["endOffset"] == len(part["text"]) for part in batch))
        self.assertEqual(batches[1][-1]["id"], "b6")
        self.assertEqual(batches[2][0]["id"], "b7")

    def test_small_batch_preserves_adjacent_label_context_and_all_fields_locally(self):
        blocks = [{"id": f"b{index}", "kind": "paragraph", "text": text} for index, text in enumerate(
            ["一级标题", "使用黑体小二号", "正文", "宋体小四，行距固定22磅"]
        )]
        calls = []

        def request(prompt):
            calls.append(prompt)
            self.assertNotIn("template_paragraph_reduce", prompt)
            data = self.map_input(prompt)
            self.assertEqual([part["id"] for part in data["textBlocks"]], ["b0", "b1", "b2", "b3"])
            return {"rules": {
                "heading_1": {"rule": {"font_size_pt": 18, "chinese_font": "黑体"},
                              "fieldEvidence": {"font_size_pt": ["b0", "b1"], "chinese_font": ["b0", "b1"]}},
                "normal_text": {"rule": {"font_size_pt": 12, "fixed_line_spacing_pt": 22},
                                "fieldEvidence": {"font_size_pt": ["b2", "b3"], "fixed_line_spacing_pt": ["b2", "b3"]}},
            }}
        result = self.run_pipeline(request, blocks=blocks)
        self.assertEqual(len(calls), 1)
        self.assertEqual(result["map_count"], 1)
        self.assertEqual(result["reduce_status"], "local_complete")
        self.assertEqual(result["rule_results"]["heading_1"]["rule"], {"chinese_font": "黑体", "font_size_pt": 18})
        self.assertEqual(result["rule_results"]["normal_text"]["rule"], {"font_size_pt": 12, "fixed_line_spacing_pt": 22})
        self.assertEqual(result["rule_results"]["normal_text"]["fieldEvidence"]["font_size_pt"], ["b2", "b3"])

    def test_small_batch_char_limit_and_long_tail_or_comment_separation(self):
        blocks = [{"id": "a", "text": "甲" * 90}, {"id": "b", "text": "乙" * 90},
                  {"id": "long", "text": "丙" * 1201}, {"id": "c", "text": "下一段"},
                  {"id": "comment", "kind": "comment", "text": "批注中的格式要求"},
                  {"id": "d", "text": "正文样例"}]
        _, batches, _ = prepare_text_batches(blocks)
        self.assertEqual([[part["id"] for part in batch] for batch in batches],
                         [["a"], ["b"], ["long"], ["long"], ["c"], ["comment"], ["d"]])

    def test_total_text_budget_and_concurrency_are_bounded(self):
        blocks = [{"id": f"b{index}", "text": "字" * 800} for index in range(100)]
        active, peak = 0, 0
        guard = threading.Lock()

        def request(prompt):
            nonlocal active, peak
            with guard:
                active += 1
                peak = max(peak, active)
            try:
                time.sleep(0.003)
                return {}
            finally:
                with guard:
                    active -= 1

        result = self.run_pipeline(request, blocks=blocks, workers=999)
        self.assertLessEqual(peak, 32)
        self.assertGreater(peak, 1)
        self.assertEqual(result["text_chars"], 48000)
        self.assertEqual(result["map_count"], 60)
        self.assertEqual(result["reduce_status"], "skipped")
        self.assertTrue(any("48000" in warning for warning in result["warnings"]))

    def test_failed_paragraph_does_not_prevent_local_merge_of_remaining_fields(self):
        progress = []

        def request(prompt):
            self.assertNotIn("template_paragraph_reduce", prompt)
            block = self.map_input(prompt)["textBlocks"][0]
            if block["id"] == "b1":
                raise TimeoutError("one paragraph timed out")
            return self.fact("normal_text", "font_size_pt", 12, "b2")

        result = self.run_pipeline(request, on_progress=progress.append)
        self.assertEqual(result["completed_count"], 2)
        self.assertEqual(result["map_errors"], {"0": "TimeoutError"})
        self.assertEqual(result["reduce_status"], "local_complete")
        self.assertEqual(result["rule_results"]["normal_text"]["rule"]["font_size_pt"], 12)
        final_map = [event for event in progress if event["stage"] == "map"][-1]
        self.assertEqual(final_map["message"], "已完成 2/2 段分析请求（返回 1 段结果）")
        self.assertNotIn("已识别", final_map["message"])
        self.assertEqual(set(result["map_timings_ms"]), {"0", "1"})
        self.assertTrue(all(value >= 0 for value in result["map_timings_ms"].values()))
        self.assertGreaterEqual(result["reduce_duration_ms"], 0)

    def test_error_diagnostics_keep_cause_types_without_exception_details(self):
        class ConnectTimeout(Exception):
            pass

        class APITimeoutError(Exception):
            pass

        def request(prompt):
            if "template_paragraph_reduce" not in prompt:
                identity = self.map_input(prompt)["textBlocks"][0]["id"]
                if identity == "b2":
                    return self.fact("normal_text", "font_size_pt", 12, identity)
            try:
                raise ConnectTimeout("https://private.example/secret-token")
            except ConnectTimeout as exc:
                raise APITimeoutError("credential=never-include-this") from exc

        result = self.run_pipeline(request)
        self.assertEqual(result["map_errors"]["0"], "APITimeoutError <- ConnectTimeout")
        self.assertIsNone(result["reduce_error"])
        self.assertNotIn("secret-token", json.dumps(result))
        self.assertNotIn("never-include-this", json.dumps(result))
        self.assertEqual(result["rule_results"]["normal_text"]["rule"]["font_size_pt"], 12)

    def test_reducer_cannot_invent_values_or_evidence(self):
        def request(prompt):
            if "template_paragraph_reduce" in prompt:
                return {"factIds": ["invented", {"value": 72}],
                        "fieldChoices": [{"rule": "heading_1", "field": "font_size_pt", "factId": "invented", "value": 72}],
                        "semanticChoices": ["invented"], "frontMatterRange": {"startParagraph": 1, "endParagraph": 9999}}
            block = self.map_input(prompt)["textBlocks"][0]
            return self.fact("heading_1", "font_size_pt", 18 if block["id"] == "b1" else 12, block["id"])
        result = self.run_pipeline(request)
        self.assertNotIn("heading_1", result["rule_results"])
        self.assertEqual(result["reduce_status"], "unconfirmed")
        self.assertNotIn("frontMatterRange", result["semantic"])

    def test_unseen_evidence_and_invalid_values_rejected_before_reduce(self):
        def request(prompt):
            self.assertNotIn("template_paragraph_reduce", prompt)
            block = self.map_input(prompt)["textBlocks"][0]
            if block["id"] == "b1":
                # b2 exists globally but was not shown to this paragraph request.
                return self.fact("heading_1", "font_size_pt", 18, "b2")
            return self.fact("normal_text", "font_size_pt", float("nan"), "b2")
        result = self.run_pipeline(request)
        self.assertEqual(result["rule_results"], {})
        self.assertEqual(result["reduce_status"], "skipped")
        self.assertTrue(any("忽略 2" in warning for warning in result["warnings"]))

    def test_specific_rule_beats_global_without_extra_ai_request(self):
        def request(prompt):
            self.assertNotIn("template_paragraph_reduce", prompt)
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            return self.fact("heading_1", "font_size_pt", 18 if identity == "b1" else 12, identity, "specific" if identity == "b1" else "global")
        result = self.run_pipeline(request)
        self.assertEqual(result["rule_results"]["heading_1"]["rule"]["font_size_pt"], 18)
        self.assertEqual(result["reduce_status"], "local_complete")

    def test_specific_font_size_alias_beats_global_alias_and_retains_proof(self):
        for specific_field, specific_value, global_field, global_value in (
            ("font_size_name", "小二", "font_size_pt", 12),
            ("font_size_pt", 18, "font_size_name", "小四"),
        ):
            with self.subTest(specific_field=specific_field):
                def request(prompt):
                    self.assertNotIn("template_paragraph_reduce", prompt)
                    identity = self.map_input(prompt)["textBlocks"][0]["id"]
                    if identity == "b1":
                        return self.fact("heading_1", specific_field, specific_value, identity, "specific")
                    return self.fact("heading_1", global_field, global_value, identity, "global")

                result = self.run_pipeline(request)
                selected = result["rule_results"]["heading_1"]
                self.assertEqual(selected["rule"], {"font_size_pt": 18})
                self.assertEqual(selected["fieldEvidence"], {"font_size_pt": ["b1"]})
                self.assertEqual(result["reduce_status"], "local_complete")
                accepted, _, rejected = DoubaoRuleParser._validated_rule_fields(
                    self.baseline["heading_1"], selected, result["blocks"])
                self.assertEqual(accepted["font_size_name"], "小二")
                self.assertEqual(accepted["font_size_pt"], 18)
                self.assertEqual(rejected, 0)

    def test_equivalent_font_size_names_and_points_deduplicate(self):
        for name in ("小二", "18磅", "18.0 pt"):
            with self.subTest(name=name):
                def request(prompt):
                    self.assertNotIn("template_paragraph_reduce", prompt)
                    identity = self.map_input(prompt)["textBlocks"][0]["id"]
                    return self.fact("heading_1", "font_size_name" if identity == "b1" else "font_size_pt",
                                     name if identity == "b1" else 18, identity)

                result = self.run_pipeline(request)
                selected = result["rule_results"]["heading_1"]
                self.assertEqual(selected["rule"], {"font_size_pt": 18})
                self.assertEqual(selected["fieldEvidence"]["font_size_pt"], ["b1", "b2"])
                self.assertEqual(result["reduce_status"], "local_complete")

    def test_same_response_conflicting_size_aliases_require_valid_resolution(self):
        for resolve in (False, True):
            with self.subTest(resolve=resolve):
                reduce_calls = []

                def request(prompt):
                    if "template_paragraph_reduce" in prompt:
                        facts = json.loads(prompt.split("\n已验证事实：", 1)[1])["facts"]
                        reduce_calls.append(facts)
                        self.assertEqual({item["field"] for item in facts}, {"font_size_pt"})
                        self.assertEqual({item["value"] for item in facts}, {12, 18})
                        self.assertTrue(all(item["scope"] == "specific" and item["evidenceIds"] == ["b1"] for item in facts))
                        return {"factIds": [next(item["id"] for item in facts if item["value"] == 18)]} if resolve else {}
                    return {"rules": {"heading_1": {
                        "rule": {"font_size_name": "小二", "font_size_pt": 12},
                        "fieldEvidence": {"font_size_name": ["b1"], "font_size_pt": ["b1"]},
                    }}}

                result = self.run_pipeline(request, blocks=self.blocks[:1])
                self.assertEqual(len(reduce_calls), 1)
                if resolve:
                    self.assertEqual(result["rule_results"]["heading_1"]["rule"], {"font_size_pt": 18})
                    self.assertEqual(result["reduce_status"], "complete")
                else:
                    self.assertNotIn("heading_1", result["rule_results"])
                    self.assertEqual(result["reduce_status"], "unconfirmed")
                    self.assertTrue(any("冲突" in warning for warning in result["warnings"]))

    def test_conflicts_remain_unconfirmed_when_reduce_fails(self):
        def request(prompt):
            if "template_paragraph_reduce" in prompt:
                raise TimeoutError()
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            return self.fact("heading_1", "font_size_pt", 18 if identity == "b1" else 12, identity)
        result = self.run_pipeline(request)
        self.assertNotIn("heading_1", result["rule_results"])
        self.assertTrue(any("冲突" in warning for warning in result["warnings"]))

    def test_only_conflicting_fields_reach_reducer_and_consistent_fields_cannot_change(self):
        for protocol in ("compact", "legacy"):
            with self.subTest(protocol=protocol):
                calls = []

                def request(prompt):
                    calls.append(prompt)
                    if "template_paragraph_reduce" in prompt:
                        self.assertIn('"factIds":["f0"]', prompt)
                        facts = json.loads(prompt.split("\n已验证事实：", 1)[1])["facts"]
                        self.assertEqual({(item["rule"], item["field"]) for item in facts}, {("heading_1", "font_size_pt")})
                        fact = next(item for item in facts if item["value"] == 18)
                        if protocol == "compact":
                            return {"factIds": [fact["id"]], "rules": {"normal_text": {"font_size_pt": 72}}}
                        return {"fieldChoices": [
                            {"rule": "heading_1", "field": "font_size_pt", "factId": fact["id"]},
                            {"rule": "normal_text", "field": "font_size_pt", "factId": fact["id"]},
                        ]}
                    identity = self.map_input(prompt)["textBlocks"][0]["id"]
                    result = self.fact("heading_1", "font_size_pt", 18 if identity == "b1" else 12, identity)
                    result["rules"].update(self.fact("normal_text", "font_size_pt", 12, identity)["rules"])
                    return result

                result = self.run_pipeline(request)
                self.assertEqual(len(calls), 3)
                self.assertEqual(result["reduce_status"], "complete")
                self.assertEqual(result["rule_results"]["heading_1"]["rule"]["font_size_pt"], 18)
                self.assertEqual(result["rule_results"]["normal_text"]["rule"]["font_size_pt"], 12)
                self.assertEqual(result["rule_results"]["normal_text"]["fieldEvidence"]["font_size_pt"], ["b1", "b2"])

    def test_failed_map_and_conflict_reduce_preserve_consistent_fields_and_error_types(self):
        class ReadTimeout(Exception):
            pass

        class APITimeoutError(Exception):
            pass

        blocks = [*self.blocks, {"id": "b3", "text": "附加说明"}]

        def request(prompt):
            if "template_paragraph_reduce" in prompt:
                try:
                    raise ReadTimeout("https://private.example/secret-token")
                except ReadTimeout as exc:
                    raise APITimeoutError("credential=private") from exc
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            if identity == "b3":
                raise TimeoutError("map timed out")
            result = self.fact("heading_1", "font_size_pt", 18 if identity == "b1" else 12, identity)
            result["rules"].update(self.fact("normal_text", "font_size_pt", 12, identity)["rules"])
            return result

        result = self.run_pipeline(request, blocks=blocks)
        self.assertEqual(result["map_errors"], {"2": "TimeoutError"})
        self.assertEqual(result["reduce_status"], "failed")
        self.assertEqual(result["reduce_error"], "APITimeoutError <- ReadTimeout")
        self.assertNotIn("heading_1", result["rule_results"])
        self.assertEqual(result["rule_results"]["normal_text"]["rule"]["font_size_pt"], 12)
        self.assertEqual(result["rule_results"]["normal_text"]["fieldEvidence"]["font_size_pt"], ["b1", "b2"])
        self.assertNotIn("private.example", json.dumps(result))
        self.assertNotIn("credential", json.dumps(result))
        self.assertGreaterEqual(result["reduce_duration_ms"], 0)

    def test_template_and_specification_sections_combine_locally_as_mixed(self):
        calls = []
        context = {"copyCandidate": {"startParagraph": 1, "endParagraph": 1, "evidenceIds": ["b1"]}}

        def request(prompt):
            calls.append(prompt)
            self.assertNotIn("template_paragraph_reduce", prompt)
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            return {"semantic": {"documentKind": "template" if identity == "b1" else "specification",
                                 "copyFrontMatter": identity == "b1", "reason": "该段用途明确", "evidenceIds": [identity]}}
        result = self.run_pipeline(request, context=context)
        self.assertEqual(len(calls), 2)
        self.assertEqual(result["semantic"]["documentKind"], "mixed")
        self.assertTrue(result["semantic"]["copyFrontMatter"])
        self.assertEqual(result["reduce_status"], "local_complete")

    def test_compact_reduce_cannot_resolve_conflicts_by_selecting_both_values(self):
        def request(prompt):
            if "template_paragraph_reduce" in prompt:
                facts = json.loads(prompt.split("\n已验证事实：", 1)[1])["facts"]
                return {"factIds": [fact["id"] for fact in facts]}
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            return self.fact("heading_1", "font_size_pt", 18 if identity == "b1" else 12, identity)
        result = self.run_pipeline(request)
        self.assertNotIn("heading_1", result["rule_results"])
        self.assertEqual(result["reduce_status"], "unconfirmed")
        self.assertTrue(any("冲突" in warning for warning in result["warnings"]))

    def test_pure_specification_cannot_copy_despite_map_and_reduce(self):
        context = {"documentKindHint": "specification", "copyCandidate": {"startParagraph": 1, "endParagraph": 1, "evidenceIds": ["b1"]}}

        def request(prompt):
            if "template_paragraph_reduce" in prompt:
                return {"semanticChoices": ["s0"]}
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            return {"semantic": {"documentKind": "template", "copyFrontMatter": True, "reason": "看起来像封面", "evidenceIds": [identity]}}
        result = self.run_pipeline(request, context=context)
        self.assertFalse(result["semantic"]["copyFrontMatter"])
        self.assertEqual(result["semantic"]["documentKind"], "specification")

    def test_copy_requires_existing_candidate_matching_map_evidence(self):
        def request(prompt):
            if "template_paragraph_reduce" in prompt:
                raise TimeoutError()
            identity = self.map_input(prompt)["textBlocks"][0]["id"]
            return {"semantic": {"documentKind": "template", "copyFrontMatter": True, "reason": "填写式封面", "evidenceIds": [identity]}}
        result = self.run_pipeline(request)
        self.assertFalse(result["semantic"]["copyFrontMatter"])
        context = {"copyCandidate": {"startParagraph": 1, "endParagraph": 1, "evidenceIds": ["b1"]}}
        found = self.run_pipeline(request, context=context)
        self.assertTrue(found["semantic"]["copyFrontMatter"])
        self.assertNotIn("frontMatterRange", found["semantic"])


if __name__ == "__main__":
    unittest.main()
