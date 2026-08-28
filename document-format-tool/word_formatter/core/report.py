from __future__ import annotations

from html import escape
from pathlib import Path

from word_formatter.core.analyzer import DocumentInfo
from word_formatter.models.results import ProcessResult


def write_html_report(
    path: str | Path, info: DocumentInfo, result: ProcessResult | None = None
) -> Path:
    target = Path(path)
    headings = "".join(
        f"<li>第 {index} 段：{escape(text)}（层级 {level or '特殊部分'}）</li>"
        for index, level, text in info.headings
    ) or "<li>未检测到明确标题</li>"
    uncertain = "".join(
        f"<li>第 {index} 段：{escape(text)}</li>" for index, text in info.uncertain_headings
    ) or "<li>无</li>"
    changed = result.changed_count if result else 0
    warnings = result.warnings if result else ["尚未执行格式修改，仅完成文档分析。"]
    warning_html = "".join(f"<li>{escape(item)}</li>" for item in warnings) or "<li>无</li>"
    captions = "".join(
        f"<li>第 {index} 段：{escape(text)}</li>" for index, text in info.figure_captions
    ) or "<li>未识别到图名</li>"
    html = f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>Word 格式检查报告</title>
<style>body{{font:15px/1.7 'Microsoft YaHei',sans-serif;max-width:960px;margin:32px auto;color:#243047}}
h1,h2{{color:#17365d}}table{{border-collapse:collapse}}td,th{{border:1px solid #ccd5e0;padding:8px 14px}}</style></head>
<body><h1>Word 格式检查报告</h1>
<h2>文档基本信息</h2><table>
<tr><th>文件</th><td>{escape(str(info.path))}</td></tr>
<tr><th>段落</th><td>{info.paragraph_count}（非空 {info.non_empty_paragraph_count}）</td></tr>
<tr><th>表格 / 图片 / 分节</th><td>{info.table_count} / {info.image_count} / {info.section_count}</td></tr>
<tr><th>已修改项目</th><td>{changed}</td></tr></table>
<h2>检测到的标题层级</h2><ul>{headings}</ul>
<h2>未能自动判断的问题</h2><ul>{uncertain}</ul>
<h2>识别到的图名</h2><ul>{captions}</ul>
<h2>处理提示与失败原因</h2><ul>{warning_html}</ul>
<p>页面、正文、表格边框、表内字体字号和段落格式已纳入规则与修改日志；页眉页脚和图片将在后续模块中接入。</p>
</body></html>"""
    target.write_text(html, encoding="utf-8")
    return target
