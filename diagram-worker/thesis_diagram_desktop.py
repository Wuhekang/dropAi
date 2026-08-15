# -*- coding: utf-8 -*-
from __future__ import annotations
import json,os,traceback,platform,struct,threading,queue,subprocess,sys
from dataclasses import asdict,is_dataclass
from datetime import datetime
from pathlib import Path
import tkinter as tk
from tkinter import filedialog,messagebox,ttk
from diagram_core.geometry import DiagramBounds,fit_transform
from diagram_core.registry import PLUGIN_REGISTRY,DISPLAY,CANONICAL,detect_header,suggest_body_type
from diagram_core.types import DiagramSession
from diagram_plugins import register_plugins
from offline_diagram_common import read_text,safe_name
register_plugins()

class CodeEditor(ttk.Frame):
 def __init__(self,master,on_change):
  super().__init__(master);self.lines=tk.Canvas(self,width=46,bg="#f3f3f3",highlightthickness=0);self.text=tk.Text(self,wrap="none",undo=True,font=("Consolas",10));ys=ttk.Scrollbar(self,orient="vertical",command=self._yview);xs=ttk.Scrollbar(self,orient="horizontal",command=self.text.xview);self.text.configure(yscrollcommand=lambda a,b:(ys.set(a,b),self.redraw()),xscrollcommand=xs.set);self.lines.grid(row=0,column=0,sticky="ns");self.text.grid(row=0,column=1,sticky="nsew");ys.grid(row=0,column=2,sticky="ns");xs.grid(row=1,column=1,sticky="ew");self.columnconfigure(1,weight=1);self.rowconfigure(0,weight=1);self.text.bind("<<Modified>>",on_change);self.text.bind("<Configure>",lambda e:self.redraw());self.redraw()
 def _yview(self,*args):self.text.yview(*args);self.redraw()
 def redraw(self):
  self.lines.delete("all");i=self.text.index("@0,0")
  while True:
   info=self.text.dlineinfo(i)
   if not info:break
   self.lines.create_text(40,info[1],anchor="ne",text=i.split('.')[0],fill="#666",font=("Consolas",9));i=self.text.index(f"{i}+1line")
 def highlight(self,line):self.text.tag_remove("issue","1.0","end");self.text.tag_config("issue",background="#ffe1e1");self.text.tag_add("issue",f"{line}.0",f"{line}.end");self.text.mark_set("insert",f"{line}.0");self.text.see(f"{line}.0");self.text.focus_set()

class Preview(ttk.Frame):
 def __init__(self,master,status_callback):
  super().__init__(master);self.canvas=tk.Canvas(self,bg="#FEFEFE",highlightthickness=0);xs=ttk.Scrollbar(self,orient="horizontal",command=self.canvas.xview);ys=ttk.Scrollbar(self,orient="vertical",command=self.canvas.yview);self.canvas.configure(xscrollcommand=xs.set,yscrollcommand=ys.set);self.canvas.grid(row=0,column=0,sticky="nsew");ys.grid(row=0,column=1,sticky="ns");xs.grid(row=1,column=0,sticky="ew");self.rowconfigure(0,weight=1);self.columnconfigure(0,weight=1);self.scale=1;self.status_callback=status_callback;self.pan=None;self.user_zoomed=False;self._fit_retry=None;self.canvas.bind("<MouseWheel>",self.zoom);self.canvas.bind("<ButtonPress-2>",self.pan_start);self.canvas.bind("<B2-Motion>",self.pan_move)
 def pan_start(self,e):self.canvas.scan_mark(e.x,e.y)
 def pan_move(self,e):self.canvas.scan_dragto(e.x,e.y,gain=1)
 def zoom(self,e):
  f=1.1 if e.delta>0 else .9;self.canvas.scale("all",e.x,e.y,f,f);self.scale*=f;self.user_zoomed=True;self.canvas.configure(scrollregion=self.canvas.bbox("all"));self.status_callback()
 def fit(self):
  bb=self.canvas.bbox("all")
  if not bb:return
  self.canvas.update_idletasks();cw=self.canvas.winfo_width();ch=self.canvas.winfo_height()
  if cw<=10 or ch<=10:
   if self._fit_retry:self.after_cancel(self._fit_retry)
   self._fit_retry=self.after(100,self.fit);return
  left,top,right,bottom=bb;dw=max(1,right-left);dh=max(1,bottom-top);margin=24;fit_factor=min((cw-2*margin)/dw,(ch-2*margin)/dh);target=max(.10,min(1.50,self.scale*fit_factor));factor=target/max(self.scale,1e-9);self.canvas.scale("all",0,0,factor,factor);new_left=left*factor;new_top=top*factor;new_width=dw*factor;new_height=dh*factor;self.canvas.move("all",(cw-new_width)/2-new_left,(ch-new_height)/2-new_top);self.scale=target;self.user_zoomed=False;self.canvas.configure(scrollregion=self.canvas.bbox("all"));self.canvas.xview_moveto(0);self.canvas.yview_moveto(0);self.status_callback()

class ThesisDiagramApp(tk.Tk):
 def __init__(self,preset=None):
  super().__init__();self.title("ThesisDiagram毕业设计图形生成器");self.geometry("1600x900");self.minsize(1100,680);self.session=None;self.plugin=None;self.last_vsdx=self.last_png=self.last_json=self.last_output_dir=self.output_dir=None;self.last_vsdx_error="";self.live=tk.BooleanVar(value=False);self.pending=None;self.resize_pending=None;self._build();self.protocol("WM_DELETE_WINDOW",self.destroy);self.after_idle(self._maximize_window)
  if preset:self.insert_template_type(preset)
 def _build(self):
  style=ttk.Style(self);style.configure("Toolbar.TButton",padding=(9,5),font=("Microsoft YaHei UI",10));style.configure("Fit.TButton",padding=(8,4),font=("Microsoft YaHei UI",10))
  self.grid_rowconfigure(0,weight=0);self.grid_rowconfigure(1,weight=1);self.grid_rowconfigure(2,weight=0);self.grid_columnconfigure(0,weight=1)
  bar=ttk.Frame(self);bar.grid(row=0,column=0,sticky="ew",padx=8,pady=(6,5))
  self.toolbar_buttons={}
  for text,cmd in (("选择描述文件",self.choose),("粘贴",self.paste),("清空",self.clear),("检查格式",self.check),("生成预览",self.preview),("导出VSDX + PNG + JSON",self.export),("插入格式模板",self.insert_template),("导出格式模板",self.export_template),("打开VSDX",self.open_last_vsdx),("打开PNG",self.open_last_png),("打开输出目录",self.open_output_dir)):
   button=ttk.Button(bar,text=text,command=cmd,style="Toolbar.TButton");button.pack(side="left",padx=(0,5));self.toolbar_buttons[text]=button
  self.open_vsdx_button=self.toolbar_buttons["打开VSDX"];self.open_png_button=self.toolbar_buttons["打开PNG"];self.open_output_button=self.toolbar_buttons["打开输出目录"]
  ttk.Checkbutton(bar,text="输入时自动检查格式",variable=self.live).pack(side="right",padx=(8,2))
  pane=ttk.Panedwindow(self,orient=tk.HORIZONTAL);pane.grid(row=1,column=0,sticky="nsew",padx=(8,7));left=ttk.Frame(pane,width=360);right=ttk.Frame(pane);pane.add(left,weight=0);pane.add(right,weight=1);self.main_pane=pane
  left.grid_rowconfigure(0,weight=1);left.grid_columnconfigure(0,weight=1);left_pane=ttk.Panedwindow(left,orient=tk.VERTICAL);left_pane.grid(row=0,column=0,sticky="nsew");editor_frame=ttk.Frame(left_pane);inspect_frame=ttk.Frame(left_pane);left_pane.add(editor_frame,weight=3);left_pane.add(inspect_frame,weight=2);self.left_pane=left_pane
  editor_frame.grid_rowconfigure(0,weight=1);editor_frame.grid_columnconfigure(0,weight=1);self.editor=CodeEditor(editor_frame,self.changed);self.editor.grid(row=0,column=0,sticky="nsew")
  inspect_frame.grid_rowconfigure(0,weight=1);inspect_frame.grid_columnconfigure(0,weight=1);tabs=ttk.Notebook(inspect_frame);tabs.grid(row=0,column=0,sticky="nsew");tree_tab=ttk.Frame(tabs);issue_tab=ttk.Frame(tabs);tabs.add(tree_tab,text="结构");tabs.add(issue_tab,text="问题");self.tabs=tabs;self.issue_tab=issue_tab;tree_tab.grid_rowconfigure(0,weight=1);tree_tab.grid_columnconfigure(0,weight=1);self.tree=ttk.Treeview(tree_tab,show="tree");self.tree.grid(row=0,column=0,sticky="nsew")
  cols=("line","severity","code","message","suggestion");self.issues=ttk.Treeview(issue_tab,columns=cols,show="headings",height=8)
  for c,t,w in zip(cols,("行号","级别","错误代码","问题说明","修改建议"),(55,60,125,290,300)):self.issues.heading(c,text=t);self.issues.column(c,width=w)
  issue_tab.grid_rowconfigure(0,weight=1);issue_tab.grid_columnconfigure(0,weight=1);self.issues.grid(row=0,column=0,sticky="nsew");self.issues.bind("<Double-1>",self.goto_issue)
  right.grid_rowconfigure(0,weight=1);right.grid_rowconfigure(1,weight=0);right.grid_columnconfigure(0,weight=1);self.preview_area=Preview(right,self.update_status);self.preview_area.grid(row=0,column=0,sticky="nsew");ttk.Button(right,text="适应窗口",command=self.preview_area.fit,style="Fit.TButton").grid(row=1,column=0,sticky="ew",padx=4,pady=4)
  self.status=ttk.Label(self,text="图形类型：未识别｜等待检查｜预览未生成｜缩放：100%");self.status.grid(row=2,column=0,sticky="ew",padx=7,pady=4)
  self.bind("<Configure>",self._window_resized)
  self.after_idle(self._set_initial_sashes)
 def _set_initial_sashes(self):
  try:
   self.update_idletasks();total_width=self.main_pane.winfo_width();total_height=self.left_pane.winfo_height()
   if total_width<100 or total_height<100:self.after(100,self._set_initial_sashes);return
   left_width=max(320,min(420,int(total_width*.23)));self.main_pane.sashpos(0,left_width);self.left_pane.sashpos(0,int(total_height*.62))
  except tk.TclError:pass
 def _maximize_window(self):
  try:self.state("zoomed")
  except tk.TclError:pass
  self.after(120,self._set_initial_sashes)
 def _window_resized(self,event=None):
  if event and event.widget is not self:return
  if self.resize_pending:self.after_cancel(self.resize_pending)
  self.resize_pending=self.after(200,self._fit_after_resize)
 def _fit_after_resize(self):
  self.resize_pending=None
  if self.session and self.session.layout and not self.preview_area.user_zoomed:self.preview_area.fit()
 def source(self):return self.editor.text.get("1.0","end-1c")
 def changed(self,e=None):
  if not self.editor.text.edit_modified():return
  self.editor.text.edit_modified(False);self.session=self.plugin=None;self.tree.delete(*self.tree.get_children());self.issues.delete(*self.issues.get_children());self.preview_area.canvas.delete("all");self.update_status()
  if self.live.get():
   if self.pending:self.after_cancel(self.pending)
   self.pending=self.after(400,lambda:self.check(show_dialog=False))
 def identify(self):
  h=detect_header(self.source())
  if h.issue:return h,None
  return h,PLUGIN_REGISTRY[h.diagram_type]
 def check(self,show_dialog=True):
  h,p=self.identify();issues=[]
  if h.issue:issues=[h.issue]
  else:
   doc,issues=p.parse(h.body_text);issues.extend(p.validate(doc) if doc else [])
   guessed=suggest_body_type(h.body_text,h.diagram_type)
   if issues and guessed:issues.insert(0,type(issues[0])(h.line_number,"错误","HEADER_BODY_MISMATCH",f"当前头标记为{h.canonical_header}，但正文更像{DISPLAY[guessed]}。",h.canonical_header,f"建议将第一行修改为：{CANONICAL[guessed]}"))
   if not any(x.severity=="错误" for x in issues):self.plugin=p;self.session=DiagramSession(h.diagram_type,h.canonical_header,self.source(),doc,None);self.tree.delete(*self.tree.get_children());p.fill_tree(self.tree,doc)
  self.show_issues(issues)
  errors=[x for x in issues if x.severity=="错误"]
  if errors:self.session=self.plugin=None
  if issues:self.tabs.select(self.issue_tab)
  self.update_status()
  if errors and show_dialog:messagebox.showerror("格式检查失败",f"发现 {len(errors)} 个错误，请查看问题列表。")
  return not errors
 def show_issues(self,issues):
  self.issues.delete(*self.issues.get_children())
  for x in issues:self.issues.insert("","end",values=(x.line_number,x.severity,x.code,x.message,x.suggestion))
 def goto_issue(self,e=None):
  sel=self.issues.selection()
  if sel:self.editor.highlight(int(self.issues.item(sel[0],"values")[0]))
 def preview(self):
  if not self.check():return
  try:self.session.layout=self.plugin.layout(self.session.document);self.preview_area.canvas.delete("all");self.plugin.draw_canvas(self.preview_area.canvas,self.session.document,self.session.layout);self.after_idle(self.preview_area.fit);self.update_status()
  except Exception:messagebox.showerror("预览失败",traceback.format_exc()[-1200:])
 def update_status(self):
  dtype=DISPLAY[self.session.diagram_type] if self.session else "未识别";warnings=sum(1 for x in self.issues.get_children() if self.issues.item(x,"values")[1]=="警告");check=(f"格式可生成，存在{warnings}项流程完整性警告" if self.session and warnings else ("格式正确" if self.session else "等待检查"));prev="预览已生成" if self.session and self.session.layout else "预览未生成";self.status.config(text=f"图形类型：{dtype}｜{check}｜{prev}｜缩放：{self.preview_area.scale*100:.0f}%")
 def choose(self):
  p=filedialog.askopenfilename(filetypes=[("ThesisDiagram DSL","*.txt *.md"),("所有文件","*.*")]);
  if p:self.set_text(read_text(p))
 def paste(self):
  try:self.editor.text.insert("insert",self.clipboard_get())
  except tk.TclError:pass
 def clear(self):self.set_text("")
 def set_text(self,text):self.editor.text.delete("1.0","end");self.editor.text.insert("1.0",text);self.editor.text.edit_modified(False);self.session=self.plugin=None;self.tree.delete(*self.tree.get_children());self.issues.delete(*self.issues.get_children());self.preview_area.canvas.delete("all");self.editor.redraw();self.update_status()
 def insert_template(self):
  h,p=self.identify()
  if p:return self.set_text(p.get_template())
  menu=tk.Menu(self,tearoff=False)
  for dtype,plugin in PLUGIN_REGISTRY.items():menu.add_command(label=plugin.display_name,command=lambda d=dtype:self.insert_template_type(d))
  menu.tk_popup(self.winfo_pointerx(),self.winfo_pointery())
 def insert_template_type(self,dtype):self.set_text(PLUGIN_REGISTRY[dtype].get_template())
 def export_template(self):
  h,p=self.identify()
  if not p:return messagebox.showwarning("提示","请先输入有效图形头标记，或使用“插入格式模板”选择图形。")
  path=filedialog.asksaveasfilename(initialfile=p.canonical_header[1:]+"_模板.txt",defaultextension=".txt")
  if path:Path(path).write_text(p.get_template(),encoding="utf-8-sig")
 def export(self):
  if not self.session or not self.session.layout:return messagebox.showwarning("提示","请先检查格式并生成预览。")
  root=filedialog.askdirectory()
  if not root:return
  title=self.plugin.title(self.session.document);folder=Path(root)/f"{safe_name(title)}_{self.session.diagram_type.value}_{datetime.now():%Y%m%d_%H%M%S}";results={};errors={}
  try:folder.mkdir(parents=True,exist_ok=True)
  except Exception as exc:return messagebox.showerror("无法创建输出目录",f"无法写入所选目录：{self._friendly_error(exc)}")
  self.output_dir=self.last_output_dir=folder;self.last_json=self.last_png=self.last_vsdx=None;self.last_vsdx_error="";self.toolbar_buttons["导出VSDX + PNG + JSON"].state(["disabled"]);self.status.config(text="正在导出：JSON…")
  try:
   path=self._export_json(folder,title);results["JSON"]=path.resolve() if path.is_file() else None
  except Exception as exc:errors["JSON"]=self._friendly_error(exc)
  self.status.config(text="正在导出：PNG…");self.update_idletasks()
  try:
   path=self._export_png(folder,title);results["PNG"]=path.resolve() if path.is_file() else None
  except Exception as exc:errors["PNG"]=self._friendly_error(exc)
  self.status.config(text="正在检测Visio并导出VSDX…");self._update_open_buttons();self.export_queue=queue.Queue()
  threading.Thread(target=self._export_vsdx_worker,args=(folder,results,errors),daemon=True).start();self.after(80,self._poll_export_worker)
 def _export_json(self,folder,title):
  payload={"dsl_version":"1.5","diagram_type":self.session.diagram_type.value,"header":self.session.header,"title":title,"source_text":self.session.source_text,"document":asdict(self.session.document),"layout":self._json_layout(self.session.layout)};path=folder/f"{safe_name(title)}.json";path.write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding="utf-8-sig");(folder/f"{safe_name(title)}.txt").write_text(self.session.source_text,encoding="utf-8-sig");return path
 def _export_png(self,folder,title):
  from PIL import Image,ImageDraw,ImageFont
  canvas=self.preview_area.canvas;bb=canvas.bbox("all")
  if not bb:raise RuntimeError("当前预览为空")
  margin=24;scale=2;left,top,right,bottom=bb;image=Image.new("RGB",(max(1,int((right-left+2*margin)*scale)),max(1,int((bottom-top+2*margin)*scale))),"white");draw=ImageDraw.Draw(image)
  def pts(coords):return [((coords[i]-left+margin)*scale,(coords[i+1]-top+margin)*scale) for i in range(0,len(coords),2)]
  font_path="C:/Windows/Fonts/msyh.ttc"
  def color(value,default=None):
   if not value:return default
   try:r,g,b=canvas.winfo_rgb(value);return (r//256,g//256,b//256)
   except tk.TclError:return default
  def option(item,name,default=""):
   try:return canvas.itemcget(item,name)
   except tk.TclError:return default
  for item in canvas.find_all():
   kind=canvas.type(item);coords=canvas.coords(item);fill=color(option(item,"fill"),(0,0,0));outline=color(option(item,"outline"),fill);width=max(1,int(float(option(item,"width",1) or 1)*scale));p=pts(coords)
   if kind=="line":draw.line(p,fill=fill,width=width,joint="curve")
   elif kind=="rectangle":draw.rectangle([p[0],p[1]],outline=outline,fill=fill,width=width)
   elif kind=="oval":draw.ellipse([p[0],p[1]],outline=outline,fill=fill,width=width)
   elif kind=="polygon":draw.polygon(p,outline=outline,fill=fill)
   elif kind=="text":
    text=canvas.itemcget(item,"text");size=18
    try:size=max(12,abs(int(canvas.itemcget(item,"font").split()[-1]))*scale)
    except Exception:pass
    try:font=ImageFont.truetype(font_path,size)
    except Exception:font=ImageFont.load_default()
    draw.multiline_text(p[0],text,fill=fill,font=font,anchor="mm",align="center")
  path=folder/f"{safe_name(title)}.png";image.save(path,"PNG");return path
 def _export_vsdx_worker(self,folder,results,errors):
  try:
   ok,reason=self.detect_visio_com()
   if not ok:raise RuntimeError(reason)
   path=Path(self.plugin.export_vsdx(self.session.document,self.session.layout,folder)).resolve()
   if not path.is_file():raise FileNotFoundError(f"VSDX导出器未返回有效文件：{path}")
   results["VSDX"]=path
  except Exception as exc:errors["VSDX"]=self._friendly_visio_error(exc)
  self.export_queue.put((results,errors))
 def _poll_export_worker(self):
  try:results,errors=self.export_queue.get_nowait()
  except queue.Empty:self.after(80,self._poll_export_worker);return
  self._finish_export(results,errors)
 @staticmethod
 def detect_visio_com():
  try:
   import pythoncom,win32com.client as win32;pythoncom.CoInitialize();app=None
   try:app=win32.DispatchEx("Visio.Application");return True,""
   finally:
    if app:
     try:app.Quit()
     except Exception:pass
    pythoncom.CoUninitialize()
  except Exception as exc:return False,ThesisDiagramApp._friendly_visio_error(exc)
 @staticmethod
 def _friendly_error(exc):
  if isinstance(exc,PermissionError):return "输出目录没有写入权限，请选择其他目录。"
  return str(exc) or exc.__class__.__name__
 @staticmethod
 def _friendly_visio_error(exc):
  text=str(exc)
  if "-2147221005" in text or "无效的类字符串" in text or "Invalid class string" in text:return "未检测到可用的Microsoft Visio桌面版或Visio COM组件未正确注册，因此本次无法导出VSDX。PNG和JSON不受影响。"
  return ThesisDiagramApp._friendly_error(exc)
 def _finish_export(self,results,errors):
  self.last_json=results.get("JSON") if results.get("JSON") and Path(results["JSON"]).is_file() else None;self.last_png=results.get("PNG") if results.get("PNG") and Path(results["PNG"]).is_file() else None;self.last_vsdx=results.get("VSDX") if results.get("VSDX") and Path(results["VSDX"]).is_file() else None;self.last_vsdx_error=errors.get("VSDX","");self.toolbar_buttons["导出VSDX + PNG + JSON"].state(["!disabled"]);self._update_open_buttons();success="\n".join(f"✓ {x}" for x in ("VSDX","PNG","JSON") if x in results and results[x]) or "无";failed="\n".join(f"✗ {x}：{errors[x]}" for x in ("VSDX","PNG","JSON") if x in errors) or "无";partial=bool(results) and bool(errors);title="导出完成（部分成功）" if partial else ("导出完成" if results else "导出失败");self.status.config(text=f"{title}｜成功 {len([x for x in results.values() if x])} 项｜失败 {len(errors)} 项");messagebox.showinfo(title,f"成功：\n{success}\n\n未生成：\n{failed}\n\n输出目录：\n{self.last_output_dir}",parent=self)
 def _update_open_buttons(self):
  # 始终允许点击；没有文件时由明确回调解释原因，避免“灰色按钮无响应”。
  for button in (self.open_vsdx_button,self.open_png_button,self.open_output_button):button.state(["!disabled"])
 def _json_layout(self,l):
  def clean(v):
   if is_dataclass(v):return {k:clean(x) for k,x in vars(v).items()}
   if isinstance(v,dict):
    if all(isinstance(k,str) for k in v):return {k:clean(x) for k,x in v.items()}
    return [{"key":clean(k),"value":clean(x)} for k,x in v.items()]
   if isinstance(v,(list,tuple,set)):return [clean(x) for x in v]
   if isinstance(v,(str,int,float,bool)) or v is None:return v
   return str(v)
  return clean(l)
 def open_last_png(self):
  self.status.config(text="正在打开PNG……");self.update_idletasks();self._open_exported_file(self.last_png,"PNG","尚未生成PNG文件，请先点击“导出VSDX + PNG + JSON”。")
 def open_last_vsdx(self):
  self.status.config(text="正在打开VSDX……");self.update_idletasks();message="尚未生成VSDX文件。若Visio不可用，请先检查VSDX导出结果。"
  if self.last_vsdx_error:message=f"VSDX未生成。\n\n原因：{self.last_vsdx_error}"
  self._open_exported_file(self.last_vsdx,"VSDX",message)
 def open_output_dir(self):
  self.status.config(text="正在打开输出目录……");self.update_idletasks()
  if not self.last_output_dir:return self._open_warning("没有输出目录","尚未执行导出。","输出目录：没有可打开的目录")
  folder=Path(self.last_output_dir).expanduser().resolve()
  if not folder.is_dir():return self._open_error("目录不存在",f"输出目录不存在：\n{folder}","输出目录不存在")
  self._open_system_path(folder,"输出目录")
 def _open_exported_file(self,file_path,format_name,missing_message):
  if not file_path:return self._open_warning(f"无法打开{format_name}",missing_message,f"{format_name}：没有可打开的文件")
  path=Path(file_path).expanduser().resolve()
  if not path.exists():return self._open_error(f"无法打开{format_name}",f"文件不存在或已被移动：\n{path}",f"{format_name}文件不存在")
  if not path.is_file():return self._open_error(f"无法打开{format_name}",f"目标不是文件：\n{path}",f"{format_name}目标不是文件")
  self._open_system_path(path,format_name)
 def _open_system_path(self,path,label):
  try:
   if sys.platform.startswith("win"):os.startfile(str(path))
   elif sys.platform=="darwin":subprocess.Popen(["open",str(path)])
   else:subprocess.Popen(["xdg-open",str(path)])
   self.status.config(text=f"已打开{label}：{path.name}")
  except OSError as exc:self._open_error(f"打开{label}失败",f"系统无法打开：\n{path}\n\n原因：{self._friendly_error(exc)}",f"打开{label}失败")
  except Exception as exc:self._open_error(f"打开{label}失败",self._friendly_error(exc),f"打开{label}失败")
 def _open_warning(self,title,text,status):messagebox.showwarning(title,text,parent=self);self.status.config(text=status)
 def _open_error(self,title,text,status):messagebox.showerror(title,text,parent=self);self.status.config(text=status)
if __name__=="__main__":ThesisDiagramApp().mainloop()
