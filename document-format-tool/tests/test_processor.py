from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest

from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn


TOOL_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_ROOT))

from word_formatter.core.processor import DocumentProcessor  # noqa: E402
from word_formatter.models.rules import DocumentRules  # noqa: E402


class DocumentProcessorTableTests(unittest.TestCase):
    def test_equation_layout_table_is_preserved_while_data_table_is_formatted(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dokiai_processor_") as directory:
            root = Path(directory)
            source = root / "source.docx"
            output = root / "output.docx"

            document = Document()
            equation_table = document.add_table(rows=1, cols=3)
            equation_paragraph = equation_table.cell(0, 1).paragraphs[0]
            equation_paragraph._p.append(OxmlElement("m:oMath"))
            equation_table.cell(0, 2).text = "(1-1)"

            data_table = document.add_table(rows=2, cols=2)
            data_table.cell(0, 0).text = "名称"
            data_table.cell(0, 1).text = "数值"
            data_table.cell(1, 0).text = "速度"
            data_table.cell(1, 1).text = "10"
            document.save(source)

            rules = DocumentRules()
            rules.page_setup.enabled = False
            rules.normal_text.enabled = False
            rules.table.border_style = "grid"
            rules.table.repeat_header_row = True

            result = DocumentProcessor().process(source, rules, output)

            formatted = Document(output)
            formatted_equation = formatted.tables[0]
            formatted_data = formatted.tables[1]

            self.assertTrue(
                DocumentProcessor._is_equation_layout_table(formatted_equation)
            )
            self.assertIsNone(
                formatted_equation._tbl.tblPr.find(qn("w:tblBorders"))
            )
            self.assertFalse(
                formatted_equation._tbl.xpath(".//w:trPr/w:tblHeader")
            )
            self.assertIsNotNone(formatted_data._tbl.tblPr.find(qn("w:tblBorders")))
            self.assertTrue(formatted_data._tbl.xpath(".//w:trPr/w:tblHeader"))
            self.assertIn(
                "已识别并保留 1 个公式排版表，未套用普通数据表样式。",
                result.warnings,
            )


if __name__ == "__main__":
    unittest.main()
