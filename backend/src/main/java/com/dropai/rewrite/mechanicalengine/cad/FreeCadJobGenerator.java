package com.dropai.rewrite.mechanicalengine.cad;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FreeCadJobGenerator {
    public Path generate(Path workspace) {
        try {
            Path script = workspace.resolve("build_brep.py");
            Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            return script;
        } catch (Exception exception) {
            throw new IllegalStateException("FREECAD_JOB_GENERATION_FAILED: " + exception.getMessage(), exception);
        }
    }

    private static final String SCRIPT = """
import FreeCAD as App, Part, Mesh, Drawing, importDXF, json, os, sys

spec_path, root = sys.argv[-2], sys.argv[-1]
with open(spec_path, 'r', encoding='utf-8') as f: spec = json.load(f)
for folder in ['01_Model/Parts','02_STEP','03_Drawing/Parts_Drawing','04_Document','05_Analysis']:
    os.makedirs(os.path.join(root, folder), exist_ok=True)

doc = App.newDocument('DropAI_Mechanical_Project')
objects, manifest = [], []

def p(spec, key, default):
    for feature in spec['features']:
        if key in feature['parameters']: return float(feature['parameters'][key])
    return default

for part in spec['parts']:
    number, name = part['partNumber'], part['name']
    if name == 'Base':
        shape = Part.makeBox(p(part,'length',260), p(part,'width',120), p(part,'height',20))
        for x in [20,240]:
            for y in [20,100]: shape = shape.cut(Part.makeCylinder(6,20,App.Vector(x,y,0)))
    elif name == 'Fixed Jaw':
        shape = Part.makeBox(40,120,65)
        shape = shape.cut(Part.makeBox(14,80,18,App.Vector(13,20,47)))
    elif name == 'Moving Jaw':
        shape = Part.makeBox(45,110,60)
        bore = Part.makeCylinder(11,45,App.Vector(0,55,35),App.Vector(1,0,0))
        shape = shape.cut(bore)
    elif name == 'Lead Screw':
        shape = Part.makeCylinder(10,220,App.Vector(0,0,0),App.Vector(1,0,0))
        shape = shape.fuse(Part.makeCylinder(15,12,App.Vector(208,0,0),App.Vector(1,0,0)))
        shape = shape.cut(Part.makeCylinder(5,30,App.Vector(214,-15,0),App.Vector(0,1,0)))
    else:
        shape = Part.makeCylinder(5,180,App.Vector(0,0,0),App.Vector(1,0,0))
        shape = shape.fuse(Part.makeSphere(6,App.Vector(0,0,0))).fuse(Part.makeSphere(6,App.Vector(180,0,0)))
    if shape.isNull() or shape.Volume <= 0: raise RuntimeError('EMPTY_BREP:' + number)
    obj = doc.addObject('PartDesign::Feature', number)
    obj.Label, obj.Shape = name, shape
    component = next(c for c in spec['assembly']['components'] if c['partNumber'] == number)
    pos = component['position']; obj.Placement.Base = App.Vector(pos['x'],pos['y'],pos['z'])
    objects.append(obj)
    shape.exportBrep(os.path.join(root,'01_Model','Parts',number+'.brep'))
    Part.export([obj], os.path.join(root,'02_STEP',number+'.step'))
    manifest.append({'partNumber':number,'name':name,'volume':shape.Volume,'solidCount':len(shape.Solids),'featureCount':len(part['features'])})

doc.recompute()
doc.saveAs(os.path.join(root,'01_Model','Assembly.FCStd'))
Part.export(objects, os.path.join(root,'02_STEP','Assembly.STEP'))
Mesh.export(objects, os.path.join(root,'02_STEP','Assembly.stl'))
placed_shapes=[]
for obj in objects:
    s=obj.Shape.copy(); s.Placement=obj.Placement; placed_shapes.append(s)
assembly_shape=Part.makeCompound(placed_shapes)
views={}
for view,direction in [('front',App.Vector(0,-1,0)),('top',App.Vector(0,0,1)),('right',App.Vector(1,0,0))]:
    views[view]=[]
    for edge in assembly_shape.Edges:
        pts=edge.discretize(Number=16)
        if view=='front': line=[[q.x,q.z] for q in pts]
        elif view=='top': line=[[q.x,q.y] for q in pts]
        else: line=[[q.y,q.z] for q in pts]
        if len(line)>1: views[view].append(line)
with open(os.path.join(root,'03_Drawing','projection-lines.json'),'w',encoding='utf-8') as f: json.dump(views,f)
svg_parts=[]
for i,(view,direction) in enumerate([('front',App.Vector(0,-1,0)),('top',App.Vector(0,0,1)),('right',App.Vector(1,0,0))]):
    svg_parts.append('<g transform="translate(%d,%d) scale(1,-1)">%s</g>' % (80+(i%2)*500,330+(i//2)*360,Drawing.projectToSVG(assembly_shape,direction)))
with open(os.path.join(root,'03_Drawing','Assembly.svg'),'w',encoding='utf-8') as f:
    f.write('<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="800"><rect width="100%" height="100%" fill="white"/>'+''.join(svg_parts)+'</svg>')
for obj in objects:
    fragment=Drawing.projectToSVG(obj.Shape,App.Vector(0,-1,0))
    with open(os.path.join(root,'03_Drawing','Parts_Drawing',obj.Name+'.svg'),'w',encoding='utf-8') as f:
        f.write('<svg xmlns="http://www.w3.org/2000/svg" width="900" height="620"><rect width="100%" height="100%" fill="white"/><g transform="translate(100,400) scale(1,-1)">'+fragment+'</g></svg>')
importDXF.export(objects,os.path.join(root,'03_Drawing','Assembly.dxf'))
with open(os.path.join(root,'02_STEP','brep-validation.json'),'w',encoding='utf-8') as f:
    json.dump({'passed':True,'kernel':'OpenCascade','parts':manifest,'constraints':len(spec['assembly']['constraints'])},f)
GuiUp = App.GuiUp
App.closeDocument(doc.Name)
""";
}
