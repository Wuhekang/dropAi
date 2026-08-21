"""Generate the seven authoritative template SVGs and a visual QA gallery."""
from __future__ import annotations

import json
from pathlib import Path

from diagram_plugins.legacy import register_plugins
from test_web_engine import HEADERS, TEMPLATES
from web_engine import execute

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "visual-baselines"


def main() -> None:
    register_plugins()
    OUTPUT.mkdir(exist_ok=True)
    cards = []
    manifest = {}
    for kind, template in TEMPLATES.items():
        dsl = HEADERS[kind] + "\n" + (ROOT / template).read_text(encoding="utf-8-sig")
        result = execute({"command": "render", "dsl": dsl, "traceId": f"visual-{kind}"})
        if not result.get("success"):
            raise RuntimeError(f"{kind}: {result.get('issues') or result}")
        svg_path = OUTPUT / f"{kind}.svg"
        svg_path.write_text(result["svg"], encoding="utf-8")
        manifest[kind] = {"svg": svg_path.name, "viewBox": result["svg"].split('viewBox="', 1)[1].split('"', 1)[0], "nodeCount": result["nodeCount"]}
        cards.append(f'<article><h2>{kind}</h2><img src="{svg_path.name}" alt="{kind}"></article>')
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUTPUT / "index.html").write_text("""<!doctype html><meta charset="utf-8"><title>ThesisDiagram visual regression</title>
<style>body{margin:0;padding:24px;background:#eef2f7;font-family:'Microsoft YaHei',sans-serif}main{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}article{background:#fff;border:1px solid #d8e0ea;border-radius:12px;padding:16px;min-height:420px}h2{margin:0 0 12px;font-size:20px}img{width:100%;height:370px;object-fit:contain}</style><main>""" + "".join(cards) + "</main>", encoding="utf-8")
    print(OUTPUT)


if __name__ == "__main__":
    main()
