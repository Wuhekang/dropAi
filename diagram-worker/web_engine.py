#!/usr/bin/env python3
"""JSON/stdin web adapter for all seven authoritative ThesisDiagram v1.6 plugins."""
from __future__ import annotations

import base64
import dataclasses
import html
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from diagram_core.registry import CANONICAL, DISPLAY, PLUGIN_REGISTRY, detect_header
from diagram_plugins.legacy import register_plugins

MAX_DSL = 100_000

class SvgCanvas:
    def __init__(self):
        self.items = []
        self.boxes = {}
        self.seq = 0
        self.min_x = self.min_y = float("inf")
        self.max_x = self.max_y = float("-inf")

    def delete(self, _item):
        self.items.clear(); self.boxes.clear()

    def configure(self, **_options):
        return None

    def _id(self, coords):
        self.seq += 1
        xs, ys = coords[::2], coords[1::2]
        if xs:
            self.min_x, self.max_x = min(self.min_x, *xs), max(self.max_x, *xs)
            self.min_y, self.max_y = min(self.min_y, *ys), max(self.max_y, *ys)
            self.boxes[self.seq] = (min(xs), min(ys), max(xs), max(ys))
        return self.seq

    @staticmethod
    def _style(options, default_fill="none"):
        fill = options.get("fill", default_fill)
        stroke = options.get("outline", options.get("fill", "#1f2937"))
        width = options.get("width", 1.4)
        dash = options.get("dash")
        return f'fill="{html.escape(str(fill))}" stroke="{html.escape(str(stroke))}" stroke-width="{width}"' + (f' stroke-dasharray="{",".join(map(str,dash))}"' if dash else "")

    def create_line(self, *coords, **options):
        item = self._id(coords)
        points = " ".join(f"{coords[i]},{coords[i+1]}" for i in range(0, len(coords), 2))
        marker_start = ' marker-start="url(#arrow)"' if options.get("arrow") == "both" else ""
        marker_end = ' marker-end="url(#arrow)"' if options.get("arrow") in ("last", "both") else ""
        self.items.append(f'<polyline points="{points}" {self._style(options)}{marker_start}{marker_end}/>' )
        return item

    def create_rectangle(self, x1, y1, x2, y2, **options):
        item = self._id((x1,y1,x2,y2)); self.items.append(f'<rect x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" rx="3" {self._style(options,"white")}/>'); return item

    def create_oval(self, x1, y1, x2, y2, **options):
        item = self._id((x1,y1,x2,y2)); self.items.append(f'<ellipse cx="{(x1+x2)/2}" cy="{(y1+y2)/2}" rx="{(x2-x1)/2}" ry="{(y2-y1)/2}" {self._style(options,"white")}/>'); return item

    def create_polygon(self, *coords, **options):
        item = self._id(coords); points=" ".join(f"{coords[i]},{coords[i+1]}" for i in range(0,len(coords),2)); self.items.append(f'<polygon points="{points}" {self._style(options,"white")}/>'); return item

    def create_text(self, x, y, **options):
        text = str(options.get("text", "")); width = float(options.get("width", 0) or 0)
        lines = text.splitlines() or [""]
        if width and len(text) * 14 > width:
            count=max(1,int(width/14)); lines=[text[i:i+count] for i in range(0,len(text),count)]
        anchor={"w":"start","e":"end"}.get(options.get("anchor"),"middle")
        size=(options.get("font") or ("",10))[-1]
        item=self._id((x-len(text)*size*.3,y-size,x+len(text)*size*.3,y+size))
        tspans="".join(f'<tspan x="{x}" dy="{0 if i==0 else size+3}">{html.escape(line)}</tspan>' for i,line in enumerate(lines))
        self.items.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" dominant-baseline="middle" font-family="Noto Sans CJK SC,Microsoft YaHei,sans-serif" font-size="{size}">{tspans}</text>')
        return item

    def bbox(self, item): return self.boxes.get(item, (0,0,0,0))

    def svg(self):
        if self.min_x == float("inf"): self.min_x=self.min_y=0; self.max_x=self.max_y=100
        pad=45; x=self.min_x-pad; y=self.min_y-pad; w=max(100,self.max_x-self.min_x+2*pad); h=max(100,self.max_y-self.min_y+2*pad)
        defs='<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#1f2937"/></marker></defs>'
        return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {y} {w} {h}" width="{w}" height="{h}"><rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#FEFEFE"/>{defs}{"".join(self.items)}</svg>', {"width":round(w),"height":round(h)}

def serial(value):
    if dataclasses.is_dataclass(value): return {f.name:serial(getattr(value,f.name)) for f in dataclasses.fields(value)}
    if isinstance(value, dict): return {str(k):serial(v) for k,v in value.items()}
    if isinstance(value, (list,tuple,set)): return [serial(v) for v in value]
    return value

def issue_dict(issue):
    severity={"错误":"error","警告":"warning","提示":"info"}.get(issue.severity,issue.severity.lower())
    code = "FLOW_UNREACHABLE_NODE" if issue.code == "FLOW_UNREACHABLE" else issue.code
    return {"line":issue.line_number,"severity":severity,"code":code,"message":issue.message,"sourceLine":issue.source_line,"suggestion":issue.suggestion}

def execute(payload):
    dsl=payload.get("dsl","")
    if not isinstance(dsl,str) or not dsl.strip(): raise ValueError("DSL不能为空")
    if len(dsl)>MAX_DSL: raise ValueError("DSL长度不能超过100000字符")
    header=detect_header(dsl)
    if header.issue: return {"valid":False,"diagramType":None,"title":"","issues":[issue_dict(header.issue)]}
    plugin=PLUGIN_REGISTRY[header.diagram_type]
    document,issues=plugin.parse(header.body_text)
    result={"valid":not any(i.severity=="错误" for i in issues),"diagramType":header.diagram_type.value,"displayName":DISPLAY[header.diagram_type],"header":CANONICAL[header.diagram_type],"title":plugin.title(document) if document else "","issues":[issue_dict(i) for i in issues]}
    if payload.get("command","validate") == "validate" or not result["valid"]: return result
    layout=plugin.layout(document); canvas=SvgCanvas(); plugin.draw_canvas(canvas,document,layout); svg,bounds=canvas.svg()
    result.update({"success":True,"svg":svg,"bounds":bounds,"structure":serial(document),"layout":serial(layout),"dslVersion":"1.6"})
    if payload.get("command") == "export":
        kind=payload.get("format","svg").lower()
        if kind=="json": data=json.dumps({"dsl":dsl,"diagramType":result["diagramType"],"structure":result["structure"],"layout":result["layout"]},ensure_ascii=False,indent=2).encode()
        elif kind=="svg": data=svg.encode()
        elif kind=="png":
            try:
                import cairosvg
                data=cairosvg.svg2png(bytestring=svg.encode(),scale=2)
            except (ImportError, OSError) as exc: raise ValueError("当前服务器缺少Cairo运行库，PNG导出暂不可用；SVG和JSON不受影响") from exc
        elif kind=="vsdx": raise ValueError("当前服务器未配置VSDX导出服务")
        else: raise ValueError("不支持的导出格式")
        result={"success":True,"format":kind,"data":base64.b64encode(data).decode(),"fileName":f'diagram.{kind}'}
    return result

def main():
    register_plugins()
    try: print(json.dumps(execute(json.load(sys.stdin)),ensure_ascii=False))
    except Exception as exc: print(json.dumps({"success":False,"error":str(exc)},ensure_ascii=False))

if __name__ == "__main__": main()
