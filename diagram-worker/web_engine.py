#!/usr/bin/env python3
"""JSON/stdin web adapter for all seven authoritative ThesisDiagram v1.6 plugins."""
from __future__ import annotations

import base64
import dataclasses
import html
import io
import json
import math
import os
import sys
import time
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from diagram_core.registry import CANONICAL, DISPLAY, PLUGIN_REGISTRY, detect_header
from diagram_core.typography import FONT_FAMILY, line_height, text_units, wrap_text
from diagram_plugins.legacy import register_plugins

MAX_DSL = 100_000

def _svg_number(value, default=0.0):
    try: return float(value)
    except (TypeError, ValueError): return float(default)

def _font_path(bold=False):
    candidates = ([r"C:\Windows\Fonts\msyhbd.ttc", r"C:\Windows\Fonts\simhei.ttf", "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"] if bold else
                  [r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simsun.ttc", r"C:\Windows\Fonts\simhei.ttf", "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"])
    candidates += ["/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
    return next((x for x in candidates if Path(x).is_file()), None)

def _load_font(ImageFont, size, bold=False):
    path=_font_path(bold)
    if path:
        try: return ImageFont.truetype(path,size)
        except OSError: pass
    try: return ImageFont.truetype("arial.ttf",size)
    except OSError:
        try: return ImageFont.load_default(size=size)
        except TypeError: return ImageFont.load_default()

def _text_box(draw, text, font):
    if hasattr(draw,"textbbox"): return draw.textbbox((0,0),text,font=font)
    width,height=draw.textsize(text,font=font)
    return (0,0,width,height)

def svg_to_png(svg, scale=2.0):
    """Render the constrained SVG emitted by SvgCanvas without Cairo."""
    from PIL import Image, ImageDraw, ImageFont
    root=ET.fromstring(svg.encode("utf-8"));view=[float(x) for x in root.attrib["viewBox"].split()]
    min_x,min_y,width,height=view;pad=24
    image=Image.new("RGB",(max(1,int(width*scale)+pad*2),max(1,int(height*scale)+pad*2)),"#FEFEFE");draw=ImageDraw.Draw(image)
    sx=lambda v:(float(v)-min_x)*scale+pad;sy=lambda v:(float(v)-min_y)*scale+pad
    def paint(value,default): return default if not value or value=="none" else value
    for element in list(root):
        tag=element.tag.rsplit("}",1)[-1];a=element.attrib
        if tag=="rect":
            x,y,w,h=map(_svg_number,(a.get("x"),a.get("y"),a.get("width"),a.get("height")));box=(sx(x),sy(y),sx(x+w),sy(y+h));stroke=a.get("stroke")
            line_width=max(1,int(_svg_number(a.get("stroke-width"),1)*scale));fill=paint(a.get("fill"),"#FFFFFF")
            if hasattr(draw,"rounded_rectangle"):
                draw.rounded_rectangle(box,radius=_svg_number(a.get("rx"))*scale,fill=fill,outline=stroke,width=line_width)
            else:
                try: draw.rectangle(box,fill=fill,outline=stroke,width=line_width)
                except TypeError: draw.rectangle(box,fill=fill,outline=stroke)
        elif tag in ("polyline","polygon"):
            points=[tuple(map(float,p.split(","))) for p in a.get("points","").split()];points=[(sx(x),sy(y)) for x,y in points]
            if not points: continue
            if tag=="polygon": draw.polygon(points,fill=paint(a.get("fill"),"#FFFFFF"),outline=paint(a.get("stroke"),"#1f2937"))
            else:
                try: draw.line(points,fill=paint(a.get("stroke"),"#1f2937"),width=max(1,int(_svg_number(a.get("stroke-width"),1.4)*scale)),joint="curve")
                except TypeError: draw.line(points,fill=paint(a.get("stroke"),"#1f2937"),width=max(1,int(_svg_number(a.get("stroke-width"),1.4)*scale)))
                if a.get("marker-end") and len(points)>1:
                    x1,y1=points[-2];x2,y2=points[-1];ang=math.atan2(y2-y1,x2-x1);size=9*scale
                    arrow=[(x2,y2),(x2-size*math.cos(ang-.5),y2-size*math.sin(ang-.5)),(x2-size*math.cos(ang+.5),y2-size*math.sin(ang+.5))]
                    draw.polygon(arrow,fill=paint(a.get("stroke"),"#1f2937"))
        elif tag=="ellipse":
            cx,cy,rx,ry=map(_svg_number,(a.get("cx"),a.get("cy"),a.get("rx"),a.get("ry")));ellipse_box=(sx(cx-rx),sy(cy-ry),sx(cx+rx),sy(cy+ry));ellipse_fill=paint(a.get("fill"),"#FFFFFF");ellipse_stroke=paint(a.get("stroke"),"#1f2937");ellipse_width=max(1,int(_svg_number(a.get("stroke-width"),1)*scale))
            try: draw.ellipse(ellipse_box,fill=ellipse_fill,outline=ellipse_stroke,width=ellipse_width)
            except TypeError: draw.ellipse(ellipse_box,fill=ellipse_fill,outline=ellipse_stroke)
        elif tag=="text":
            base_x,base_y=_svg_number(a.get("x")),_svg_number(a.get("y"));size=max(8,int(_svg_number(a.get("font-size"),21)*scale));font=_load_font(ImageFont,size,int(a.get("font-weight","400"))>=600);current_y=base_y
            spans=[x for x in list(element) if x.tag.rsplit("}",1)[-1]=="tspan"] or [element]
            for span in spans:
                current_y+=_svg_number(span.attrib.get("dy"));text=span.text or "";box=_text_box(draw,text,font);anchor=a.get("text-anchor","middle");x=sx(_svg_number(span.attrib.get("x"),base_x));x=x-(box[2]-box[0])/2 if anchor=="middle" else x-(box[2]-box[0]) if anchor=="end" else x;y=sy(current_y)-(box[3]-box[1])/2
                draw.text((x,y),text,font=font,fill=paint(a.get("fill"),"#111827"))
    output=io.BytesIO();image.save(output,"PNG",optimize=True);return output.getvalue()

def png_to_vsdx(png, title="ThesisDiagram"):
    """Create a portable VSDX package containing the watermark-free PNG."""
    from PIL import Image
    with Image.open(io.BytesIO(png)) as image: px_w,px_h=image.size
    image_w=14.0;image_h=image_w*px_h/max(px_w,1);page_w,page_h=image_w+.5,image_h+.5;safe=html.escape(title or "ThesisDiagram")
    entries={
      "[Content_Types].xml":'''<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/visio/document.xml" ContentType="application/vnd.ms-visio.drawing.main+xml"/><Override PartName="/visio/pages/pages.xml" ContentType="application/vnd.ms-visio.pages+xml"/><Override PartName="/visio/pages/page1.xml" ContentType="application/vnd.ms-visio.page+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>''',
      "_rels/.rels":'''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/document" Target="visio/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>''',
      "docProps/core.xml":f'''<?xml version="1.0" encoding="UTF-8"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>{safe}</dc:title><dc:creator>DropAI ThesisDiagram</dc:creator></cp:coreProperties>''',
      "docProps/app.xml":'''<?xml version="1.0" encoding="UTF-8"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Microsoft Visio</Application><AppVersion>16.0000</AppVersion></Properties>''',
      "visio/document.xml":'''<?xml version="1.0" encoding="UTF-8"?><VisioDocument xmlns="http://schemas.microsoft.com/office/visio/2012/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><DocumentSettings/><Colors/><StyleSheets/><DocumentSheet NameU="TheDoc" Name="TheDoc" ID="0"/></VisioDocument>''',
      "visio/_rels/document.xml.rels":'''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/pages" Target="pages/pages.xml"/></Relationships>''',
      "visio/pages/pages.xml":f'''<?xml version="1.0" encoding="UTF-8"?><Pages xmlns="http://schemas.microsoft.com/office/visio/2012/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><Page ID="0" NameU="{safe}" Name="{safe}" ViewCenterX="{page_w/2:.4f}" ViewCenterY="{page_h/2:.4f}"><PageSheet><Cell N="PageWidth" V="{page_w:.4f}"/><Cell N="PageHeight" V="{page_h:.4f}"/></PageSheet><Rel r:id="rId1"/></Page></Pages>''',
      "visio/pages/_rels/pages.xml.rels":'''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/page" Target="page1.xml"/></Relationships>''',
      "visio/pages/page1.xml":f'''<?xml version="1.0" encoding="UTF-8"?><PageContents xmlns="http://schemas.microsoft.com/office/visio/2012/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><Shapes><Shape ID="1" NameU="{safe}" Name="{safe}" Type="Foreign" LineStyle="0" FillStyle="0" TextStyle="0"><Cell N="PinX" V="{page_w/2:.4f}"/><Cell N="PinY" V="{page_h/2:.4f}"/><Cell N="Width" V="{image_w:.4f}"/><Cell N="Height" V="{image_h:.4f}"/><Cell N="LocPinX" V="{image_w/2:.4f}" F="Width*0.5"/><Cell N="LocPinY" V="{image_h/2:.4f}" F="Height*0.5"/><Cell N="Angle" V="0"/><ForeignData ForeignType="Bitmap" CompressionType="PNG"><Rel r:id="rId1"/></ForeignData></Shape></Shapes></PageContents>''',
      "visio/pages/_rels/page1.xml.rels":'''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.png"/></Relationships>''',
      "visio/media/image1.png":png,
    }
    output=io.BytesIO()
    with zipfile.ZipFile(output,"w",zipfile.ZIP_DEFLATED) as package:
        for name,data in entries.items(): package.writestr(name,data.encode("utf-8") if isinstance(data,str) else data)
    return output.getvalue()

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
    def _style(options, default_fill="none", line=False):
        fill = options.get("fill", default_fill)
        # Tk canvas uses ``fill`` as a line colour, but as the interior colour
        # for shapes.  Treating both cases alike made white-filled nodes also
        # receive a white SVG stroke, so their borders vanished on white pages.
        stroke = options.get("outline", options.get("fill", "#1f2937") if line else "#1f2937")
        width = options.get("width", 1.4)
        dash = options.get("dash")
        return f'fill="{html.escape(str(fill))}" stroke="{html.escape(str(stroke))}" stroke-width="{width}"' + (f' stroke-dasharray="{",".join(map(str,dash))}"' if dash else "")

    def create_line(self, *coords, **options):
        item = self._id(coords)
        points = " ".join(f"{coords[i]},{coords[i+1]}" for i in range(0, len(coords), 2))
        marker_start = ' marker-start="url(#arrow)"' if options.get("arrow") == "both" else ""
        marker_end = ' marker-end="url(#arrow)"' if options.get("arrow") in ("last", "both") else ""
        self.items.append(f'<polyline points="{points}" {self._style(options, line=True)}{marker_start}{marker_end}/>' )
        return item

    def create_rectangle(self, x1, y1, x2, y2, **options):
        item = self._id((x1,y1,x2,y2)); css=html.escape(str(options.get("svg_class",""))); self.items.append(f'<rect class="{css}" x="{x1}" y="{y1}" width="{x2-x1}" height="{y2-y1}" rx="3" vector-effect="non-scaling-stroke" {self._style(options,"white")}/>'); return item

    def create_oval(self, x1, y1, x2, y2, **options):
        item = self._id((x1,y1,x2,y2)); self.items.append(f'<ellipse cx="{(x1+x2)/2}" cy="{(y1+y2)/2}" rx="{(x2-x1)/2}" ry="{(y2-y1)/2}" {self._style(options,"white")}/>'); return item

    def create_polygon(self, *coords, **options):
        item = self._id(coords); points=" ".join(f"{coords[i]},{coords[i+1]}" for i in range(0,len(coords),2)); self.items.append(f'<polygon points="{points}" {self._style(options,"white")}/>'); return item

    def create_text(self, x, y, **options):
        text = str(options.get("text", "")); width = float(options.get("width", 0) or 0)
        size=int((options.get("font") or ("",10))[-1]); max_units=options.get("max_units")
        if options.get("vertical"):
            lines = [char for char in text if char not in "\r\n"] or [""]
        elif max_units:
            lines = wrap_text(text, float(max_units))
        elif width:
            unit_limit=max(1.0,(width-12)/max(size,1)); lines=wrap_text(text,unit_limit)
        else:
            lines=text.splitlines() or [""]
        anchor={"w":"start","e":"end"}.get(options.get("anchor"),"middle")
        line_spacing=float(options.get("line_spacing",line_height(size)))
        half_width=max((text_units(line)*size/2 for line in lines),default=size/2)
        half_height=max(size/2,len(lines)*line_spacing/2)
        item=self._id((x-half_width,y-half_height,x+half_width,y+half_height))
        first_offset=-(len(lines)-1)*line_spacing/2
        tspans="".join(f'<tspan x="{x}" dy="{first_offset if i==0 else line_spacing}">{html.escape(line)}</tspan>' for i,line in enumerate(lines))
        weight=html.escape(str(options.get("font_weight","400"))); fill=html.escape(str(options.get("fill","#111827")))
        self.items.append(f'<text x="{x}" y="{y}" text-anchor="{anchor}" dominant-baseline="middle" font-family="{FONT_FAMILY}" font-size="{size}" font-weight="{weight}" fill="{fill}">{tspans}</text>')
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
    started=time.perf_counter()
    trace_id=str(payload.get("traceId", "cli"))[:48]
    last=started
    def trace(step,name,diagram_type="",nodes=0):
        nonlocal last
        now=time.perf_counter(); stage=round((now-last)*1000); total=round((now-started)*1000); last=now
        print(f"[diagram-render][{trace_id}] {step:02d} {name} pid={__import__('os').getpid()} stage={stage}ms total={total}ms type={diagram_type} nodes={nodes}",file=sys.stderr,flush=True)
    trace(1,"request_received")
    dsl=payload.get("dsl","")
    if not isinstance(dsl,str) or not dsl.strip(): raise ValueError("DSL不能为空")
    if len(dsl)>MAX_DSL: raise ValueError("DSL长度不能超过100000字符")
    trace(2,"source_normalized")
    header=detect_header(dsl)
    if header.issue: return {"valid":False,"diagramType":None,"title":"","issues":[issue_dict(header.issue)]}
    trace(3,"header_detected",header.canonical_header)
    plugin=PLUGIN_REGISTRY[header.diagram_type]
    trace(4,"parser_selected",header.canonical_header)
    trace(5,"parse_started",header.canonical_header)
    document,issues=plugin.parse(header.body_text)
    trace(6,"parse_completed",header.canonical_header)
    trace(7,"validation_started",header.canonical_header)
    result={"valid":not any(i.severity=="错误" for i in issues),"diagramType":header.diagram_type.value,"displayName":DISPLAY[header.diagram_type],"header":CANONICAL[header.diagram_type],"title":plugin.title(document) if document else "","issues":[issue_dict(i) for i in issues]}
    trace(8,"validation_completed",header.canonical_header)
    if payload.get("command","validate") == "validate" or not result["valid"]: return result
    trace(9,"layout_started",header.canonical_header)
    layout=plugin.layout(document)
    trace(10,"layout_completed",header.canonical_header)
    canvas=SvgCanvas(); trace(11,"svg_render_started",header.canonical_header)
    plugin.draw_canvas(canvas,document,layout); svg,bounds=canvas.svg()
    structure=serial(document)
    if header.diagram_type.value=="function_module": node_count=1+len(structure.get("modules",[]))+sum(len(x.get("functions",[])) for x in structure.get("modules",[]))
    elif header.diagram_type.value=="flowchart": node_count=len(structure.get("nodes",[]))
    elif header.diagram_type.value=="er_diagram":
        entity_count=len(structure.get("entities",[]));attribute_count=sum(len(x.get("attributes",[])) for x in structure.get("entities",[]));relationship_count=len(structure.get("relationships",[]));node_count=entity_count+attribute_count+relationship_count
    elif header.diagram_type.value=="architecture": node_count=len(structure.get("layers",[]))+sum(len(x.get("components",[])) for x in structure.get("layers",[]))
    elif header.diagram_type.value=="use_case": node_count=len(structure.get("systems",[]))+len(structure.get("actors",[]))+len(structure.get("use_cases",[]))
    elif header.diagram_type.value=="block_diagram": node_count=len(structure.get("nodes",[]))
    else: node_count=len(structure.get("participants",[]))+len(structure.get("messages",[]))
    trace(12,"svg_render_completed",header.canonical_header,node_count)
    result.update({"ok":True,"success":True,"diagramType":header.canonical_header,"diagramTypeKey":header.diagram_type.value,"svg":svg,"width":bounds["width"],"height":bounds["height"],"bounds":bounds,"nodeCount":node_count,"warnings":[issue_dict(i) for i in issues if i.severity!="错误"],"exports":{"svg":True,"png":True,"json":True,"vsdx":True},"durationMs":round((time.perf_counter()-started)*1000),"structure":structure,"layout":serial(layout),"dslVersion":"1.6"})
    if header.diagram_type.value=="er_diagram":result.update({"entityCount":entity_count,"attributeCount":attribute_count,"relationshipCount":relationship_count,"visualNodeCount":node_count,"layoutMode":"chen_skeleton_attributes","layoutDiagnostics":serial(getattr(layout,"diagnostics",{}))})
    trace(13,"response_serialized",header.canonical_header,node_count)
    if payload.get("command") == "export":
        kind=payload.get("format","svg").lower()
        if kind=="json": data=json.dumps({"dsl":dsl,"diagramType":result["diagramType"],"structure":result["structure"],"layout":result["layout"]},ensure_ascii=False,indent=2).encode()
        elif kind=="svg": data=svg.encode()
        elif kind=="png": data=svg_to_png(svg,scale=2)
        elif kind=="vsdx": data=png_to_vsdx(svg_to_png(svg,scale=2),result.get("title") or "ThesisDiagram")
        else: raise ValueError("不支持的导出格式")
        result={"success":True,"format":kind,"data":base64.b64encode(data).decode(),"fileName":f'diagram.{kind}'}
    trace(14,"response_sent",header.canonical_header,node_count)
    return result

def main():
    register_plugins()
    try: print(json.dumps(execute(json.load(sys.stdin)),ensure_ascii=False))
    except Exception as exc: print(json.dumps({"success":False,"error":str(exc)},ensure_ascii=False))

if __name__ == "__main__":
    if "--self-test" in sys.argv:
        sample='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 120"><rect x="10" y="10" width="300" height="100" fill="#fff" stroke="#111827"/><text x="160" y="60" text-anchor="middle" font-size="24" font-weight="600" fill="#111827"><tspan x="160" dy="0">PNG与VSDX导出自检</tspan></text></svg>'
        png=svg_to_png(sample);vsdx=png_to_vsdx(png,"导出自检")
        print(json.dumps({"success":png.startswith(b"\x89PNG") and vsdx.startswith(b"PK"),"pngBytes":len(png),"vsdxBytes":len(vsdx),"font":_font_path(False)},ensure_ascii=False))
    else: main()
