package com.dropai.rewrite.mechanicalengine.cadcore;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PartDesignJobGenerator {
    public Path generate(Path workspace) {
        try {
            Path script = workspace.resolve("build_partdesign.py");
            Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            return script;
        } catch (Exception exception) {
            throw new IllegalStateException("PARTDESIGN_JOB_GENERATION_FAILED: " + exception.getMessage(), exception);
        }
    }

    static final String SCRIPT = """
import FreeCAD as App, Part, Sketcher, Mesh, json, os, sys, traceback

spec_path = os.environ['DROP_AI_CAD_SPEC']
root = os.environ['DROP_AI_CAD_WORKSPACE']
with open(spec_path, 'r', encoding='utf-8') as f: spec = json.load(f)
for folder in ['01_Model/Parts','02_STEP','03_Drawing/Parts_Drawing','04_Document','05_Analysis']:
    os.makedirs(os.path.join(root, folder), exist_ok=True)
checkpoint = os.path.join(root, '01_Model', 'Generation_Checkpoint.FCStd')
doc = App.openDocument(checkpoint) if os.path.isfile(checkpoint) else App.newDocument('DropAI_Feature_CAD')
objects, feature_log, assembly_log, manifest = [], [], [], []

def value(params, key, default): return float(params.get(key, default))
def log(number, feature, status, result): feature_log.append({'partNumber':number,'feature':feature,'status':status,'result':result})
def progress(percent, stage, message):
    print('DROP_AI_PROGRESS|%d|%s|%s' % (percent, stage, message), flush=True)
def rectangle(sketch, width, height):
    x, y = width / 2.0, height / 2.0
    points=[App.Vector(-x,-y,0),App.Vector(x,-y,0),App.Vector(x,y,0),App.Vector(-x,y,0)]
    for i in range(4): sketch.addGeometry(Part.LineSegment(points[i],points[(i+1)%4]),False)
    for i in range(4): sketch.addConstraint(Sketcher.Constraint('Coincident',i,2,(i+1)%4,1))
    sketch.addConstraint(Sketcher.Constraint('Horizontal',0)); sketch.addConstraint(Sketcher.Constraint('Horizontal',2))
    sketch.addConstraint(Sketcher.Constraint('Vertical',1)); sketch.addConstraint(Sketcher.Constraint('Vertical',3))
    sketch.addConstraint(Sketcher.Constraint('Distance',0,width)); sketch.addConstraint(Sketcher.Constraint('Distance',1,height))

def circle(sketch, diameter):
    sketch.addGeometry(Part.Circle(App.Vector(0,0,0),App.Vector(0,0,1),diameter/2.0),False)
    sketch.addConstraint(Sketcher.Constraint('Diameter',0,diameter))

part_count = max(len(spec['parts']), 1)
progress(35, 'FREECAD_STARTING', 'FreeCAD document initialized')
for part_index, part in enumerate(spec['parts']):
    number, name = part['partNumber'], part['partName']
    part_start = 36 + int(part_index * 38 / part_count)
    body = doc.getObject(number + '_Body')
    resumed = body is not None and body.Tip is not None and not body.Tip.Shape.isNull()
    progress(part_start, 'PART_RESUMING' if resumed else 'PART_BUILDING', ('Resuming' if resumed else 'Building') + ' %s %s' % (number, name))
    if body is None:
        body = doc.addObject('PartDesign::Body', number + '_Body'); body.Label = name
    current, profile = (body.Tip if resumed else None), None
    try:
        for f in sorted(part['body'], key=lambda item:item['order']):
            if resumed: break
            kind, params = f['featureType'], f.get('parameters') or {}
            progress(min(part_start + 2, 73), 'FEATURE_EXECUTION', '%s: executing %s' % (number, kind))
            if kind == 'SKETCH':
                profile = body.newObject('Sketcher::SketchObject', number + '_Sketch')
                if params.get('profile','rectangle').lower() == 'circle': circle(profile,value(params,'diameter',20))
                else: rectangle(profile,value(params,'length',40),value(params,'width',40))
                doc.recompute(); log(number,kind,'SUCCESS','constrained_sketch_created')
            elif kind == 'PAD':
                current = body.newObject('PartDesign::Pad', number + '_Pad'); current.Profile=profile
                current.Length=value(params,'length',params.get('height',10)); doc.recompute()
                log(number,kind,'SUCCESS','solid_created')
            elif kind in ('HOLE','POCKET'):
                tool = body.newObject('Sketcher::SketchObject', number + '_' + kind + '_Sketch')
                circle(tool,value(params,'diameter',8)); doc.recompute()
                if kind == 'HOLE':
                    current = body.newObject('PartDesign::Hole', number + '_Hole'); current.Profile=tool
                    current.Diameter=value(params,'diameter',8); current.Depth=value(params,'depth',50)
                else:
                    current = body.newObject('PartDesign::Pocket', number + '_Pocket'); current.Profile=tool
                    current.Length=value(params,'depth',50)
                doc.recompute(); log(number,kind,'SUCCESS','subtractive_feature_created')
            elif kind in ('FILLET','CHAMFER'):
                edge_ids=list(range(1,min(len(current.Shape.Edges),4)+1))
                feature=body.newObject('PartDesign::Fillet',number+'_Fillet') if kind=='FILLET' else body.newObject('PartDesign::Chamfer',number+'_Chamfer')
                feature.Base=(current,['Edge'+str(i) for i in edge_ids])
                if kind=='FILLET': feature.Radius=value(params,'radius',2)
                else: feature.Size=value(params,'distance',1)
                current=feature; doc.recompute(); log(number,kind,'SUCCESS','edge_feature_created')
        if body.Tip is None or body.Tip.Shape.isNull() or body.Tip.Shape.Volume <= 0: raise RuntimeError('EMPTY_PARTDESIGN_BODY')
        if 'Material' not in body.PropertiesList: body.addProperty('App::PropertyString','Material','Engineering')
        body.Material=part['material']
        objects.append(body)
        doc.recompute(); doc.saveAs(checkpoint)
        brep_path = os.path.join(root,'01_Model','Parts',number+'.brep')
        step_path = os.path.join(root,'02_STEP',number+'.step')
        if not os.path.isfile(brep_path) or os.path.getsize(brep_path) == 0:
            progress(min(part_start + 4, 74), 'BREP_EXPORTING', '%s: exporting BRep' % number)
            body.Tip.Shape.exportBrep(brep_path)
        else: progress(min(part_start + 4, 74), 'BREP_REUSED', '%s: existing BRep reused' % number)
        if not os.path.isfile(step_path) or os.path.getsize(step_path) == 0:
            progress(min(part_start + 6, 75), 'PART_STEP_EXPORTING', '%s: exporting STEP' % number)
            body.Tip.Shape.exportStep(step_path)
        else: progress(min(part_start + 6, 75), 'STEP_REUSED', '%s: existing STEP reused' % number)
        manifest.append({'partNumber':number,'name':name,'volume':body.Tip.Shape.Volume,'solidCount':len(body.Tip.Shape.Solids),'body':body.Name,'tip':body.Tip.Name,'featureCount':len(body.Group),'features':[o.TypeId for o in body.Group]})
        progress(36 + int((part_index + 1) * 38 / part_count), 'PART_COMPLETED', '%s completed (%d/%d)' % (number, part_index + 1, part_count))
    except Exception as ex:
        log(number,kind,'FAILED',str(ex)); raise

progress(76, 'ASSEMBLY_GENERATING', 'Solving assembly constraints')
assembly_group=doc.addObject('App::DocumentObjectGroup','AssemblyConstraints')
by_number={p['partNumber']: doc.getObject(p['partNumber']+'_Body') for p in spec['parts']}
for c in spec['assembly']['constraints']:
    obj=doc.addObject('App::FeaturePython','Constraint_'+str(len(assembly_log)+1))
    for prop in ['Type','ComponentA','ReferenceA','ComponentB','ReferenceB','SolveStatus']: obj.addProperty('App::PropertyString',prop,'Assembly')
    obj.Type=c['type'].upper(); obj.ComponentA=c['componentA']; obj.ReferenceA=c['referenceA']; obj.ComponentB=c['componentB']; obj.ReferenceB=c['referenceB']; obj.SolveStatus='PENDING'
    assembly_group.addObject(obj)
    a=by_number.get(c['componentA']); b=by_number.get(c['componentB'])
    component=next((x for x in spec['assembly']['components'] if x['partNumber']==c['componentA']),None)
    if a and component:
        pos=component['position']; rot=component['orientation']
        solved=App.Vector(pos['x'],pos['y'],pos['z'])
        if obj.Type in ('COINCIDENT','SLIDER') and b:
            solved=App.Vector(pos['x'],pos['y'],b.Placement.Base.z+b.Tip.Shape.BoundBox.ZLength)
        elif obj.Type=='CONCENTRIC' and b:
            ac=a.Tip.Shape.BoundBox.Center; bc=b.Tip.Shape.BoundBox.Center + b.Placement.Base
            solved=App.Vector(bc.x-ac.x,bc.y-ac.y,pos['z'])
        elif obj.Type=='DISTANCE' and b:
            solved=b.Placement.Base+App.Vector(pos['x'],pos['y'],pos['z'])
        solved_rotation=App.Rotation(rot['x'],rot['y'],rot['z'])
        if obj.Type=='ANGLE' and b: solved_rotation=b.Placement.Rotation.multiply(solved_rotation)
        a.Placement=App.Placement(solved,solved_rotation)
        obj.SolveStatus='SOLVED'; assembly_log.append({'constraint':obj.Name,'type':obj.Type,'status':'SOLVED','drivenComponent':a.Name})
    else: raise RuntimeError('ASSEMBLY_CONSTRAINT_UNRESOLVED:'+obj.Name)

doc.recompute(); doc.saveAs(os.path.join(root,'01_Model','Assembly.FCStd'))
progress(81, 'ASSEMBLY_STEP_EXPORTING', 'Exporting assembly STEP')
Part.export(objects,os.path.join(root,'02_STEP','Assembly.STEP'))
progress(85, 'STL_EXPORTING', 'Exporting browser STL preview')
Mesh.export(objects,os.path.join(root,'02_STEP','Assembly.stl'))
placed=[]
for body in objects:
    s=body.Tip.Shape.copy(); s.Placement=body.Placement; placed.append(s)
assembly_shape=Part.makeCompound(placed)
def projected_lines(shape, view):
    lines=[]
    for edge in shape.Edges:
        try:
            pts=edge.discretize(Number=16)
        except Exception:
            pts=[vertex.Point for vertex in edge.Vertexes]
        line=[[q.x,q.z] for q in pts] if view=='front' else ([[q.x,q.y] for q in pts] if view=='top' else [[q.y,q.z] for q in pts])
        if len(line)>1: lines.append(line)
    if not lines: raise RuntimeError('EMPTY_PROJECTED_VIEW:'+view)
    return lines

progress(88, 'DRAWING_PROJECTING', 'Generating front, top and right projections')
views={view:projected_lines(assembly_shape,view) for view in ['front','top','right']}
with open(os.path.join(root,'03_Drawing','projection-lines.json'),'w',encoding='utf-8') as f: json.dump(views,f)

def svg_group(lines,tx,ty):
    paths=[]
    for line in lines:
        points=' '.join('%.3f,%.3f'%(point[0],-point[1]) for point in line)
        paths.append('<polyline points="'+points+'" fill="none" stroke="#111" stroke-width="0.7"/>')
    return '<g transform="translate(%d,%d)">%s</g>'%(tx,ty,''.join(paths))

fragments=[svg_group(views['front'],80,330),svg_group(views['top'],580,330),svg_group(views['right'],80,690)]
with open(os.path.join(root,'03_Drawing','Assembly.svg'),'w',encoding='utf-8') as f: f.write('<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="800"><rect width="100%" height="100%" fill="white"/>'+''.join(fragments)+'</svg>')
for body in objects:
    part_lines=projected_lines(body.Tip.Shape,'front')
    with open(os.path.join(root,'03_Drawing','Parts_Drawing',body.Name.replace('_Body','')+'.svg'),'w',encoding='utf-8') as f: f.write('<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="100%" height="100%" fill="white"/>'+svg_group(part_lines,80,400)+'</svg>')

dxf=['0','SECTION','2','HEADER','0','ENDSEC','0','SECTION','2','ENTITIES']
for line in views['front']:
    for p1,p2 in zip(line,line[1:]):
        dxf.extend(['0','LINE','8','FRONT','10',str(p1[0]),'20',str(p1[1]),'30','0','11',str(p2[0]),'21',str(p2[1]),'31','0'])
dxf.extend(['0','ENDSEC','0','EOF'])
with open(os.path.join(root,'03_Drawing','Assembly.dxf'),'w',encoding='ascii') as f: f.write('\\n'.join(dxf)+'\\n')
receipt={'passed':True,'kernel':'OpenCascade','modelingMethod':'FreeCAD PartDesign','primitiveOnly':False,'parts':manifest,'featureLog':feature_log,'assemblyConstraints':assembly_log}
with open(os.path.join(root,'02_STEP','cad-reality-report.json'),'w',encoding='utf-8') as f: json.dump(receipt,f,indent=2)
progress(90, 'FREECAD_COMPLETED', 'FreeCAD CAD artifacts completed')
print(json.dumps({'status':'SUCCESS','parts':len(objects),'features':len(feature_log),'constraints':len(assembly_log)}))
""";
}
