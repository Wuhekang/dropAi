# -*- coding: utf-8 -*-
from __future__ import annotations
from dataclasses import dataclass,field
from pathlib import Path
from offline_diagram_common import *
@dataclass
class ArchitectureComponent:name:str
@dataclass
class ArchitectureLayer:name:str;components:list[ArchitectureComponent]
@dataclass
class SystemArchitectureStructure:title:str;layers:list[ArchitectureLayer]
@dataclass
class SystemArchitectureLayoutResult:layer_bounds:dict[str,RectBounds]=field(default_factory=dict);component_bounds:dict[tuple,RectBounds]=field(default_factory=dict);connectors:list[tuple[Point,Point]]=field(default_factory=list)
class App(OfflineDiagramApp):
 app_title="系统架构图生成器";output_suffix="系统架构图";template_name="系统架构图_输入模板.txt";log_path=Path(__file__).with_name("system_architecture_diagram_error.log");template_text=Path(__file__).with_name(template_name).read_text(encoding="utf-8-sig")
 def parse(self,text):
  issues=[];title="";layers=[];pending=None;seen=set();effective=0
  for no,raw in enumerate(text.splitlines(),1):
   line=raw.strip()
   if not line or line.startswith("#"):continue
   effective+=1;m=re.match(r"^(标题|层|组件)\s*[:：]\s*(.*)$",line)
   if not m:issues.append(ParseIssue(no,"错误","UNKNOWN_LINE","非法额外内容",raw,"仅使用标题、层、组件格式。"));continue
   kind,value=m.group(1),m.group(2).strip()
   if not value:issues.append(ParseIssue(no,"错误","EMPTY_VALUE",f"{kind}不能为空",raw,"在冒号后填写内容。"));continue
   if kind=="标题":
    if effective!=1 or title:issues.append(ParseIssue(no,"错误","TITLE_ORDER","标题必须是唯一的第一条有效内容",raw,"将标题移到最前并删除重复标题。"))
    else:title=value
   elif kind=="层":
    if pending is not None:issues.append(ParseIssue(no,"错误","MISSING_COMPONENT","上一层缺少紧随其后的组件行",raw,"为上一层增加组件行。"))
    if value in seen:issues.append(ParseIssue(no,"错误","DUP_LAYER","层名称重复",raw,"修改重复层名。"))
    pending=value;seen.add(value)
   else:
    if pending is None:issues.append(ParseIssue(no,"错误","ORPHAN_COMPONENT","组件行前没有层",raw,"先定义层。"));continue
    values=[x.strip() for x in re.split(r"[,，]",value) if x.strip()]
    if len(values)!=len(set(values)):issues.append(ParseIssue(no,"错误","DUP_COMPONENT","同层组件重复",raw,"删除重复组件。"))
    layers.append(ArchitectureLayer(pending,[ArchitectureComponent(x) for x in values]));pending=None
  if pending:issues.append(ParseIssue(len(text.splitlines()) or 1,"错误","MISSING_COMPONENT","最后一层缺少组件",pending,"增加组件行。"))
  if not title:issues.append(ParseIssue(1,"错误","NO_TITLE","缺少标题","","首行写标题：名称。"))
  if len(layers)<2:issues.append(ParseIssue(1,"错误","FEW_LAYERS","至少需要两个完整层","","增加层和组件。"))
  return (SystemArchitectureStructure(title,layers) if not issues else None),issues
 def fill_tree(self,s):
  self.tree.delete(*self.tree.get_children());root=self.tree.insert("","end",text=s.title,open=True)
  for layer in s.layers:
   lid=self.tree.insert(root,"end",text=layer.name,open=True)
   for c in layer.components:self.tree.insert(lid,"end",text=c.name)
 def make_layout(self,s):
  result=SystemArchitectureLayoutResult();width=900;y=110
  for layer in s.layers:
   h=110;lb=RectBounds(500,y,width,h);result.layer_bounds[layer.name]=lb;usable=width-190;step=usable/max(1,len(layer.components))
   for i,c in enumerate(layer.components):result.component_bounds[(layer.name,c.name)]=RectBounds(500-width/2+160+step*(i+.5),y,min(180,step-16),56)
   y+=155
  for a,b in zip(s.layers,s.layers[1:]):
   x=500;aa=result.layer_bounds[a.name];bb=result.layer_bounds[b.name];result.connectors.append((Point(x,aa.center_y+aa.height/2),Point(x,bb.center_y-bb.height/2)))
  return result
 def draw(self,c,s,l):
  c.delete("all")
  for a,b in l.connectors:c.create_line(a.x,a.y,b.x,b.y,arrow="both")
  for layer in s.layers:
   b=l.layer_bounds[layer.name];c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white");c.create_line(b.center_x-b.width/2+140,b.center_y-b.height/2,b.center_x-b.width/2+140,b.center_y+b.height/2);c.create_text(b.center_x-b.width/2+70,b.center_y,text=layer.name,font=(FONT,11))
   for comp in layer.components:
    q=l.component_bounds[(layer.name,comp.name)];c.create_rectangle(q.center_x-q.width/2,q.center_y-q.height/2,q.center_x+q.width/2,q.center_y+q.height/2,fill="white");c.create_text(q.center_x,q.center_y,text=comp.name,width=q.width-8,font=(FONT,9))
  c.configure(scrollregion=c.bbox("all"))
 def export_title(self,s):return s.title
 def export_visio(self,s,l,out):
  import pythoncom,win32com.client as win32
  out.mkdir(parents=True,exist_ok=True);base=safe_name(s.title)+"_系统架构图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None;scale=.01
  try:
   app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1)
   for a,b in l.connectors:ln=page.DrawLine(a.x*scale,(1000-a.y)*scale,b.x*scale,(1000-b.y)*scale);ln.CellsU("BeginArrow").FormulaU="13";ln.CellsU("EndArrow").FormulaU="13"
   for layer in s.layers:
    b=l.layer_bounds[layer.name];page.DrawRectangle((b.center_x-b.width/2)*scale,(1000-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(1000-b.center_y+b.height/2)*scale);t=page.DrawRectangle((b.center_x-b.width/2)*scale,(1000-b.center_y-b.height/2)*scale,(b.center_x-b.width/2+140)*scale,(1000-b.center_y+b.height/2)*scale);t.Text=layer.name
    for comp in layer.components:
     q=l.component_bounds[(layer.name,comp.name)];sh=page.DrawRectangle((q.center_x-q.width/2)*scale,(1000-q.center_y-q.height/2)*scale,(q.center_x+q.width/2)*scale,(1000-q.center_y+q.height/2)*scale);sh.Text=comp.name
   doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
  finally:
   if doc:doc.Close()
   if app:app.Quit()
   pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
