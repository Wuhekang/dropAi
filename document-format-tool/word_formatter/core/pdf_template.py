"""Text-only PDF specification input; never a source of copied Word pages."""
from __future__ import annotations

from pathlib import Path
import re


MAX_PDF_PAGES = 120
MAX_READ_CHARACTERS = 240_000


class PdfTemplateError(ValueError):
    """Safe public PDF diagnostic with a stable transport error code."""

    def __init__(self, message: str, code: str) -> None:
        super().__init__(message)
        self.code = code


def read_pdf_template_text(path: str | Path) -> dict:
    try:
        from pypdf import PdfReader
        from pypdf.errors import PdfReadError
    except ImportError as exc:
        raise PdfTemplateError("读取 PDF 规范需要 pypdf，请安装 requirements-web.txt 中的依赖。", "PDF_DEPENDENCY_MISSING") from exc
    source = Path(path)
    if not source.is_file():
        raise PdfTemplateError("PDF 模板文件不存在。", "PDF_INVALID")
    blocks, notes, body_characters = [], [], 0
    try:
        with source.open("rb") as stream:
            reader = PdfReader(stream)
            if reader.is_encrypted:
                raise PdfTemplateError("暂不支持加密 PDF 规范，请先解除加密或上传可读的 Word 模板。", "PDF_ENCRYPTED")
            for page_number, page in enumerate(reader.pages, 1):
                if page_number > MAX_PDF_PAGES or body_characters >= MAX_READ_CHARACTERS:
                    notes.append(f"PDF 超过文字读取预算，已读取前 {page_number - 1} 页，其余页未分析；请核对或拆分规范。")
                    break
                text = (page.extract_text() or "").strip()
                if text:
                    body_characters += len(re.sub(r"\s+", "", text))
                    lines = [re.sub(r"\s+", "", line) for line in text.splitlines() if line.strip()]
                    # Only a standalone early page heading identifies a special
                    # region. A prose mention of references/contents cannot do so.
                    region = "main"
                    for line in lines[:6]:
                        if line in {"目录", "Contents"}:
                            region = "toc"
                            break
                        if line == "参考文献":
                            region = "reference"
                            break
                        if line in {"摘要", "ABSTRACT", "Abstract"}:
                            region = "abstract"
                            break
                    blocks.append({"id": f"pdf-p{page_number}", "kind": "paragraph", "text": text,
                                   "paragraphStart": page_number, "paragraphEnd": page_number,
                                   "pageNumber": page_number, "semanticRegion": region})
                for index, annotation_ref in enumerate(page.get("/Annots", []), 1):
                    annotation = annotation_ref.get_object()
                    comment = str(annotation.get("/Contents", "")).strip()
                    if comment:
                        blocks.append({"id": f"pdf-p{page_number}-a{index}", "kind": "comment", "text": comment,
                                       "paragraphStart": page_number, "paragraphEnd": page_number,
                                       "pageNumber": page_number, "semanticRegion": "annotation"})
    except (PdfReadError, KeyError, TypeError, NotImplementedError) as exc:
        raise PdfTemplateError("PDF 规范损坏或无法解析，请重新导出 PDF 或上传 Word 模板。", "PDF_INVALID") from exc
    if body_characters < 20:
        raise PdfTemplateError("PDF 未提取到可用文字，可能是扫描件；当前只支持文本型 PDF，请上传带文字层的 PDF 或 Word 模板。", "PDF_TEXT_UNAVAILABLE")
    # Shared limits preserve requirement-containing blocks first and explicitly
    # report omissions. Imported lazily to avoid the input-dispatch cycle.
    from .template_text import _bound_blocks
    blocks = _bound_blocks(blocks, set(), notes)
    notes.append("PDF 仅作为文字格式规范读取；页内示例、红框与批注仅供核对，绝不转换或复制为论文封面。")
    return {"textBlocks": blocks, "documentKindHint": "specification", "copyCandidate": None,
            "sourceFormat": "pdf", "notes": notes}
