# -*- coding: utf-8 -*-
from dataclasses import dataclass
from pathlib import Path
from __future__ import annotations
from offline_diagram_common import *
@dataclass
class FunctionModule:name:str;functions:list[str]
@dataclass
class FunctionDiagramStructure:system_name:str;modules:list[FunctionModule]
class App(OfflineDiagramApp):
    app_title="三级功能模块图生成器";output_suffix="功能模块图";template_name="三级功能模块图_输入模板.txt";log_path=Path(__file__).with_name("function_module_diagram_error.log")
    template_text="""系统：个人健康管理系统

模块：管理端
功能：用户管理，公告管理，健康数据查看

模块：用户端
功能：首页健康概览，健康数据管理，运动记录
"""
    def parse(self,text):
        issues=[];system=None;mods=[];pending=None;seen=set();valid=[]
        for no,raw in enumerate(text.splitlines(),1):
            line=raw.strip()
            if not line or line.startswith("#"):continue
            valid.append(no);m=re.match(r"^(系统|模块|功能)\s*[:：]\s*(.*)$",line)
            if not m:issues.append(ParseIssue(no,"错误","UNKNOWN_LINE","无法识别的内容",raw,"仅使用“系统：”“模块：”“功能：”格式。"));continue
            kind,value=m.group(1),m.group(2).strip()
            if not value:issues.append(ParseIssue(no,"错误","EMPTY_VALUE",f"{kind}名称不能为空",raw,"在冒号后填写内容。"));continue
            if kind=="系统":
                if system is not None:issues.append(ParseIssue(no,"错误","DUP_SYSTEM","只能定义一个系统",raw,"删除重复的系统行。"))
                elif len(valid)>1:issues.append(ParseIssue(no,"错误","SYSTEM_ORDER","系统必须是第一条有效内容",raw,"把系统行移到最前。"))
                else:system=value
            elif kind=="模块":
                if pending is not None:issues.append(ParseIssue(no,"错误","MISSING_FUNCTION","上一模块缺少紧随其后的功能行",raw,"为上一模块增加功能行。"))
                if value in seen:issues.append(ParseIssue(no,"错误","DUP_MODULE","模块名称重复",raw,"修改或删除重复模块。"))
                pending=value;seen.add(value)
            else:
                if pending is None:issues.append(ParseIssue(no,"错误","ORPHAN_FUNCTION","功能行前没有模块",raw,"先定义模块。"));continue
                fs=[x.strip() for x in re.split(r"[,，]",value) if x.strip()]
                if len(fs)!=len(set(fs)):issues.append(ParseIssue(no,"错误","DUP_FUNCTION","同一模块内功能重复",raw,"删除重复功能。"))
                mods.append(FunctionModule(pending,fs));pending=None
        if system is None:issues.append(ParseIssue(1,"错误","MISSING_SYSTEM","缺少系统行","","首行写“系统：系统名称”。"))
        if pending is not None:issues.append(ParseIssue(len(text.splitlines()) or 1,"错误","MISSING_FUNCTION","模块缺少功能行",pending,"在模块后紧跟功能行。"))
        if not mods:issues.append(ParseIssue(1,"错误","NO_MODULE","至少需要一个完整模块","","增加模块及功能。"))
        return (FunctionDiagramStructure(system or "",mods) if not issues else None),issues
    def fill_tree(self,s):
        self.tree.delete(*self.tree.get_children());root=self.tree.insert("","end",text=s.system_name,open=True)
        for m in s.modules:
            mid=self.tree.insert(root,"end",text=m.name,open=True)
            for f in m.functions:self.tree.insert(mid,"end",text=f)
    def make_layout(self,s):
        x=60;modules=[]
        for m in s.modules:
            w=max(180,len(m.functions)*58);modules.append((m,x+w/2,w));x+=w+35
        return {"width":x,"height":520,"modules":modules}
    def draw(self,c,s,l):
        c.delete("all");cx=l["width"]/2;c.create_rectangle(cx-150,25,cx+150,75,fill="white");c.create_text(cx,50,text=s.system_name,font=(FONT,12))
        for m,mx,w in l["modules"]:
            c.create_line(cx,75,cx,100,mx,100,mx,130);c.create_rectangle(mx-65,130,mx+65,170,fill="white");c.create_text(mx,150,text=m.name,font=(FONT,10));step=w/max(1,len(m.functions))
            for i,f in enumerate(m.functions):
                fx=mx-w/2+step*(i+.5);c.create_line(mx,170,mx,195,fx,195,fx,220);c.create_rectangle(fx-20,220,fx+20,450,fill="white");c.create_text(fx,335,text="\n".join(f),font=(FONT,9))
        c.configure(scrollregion=c.bbox("all"))
    def export_title(self,s):return s.system_name
    def export_visio(self,s,l,out):
        import pythoncom,win32com.client as win32
        out.mkdir(parents=True,exist_ok=True);base=safe_name(s.system_name)+"_功能模块图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None
        try:
            app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1);scale=.015
            for item in self.preview_canvas.canvas.find_all():
                bb=self.preview_canvas.canvas.bbox(item)
                if not bb:continue
                sh=page.DrawRectangle(bb[0]*scale,(520-bb[3])*scale,bb[2]*scale,(520-bb[1])*scale);text=self.preview_canvas.canvas.itemcget(item,"text")
                if text:sh.Text=text
            doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
        finally:
            if doc:doc.Close()
            if app:app.Quit()
            pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
