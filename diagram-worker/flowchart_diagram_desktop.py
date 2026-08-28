# -*- coding: utf-8 -*-
from __future__ import annotations
from dataclasses import dataclass,field
from pathlib import Path
from collections import deque
from offline_diagram_common import *
@dataclass
class FlowNode:id:str;type:str;text:str;source_line:int=0
@dataclass(frozen=True)
class FlowEdge:source:str;target:str;label:str="";source_line:int=0
@dataclass
class FlowChartStructure:title:str;nodes:list[FlowNode];edges:list[FlowEdge]
@dataclass
class FlowConnectorLayout:
 source_id:str;target_id:str;label:str;points:list[Point];arrow_at_end:bool=True;source_port:str="bottom";target_port:str="top";connector_role:str="main";label_position:Point|None=None;label_segment_index:int=-1;label_anchor:str="above_center"
 @property
 def start(self):return self.points[0]
 @property
 def end(self):return self.points[-1]
def place_connector_label(points,label,source_port="bottom"):
 if not label:return None,-1,"above_center"
 segments=list(zip(points,points[1:]));horizontal=[(i,a,b,abs(b.x-a.x)) for i,(a,b) in enumerate(segments) if a.y==b.y and a.x!=b.x]
 if horizontal:
  i,a,b,_=max(horizontal,key=lambda x:x[3]);return Point((a.x+b.x)/2,a.y-12),i,"above_center"
 vertical=[(i,a,b,abs(b.y-a.y)) for i,(a,b) in enumerate(segments) if a.x==b.x and a.y!=b.y]
 if not vertical:return points[0],0,"above_center"
 i,a,b,_=max(vertical,key=lambda x:x[3]);return Point(a.x+16,(a.y+b.y)/2),i,"right_center"
def _label_box(point,label):
 width=max(48,min(260,len(label)*16+18));return RectBounds(point.x,point.y,width,30)
def _boxes_overlap(a,b,padding=8):
 return abs(a.center_x-b.center_x)<(a.width+b.width)/2+padding and abs(a.center_y-b.center_y)<(a.height+b.height)/2+padding
def _layout_extent(node_bounds,label_boxes):
 boxes=list(node_bounds.values())+list(label_boxes)
 return max(b.center_x+b.width/2 for b in boxes)+100,max(b.center_y+b.height/2 for b in boxes)+100
def place_connector_label_clear(points,label,node_bounds,used_boxes):
 if not label:return None,-1,"above_center"
 segments=list(zip(points,points[1:]));label_width=max(48,min(260,len(label)*16+18));candidates=[]
 for i,(a,b) in enumerate(segments):
  if a.y==b.y and a.x!=b.x:
   mid=Point((a.x+b.x)/2,a.y);length=abs(b.x-a.x)
   candidates.extend([(Point(mid.x,mid.y-27),i,"above_center",0,-length),(Point(mid.x,mid.y+27),i,"below_center",1,-length)])
  elif a.x==b.x and a.y!=b.y:
   mid=Point(a.x,(a.y+b.y)/2);length=abs(b.y-a.y);offset=label_width/2+12
   candidates.extend([(Point(mid.x+offset,mid.y),i,"right_center",2,-length),(Point(mid.x-offset,mid.y),i,"left_center",3,-length)])
 def score(item):
  point,_,_,preference,negative_length=item;box=_label_box(point,label)
  node_hits=sum(_boxes_overlap(box,node,5) for node in node_bounds.values());label_hits=sum(_boxes_overlap(box,other,4) for other in used_boxes)
  return node_hits*1000+label_hits*500+preference*5+negative_length/1000
 point,index,anchor,_,_=min(candidates,key=score) if candidates else (points[0],0,"above_center",0,0)
 used_boxes.append(_label_box(point,label));return point,index,anchor
@dataclass
class FlowLayoutResult:node_bounds:dict[str,RectBounds]=field(default_factory=dict);connectors:list[FlowConnectorLayout]=field(default_factory=list);layers:dict[str,int]=field(default_factory=dict);virtual_branch_ids:set[str]=field(default_factory=set);width:float=0;height:float=0
class App(OfflineDiagramApp):
 app_title="标准程序流程图生成器";output_suffix="程序流程图";template_name="标准程序流程图_输入模板.txt";log_path=Path(__file__).with_name("flowchart_diagram_error.log")
 template_text=Path(__file__).with_name(template_name).read_text(encoding="utf-8-sig")
 def parse(self,text):
  issues=[];title="";section="";nodes=[];edges=[];ids=set();edgekeys=set();lines=text.splitlines();allowed={"start","process","decision","input","output","subprocess","branch","merge","end"}
  for no,raw in enumerate(lines,1):
   line=raw.strip()
   if not line or line.startswith("#"):continue
   if re.match(r"^标题\s*[:：]",line):title=re.split(r"[:：]",line,1)[1].strip();continue
   if line=="[节点]":section="nodes";continue
   if line=="[连接]":section="edges";continue
   if section=="nodes":
    parts=[x.strip() for x in line.split("|")]
    if len(parts)!=3:issues.append(ParseIssue(no,"错误","NODE_FORMAT","节点必须包含ID、类型和文字",raw,"写成 N1|process|处理内容。"));continue
    nid,typ,label=parts
    if nid in ids:issues.append(ParseIssue(no,"错误","DUP_NODE","节点ID重复",raw,"使用唯一节点ID。"))
    if typ not in allowed:issues.append(ParseIssue(no,"错误","NODE_TYPE","非法节点类型",raw,"使用 start/process/decision/input/output/subprocess/branch/merge/end。"))
    if not nid or (not label and typ not in ("branch","merge")):issues.append(ParseIssue(no,"错误","EMPTY_NODE","普通节点ID和文字不能为空",raw,"补全节点内容；branch/merge可省略名称。"))
    ids.add(nid);nodes.append(FlowNode(nid,typ,label,no))
   elif section=="edges":
    m=re.match(r"^([^|>\s]+)\s*->\s*([^|\s]+)(?:\s*\|\s*(.+))?$",line)
    if not m:issues.append(ParseIssue(no,"错误","EDGE_FORMAT","连接格式错误",raw,"写成 N1->N2 或 N1->N2|是。"));continue
    a,b,label=m.group(1),m.group(2),m.group(3) or "";key=(a,b,label.strip())
    if a==b:issues.append(ParseIssue(no,"错误","SELF_EDGE","禁止自连接",raw,"连接到其他节点。"))
    if key in edgekeys:issues.append(ParseIssue(no,"错误","DUP_EDGE","连接重复",raw,"删除重复连接。"))
    edgekeys.add(key);edges.append(FlowEdge(a,b,label.strip(),no))
   else:issues.append(ParseIssue(no,"错误","SECTION","内容不在固定区段内",raw,"放入[节点]或[连接]区段。"))
  if not title:issues.append(ParseIssue(1,"错误","NO_TITLE","缺少标题","","增加“标题：流程图名称”。"))
  by={n.id:n for n in nodes};ins={x:[] for x in ids};outs={x:[] for x in ids}
  for e in edges:
   if e.source not in ids or e.target not in ids:issues.append(ParseIssue(e.source_line,"错误","UNKNOWN_NODE","连接引用不存在节点",f"{e.source}->{e.target}","先定义连接两端节点。"));continue
   ins[e.target].append(e);outs[e.source].append(e)
  starts=[n for n in nodes if n.type=="start"];ends=[n for n in nodes if n.type=="end"]
  if not starts:issues.append(ParseIssue(1,"错误","NO_START","至少需要一个开始节点","","增加start节点。"))
  if not ends:issues.append(ParseIssue(1,"警告","FLOW_END_MISSING","当前流程图没有结束节点，仍允许生成预览。","","建议增加 N99|end|结束，并将所有末端流程连接到N99。"))
  for n in nodes:
   if n.type=="start" and ins[n.id]:issues.append(ParseIssue(n.source_line,"错误","START_IN","开始节点不能有入边",n.id,"删除指向开始节点的连接。"))
   if n.type=="end" and outs[n.id]:issues.append(ParseIssue(n.source_line,"错误","END_OUT","结束节点不能有出边",n.id,"删除结束节点的出边。"))
   if n.type=="decision" and len(outs[n.id])<2:issues.append(ParseIssue(n.source_line,"错误","DECISION_BRANCH","判断节点至少需要两个出口",n.id,"增加带条件标签的分支。"))
   if n.type=="decision" and any(not e.label for e in outs[n.id]):issues.append(ParseIssue(n.source_line,"错误","DECISION_LABEL","判断节点的每条出线必须有条件标签",n.id,"增加“是/否”等标签。"))
   if n.type not in ("branch","decision") and len(outs[n.id])>3:issues.append(ParseIssue(n.source_line,"警告","FLOW_TOO_MANY_BRANCHES","节点存在超过3条后续流程，固定四端口无法保持图形清晰。",n.id,"拆分流程节点、显式使用branch，或拆分为子流程图。"))
   if n.type=="branch" and len(outs[n.id])<2:issues.append(ParseIssue(n.source_line,"错误","BRANCH_OUT","branch至少需要两条出线",n.id,"增加并行分支。"))
   if n.type=="branch" and len(ins[n.id])!=1:issues.append(ParseIssue(n.source_line,"错误","BRANCH_IN","branch必须且只能有一条入线",n.id,"保留一条进入分流点的连接。"))
   if n.type=="merge" and len(ins[n.id])<2:issues.append(ParseIssue(n.source_line,"错误","MERGE_IN","merge至少需要两条入线",n.id,"连接至少两个前置分支。"))
   if n.type=="merge" and len(outs[n.id])>1:issues.append(ParseIssue(n.source_line,"错误","MERGE_OUT","merge最多只能有一条出线",n.id,"保留一条后续主流程连接。"))
   if not ins[n.id] and not outs[n.id]:issues.append(ParseIssue(n.source_line,"错误","ISOLATED","节点完全没有连接",n.id,"补充连接。"))
   elif n.type not in ("end",) and not outs[n.id]:issues.append(ParseIssue(n.source_line,"警告","FLOW_DANGLING_NODE",f"节点“{n.id}：{n.text}”没有后续连接，当前流程在此处中断。",n.id,"连接到汇合或结束节点；若确实结束则改为end。"))
  if starts:
   reachable=set();q=deque(n.id for n in starts)
   while q:
    x=q.popleft()
    if x in reachable:continue
    reachable.add(x);q.extend(e.target for e in outs.get(x,[]))
   for n in nodes:
    if n.id not in reachable:issues.append(ParseIssue(n.source_line,"错误","FLOW_UNREACHABLE",f"节点{n.id}无法从开始节点到达",n.id,"连接到可达流程。"))
   can_end=set(n.id for n in ends);changed=True
   while changed:
    changed=False
    for e in edges:
     if e.target in can_end and e.source not in can_end:can_end.add(e.source);changed=True
   if ends:
    for n in nodes:
     if n.id in reachable and n.id not in can_end and outs[n.id]:issues.append(ParseIssue(n.source_line,"错误","FLOW_NO_END_PATH",f"从节点{n.id}无法到达任何结束节点",n.id,"补充到end的路径，避免死循环或悬空分支。"))
  fatal=any(x.severity=="错误" for x in issues);return (None if fatal else FlowChartStructure(title,nodes,edges)),issues
 def fill_tree(self,s):
  self.tree.delete(*self.tree.get_children());a=self.tree.insert("","end",text="节点列表",open=True);b=self.tree.insert("","end",text="连接列表",open=True)
  for n in s.nodes:self.tree.insert(a,"end",text=f"{n.id}｜{n.type}｜{n.text}")
  for e in s.edges:self.tree.insert(b,"end",text=f"{e.source}->{e.target}"+(f"｜{e.label}" if e.label else ""))
 def make_layout(self,s):
  if any(n.type in ("branch","merge") for n in s.nodes):return App._make_legacy_layout(self,s)
  return App._make_direct_layout(self,s)
 def _make_direct_layout(self,s):
  by={n.id:n for n in s.nodes};outs={n.id:[] for n in s.nodes};ins={n.id:[] for n in s.nodes}
  for e in s.edges:outs[e.source].append(e);ins[e.target].append(e)
  start=next(n.id for n in s.nodes if n.type=="start");main=[];main_edges=set();seen=set();current=start
  while current not in seen:
   seen.add(current);main.append(current)
   if not outs[current]:break
   edge=outs[current][0];main_edges.add((edge.source,edge.target,edge.label));current=edge.target
  main_index={x:i for i,x in enumerate(main)};cx=520;side_x=900;row_gap=165;l=FlowLayoutResult()
  for nid in main:
   n=by[nid];y=90+main_index[nid]*row_gap;font=MEDIUM_FONT if n.type=="decision" else NODE_FONT;w=node_width(n.text,font,8,230 if n.type=="decision" else 210);h=node_height(n.text,font,8,110 if n.type=="decision" else 72);l.node_bounds[nid]=RectBounds(cx,y,w,h);l.layers[nid]=main_index[nid]
  branch_role={};branch_owner={};return_exit={}
  for source in main:
   extras=outs[source][1:]
   for branch_no,first in enumerate(extras):
    side=1 if branch_no%2==0 else -1;x=cx+side*310;node=first.target;depth=0;local=set()
    branch_role[(first.source,first.target,first.label)]="decision_side" if by[source].type=="decision" else "side_branch";branch_owner[node]=(source,side)
    while node not in main_index and node not in local:
     local.add(node);n=by[node];y=l.node_bounds[source].center_y+depth*row_gap;font=MEDIUM_FONT if n.type=="decision" else NODE_FONT;w=node_width(n.text,font,8,230 if n.type=="decision" else 210);h=node_height(n.text,font,8,110 if n.type=="decision" else 72);l.node_bounds[node]=RectBounds(x,y,w,h);l.layers[node]=main_index[source]+depth;nexts=outs[node]
     if not nexts:break
     edge=nexts[0]
     if edge.target in main_index:
      key=(edge.source,edge.target,edge.label);branch_role[key]="branch_return";branch_node_count=depth+1;is_immediate_return=len(nexts)==1;return_exit[key]="left" if branch_node_count>=2 and is_immediate_return else "bottom";break
     branch_role[(edge.source,edge.target,edge.label)]="branch_internal";branch_owner[edge.target]=(source,side);node=edge.target;depth+=1
  # Any remaining non-main nodes get a deterministic side position.
  for n in s.nodes:
   if n.id not in l.node_bounds:l.node_bounds[n.id]=RectBounds(side_x,90+len(l.node_bounds)*row_gap,node_width(n.text,NODE_FONT,8,210),node_height(n.text,NODE_FONT,8,72))
  def port(b,name):return {"top":Point(b.center_x,b.center_y-b.height/2),"bottom":Point(b.center_x,b.center_y+b.height/2),"left":Point(b.center_x-b.width/2,b.center_y),"right":Point(b.center_x+b.width/2,b.center_y)}[name]
  used_label_boxes=[]
  for e in s.edges:
   key=(e.source,e.target,e.label);role=branch_role.get(key)
   if key in main_edges:role="decision_main" if by[e.source].type=="decision" else "main"
   role=role or ("branch_return" if e.target in main_index and e.source not in main_index else "branch_internal")
   a,b=l.node_bounds[e.source],l.node_bounds[e.target]
   if role in ("main","decision_main","branch_internal"):sp,tp="bottom","top"
   elif role in ("side_branch","decision_side"):sp,tp=("right","left") if b.center_x>a.center_x else ("left","right")
   elif role=="branch_return":sp,tp=(return_exit.get(key,"bottom"),"right" if a.center_x>b.center_x else "left")
   else:sp,tp=("left","right") if a.center_x>b.center_x else ("right","left")
   start_p,end_p=port(a,sp),port(b,tp)
   if start_p.x==end_p.x or start_p.y==end_p.y:points=[start_p,end_p]
   elif role=="branch_return" and sp=="bottom":points=[start_p,Point(start_p.x,end_p.y),end_p]
   elif role=="branch_return":
    channel_x=(start_p.x+end_p.x)/2;points=[start_p,Point(channel_x,start_p.y),Point(channel_x,end_p.y),end_p]
   else:
    channel_y=(start_p.y+end_p.y)/2;points=[start_p,Point(start_p.x,channel_y),Point(end_p.x,channel_y),end_p]
   lp,lsi,la=place_connector_label_clear(points,e.label,l.node_bounds,used_label_boxes);l.connectors.append(FlowConnectorLayout(e.source,e.target,e.label,points,True,sp,tp,role,lp,lsi,la))
  l.width,l.height=_layout_extent(l.node_bounds,used_label_boxes);return l
 def _make_legacy_layout(self,s):
  original={n.id:n for n in s.nodes};nodes=dict(original);edges=list(s.edges);virtual=set()
  raw_out={n.id:[] for n in s.nodes}
  for e in s.edges:raw_out[e.source].append(e)
  outs={x:[] for x in nodes};ins={x:[] for x in nodes}
  for e in edges:outs[e.source].append(e);ins[e.target].append(e)
  indeg={x:len(ins[x]) for x in nodes};layer={};q=deque([x for x in nodes if indeg[x]==0])
  while q:
   x=q.popleft();layer.setdefault(x,0)
   for e in outs[x]:layer[e.target]=max(layer.get(e.target,0),layer[x]+1);indeg[e.target]-=1;q.append(e.target) if indeg[e.target]==0 else None
  for x in nodes:layer.setdefault(x,max(layer.values(),default=0)+1)
  col={next(n.id for n in s.nodes if n.type=="start"):0}
  for x in sorted(nodes,key=lambda z:layer[z]):
   col.setdefault(x,0)
   for i,e in enumerate(outs[x]):
    if nodes[e.target].type=="merge":col[e.target]=0
    elif nodes[x].type in ("branch","decision") or len(outs[x])>1:col[e.target]=col[x] if i==0 else (i+1)//2*(1 if i%2 else -1)
    else:col.setdefault(e.target,col[x])
  cx=520;col_gap=350;row_gap=165;l=FlowLayoutResult(layers=layer,virtual_branch_ids=virtual)
  for nid,n in nodes.items():
   x,y=cx+col[nid]*col_gap,90+layer[nid]*row_gap;font=MEDIUM_FONT if n.type=="decision" else NODE_FONT;w,h=((10,10) if n.type in ("branch","merge") else (node_width(n.text,font,8,230 if n.type=="decision" else 210),node_height(n.text,font,8,110 if n.type=="decision" else 72)));l.node_bounds[nid]=RectBounds(x,y,w,h)
  def port(b,name):
   return {"top":Point(b.center_x,b.center_y-b.height/2),"bottom":Point(b.center_x,b.center_y+b.height/2),"left":Point(b.center_x-b.width/2,b.center_y),"right":Point(b.center_x+b.width/2,b.center_y)}[name]
  used_label_boxes=[]
  for e in edges:
   a,b=l.node_bounds[e.source],l.node_bounds[e.target];st=nodes[e.source].type;tt=nodes[e.target].type;sc,tc=col[e.source],col[e.target]
   if st in ("branch","decision"):
    index=outs[e.source].index(e);side="bottom" if index==0 else ("right" if tc>sc else "left");start=port(a,side)
   else:start=port(a,"bottom")
   if tt=="merge":end=port(b,"top" if sc==0 else ("right" if sc>0 else "left"))
   elif sc==tc:end=port(b,"top")
   else:end=port(b,"left" if tc>sc else "right")
   if start.x==end.x or start.y==end.y:points=[start,end]
   elif st in ("branch","decision") and start.y==a.center_y:points=[start,Point(end.x,start.y),end]
   elif tt=="merge" and end.y==b.center_y:points=[start,Point(start.x,end.y),end]
   else:
    mid_y=(start.y+end.y)/2;points=[start,Point(start.x,mid_y),Point(end.x,mid_y),end]
   lp,lsi,la=place_connector_label_clear(points,e.label,l.node_bounds,used_label_boxes);l.connectors.append(FlowConnectorLayout(e.source,e.target,e.label,points,True,"bottom","top","legacy",lp,lsi,la))
  l.width,l.height=_layout_extent(l.node_bounds,used_label_boxes);return l
 def draw(self,c,s,l):
  c.delete("all");c.create_text(l.width/2-50,20,text=s.title,font=(FONT,DIAGRAM_TITLE_FONT),font_weight="600",max_units=18)
  for x in l.connectors:
   coords=[v for p in x.points for v in (p.x,p.y)];c.create_line(*coords,arrow="last")
   if x.label and x.label_position:c.create_text(x.label_position.x,x.label_position.y,text=x.label,font=(FONT,EDGE_LABEL_FONT),anchor="center",max_units=8)
  draw_nodes=list(s.nodes)+[FlowNode(x,"branch","") for x in l.virtual_branch_ids]
  for n in draw_nodes:
   b=l.node_bounds[n.id];x,y=b.center_x,b.center_y
   if n.type in ("branch","merge"):c.create_oval(x-5,y-5,x+5,y+5,fill="black",outline="black");continue
   if n.type=="decision":c.create_polygon(x,y-b.height/2,x+b.width/2,y,x,y+b.height/2,x-b.width/2,y,fill="white",outline="black")
   elif n.type in ("start","end"):
    terminator=getattr(c,"create_terminator",None)
    if terminator:terminator(x-b.width/2,y-b.height/2,x+b.width/2,y+b.height/2,fill="white")
    else:c.create_oval(x-b.width/2,y-b.height/2,x+b.width/2,y+b.height/2,fill="white")
   elif n.type in ("input","output"):c.create_polygon(x-b.width/2+15,y-b.height/2,x+b.width/2,y-b.height/2,x+b.width/2-15,y+b.height/2,x-b.width/2,y+b.height/2,fill="white",outline="black")
   else:c.create_rectangle(x-b.width/2,y-b.height/2,x+b.width/2,y+b.height/2,fill="white")
   c.create_text(x,y,text=n.text,font=(FONT,MEDIUM_FONT if n.type=="decision" else NODE_FONT),max_units=8)
  c.configure(scrollregion=c.bbox("all"))
 def export_title(self,s):return s.title
 def export_visio(self,s,l,out):
  import pythoncom,win32com.client as win32
  out.mkdir(parents=True,exist_ok=True);base=safe_name(s.title)+"_程序流程图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None;scale=.012
  try:
   app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1)
   for x in l.connectors:
    for i,(a,b) in enumerate(zip(x.points,x.points[1:])):
     ln=page.DrawLine(a.x*scale,(l.height-a.y)*scale,b.x*scale,(l.height-b.y)*scale);ln.CellsU("EndArrow").FormulaU="13" if i==len(x.points)-2 else "0";
    if x.label and x.label_position:
     p=x.label_position;tw=max(70,len(x.label)*15);th=24;label_shape=page.DrawRectangle((p.x-tw/2)*scale,(l.height-p.y-th/2)*scale,(p.x+tw/2)*scale,(l.height-p.y+th/2)*scale);label_shape.Text=x.label;label_shape.CellsU("LinePattern").FormulaU="0";label_shape.CellsU("FillPattern").FormulaU="0"
   export_nodes=list(s.nodes)+[FlowNode(x,"branch","") for x in l.virtual_branch_ids]
   for n in export_nodes:
    b=l.node_bounds[n.id];x1=(b.center_x-b.width/2)*scale;x2=(b.center_x+b.width/2)*scale;y1=(l.height-b.center_y-b.height/2)*scale;y2=(l.height-b.center_y+b.height/2)*scale
    if n.type in ("branch","merge"):sh=page.DrawOval(x1,y1,x2,y2);sh.CellsU("FillForegnd").FormulaU="RGB(0,0,0)"
    elif n.type in ("start","end"):sh=page.DrawOval(x1,y1,x2,y2);sh.Text=n.text
    else:sh=page.DrawRectangle(x1,y1,x2,y2);sh.Text=n.text
   doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
  finally:
   if doc:doc.Close()
   if app:app.Quit()
   pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
