# -*- coding: utf-8 -*-
from dataclasses import dataclass,field
from pathlib import Path
from offline_diagram_common import *
@dataclass
class BlockNode:id:str;zone:str;text:str
@dataclass(frozen=True)
class BlockEdge:source:str;target:str
@dataclass
class SystemBlockStructure:title:str;nodes:list[BlockNode];edges:list[BlockEdge]
@dataclass
class ConnectorLayout:source_id:str;target_id:str;start:Point;end:Point;points:list[Point];connector_type:str="block"
@dataclass
class SystemBlockLayoutResult:node_bounds:dict[str,RectBounds]=field(default_factory=dict);edge_paths:dict[BlockEdge,list[Point]]=field(default_factory=dict);connectors:list[ConnectorLayout]=field(default_factory=list);width:float=0;height:float=0
class App(OfflineDiagramApp):
 app_title="系统框图生成器";output_suffix="系统框图";template_name="系统框图_输入模板.txt";log_path=Path(__file__).with_name("system_block_diagram_error.log");template_text=Path(__file__).with_name(template_name).read_text(encoding="utf-8-sig")
 def parse(self,text):
  issues=[];title="";section="";nodes=[];edges=[];ids=set();seen=set();sections=set();lines=text.splitlines()
  for no,raw in enumerate(lines,1):
   line=raw.strip()
   if not line or line.startswith("#"):continue
   if re.match(r"^标题\s*[:：]",line):title=re.split(r"[:：]",line,1)[1].strip();continue
   if line in ("[节点]","[连接]"):section=line;sections.add(line);continue
   if section=="[节点]":
    parts=[x.strip() for x in line.split("|",2)]
    if len(parts)!=3:issues.append(ParseIssue(no,"错误","NODE_FORMAT","节点应为ID|分区|名称",raw,"补全三项。"));continue
    if parts[0] in ids:issues.append(ParseIssue(no,"错误","DUP_NODE","节点ID重复",raw,"使用唯一ID。"))
    if parts[1] not in ("left","center","right"):issues.append(ParseIssue(no,"错误","ZONE","分区只能为left、center、right",raw,"修改分区。"))
    ids.add(parts[0]);nodes.append(BlockNode(parts[0],parts[1],parts[2].replace("\\n","\n")))
   elif section=="[连接]":
    m=re.match(r"^([^\s>]+)\s*->\s*([^\s]+)$",line)
    if not m:issues.append(ParseIssue(no,"错误","EDGE_FORMAT","连接格式错误",raw,"写成 I1->C1。"));continue
    e=BlockEdge(*m.groups())
    if e.source==e.target:issues.append(ParseIssue(no,"错误","SELF_EDGE","禁止自连接",raw,"连接不同节点。"))
    if e in seen:issues.append(ParseIssue(no,"错误","DUP_EDGE","连接重复",raw,"删除重复连接。"))
    seen.add(e);edges.append(e)
   else:issues.append(ParseIssue(no,"错误","SECTION","内容不在节点或连接区段",raw,"增加正确区段标题。"))
  if not title:issues.append(ParseIssue(1,"错误","NO_TITLE","缺少标题","","增加标题。"))
  for x in ("[节点]","[连接]"):
   if x not in sections:issues.append(ParseIssue(1,"错误","MISSING_SECTION",f"缺少{x}","",f"增加{x}。"))
  by={x.id:x for x in nodes};degree={x.id:0 for x in nodes}
  for e in edges:
   if e.source not in by or e.target not in by:issues.append(ParseIssue(1,"错误","UNKNOWN_NODE","连接引用不存在节点",str(e),"修正节点ID。"));continue
   if by[e.source].zone=="right" and by[e.target].zone=="left":issues.append(ParseIssue(1,"错误","REVERSE_FLOW","默认禁止right反向连接left",str(e),"保持从左到右。"))
   degree[e.source]+=1;degree[e.target]+=1
  for zone in ("left","center","right"):
   if not any(x.zone==zone for x in nodes):issues.append(ParseIssue(1,"错误","MISSING_ZONE",f"缺少{zone}节点","",f"增加{zone}节点。"))
  for n in nodes:
   if degree[n.id]==0:issues.append(ParseIssue(1,"错误","ISOLATED","节点孤立",n.id,"增加连接。"))
  return (SystemBlockStructure(title,nodes,edges) if not issues else None),issues
 def fill_tree(self,s):
  self.tree.delete(*self.tree.get_children());groups={z:self.tree.insert("","end",text=z,open=True) for z in ("left","center","right")}
  for n in s.nodes:self.tree.insert(groups[n.zone],"end",text=f"{n.id}｜{n.text}")
 def make_layout(self,s):
  l=SystemBlockLayoutResult();by={n.id:n for n in s.nodes};centers=[n for n in s.nodes if n.zone=="center"];center=centers[0]
  direct_left=[];direct_right=[]
  for n in s.nodes:
   if n.zone=="left" and any({e.source,e.target}=={n.id,center.id} for e in s.edges):direct_left.append(n)
   if n.zone=="right" and any({e.source,e.target}=={n.id,center.id} for e in s.edges):direct_right.append(n)
  node_h=66;gap=42
  def total(items):return len(items)*node_h+max(0,len(items)-1)*gap
  center_h=max(240,total(direct_left),total(direct_right))+2*gap;center_b=RectBounds(520,400,300,center_h);l.node_bounds[center.id]=center_b
  def place_primary(items,x,side):
   for i,n in enumerate(items):
    y=center_b.center_y-center_b.height/2+(i+1)*center_b.height/(len(items)+1);l.node_bounds[n.id]=RectBounds(x,y,180,node_h)
  place_primary(direct_left,170,"left");place_primary(direct_right,870,"right")
  primary_ids={n.id for n in direct_left+direct_right}
  secondary=[n for n in s.nodes if n.id!=center.id and n.id not in primary_ids]
  child_groups={pid:[] for pid in primary_ids}
  for n in secondary:
   parent=next((pid for pid in primary_ids if any({e.source,e.target}=={n.id,pid} for e in s.edges)),None)
   if parent:child_groups[parent].append(n)
  for pid,children in child_groups.items():
   pb=l.node_bounds[pid]
   for i,n in enumerate(children):
    offset=(i//2+1)*115*(-1 if i%2==0 else 1);l.node_bounds[n.id]=RectBounds(pb.center_x,pb.center_y+offset,180,node_h)
  leftovers=[n for n in secondary if n.id not in l.node_bounds]
  for i,n in enumerate(leftovers):l.node_bounds[n.id]=RectBounds(1050,120+i*100,180,node_h)
  for e in s.edges:
   a,b=l.node_bounds[e.source],l.node_bounds[e.target]
   if e.source==center.id or e.target==center.id:
    y=b.center_y if e.source==center.id else a.center_y;start=Point(a.center_x+(a.width/2 if b.center_x>a.center_x else -a.width/2),y);end=Point(b.center_x+(-b.width/2 if b.center_x>a.center_x else b.width/2),y);points=[start,end]
   elif abs(a.center_x-b.center_x)<1:
    start=Point(a.center_x,a.center_y+(a.height/2 if b.center_y>a.center_y else -a.height/2));end=Point(b.center_x,b.center_y+(-b.height/2 if b.center_y>a.center_y else b.height/2));points=[start,end]
   else:
    start=rectangle_boundary_point(a,Point(b.center_x,b.center_y));end=rectangle_boundary_point(b,Point(a.center_x,a.center_y));points=[start,end]
   l.edge_paths[e]=points;l.connectors.append(ConnectorLayout(e.source,e.target,start,end,points))
  l.width=max(b.center_x+b.width/2 for b in l.node_bounds.values())+80;l.height=max(b.center_y+b.height/2 for b in l.node_bounds.values())+80
  return l
 def draw(self,c,s,l):
  c.delete("all")
  for path in l.edge_paths.values():
   coords=[v for p in path for v in (p.x,p.y)];c.create_line(*coords,arrow="last")
  for n in s.nodes:
   b=l.node_bounds[n.id];c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white");c.create_text(b.center_x,b.center_y,text=n.text,font=(FONT,10))
  c.configure(scrollregion=c.bbox("all"))
 def export_title(self,s):return s.title
 def json_payload(self):
  return {"diagram_type":"system_block","structure":asdict(self.structure),"layout":{"node_bounds":{k:asdict(v) for k,v in self.layout.node_bounds.items()},"connectors":[asdict(x) for x in self.layout.connectors],"width":self.layout.width,"height":self.layout.height}}
 def export_visio(self,s,l,out):
  import pythoncom,win32com.client as win32
  out.mkdir(parents=True,exist_ok=True);base=safe_name(s.title)+"_系统框图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None;scale=.012
  try:
   app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1)
   for path in l.edge_paths.values():
    for a,b in zip(path,path[1:]):ln=page.DrawLine(a.x*scale,(800-a.y)*scale,b.x*scale,(800-b.y)*scale);ln.CellsU("EndArrow").FormulaU="13" if b==path[-1] else "0"
   for n in s.nodes:
    b=l.node_bounds[n.id];sh=page.DrawRectangle((b.center_x-b.width/2)*scale,(800-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(800-b.center_y+b.height/2)*scale);sh.Text=n.text
   doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
  finally:
   if doc:doc.Close()
   if app:app.Quit()
   pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
