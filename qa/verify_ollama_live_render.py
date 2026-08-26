from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "diagram-worker" / "web_engine.py"
DSL_DIR = ROOT / "backend" / "target" / "ollama-live"


def invoke(payload: dict) -> dict:
    environment = os.environ.copy(); environment["PYTHONUTF8"] = "1"
    process = subprocess.run(
        [sys.executable, str(ENGINE)],
        input=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=60,
        check=True,
        env=environment,
    )
    result = json.loads(process.stdout.decode("utf-8"))
    if not result.get("success"):
        raise RuntimeError(result.get("error") or json.dumps(result.get("issues", []), ensure_ascii=False))
    return result


def main() -> None:
    cases = []
    for path in sorted(DSL_DIR.glob("*.dsl")):
        dsl = path.read_text(encoding="utf-8")
        rendered = invoke({"command": "render", "dsl": dsl, "traceId": "ollama-live-" + path.stem})
        svg = rendered["svg"]
        if "<svg" not in svg or rendered.get("nodeCount", 0) < 1:
            raise RuntimeError(f"invalid SVG for {path.name}")
        svg_path = path.with_suffix(".svg")
        svg_path.write_text(svg, encoding="utf-8")
        png = invoke({"command": "export", "format": "png", "dsl": dsl, "traceId": "ollama-png-" + path.stem})
        png_path = path.with_suffix(".png")
        png_path.write_bytes(base64.b64decode(png["data"]))
        cases.append({
            "type": path.stem,
            "valid": rendered["valid"],
            "nodeCount": rendered["nodeCount"],
            "width": rendered["width"],
            "height": rendered["height"],
            "svg": str(svg_path),
            "png": str(png_path),
        })
    if len(cases) != 7:
        raise RuntimeError(f"expected 7 DSL files, found {len(cases)}")
    report = DSL_DIR / "render-report.json"
    report.write_text(json.dumps({"count": len(cases), "passed": len(cases), "cases": cases}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
