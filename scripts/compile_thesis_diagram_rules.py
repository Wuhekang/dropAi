#!/usr/bin/env python3
"""Deterministically compile the authority ThesisDiagram DSL v1.6 document."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"diagram-worker"/"ThesisDiagram_DSL.md"
OUT=ROOT/"knowledge"/"thesis-diagram"/"v1.6"
HEADERS={"@FunctionModule":"function_module.md","@Flowchart":"flowchart.md","@ERDiagram":"er_diagram.md","@ArchitectureDiagram":"architecture.md","@UseCaseDiagram":"use_case.md","@BlockDiagram":"block_diagram.md","@SequenceDiagram":"sequence.md"}

def main():
    text=SOURCE.read_text(encoding="utf-8-sig"); OUT.mkdir(parents=True,exist_ok=True)
    (OUT/"common.md").write_text("# ThesisDiagram DSL v1.6 公共规则\n\n唯一权威源：`ThesisDiagram_DSL.md`。第一条有效行必须为七种标准头标记之一；本地解析、校验和布局具有最终裁决权。\n",encoding="utf-8")
    for header,name in HEADERS.items():
        matching=[]; active=False
        for line in text.splitlines():
            if header.lower() in line.lower(): active=True
            elif active and line.startswith("## "): break
            if active: matching.append(line)
        body="\n".join(matching).strip() or text
        (OUT/name).write_text(f"# {header} 规则（v1.6）\n\n{body}\n",encoding="utf-8")
    manifest={"name":"ThesisDiagram DSL","version":"1.6","source":"ThesisDiagram_DSL.md","headers":HEADERS}
    (OUT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
if __name__=="__main__": main()
