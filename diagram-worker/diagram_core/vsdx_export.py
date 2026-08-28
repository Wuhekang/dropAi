"""Portable SVG -> editable VSDX exporter.

The web renderer already owns the authoritative geometry.  Reusing its SVG
primitives keeps PNG, SVG and Visio exports on the same layout while avoiding
the old single embedded bitmap which could not be edited in Visio.
"""
from __future__ import annotations

import html
import io
import math
import zipfile
import xml.etree.ElementTree as ET

from diagram_core.typography import line_height, text_units


VISIO_NS = "http://schemas.microsoft.com/office/visio/2012/main"
RELS_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def _number(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return float(default)


def _fmt(value):
    return f"{float(value):.6f}".rstrip("0").rstrip(".") or "0"


def _tag(element):
    return element.tag.rsplit("}", 1)[-1]


def _color(value, default="#1F2937"):
    value = str(value or "").strip()
    if value.lower() in ("none", "transparent"):
        return default
    if value.startswith("#") and len(value) in (4, 7):
        if len(value) == 4:
            value = "#" + "".join(char * 2 for char in value[1:])
        return value.upper()
    return default


def _cell(name, value, formula=None):
    extra = f' F="{html.escape(str(formula), quote=True)}"' if formula else ""
    return f'<Cell N="{name}" V="{html.escape(str(value), quote=True)}"{extra}/>'


def _row(index, row_type, x, y):
    return f'<Row T="{row_type}" IX="{index}">{_cell("X", _fmt(x))}{_cell("Y", _fmt(y))}</Row>'


def _geometry(points, closed, width, height):
    if not points:
        points = [(0.0, 0.0), (width, 0.0), (width, height), (0.0, height)]
        closed = True
    rows = [_row(1, "MoveTo", points[0][0], points[0][1])]
    for index, point in enumerate(points[1:], 2):
        rows.append(_row(index, "LineTo", point[0], point[1]))
    if closed and points[-1] != points[0]:
        rows.append(_row(len(rows) + 1, "LineTo", points[0][0], points[0][1]))
    return (
        '<Section N="Geometry" IX="0">'
        + _cell("NoFill", "0" if closed else "1")
        + _cell("NoLine", "0")
        + _cell("NoShow", "0")
        + "".join(rows)
        + "</Section>"
    )


def _shape_xml(shape_id, name, pin_x, pin_y, width, height, points, closed,
               fill, stroke, stroke_width, dash=False, begin_arrow=False,
               end_arrow=False, text="", font_size=12.0, bold=False):
    width = max(float(width), 0.002)
    height = max(float(height), 0.002)
    visible_stroke = bool(stroke) and str(stroke).lower() not in ("none", "transparent")
    visible_fill = bool(fill) and str(fill).lower() not in ("none", "transparent")
    line_pattern = "0" if not visible_stroke else ("2" if dash else "1")
    fill_pattern = "1" if closed and visible_fill else "0"
    cells = [
        _cell("PinX", _fmt(pin_x)), _cell("PinY", _fmt(pin_y)),
        _cell("Width", _fmt(width)), _cell("Height", _fmt(height)),
        _cell("LocPinX", _fmt(width / 2)), _cell("LocPinY", _fmt(height / 2)),
        _cell("Angle", "0"), _cell("LinePattern", line_pattern),
        _cell("LineColor", _color(stroke)),
        _cell("LineWeight", _fmt(max(0.006, stroke_width / 72.0))),
        _cell("FillPattern", fill_pattern), _cell("FillForegnd", _color(fill, "#FFFFFF")),
        _cell("BeginArrow", "13" if begin_arrow else "0"),
        _cell("EndArrow", "13" if end_arrow else "0"),
        _cell("VerticalAlign", "1"),
    ]
    sections = [_geometry(points, closed, width, height)]
    if text:
        sections.extend([
            '<Section N="Character"><Row IX="0">'
            + _cell("Font", "0") + _cell("Size", _fmt(max(8.0, font_size) / 72.0))
            + _cell("Style", "1" if bold else "0") + _cell("Color", "#111827")
            + "</Row></Section>",
            '<Section N="Paragraph"><Row IX="0">'
            + _cell("HorzAlign", "1") + "</Row></Section>",
        ])
    text_xml = f"<Text>{html.escape(text)}</Text>" if text else ""
    return (
        f'<Shape ID="{shape_id}" NameU="{html.escape(name, quote=True)}" '
        'Type="Shape" LineStyle="0" FillStyle="0" TextStyle="0">'
        + "".join(cells) + "".join(sections) + text_xml + "</Shape>"
    )


def _text_lines(element):
    spans = [child for child in list(element) if _tag(child) == "tspan"]
    if spans:
        return [(span.text or "") for span in spans]
    return [(element.text or "")]


def _svg_shapes(svg, scale, min_x, min_y, view_width, view_height):
    root = ET.fromstring(svg.encode("utf-8"))
    shapes = []
    shape_id = 1
    for element in list(root):
        tag = _tag(element)
        if tag in ("defs",):
            continue
        attributes = element.attrib
        stroke = attributes.get("stroke", "#1f2937")
        fill = attributes.get("fill", "none")
        stroke_width = _number(attributes.get("stroke-width"), 1.4)
        dash = bool(attributes.get("stroke-dasharray"))
        begin_arrow = bool(attributes.get("marker-start"))
        end_arrow = bool(attributes.get("marker-end"))

        if tag == "rect":
            x, y = _number(attributes.get("x")), _number(attributes.get("y"))
            width, height = _number(attributes.get("width")), _number(attributes.get("height"))
            if (abs(x - min_x) < 0.01 and abs(y - min_y) < 0.01
                    and abs(width - view_width) < 0.01 and abs(height - view_height) < 0.01):
                continue
            is_terminator = attributes.get("data-shape") == "terminator"
            if is_terminator:
                radius = min(width / 2, height / 2)
                local = []
                for index in range(9):
                    angle = -math.pi / 2 + math.pi * index / 8
                    local.append(((width - radius + radius * math.cos(angle)) * scale,
                                  (radius + radius * math.sin(angle)) * scale))
                for index in range(9):
                    angle = math.pi / 2 + math.pi * index / 8
                    local.append(((radius + radius * math.cos(angle)) * scale,
                                  (radius + radius * math.sin(angle)) * scale))
            else:
                local = [(0, 0), (width * scale, 0), (width * scale, height * scale), (0, height * scale)]
            pin_x = (x - min_x + width / 2) * scale
            pin_y = (view_height - (y - min_y + height / 2)) * scale
            shape_name = "Terminator" if is_terminator else "Rectangle"
            shapes.append(_shape_xml(shape_id, f"{shape_name}.{shape_id}", pin_x, pin_y,
                                     width * scale, height * scale, local, True, fill,
                                     stroke, stroke_width))
        elif tag == "ellipse":
            cx, cy = _number(attributes.get("cx")), _number(attributes.get("cy"))
            rx, ry = _number(attributes.get("rx")), _number(attributes.get("ry"))
            width, height = max(rx * 2, 1.0), max(ry * 2, 1.0)
            points = []
            for index in range(32):
                angle = 2 * math.pi * index / 32
                points.append(((rx + rx * math.cos(angle)) * scale,
                               (ry + ry * math.sin(angle)) * scale))
            shapes.append(_shape_xml(shape_id, f"Ellipse.{shape_id}",
                                     (cx - min_x) * scale,
                                     (view_height - (cy - min_y)) * scale,
                                     width * scale, height * scale, points, True, fill,
                                     stroke, stroke_width))
        elif tag in ("polyline", "polygon"):
            raw_points = []
            for pair in attributes.get("points", "").replace(", ", ",").split():
                if "," not in pair:
                    continue
                x, y = pair.split(",", 1)
                raw_points.append((_number(x), _number(y)))
            if len(raw_points) < 2:
                continue
            xs, ys = [point[0] for point in raw_points], [point[1] for point in raw_points]
            left, right, top, bottom = min(xs), max(xs), min(ys), max(ys)
            pixel_width, pixel_height = max(right - left, 0.1), max(bottom - top, 0.1)
            local = [((x - left) * scale, (bottom - y) * scale) for x, y in raw_points]
            shapes.append(_shape_xml(shape_id,
                                     ("Polygon" if tag == "polygon" else "Connector") + f".{shape_id}",
                                     (left - min_x + pixel_width / 2) * scale,
                                     (view_height - (top - min_y + pixel_height / 2)) * scale,
                                     pixel_width * scale, pixel_height * scale, local,
                                     tag == "polygon", fill, stroke, stroke_width, dash,
                                     begin_arrow, end_arrow))
        elif tag == "text":
            lines = _text_lines(element)
            text = "\n".join(lines)
            if not text:
                continue
            x, y = _number(attributes.get("x")), _number(attributes.get("y"))
            font_size = _number(attributes.get("font-size"), 21)
            pixel_width = max(font_size, max((text_units(line) for line in lines), default=1) * font_size) + 12
            pixel_height = max(font_size, len(lines) * line_height(font_size)) + 8
            anchor = attributes.get("text-anchor", "middle")
            center_x = x + pixel_width / 2 if anchor == "start" else x - pixel_width / 2 if anchor == "end" else x
            shapes.append(_shape_xml(shape_id, f"Text.{shape_id}",
                                     (center_x - min_x) * scale,
                                     (view_height - (y - min_y)) * scale,
                                     pixel_width * scale, pixel_height * scale,
                                     [(0, 0), (pixel_width * scale, 0),
                                      (pixel_width * scale, pixel_height * scale),
                                      (0, pixel_height * scale)], True, "none", "none", 0,
                                     text=text, font_size=font_size,
                                     bold=int(_number(attributes.get("font-weight"), 400)) >= 600))
        else:
            continue
        shape_id += 1
    return shapes


def svg_to_vsdx(svg, title="ThesisDiagram"):
    """Return a VSDX whose shapes, connectors and text remain editable."""
    root = ET.fromstring(svg.encode("utf-8"))
    view = [_number(value) for value in root.attrib.get("viewBox", "0 0 100 100").split()]
    if len(view) != 4 or view[2] <= 0 or view[3] <= 0:
        raise ValueError("SVG缺少有效viewBox，无法生成可编辑VSDX")
    min_x, min_y, width, height = view
    page_width = 14.0
    scale = page_width / width
    page_height = max(1.0, height * scale)
    shapes = _svg_shapes(svg, scale, min_x, min_y, width, height)
    if not shapes:
        raise ValueError("SVG没有可转换的矢量图元")

    safe_title = html.escape(title or "ThesisDiagram")
    page_xml = (
        f'<?xml version="1.0" encoding="UTF-8"?><PageContents xmlns="{VISIO_NS}" '
        f'xmlns:r="{RELS_NS}"><Shapes>{"".join(shapes)}</Shapes></PageContents>'
    )
    entries = {
        "[Content_Types].xml": f'''<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/visio/document.xml" ContentType="application/vnd.ms-visio.drawing.main+xml"/><Override PartName="/visio/pages/pages.xml" ContentType="application/vnd.ms-visio.pages+xml"/><Override PartName="/visio/pages/page1.xml" ContentType="application/vnd.ms-visio.page+xml"/><Override PartName="/visio/windows.xml" ContentType="application/vnd.ms-visio.windows+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>''',
        "_rels/.rels": '''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/document" Target="visio/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>''',
        "docProps/core.xml": f'''<?xml version="1.0" encoding="UTF-8"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>{safe_title}</dc:title><dc:creator>DropAI ThesisDiagram</dc:creator></cp:coreProperties>''',
        "docProps/app.xml": '''<?xml version="1.0" encoding="UTF-8"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Microsoft Visio</Application><AppVersion>16.0000</AppVersion></Properties>''',
        "visio/document.xml": f'''<?xml version="1.0" encoding="UTF-8"?><VisioDocument xmlns="{VISIO_NS}" xmlns:r="{RELS_NS}"><DocumentSettings/><Colors/><StyleSheets/><DocumentSheet NameU="TheDoc" Name="TheDoc" ID="0"/></VisioDocument>''',
        "visio/_rels/document.xml.rels": '''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/pages" Target="pages/pages.xml"/><Relationship Id="rId2" Type="http://schemas.microsoft.com/visio/2010/relationships/windows" Target="windows.xml"/></Relationships>''',
        "visio/windows.xml": f'''<?xml version="1.0" encoding="UTF-8"?><Windows ClientWidth="1200" ClientHeight="800" xmlns="{VISIO_NS}" xmlns:r="{RELS_NS}"><Window ID="0" WindowType="Drawing" WindowState="1073741824" WindowLeft="0" WindowTop="0" WindowWidth="1200" WindowHeight="800" ContainerType="Page" Page="0" ViewScale="-1" ViewCenterX="{_fmt(page_width/2)}" ViewCenterY="{_fmt(page_height/2)}"><ShowRulers>1</ShowRulers><ShowGrid>0</ShowGrid><ShowPageBreaks>0</ShowPageBreaks><ShowGuides>1</ShowGuides><ShowConnectionPoints>1</ShowConnectionPoints><GlueSettings>9</GlueSettings><SnapSettings>65847</SnapSettings><SnapExtensions>34</SnapExtensions><SnapAngles/><DynamicGridEnabled>1</DynamicGridEnabled><TabSplitterPos>0.5</TabSplitterPos></Window></Windows>''',
        "visio/pages/pages.xml": f'''<?xml version="1.0" encoding="UTF-8"?><Pages xmlns="{VISIO_NS}" xmlns:r="{RELS_NS}"><Page ID="0" NameU="Page-1" Name="{safe_title}" ViewCenterX="{_fmt(page_width/2)}" ViewCenterY="{_fmt(page_height/2)}"><PageSheet>{_cell("PageWidth",_fmt(page_width))}{_cell("PageHeight",_fmt(page_height))}{_cell("PageScale","1")}{_cell("DrawingScale","1")}</PageSheet><Rel r:id="rId1"/></Page></Pages>''',
        "visio/pages/_rels/pages.xml.rels": '''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.microsoft.com/visio/2010/relationships/page" Target="page1.xml"/></Relationships>''',
        "visio/pages/page1.xml": page_xml,
    }
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as package:
        for name, data in entries.items():
            package.writestr(name, data.encode("utf-8"))
    return output.getvalue()
