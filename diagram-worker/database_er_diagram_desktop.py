# -*- coding: utf-8 -*-
from __future__ import annotations
from dataclasses import dataclass,field
import math
from pathlib import Path
from offline_diagram_common import *
@dataclass
class ERAttribute:name:str;is_primary_key:bool=False
@dataclass
class EREntity:name:str;attributes:list[ERAttribute]
@dataclass
class ERRelationship:source_entity:str;target_entity:str;source_cardinality:str;target_cardinality:str;name:str
@dataclass
class ERDiagramStructure:title:str;entities:list[EREntity];relationships:list[ERRelationship]
@dataclass(frozen=True)
class Point:x:float;y:float
@dataclass(frozen=True)
class Bounds:center_x:float;center_y:float;width:float;height:float
@dataclass
class AttributeLayout:entity_name:str;attribute_name:str;bounds:Bounds;entity_anchor:Point;attribute_anchor:Point
@dataclass
class RelationshipLayout:relationship:ERRelationship;bounds:Bounds;source_anchor:Point;target_anchor:Point;source_diamond_anchor:Point;target_diamond_anchor:Point;source_cardinality_position:Point;target_cardinality_position:Point
@dataclass
class ERLayout:entities:dict[str,Bounds]=field(default_factory=dict);attributes:list[AttributeLayout]=field(default_factory=list);relationships:list[RelationshipLayout]=field(default_factory=list)

def rectangle_boundary_point(center_x,center_y,width,height,toward_x,toward_y):
    dx,dy=toward_x-center_x,toward_y-center_y
    if dx==0 and dy==0:return Point(center_x,center_y-height/2)
    sx=(width/2)/abs(dx) if dx else math.inf;sy=(height/2)/abs(dy) if dy else math.inf;scale=min(sx,sy)
    return Point(center_x+dx*scale,center_y+dy*scale)
def ellipse_boundary_point(center_x,center_y,width,height,toward_x,toward_y):
    dx,dy=toward_x-center_x,toward_y-center_y
    if dx==0 and dy==0:return Point(center_x,center_y-height/2)
    rx,ry=width/2,height/2;factor=1/math.sqrt(dx*dx/(rx*rx)+dy*dy/(ry*ry))
    return Point(center_x+dx*factor,center_y+dy*factor)
def diamond_boundary_point(center_x,center_y,width,height,toward_x,toward_y):
    dx,dy=toward_x-center_x,toward_y-center_y
    if dx==0 and dy==0:return Point(center_x,center_y-height/2)
    half_w,half_h=width/2,height/2;factor=1/(abs(dx)/half_w+abs(dy)/half_h)
    return Point(center_x+dx*factor,center_y+dy*factor)
def offset_from_anchor(anchor,toward,distance=12):
    dx,dy=toward.x-anchor.x,toward.y-anchor.y;length=math.hypot(dx,dy) or 1
    return Point(anchor.x+dx/length*distance,anchor.y+dy/length*distance)
class App(OfflineDiagramApp):
    app_title="Chen ER图生成器";output_suffix="数据库ER图";template_name="Chen_ER图_输入模板.txt";log_path=Path(__file__).with_name("database_er_diagram_error.log")
    template_text=Path(__file__).with_name(template_name).read_text(encoding="utf-8-sig")
    def parse(self,text):
        text=(text or "").replace("\ufeff","").replace("\r\n","\n").replace("\r","\n")
        issues=[];title="ER图";entities=[];rels=[];names=set();section=None;lines=text.splitlines();seen_rel=set()
        def build_attributes(entity_name,attribute_text,no,raw,standard):
            attributes=[]
            for value in [x.strip() for x in re.split(r"[,，]",attribute_text) if x.strip()]:
                marked=bool(re.search(r"(?:^\*|\*$|\[(?i:PK)\]$|[（(](?i:PK)[）)]$|[|｜](?i:PK)$)",value))
                clean=re.sub(r"^\*|\*$|\[(?i:PK)\]$|[（(](?i:PK)[）)]$|[|｜](?i:PK)$","",value).strip()
                attributes.append(ERAttribute(clean,marked))
            if not any(a.is_primary_key for a in attributes):
                exact=next((a for a in attributes if a.name.casefold()==(entity_name+"ID").casefold()),None)
                candidate=exact or next((a for a in attributes if re.search(r"(?:ID|编号|编码)$",a.name,re.I)),None)
                if candidate:candidate.is_primary_key=True
                else:issues.append(ParseIssue(no,"警告","ER_PRIMARY_KEY_NOT_FOUND","未识别到主键，仍允许生成预览",raw,"建议使用属性名*显式标记主键。"))
            if len(attributes)>6:issues.append(ParseIssue(no,"警告","ER_ATTRIBUTE_LIMIT",f"实体“{entity_name}”包含{len(attributes)}个属性，建议不超过6个",raw,"可拆分实体以保持图形清晰。"))
            return attributes
        def add_entity(name,attribute_text,no,raw,standard):
            name=name.strip();attributes=build_attributes(name,attribute_text,no,raw,standard)
            if standard and not re.search(r"[\u4e00-\u9fff]",name):issues.append(ParseIssue(no,"错误","ER_ENTITY_NAME_CHINESE","标准格式的实体名称必须包含中文",raw,"使用中文实体名称。"))
            if name in names:issues.append(ParseIssue(no,"错误","DUP_ENTITY","实体名称重复",raw,"修改或删除重复实体。"))
            if not attributes:issues.append(ParseIssue(no,"错误","NO_ATTRIBUTE","实体至少需要一个属性",raw,"在竖线后添加主键属性。"))
            if len({a.name for a in attributes})!=len(attributes):issues.append(ParseIssue(no,"错误","DUP_ATTRIBUTE","同一实体属性重复",raw,"删除重复属性。"))
            names.add(name);entities.append(EREntity(name,attributes))
        def add_relation(a,b,verb,left,right,no,raw):
            a,b,verb,left,right=a.strip(),b.strip(),verb.strip(),left.lower(),right.lower();key=(a,b,left,right,verb)
            if a not in names or b not in names:issues.append(ParseIssue(no,"错误","ER_RELATION_ENTITY_NOT_FOUND","关系引用不存在实体",raw,"先在[实体]区段定义关系两端实体。"))
            if a==b:issues.append(ParseIssue(no,"错误","SELF_REL","禁止实体自连接",raw,"连接两个不同实体。"))
            if key in seen_rel:issues.append(ParseIssue(no,"错误","DUP_REL","关系完全重复",raw,"删除重复关系。"))
            seen_rel.add(key);rels.append(ERRelationship(a,b,left,right,verb))
        for no,raw in enumerate(lines,1):
            line=raw.strip()
            if not line or line.startswith("#"):continue
            if re.match(r"^标题\s*[:：]",line):
                if entities or section:issues.append(ParseIssue(no,"错误","TITLE_ORDER","标题只能位于实体定义之前",raw,"将标题移到文件开头。"))
                else:title=re.split(r"[:：]",line,1)[1].strip() or "ER图"
                continue
            if line=="[实体]":section="entities";continue
            if line=="[关系]":section="relationships";continue
            if re.fullmatch(r"-{3,}",line):
                section="relationships";continue
            if line.startswith("实体：") or line.startswith("实体:"):
                explicit=re.match(r"^实体\s*[:：]\s*([^|｜]+?)\s*[|｜]\s*(.+)$",line)
                if not explicit:issues.append(ParseIssue(no,"错误","ER_ENTITY_MISSING_SEPARATOR","标准实体格式缺少名称与属性分隔符",raw,"写成 实体：角色|角色ID*，角色名称。"));continue
                add_entity(explicit.group(1),explicit.group(2),no,raw,True);continue
            if line.startswith("关系：") or line.startswith("关系:"):
                body=re.split(r"[:：]",line,1)[1];parts=[x.strip() for x in re.split(r"[|｜]",body)]
                if len(parts)!=5:issues.append(ParseIssue(no,"错误","ER_RELATION_FIELD_COUNT","标准关系必须包含5个字段",raw,"写成 关系：实体A|实体B|关系名称|1|n。"));continue
                a,b,verb,left,right=parts
                if left.lower() not in ("1","m","n") or right.lower() not in ("1","m","n"):issues.append(ParseIssue(no,"错误","ER_CARDINALITY_INVALID","关系基数只能是1、m或n",raw,"使用1|n、1|1或m|n。"));continue
                add_relation(a,b,verb,left,right,no,raw);continue
            if section=="relationships":
                m=re.match(r"^(.+?)\s*-\s*(.+?)\s*[:：]\s*([1mMnN])\s*(?:\*|×|:|\.\.)\s*([1mMnN])\s*[,，]\s*(.+)$",line)
                if not m:issues.append(ParseIssue(no,"错误","REL_FORMAT","关系格式错误",raw,"写成 关系：实体A|实体B|关系名称|1|n。"));continue
                add_relation(m.group(1),m.group(2),m.group(5),m.group(3),m.group(4),no,raw);continue
            compact=re.match(r"^(.+?)\s*[:：]\s*(.+)$",line)
            if compact and section in (None,"entities"):add_entity(compact.group(1),compact.group(2),no,raw,False);continue
            issues.append(ParseIssue(no,"错误","ER_ENTITY_FORMAT_INVALID","实体格式错误",raw,"标准格式：实体：角色|角色ID*，角色名称。"))
        if len(entities)<2:issues.append(ParseIssue(1,"错误","FEW_ENTITIES","至少需要两个实体","","增加实体定义。"))
        if not rels:issues.append(ParseIssue(len(lines) or 1,"错误","NO_RELATION","至少需要一个关系","","在分隔线后增加关系。"))
        degree={x.name:0 for x in entities}
        for r in rels:
            if r.source_entity in degree:degree[r.source_entity]+=1
            if r.target_entity in degree:degree[r.target_entity]+=1
        for name,d in degree.items():
            if d==0:issues.append(ParseIssue(1,"错误","ISOLATED_ENTITY","实体没有参与任何关系",name,"增加该实体的关系或删除实体。"))
        fatal=any(x.severity=="错误" for x in issues)
        return (None if fatal else ERDiagramStructure(title,entities,rels)),issues
    def fill_tree(self,s):
        self.tree.delete(*self.tree.get_children());a=self.tree.insert("","end",text="实体与属性",open=True);b=self.tree.insert("","end",text="实体关系",open=True)
        for e in s.entities:
            eid=self.tree.insert(a,"end",text=e.name,open=True)
            for x in e.attributes:self.tree.insert(eid,"end",text=x.name+("（主键）" if x.is_primary_key else ""))
        for r in s.relationships:self.tree.insert(b,"end",text=f"{r.source_entity}-{r.target_entity}：{r.source_cardinality}*{r.target_cardinality}，{r.name}")
    def make_layout(self,s):
        cy=430;spacing=500;start_x=220;layout=ERLayout()
        for i,e in enumerate(s.entities):
            layout.entities[e.name]=Bounds(start_x+i*spacing,cy,node_width(e.name,NODE_FONT,8,190),node_height(e.name,NODE_FONT,8,70))
        for e in s.entities:
            eb=layout.entities[e.name]
            split=(len(e.attributes)+1)//2
            for i,a in enumerate(e.attributes):
                row=i//split;column=i%split;count=split if row==0 else len(e.attributes)-split;aw=node_width(a.name,SMALL_FONT,8,145,32);ah=node_height(a.name,SMALL_FONT,8,56,20);offset=(column-(count-1)/2)*155;ab=Bounds(eb.center_x+offset,245 if row==0 else 615,aw,ah)
                ea=rectangle_boundary_point(eb.center_x,eb.center_y,eb.width,eb.height,ab.center_x,ab.center_y);aa=ellipse_boundary_point(ab.center_x,ab.center_y,ab.width,ab.height,eb.center_x,eb.center_y)
                layout.attributes.append(AttributeLayout(e.name,a.name,ab,ea,aa))
        for r in s.relationships:
            sb,tb=layout.entities[r.source_entity],layout.entities[r.target_entity];rb=Bounds((sb.center_x+tb.center_x)/2,(sb.center_y+tb.center_y)/2,node_width(r.name,MEDIUM_FONT,8,140),node_height(r.name,MEDIUM_FONT,8,76))
            sa=rectangle_boundary_point(sb.center_x,sb.center_y,sb.width,sb.height,rb.center_x,rb.center_y);ta=rectangle_boundary_point(tb.center_x,tb.center_y,tb.width,tb.height,rb.center_x,rb.center_y)
            sda=diamond_boundary_point(rb.center_x,rb.center_y,rb.width,rb.height,sb.center_x,sb.center_y);tda=diamond_boundary_point(rb.center_x,rb.center_y,rb.width,rb.height,tb.center_x,tb.center_y)
            layout.relationships.append(RelationshipLayout(r,rb,sa,ta,sda,tda,offset_from_anchor(sa,sda),offset_from_anchor(ta,tda)))
        return layout
    def draw(self,c,s,l):
        c.delete("all");c.create_text(220+(len(s.entities)-1)*250,115,text=s.title,font=(FONT,DIAGRAM_TITLE_FONT),font_weight="600",max_units=18)
        for r in l.relationships:c.create_line(r.source_anchor.x,r.source_anchor.y,r.source_diamond_anchor.x,r.source_diamond_anchor.y);c.create_line(r.target_anchor.x,r.target_anchor.y,r.target_diamond_anchor.x,r.target_diamond_anchor.y)
        for a in l.attributes:c.create_line(a.entity_anchor.x,a.entity_anchor.y,a.attribute_anchor.x,a.attribute_anchor.y)
        for r in l.relationships:
            b=r.bounds;c.create_polygon(b.center_x,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y,b.center_x,b.center_y+b.height/2,b.center_x-b.width/2,b.center_y,fill="white",outline="black")
        for e in s.entities:
            b=l.entities[e.name];c.create_rectangle(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white",outline="black")
        attrs={(e.name,a.name):a for e in s.entities for a in e.attributes}
        for a in l.attributes:
            b=a.bounds;c.create_oval(b.center_x-b.width/2,b.center_y-b.height/2,b.center_x+b.width/2,b.center_y+b.height/2,fill="white",outline="black")
        for r in l.relationships:c.create_text(r.source_cardinality_position.x,r.source_cardinality_position.y,text=r.relationship.source_cardinality,font=(FONT,SMALL_FONT));c.create_text(r.target_cardinality_position.x,r.target_cardinality_position.y,text=r.relationship.target_cardinality,font=(FONT,SMALL_FONT))
        for r in l.relationships:c.create_text(r.bounds.center_x,r.bounds.center_y,text=r.relationship.name,font=(FONT,MEDIUM_FONT),max_units=8)
        for e in s.entities:
            b=l.entities[e.name];c.create_text(b.center_x,b.center_y,text=e.name,font=(FONT,NODE_FONT),font_weight="600",max_units=8)
        for a in l.attributes:
            b=a.bounds;tid=c.create_text(b.center_x,b.center_y,text=a.attribute_name,font=(FONT,SMALL_FONT),max_units=8)
            if attrs[(a.entity_name,a.attribute_name)].is_primary_key:
                bb=c.bbox(tid);c.create_line(bb[0],bb[3]+1,bb[2],bb[3]+1)
        c.configure(scrollregion=c.bbox("all"))
    def export_title(self,s):return s.title
    def export_visio(self,s,l,out):
        import pythoncom,win32com.client as win32
        out.mkdir(parents=True,exist_ok=True);base=safe_name(s.title)+"_数据库ER图";vsdx=out/(base+".vsdx");png=out/(base+".png");pythoncom.CoInitialize();app=doc=None
        try:
            app=win32.DispatchEx("Visio.Application");app.Visible=False;doc=app.Documents.Add("");page=doc.Pages.Item(1);scale=.012
            for r in l.relationships:
                page.DrawLine(r.source_anchor.x*scale,(800-r.source_anchor.y)*scale,r.source_diamond_anchor.x*scale,(800-r.source_diamond_anchor.y)*scale);page.DrawLine(r.target_anchor.x*scale,(800-r.target_anchor.y)*scale,r.target_diamond_anchor.x*scale,(800-r.target_diamond_anchor.y)*scale)
            for a in l.attributes:page.DrawLine(a.entity_anchor.x*scale,(800-a.entity_anchor.y)*scale,a.attribute_anchor.x*scale,(800-a.attribute_anchor.y)*scale)
            for r in l.relationships:
                b=r.bounds;sh=page.DrawPolyline([(b.center_x*scale,(800-b.center_y-b.height/2)*scale),((b.center_x+b.width/2)*scale,(800-b.center_y)*scale),(b.center_x*scale,(800-b.center_y+b.height/2)*scale),((b.center_x-b.width/2)*scale,(800-b.center_y)*scale),(b.center_x*scale,(800-b.center_y-b.height/2)*scale)],0);sh.Text=r.relationship.name
                for text,pos in ((r.relationship.source_cardinality,r.source_cardinality_position),(r.relationship.target_cardinality,r.target_cardinality_position)):
                    t=page.DrawRectangle((pos.x-8)*scale,(800-pos.y-8)*scale,(pos.x+8)*scale,(800-pos.y+8)*scale);t.Text=text;t.CellsU("LinePattern").FormulaU="0";t.CellsU("FillPattern").FormulaU="0"
            for e in s.entities:
                b=l.entities[e.name];sh=page.DrawRectangle((b.center_x-b.width/2)*scale,(800-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(800-b.center_y+b.height/2)*scale);sh.Text=e.name
            attrs={(e.name,a.name):a for e in s.entities for a in e.attributes}
            for a in l.attributes:
                b=a.bounds;at=page.DrawOval((b.center_x-b.width/2)*scale,(800-b.center_y-b.height/2)*scale,(b.center_x+b.width/2)*scale,(800-b.center_y+b.height/2)*scale);at.Text=a.attribute_name
                if attrs[(a.entity_name,a.attribute_name)].is_primary_key:page.DrawLine((b.center_x-25)*scale,(800-b.center_y-8)*scale,(b.center_x+25)*scale,(800-b.center_y-8)*scale)
            doc.SaveAs(str(vsdx));page.Export(str(png));return vsdx,png
        finally:
            if doc:doc.Close()
            if app:app.Quit()
            pythoncom.CoUninitialize()
if __name__=="__main__":App().mainloop()
