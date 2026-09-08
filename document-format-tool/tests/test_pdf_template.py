from __future__ import annotations

from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch

from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from word_formatter.core.template_text import read_template_text
from word_formatter.core.template_extractor import TemplateRuleExtractor
from word_formatter.core.pdf_template import PdfTemplateError


class PdfTemplateTests(unittest.TestCase):
    def make_pdf(self, path, text=True, encrypted=False):
        writer = PdfWriter()
        page = writer.add_blank_page(width=595, height=842)
        if text:
            font = DictionaryObject({NameObject("/Type"): NameObject("/Font"),
                                     NameObject("/Subtype"): NameObject("/Type1"),
                                     NameObject("/BaseFont"): NameObject("/Helvetica")})
            page[NameObject("/Resources")] = DictionaryObject({NameObject("/Font"): DictionaryObject({NameObject("/F1"): font})})
            stream = DecodedStreamObject()
            stream.set_data(b"BT /F1 12 Tf 40 750 Td (Readable specification text with enough characters.) Tj ET")
            page[NameObject("/Contents")] = writer._add_object(stream)
        if encrypted:
            writer.encrypt("password")
        with path.open("wb") as output:
            writer.write(output)

    def test_text_pdf_is_specification_and_never_copy_candidate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "spec.pdf"
            self.make_pdf(path)
            result = read_template_text(path)
            self.assertEqual(result["documentKindHint"], "specification")
            self.assertEqual(result["sourceFormat"], "pdf")
            self.assertIsNone(result["copyCandidate"])
            self.assertEqual(result["textBlocks"][0]["pageNumber"], 1)
            self.assertTrue(result["textBlocks"][0]["id"])
            self.assertEqual(TemplateRuleExtractor().extract(path).rules.normal_text.font_size_pt, 12)

    def test_image_only_or_empty_pdf_fails_clearly(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "scan.pdf"
            self.make_pdf(path, text=False)
            with self.assertRaisesRegex(PdfTemplateError, "扫描件") as caught:
                read_template_text(path)
            self.assertEqual(caught.exception.code, "PDF_TEXT_UNAVAILABLE")

    def test_encrypted_pdf_fails_clearly(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "encrypted.pdf"
            self.make_pdf(path, encrypted=True)
            with self.assertRaisesRegex(PdfTemplateError, "加密") as caught:
                read_template_text(path)
            self.assertEqual(caught.exception.code, "PDF_ENCRYPTED")

    def test_broken_pdf_fails_clearly(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "broken.pdf"
            path.write_bytes(b"not a PDF")
            with self.assertRaisesRegex(PdfTemplateError, "损坏|解析") as caught:
                read_template_text(path)
            self.assertEqual(caught.exception.code, "PDF_INVALID")

    def test_missing_dependency_has_safe_stable_error_code(self):
        with patch.dict(sys.modules, {"pypdf": None}):
            with self.assertRaisesRegex(PdfTemplateError, "pypdf") as caught:
                read_template_text("template.pdf")
        self.assertEqual(caught.exception.code, "PDF_DEPENDENCY_MISSING")

    def test_page_budget_reports_omitted_pages_instead_of_silent_success(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "long.pdf"
            self.make_pdf(path)
            writer = PdfWriter()
            writer.append(path)
            writer.add_blank_page(width=595, height=842)
            with path.open("wb") as stream:
                writer.write(stream)
            with patch("word_formatter.core.pdf_template.MAX_PDF_PAGES", 1):
                result = read_template_text(path)
            self.assertTrue(any("其余页未分析" in note for note in result["notes"]))


if __name__ == "__main__":
    unittest.main()
