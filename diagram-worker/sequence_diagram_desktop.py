# -*- coding: utf-8 -*-
from __future__ import annotations
from dataclasses import dataclass,field
from pathlib import Path
import queue,threading
from offline_diagram_common import *
@dataclass
class SequenceParticipant:participant_id:str;participant_type:str;name:str;source_line:int
@dataclass
class SequenceMessage:index:int;source_id:str;target_id:str;text:str;message_type:str;source_line:int
@dataclass
class ActivationRange:participant_id:str;start_message:int;end_message:int
@dataclass
class SequenceDocument:title:str;participants:list[SequenceParticipant];messages:list[SequenceMessage];activations:list[ActivationRange]
@dataclass
class MessageLayout:message:SequenceMessage;y:float;start:Point;end:Point
@dataclass
class SequenceLayoutResult:participant_bounds:dict[str,RectBounds]=field(default_factory=dict);lifeline_x:dict[str,float]=field(default_factory=dict);message_layouts:list[MessageLayout]=field(default_factory=list);activation_bounds:list[tuple[ActivationRange,RectBounds]]=field(default_factory=list);width:float=0;height:float=0
class App(OfflineDiagramApp):
 app_title="UML时序图生成器";output_suffix="UML时序图";template_name="UML时序图_输入模板.txt";log_path=Path(__file__).with_name("sequence_diagram_error.log");template_text=Path(__file__).with_name(template_name).read_text(encoding="utf-8-sig")
 def __init__(self):
  self._export_events=queue.Queue();super().__init__();self.after(100,self._poll_export_events)
 def _poll_export_events(self):
  try:
   kind,data=self._export_events.get_nowait()
   if kind=="ok":self._export_done(*data)
   else:messagebox.showerror("导出失败","Visio 导出失败，详细信息已写入 sequence_diagram_error.log。");self.status.config(text="导出失败。")
  except queue.Empty:pass
  self.after(100,self._poll_export_events)
 def parse(self,text):
  issues=[];title="";section="";participants=[];messages=[];events=[];ids=set();sections=set();lines=text.splitlines();allowed={"actor","boundary","control","service","database"}
  for no,raw in enumerate(lines,1):
   line=raw.strip()
   if not line or line.startswith("#"):continue
   if re.match(r"^标题\s*[:：]",line):
    if title:issues.append(ParseIssue(no,"错误","SEQ_DUP_TITLE","只能有一个标题",raw,"删除重复标题。"))
    title=re.split(r"[:：]",line,1)[1].strip();continue
   if line in ("[参与者]","[消息]","[激活]"):section=line;sections.add(line);continue
   if section=="[参与者]":
    p=[x.strip() for x in line.split("|",2)]
    if len(p)!=3:issues.append(ParseIssue(no,"错误","SEQ_PARTICIPANT_FORMAT","参与者格式不正确",raw,"正确格式：A1|actor|用户。"));continue
    if p[0] in ids:issues.append(ParseIssue(no,"错误","SEQ_DUP_ID","参与者ID重复",raw,"使用唯一ID。"))
    if p[1] not in allowed:issues.append(ParseIssue(no,"错误","SEQ_PARTICIPANT_TYPE","参与者类型非法",raw,"使用actor/boundary/control/service/database。"))
    if not p[2]:issues.append(ParseIssue(no,"错误","SEQ_EMPTY_NAME","参与者名称不能为空",raw,"填写中文名称。"))
    if re.search(r"Mapper|映射层|数据访问映射器",p[2],re.I):issues.append(ParseIssue(no,"错误","SEQ_MAPPER_FORBIDDEN","时序图禁止Mapper或映射层",raw,"由service直接与database交互。"))
    ids.add(p[0]);participants.append(SequenceParticipant(p[0],p[1],p[2],no))
   elif section=="[消息]":
    m=re.match(r"^([^|>\s-]+)(-->|->)([^|\s]+)\|(.+)$",line)
    if not m:issues.append(ParseIssue(no,"错误","SEQ_MESSAGE_FORMAT","消息格式不正确",raw,"正确格式：A1->A2|消息内容。"));continue
    if len(messages)>=8:issues.append(ParseIssue(no,"错误","SEQ_MESSAGE_LIMIT",f"时序图最多允许8条消息，当前检测到第{len(messages)+1}条",raw,"删除非核心交互或拆分成两张图。"))
    if re.search(r"Mapper|映射层|数据访问映射器",m.group(4),re.I):issues.append(ParseIssue(no,"错误","SEQ_MAPPER_FORBIDDEN","消息中禁止Mapper或映射层",raw,"改为service直接访问database。"))
    messages.append(SequenceMessage(len(messages)+1,m.group(1),m.group(3),m.group(4).strip(),"return" if m.group(2)=="-->" else "call",no))
   elif section=="[激活]":
    p=[x.strip() for x in line.split("|")]
    if len(p)!=3 or p[0] not in ("activate","deactivate") or not p[2].isdigit():issues.append(ParseIssue(no,"错误","SEQ_ACTIVATION_FORMAT","激活格式不正确",raw,"正确格式：activate|A2|2。"));continue
    events.append((p[0],p[1],int(p[2]),no,raw))
   else:issues.append(ParseIssue(no,"错误","SEQ_SECTION","内容不在规定区段",raw,"放入[参与者]、[消息]或[激活]。"))
  if not title:issues.append(ParseIssue(1,"错误","SEQ_NO_TITLE","缺少标题","","增加标题：名称。"))
  for s in ("[参与者]","[消息]"):
   if s not in sections:issues.append(ParseIssue(1,"错误","SEQ_MISSING_SECTION",f"缺少{s}","",f"增加{s}。"))
  if not 2<=len(participants)<=6:issues.append(ParseIssue(1,"错误","SEQ_PARTICIPANT_COUNT","参与者数量必须为2至6个","","调整参与者数量。"))
  if not 1<=len(messages)<=8:issues.append(ParseIssue(1,"错误","SEQ_MESSAGE_COUNT","消息数量必须为1至8条","","调整消息数量。"))
  for m in messages:
   if m.source_id not in ids or m.target_id not in ids:issues.append(ParseIssue(m.source_line,"错误","SEQ_UNKNOWN_PARTICIPANT","消息引用不存在的参与者",lines[m.source_line-1],"先定义消息两端参与者。"))
  activations=[];open_ranges={}
  for kind,pid,index,no,raw in events:
   if pid not in ids:issues.append(ParseIssue(no,"错误","SEQ_ACTIVATION_UNKNOWN","激活引用不存在参与者",raw,"修正参与者ID。"));continue
   if not 1<=index<=len(messages):issues.append(ParseIssue(no,"错误","SEQ_ACTIVATION_INDEX","激活消息序号超出范围",raw,"使用有效消息序号。"));continue
   if kind=="activate":
    if pid in open_ranges:issues.append(ParseIssue(no,"错误","SEQ_ACTIVATION_OVERLAP","参与者尚未结束上一次激活",raw,"先deactivate再重新activate。"))
    else:open_ranges[pid]=(index,no)
   else:
    if pid not in open_ranges:issues.append(ParseIssue(no,"错误","SEQ_DEACTIVATE_WITHOUT_ACTIVATE","deactivate没有对应activate",raw,"先增加activate。"))
    else:
     start,start_line=open_ranges.pop(pid)
     if index<start:issues.append(ParseIssue(no,"错误","SEQ_ACTIVATION_ORDER","激活结束序号小于开始序号",raw,"增大结束序号。"))
     else:activations.append(ActivationRange(pid,start,index))
  for pid,(index,no) in open_ranges.items():issues.append(ParseIssue(no,"错误","SEQ_ACTIVATION_UNCLOSED","activate缺少对应deactivate",lines[no-1],"增加deactivate。"))
  return (SequenceDocument(title,participants,messages,activations) if not issues else None),issues
 def fill_tree(self,s):
  self.tree.delete(*self.tree.get_children());p=self.tree.insert("","end",text="参与者",open=True);m=self.tree.insert("","end",text="消息",open=True);a=self.tree.insert("","end",text="激活",open=True)
  for x in s.participants:self.tree.insert(p,"end",text=f"{x.name}｜{x.participant_type}")
  for x in s.messages:self.tree.insert(m,"end",text=f"{x.index}. {x.source_id}->{x.target_id}｜{x.text}")
  for x in s.activations:self.tree.insert(a,"end",text=f"{x.participant_id}｜{x.start_message}-{x.end_message}")
 def make_layout(self,s):
  l=SequenceLayoutResult();margin=90;spacing=max(190,max((len(x.text)*13+70 for x in s.messages),default=190));header_y=80;message_start=180;message_gap=78
  for i,p in enumerate(s.participants):
   x=margin+i*spacing;l.lifeline_x[p.participant_id]=x;l.participant_bounds[p.participant_id]=RectBounds(x,header_y,150,54)
  last_y=message_start+(len(s.messages)-1)*message_gap
  for a in s.activations:
   y1=message_start+(a.start_message-1)*message_gap-18;y2=message_start+(a.end_message-1)*message_gap+22;l.activation_bounds.append((a,RectBounds(l.lifeline_x[a.participant_id],(y1+y2)/2,16,y2-y1)))
  def activation_at(pid,index):
   return next((b for a,b in l.activation_bounds if a.participant_id==pid and a.start_message<=index<=a.end_message),None)
  for m in s.messages:
   y=message_start+(m.index-1)*message_gap;sx,tx=l.lifeline_x[m.source_id],l.lifeline_x[m.target_id];sb,tb=activation_at(m.source_id,m.index),activation_at(m.target_id,m.index);direction=1 if tx>sx else -1
   start=Point(sx+(sb.width/2*direction if sb else 0),y);end=Point(tx-(tb.width/2*direction if tb else 0),y);l.message_layouts.append(MessageLayout(m,y,start,end))
  l.width=margin*2+(len(s.participants)-1)*spacing;l.height=last_y+100;return l
 def draw_actor(self,c,b,name):
  x,y=b.center_x,b.center_y;c.create_oval(x-9,y-23,x+9,y-5,fill="white");c.create_line(x,y-5,x,y+14);c.create_line(x-16,y+2,x+16,y+2);c.create_line(x,y+14,x-14,y+28);c.create_line(x,y+14,x+14,y+28);c.create_text(x,y+43,text=name,font=(FONT,9))
 def draw(self,c,s,l):
  c.delete("all");bottom=l.height-35
  for p in s.participants:
   b=l.participant_bounds[p.participant_id]
   if p.participant_type=="actor":self.draw_actor(c,b,p.name)
   elif p.participant_type=="database":c.create_oval(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y-b.height/2+16,fill="white");c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2+8,b.center_x+b.width/2,b.center_y+b.height/2,fill="white");c.create_oval(b.center_x-b.width/2,b.center_y+b.height/2-16,b.center_x+b.width/2,b.center_y+b.height/2,fill="white");c.create_text(b.center_x,b.center_y,text=p.name,font=(FONT,9))
   else:c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white");c.create_text(b.center_x,b.center_y,text=p.name,font=(FONT,9))
   c.create_line(l.lifeline_x[p.participant_id],b.center_y+b.height/2,l.lifeline_x[p.participant_id],bottom,dash=(5,4))
  for a,b in l.activation_bounds:c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white")
  for item in l.message_layouts:
   dash=(5,3) if item.message.message_type=="return" else None;c.create_line(item.start.x,item.y,item.end.x,item.y,arrow="last",dash=dash);c.create_text((item.start.x+item.end.x)/2,item.y-13,text=f"{item.message.index}. {item.message.text}",font=(FONT,9))
  c.configure(scrollregion=c.bbox("all"))
 def export_title(self,s):return s.title
 def json_payload(self):
  return {"diagram_type":"uml_sequence","title":self.structure.title,"participants":[asdict(x) for x in self.structure.participants],"messages":[asdict(x) for x in self.structure.messages],"activations":[asdict(x) for x in self.structure.activations],"layout":asdict(self.layout)}
 def export(self):
  if not self.structure or not self.layout:return messagebox.showwarning("提示","请先检查格式并生成预览。")
  out=filedialog.askdirectory()
  if not out:return
  self.status.config(text="正在后台导出 VSDX、PNG 和 JSON……")
  threading.Thread(target=self._export_worker,args=(Path(out),self.structure,self.layout),daemon=True).start()
 def _export_worker(self,out,s,l):
  try:
   vsdx,png=self.export_visio(s,l,out);payload={"diagram_type":"uml_sequence","title":s.title,"participants":[asdict(x) for x in s.participants],"messages":[asdict(x) for x in s.messages],"activations":[asdict(x) for x in s.activations],"layout":asdict(l)};json_path=out/f"{safe_name(s.title)}_{self.output_suffix}.json";json_path.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8");self._export_events.put(("ok",(vsdx,png)))
  except Exception:self.log_error();self._export_events.put(("error",None))
 def _export_done(self,vsdx,png):
  self.last_vsdx,self.last_png=vsdx,png;self.status.config(text="导出完成。");messagebox.showinfo("导出完成",f"已生成：\n{vsdx}\n{png}")
 def export_visio(self,s,l,out):
  import pythoncom,win32com.client as win32
  out.mkdir(parents=True,exist_ok=True);base=safe_name(s.title)+"_UML时序图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None;scale=.012
  try:
   app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1)
   for p in s.participants:
    b=l.participant_bounds[p.participant_id];sh=page.DrawRectangle((b.center_x-b.width/2)*scale,(l.height-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(l.height-b.center_y+b.height/2)*scale);sh.Text=p.name;life=page.DrawLine(b.center_x*scale,(l.height-b.center_y-b.height/2)*scale,b.center_x*scale,35*scale);life.CellsU("LinePattern").FormulaU="2"
   for a,b in l.activation_bounds:page.DrawRectangle((b.center_x-b.width/2)*scale,(l.height-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(l.height-b.center_y+b.height/2)*scale)
   for item in l.message_layouts:
    ln=page.DrawLine(item.start.x*scale,(l.height-item.y)*scale,item.end.x*scale,(l.height-item.y)*scale);ln.CellsU("EndArrow").FormulaU="13";ln.Text=f"{item.message.index}. {item.message.text}";
    if item.message.message_type=="return":ln.CellsU("LinePattern").FormulaU="2"
   doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
  finally:
   if doc:doc.Close()
   if app:app.Quit()
   pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
