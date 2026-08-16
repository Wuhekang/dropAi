# -*- coding: utf-8 -*-
from __future__ import annotations
from dataclasses import dataclass,field
from pathlib import Path
from offline_diagram_common import *
@dataclass
class UseCaseSystem:id:str;name:str
@dataclass
class UseCaseActor:id:str;name:str
@dataclass
class UseCase:id:str;system_id:str;name:str
@dataclass
class ActorAssociation:actor_id:str;use_case_id:str
@dataclass
class UseCaseRelation:source_use_case_id:str;target_use_case_id:str;relation_type:str
@dataclass
class UseCaseDiagramStructure:title:str;systems:list[UseCaseSystem];actors:list[UseCaseActor];use_cases:list[UseCase];associations:list[ActorAssociation];use_case_relations:list[UseCaseRelation]
@dataclass
class ConnectorLayout:source_id:str;target_id:str;start:Point;end:Point;points:list[Point];connector_type:str;label:str=""
@dataclass
class UseCaseDiagramLayoutResult:system_bounds:dict[str,RectBounds]=field(default_factory=dict);actor_points:dict[str,Point]=field(default_factory=dict);use_case_bounds:dict[str,RectBounds]=field(default_factory=dict);association_lines:list[tuple[Point,Point]]=field(default_factory=list);relation_lines:list[tuple[list[Point],str]]=field(default_factory=list);connectors:list[ConnectorLayout]=field(default_factory=list);width:float=0;height:float=0
class App(OfflineDiagramApp):
 app_title="UML用例图生成器";output_suffix="UML用例图";template_name="UML用例图_输入模板.txt";log_path=Path(__file__).with_name("use_case_diagram_error.log");template_text=Path(__file__).with_name(template_name).read_text(encoding="utf-8-sig")
 def parse(self,text):
  issues=[];title="";section="";systems=[];actors=[];cases=[];assocs=[];rels=[];seen=set();sections=set();lines=text.splitlines()
  for no,raw in enumerate(lines,1):
   line=raw.strip()
   if not line or line.startswith("#"):continue
   if re.match(r"^标题\s*[:：]",line):title=re.split(r"[:：]",line,1)[1].strip();continue
   if line in ("[系统]","[参与者]","[用例]","[关联]","[用例关系]"):section=line;sections.add(line);continue
   parts=[x.strip() for x in line.split("|")]
   if section in ("[系统]","[参与者]"):
    if len(parts)!=2:issues.append(ParseIssue(no,"错误","ITEM_FORMAT","条目应为ID|名称",raw,"补全ID和名称。"));continue
    if parts[0] in seen:issues.append(ParseIssue(no,"错误","DUP_ID","ID重复",raw,"使用全局唯一ID。"))
    seen.add(parts[0]);(systems if section=="[系统]" else actors).append((UseCaseSystem if section=="[系统]" else UseCaseActor)(*parts))
   elif section=="[用例]":
    if len(parts)!=3:issues.append(ParseIssue(no,"错误","CASE_FORMAT","用例应为ID|系统ID|名称",raw,"补全三项。"));continue
    if parts[0] in seen:issues.append(ParseIssue(no,"错误","DUP_ID","ID重复",raw,"使用全局唯一ID。"))
    seen.add(parts[0]);cases.append(UseCase(*parts))
   elif section=="[关联]":
    m=re.match(r"^([^\s>]+)\s*->\s*([^\s]+)$",line)
    if not m:issues.append(ParseIssue(no,"错误","ASSOC_FORMAT","关联格式错误",raw,"写成 A1->U1。"));continue
    item=ActorAssociation(*m.groups())
    if item in assocs:issues.append(ParseIssue(no,"错误","DUP_ASSOC","关联重复",raw,"删除重复关联。"))
    assocs.append(item)
   elif section=="[用例关系]":
    m=re.match(r"^([^|>\s]+)->([^|\s]+)\|(.+)$",line)
    if not m or m.group(3) not in ("include","extend"):issues.append(ParseIssue(no,"错误","REL_TYPE","用例关系必须为include或extend",raw,"写成 U1->U2|include。"));continue
    if m.group(1)==m.group(2):issues.append(ParseIssue(no,"错误","SELF_REL","禁止自连接",raw,"连接不同用例。"))
    rels.append(UseCaseRelation(*m.groups()))
   else:issues.append(ParseIssue(no,"错误","SECTION","内容不在规定区段",raw,"先写区段标题。"))
  required={"[系统]","[参与者]","[用例]","[关联]"}
  for x in required-sections:issues.append(ParseIssue(1,"错误","MISSING_SECTION",f"缺少{x}区段","",f"增加{x}区段。"))
  if not title:issues.append(ParseIssue(1,"错误","NO_TITLE","缺少标题","","增加标题。"))
  sysids={x.id for x in systems};actorids={x.id for x in actors};caseids={x.id for x in cases}
  for x in cases:
   if x.system_id not in sysids:issues.append(ParseIssue(1,"错误","UNKNOWN_SYSTEM","用例引用不存在系统",x.id,"修正系统ID。"))
  connected=set()
  for x in assocs:
   if x.actor_id not in actorids:issues.append(ParseIssue(1,"错误","UNKNOWN_ACTOR","关联引用不存在参与者",x.actor_id,"修正参与者ID。"))
   if x.use_case_id not in caseids:issues.append(ParseIssue(1,"错误","UNKNOWN_CASE","关联引用不存在用例",x.use_case_id,"修正用例ID。"))
   connected.add(x.use_case_id)
  for x in rels:
   if x.source_use_case_id not in caseids or x.target_use_case_id not in caseids:issues.append(ParseIssue(1,"错误","UNKNOWN_CASE_REL","用例关系引用不存在对象",str(x),"修正用例ID。"))
   elif next(c.system_id for c in cases if c.id==x.source_use_case_id)!=next(c.system_id for c in cases if c.id==x.target_use_case_id):issues.append(ParseIssue(1,"错误","CROSS_SYSTEM_RELATION","用例关系不能跨越不同的系统边界。",str(x),"请将两个用例设置为同一所属系统，或者删除该关系。"))
   connected|={x.source_use_case_id,x.target_use_case_id}
  for x in cases:
   if x.id not in connected:issues.append(ParseIssue(1,"错误","ISOLATED_CASE","用例没有任何关系",x.id,"增加参与者关联或用例关系。"))
  for x in systems:
   if not any(c.system_id==x.id for c in cases):issues.append(ParseIssue(1,"错误","EMPTY_SYSTEM","系统没有用例",x.id,"增加所属用例。"))
  return (UseCaseDiagramStructure(title,systems,actors,cases,assocs,rels) if not issues else None),issues
 def fill_tree(self,s):
  self.tree.delete(*self.tree.get_children());root=self.tree.insert("","end",text=s.title,open=True)
  for system in s.systems:
   sid=self.tree.insert(root,"end",text=system.name,open=True)
   for u in s.use_cases:
    if u.system_id==system.id:self.tree.insert(sid,"end",text=u.name)
  a=self.tree.insert(root,"end",text="参与者",open=True)
  for x in s.actors:self.tree.insert(a,"end",text=x.name)
 def make_layout(self,s):
  l=UseCaseDiagramLayoutResult();case_by={x.id:x for x in s.use_cases}
  for si,system in enumerate(s.systems):
   cases=[x for x in s.use_cases if x.system_id==system.id];relations=[r for r in s.use_case_relations if case_by[r.source_use_case_id].system_id==system.id];ellipse_w=max(250,max((node_width(x.name,MEDIUM_FONT,8,250) for x in cases),default=250));case_heights=[node_height(x.name,MEDIUM_FONT,8,72) for x in cases];channel_w=160 if relations else 0;w=max(ellipse_w+120+channel_w,len(system.name)*SECONDARY_FONT+110);h=max(340,sum(case_heights)+45*max(0,len(cases)-1)+130);x=400+si*760;l.system_bounds[system.id]=RectBounds(x,100+h/2,w,h);case_x=x-channel_w/2
   y=180
   for u,ch in zip(cases,case_heights):l.use_case_bounds[u.id]=RectBounds(case_x,y,ellipse_w,ch);y+=ch+45
  actors_by_system={z.id:[] for z in s.systems}
  for a in s.actors:
   linked=[case_by[x.use_case_id] for x in s.associations if x.actor_id==a.id and x.use_case_id in case_by];sid=linked[0].system_id if linked else s.systems[0].id;actors_by_system[sid].append((a,linked))
  for sid,items in actors_by_system.items():
   sb=l.system_bounds[sid]
   for i,(a,linked) in enumerate(items):
    ys=[l.use_case_bounds[x.id].center_y for x in linked];l.actor_points[a.id]=Point(sb.center_x-sb.width/2-105-i*85,sum(ys)/len(ys) if ys else sb.center_y)
  for a in s.associations:
   p=l.actor_points[a.actor_id];b=l.use_case_bounds[a.use_case_id];start=Point(p.x+24,p.y);end=Point(b.center_x-b.width/2,b.center_y);points=[start,end];l.association_lines.append((start,end));l.connectors.append(ConnectorLayout(a.actor_id,a.use_case_id,start,end,points,"straight_association"))
  for ri,r in enumerate(s.use_case_relations):
   a,b=l.use_case_bounds[r.source_use_case_id],l.use_case_bounds[r.target_use_case_id];system=l.system_bounds[case_by[r.source_use_case_id].system_id];route_x=a.center_x+a.width/2+38+(ri%4)*42;route_x=min(route_x,system.center_x+system.width/2-52);start=Point(a.center_x+a.width/2,a.center_y);end=Point(b.center_x+b.width/2,b.center_y);points=[start,Point(route_x,a.center_y),Point(route_x,b.center_y),end];label=f"<<{r.relation_type}>>";l.relation_lines.append((points,r.relation_type));l.connectors.append(ConnectorLayout(r.source_use_case_id,r.target_use_case_id,start,end,points,r.relation_type,label))
  l.width=max(b.center_x+b.width/2 for b in l.system_bounds.values())+80;l.height=max(b.center_y+b.height/2 for b in l.system_bounds.values())+80
  return l
 def draw_actor(self,c,p,name):c.create_oval(p.x-12,p.y-40,p.x+12,p.y-16,fill="white");c.create_line(p.x,p.y-16,p.x,p.y+18);c.create_line(p.x-22,p.y-2,p.x+22,p.y-2);c.create_line(p.x,p.y+18,p.x-18,p.y+40);c.create_line(p.x,p.y+18,p.x+18,p.y+40);c.create_text(p.x,p.y+62,text=name,font=(FONT,MEDIUM_FONT),max_units=8)
 def draw(self,c,s,l):
  c.delete("all");c.create_text(l.width/2,30,text=s.title,font=(FONT,DIAGRAM_TITLE_FONT),font_weight="600",max_units=18)
  for system in s.systems:
   b=l.system_bounds[system.id];c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white")
  for system in s.systems:
   b=l.system_bounds[system.id];c.create_text(b.center_x,b.center_y-b.height/2+28,text=system.name,font=(FONT,SECONDARY_FONT),font_weight="600",max_units=14)
  for x in [z for z in l.connectors if z.connector_type=="straight_association"]:c.create_line(x.start.x,x.start.y,x.end.x,x.end.y,fill="black",width=1,arrow=tk.NONE)
  for points,t in l.relation_lines:
   coords=[v for p in points for v in (p.x,p.y)];c.create_line(*coords,dash=(5,3),arrow="last")
  for u in s.use_cases:
   b=l.use_case_bounds[u.id];c.create_oval(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white")
  for a in s.actors:self.draw_actor(c,l.actor_points[a.id],a.name)
  for u in s.use_cases:
   b=l.use_case_bounds[u.id];c.create_text(b.center_x,b.center_y,text=u.name,font=(FONT,MEDIUM_FONT),max_units=8)
  for x in [z for z in l.connectors if z.connector_type in ("include","extend")]:
   mid=x.points[2];c.create_text(mid.x+6,(x.start.y+x.end.y)/2,text=x.label,font=(FONT,SMALL_FONT),anchor="w")
  c.configure(scrollregion=c.bbox("all"))
 def export_title(self,s):return s.title
 def json_payload(self):return {"diagram_type":"uml_use_case","structure":asdict(self.structure),"layout":asdict(self.layout)}
 def export_visio(self,s,l,out):
  import pythoncom,win32com.client as win32
  out.mkdir(parents=True,exist_ok=True);base=safe_name(s.title)+"_UML用例图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None;scale=.012
  try:
   app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1)
   for x in [z for z in l.connectors if z.connector_type=="straight_association"]:
    ln=page.DrawLine(x.start.x*scale,(800-x.start.y)*scale,x.end.x*scale,(800-x.end.y)*scale);ln.CellsU("BeginArrow").FormulaU="0";ln.CellsU("EndArrow").FormulaU="0"
   for points,t in l.relation_lines:
    for i,(a,b) in enumerate(zip(points,points[1:])):
     ln=page.DrawLine(a.x*scale,(800-a.y)*scale,b.x*scale,(800-b.y)*scale);ln.CellsU("LinePattern").FormulaU="2";ln.CellsU("EndArrow").FormulaU="13" if i==len(points)-2 else "0"
     if i==1:ln.Text=f"<<{t}>>"
   for system in s.systems:
    b=l.system_bounds[system.id];sh=page.DrawRectangle((b.center_x-b.width/2)*scale,(800-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(800-b.center_y+b.height/2)*scale);sh.Text=system.name
   for u in s.use_cases:
    b=l.use_case_bounds[u.id];sh=page.DrawOval((b.center_x-b.width/2)*scale,(800-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(800-b.center_y+b.height/2)*scale);sh.Text=u.name
   for a in s.actors:
    p=l.actor_points[a.id];page.DrawOval((p.x-10)*scale,(800-p.y-35)*scale,(p.x+10)*scale,(800-p.y-15)*scale);page.DrawLine(p.x*scale,(800-p.y+15)*scale,p.x*scale,(800-p.y-15)*scale);page.DrawLine((p.x-18)*scale,(800-p.y+3)*scale,(p.x+18)*scale,(800-p.y+3)*scale);page.DrawLine(p.x*scale,(800-p.y-15)*scale,(p.x-16)*scale,(800-p.y-35)*scale);page.DrawLine(p.x*scale,(800-p.y-15)*scale,(p.x+16)*scale,(800-p.y-35)*scale)
   doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
  finally:
   if doc:doc.Close()
   if app:app.Quit()
   pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
