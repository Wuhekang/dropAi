import json
from pathlib import Path
import tempfile
import unittest

from format_cli import _apply_confirmed_rules, _editable_rules, _error_code
from word_formatter.core.pdf_template import PdfTemplateError
from word_formatter.models.rules import (
    DocumentRules, enforce_locked_document_policy, normalize_inferred_layout,
)


class ConfirmationLayoutTests(unittest.TestCase):
    def test_pdf_error_code_reaches_backend_boundary(self):
        for code in ("PDF_TEXT_UNAVAILABLE", "PDF_DEPENDENCY_MISSING", "PDF_ENCRYPTED", "PDF_INVALID"):
            self.assertEqual(_error_code(PdfTemplateError("safe message", code)), code)

    def test_incidental_body_indent_bold_and_caption_layout_are_not_global_rules(self):
        rules = DocumentRules()
        rules.normal_text.left_indent_cm = 3.736
        rules.normal_text.bold = True
        rules.normal_text.font_size_pt = 12
        rules.heading_2.left_indent_cm = 4.129
        rules.heading_2.font_size_pt = 14
        rules.figure_caption.left_indent_cm = 8.204
        self.assertTrue(normalize_inferred_layout(rules))
        self.assertEqual(rules.normal_text.left_indent_cm, 0)
        self.assertFalse(rules.normal_text.bold)
        self.assertEqual(rules.heading_2.left_indent_cm, 0)
        self.assertEqual(rules.heading_2.font_size_pt, 14)
        self.assertEqual(rules.figure_caption.left_indent_cm, 0)
        self.assertEqual(rules.table_caption.alignment, "center")

    def test_explicit_prose_layout_is_preserved_field_by_field(self):
        rules = DocumentRules()
        rules.normal_text.left_indent_cm = 1
        rules.normal_text.right_indent_cm = 3
        rules.normal_text.bold = True
        normalize_inferred_layout(rules, {"normal_text": {"rule": {"left_indent_cm": 1, "bold": True}}})
        self.assertEqual(rules.normal_text.left_indent_cm, 1)
        self.assertEqual(rules.normal_text.right_indent_cm, 0)
        self.assertTrue(rules.normal_text.bold)

    def test_customer_can_edit_size_indent_and_table_font_but_not_structural_policy(self):
        rules = DocumentRules()
        normalize_inferred_layout(rules)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "rules.json"
            path.write_text(json.dumps({"body": {"normal": {"fontSizePt": 14, "bold": True, "leftIndentCm": 0.5}},
                                       "details": {"table": {"fontSizePt": 10.5, "chineseFont": "仿宋", "bold": True},
                                                   "reference": {"fontSizePt": 10.5}}}), encoding="utf-8")
            _apply_confirmed_rules(rules, path)
        enforce_locked_document_policy(rules)
        self.assertEqual(rules.normal_text.font_size_pt, 14)
        self.assertTrue(rules.normal_text.bold)
        self.assertEqual(rules.normal_text.left_indent_cm, 0.5)
        self.assertEqual(rules.normal_text.first_line_indent_chars, 2)
        self.assertEqual(rules.table.font_size_pt, 10.5)
        self.assertEqual(rules.table.chinese_font, "仿宋")
        self.assertFalse(rules.table.bold)
        self.assertEqual(rules.reference.font_size_pt, 10.5)
        self.assertEqual(_editable_rules(rules)["details"]["table"]["fontSizeName"], "五号")

    def test_explicit_false_corrects_conflicting_ai_bold(self):
        rules = DocumentRules()
        rules.normal_text.bold = True
        rules.normal_text.left_indent_cm = 3.7
        normalize_inferred_layout(rules, {"normal_text": {"rule": {"bold": False, "left_indent_cm": 0}}})
        self.assertFalse(rules.normal_text.bold)
        self.assertEqual(rules.normal_text.left_indent_cm, 0)

    def test_unchanged_legacy_form_does_not_restore_unsafe_inferred_layout(self):
        rules = DocumentRules()
        rules.normal_text.bold = True
        rules.normal_text.alignment = "left"
        old = rules.to_dict()
        normalize_inferred_layout(rules)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "rules.json"
            path.write_text(json.dumps({"analyzedRules": old, "editableRules": {"body": {"normal": {
                "bold": True, "alignment": "left", "fontSizePt": 14}}}}), encoding="utf-8")
            _apply_confirmed_rules(rules, path)
        self.assertFalse(rules.normal_text.bold)
        self.assertEqual(rules.normal_text.alignment, "justify")
        self.assertEqual(rules.normal_text.font_size_pt, 14)
