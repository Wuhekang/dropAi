#!/usr/bin/env sh
set -eu

PYTHON_BIN="${CAD_WORKER_PYTHON:-/opt/dropai-cad-venv/bin/python}"
WORKER_SCRIPT="${CAD_WORKER_SCRIPT:-/app/cad_worker/cad_worker.py}"

echo "[render-cad-preflight] java"
java -version

echo "[render-cad-preflight] python"
test -x "$PYTHON_BIN"
"$PYTHON_BIN" --version
"$PYTHON_BIN" -m pip --version

echo "[render-cad-preflight] worker"
test -f "$WORKER_SCRIPT"

echo "[render-cad-preflight] cadquery import"
"$PYTHON_BIN" -c "import sys; import cadquery as cq; print('PYTHON_EXECUTABLE=' + sys.executable); print('CADQUERY_OK=' + cq.__version__)"

echo "[render-cad-preflight] worker health"
"$PYTHON_BIN" "$WORKER_SCRIPT" --health

echo "[render-cad-preflight] minimal step export"
TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

cat > "$TMP_DIR/input.json" <<'JSON'
{
  "assemblyModel": {
    "projectName": "DropAI CAD Preflight",
    "components": [
      {"id":"base","name":"base plate","type":"plate","position":{"x":0,"y":0,"z":0},"size":{"length":120,"width":80,"height":12}},
      {"id":"shaft","name":"stepped shaft","type":"shaft","position":{"x":160,"y":0,"z":20},"size":{"length":100,"width":24,"height":24}},
      {"id":"bracket","name":"support bracket","type":"bracket","position":{"x":0,"y":120,"z":0},"size":{"length":80,"width":50,"height":40}},
      {"id":"wheel","name":"drive wheel","type":"wheel","position":{"x":160,"y":120,"z":20},"size":{"length":30,"width":48,"height":48}},
      {"id":"cover","name":"cover plate","type":"cover","position":{"x":0,"y":0,"z":60},"size":{"length":100,"width":60,"height":8}}
    ],
    "constraints": [
      {"type":"fixed","componentA":"base","componentB":"world"},
      {"type":"distance","componentA":"shaft","componentB":"base"},
      {"type":"distance","componentA":"bracket","componentB":"base"},
      {"type":"concentric","componentA":"wheel","componentB":"shaft"},
      {"type":"coincident","componentA":"cover","componentB":"base"}
    ]
  }
}
JSON

mkdir -p "$TMP_DIR/out"
"$PYTHON_BIN" "$WORKER_SCRIPT" "$TMP_DIR/input.json" "$TMP_DIR/out"
test -s "$TMP_DIR/out/assembly.step"
test -s "$TMP_DIR/out/part_01.step"
test -s "$TMP_DIR/out/assembly-validation.json"

echo "[render-cad-preflight] OK"
