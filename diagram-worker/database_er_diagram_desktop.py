# -*- coding: utf-8 -*-
from __future__ import annotations
from dataclasses import dataclass,field
import math
from pathlib import Path
from offline_diagram_common import *
@dataclass
class ERAttribute:name:str;is_primary_key:bool=False;id:str="";owner_entity_id:str=""
@dataclass
class EREntity:name:str;attributes:list[ERAttribute];id:str=""
@dataclass
class ERRelationship:source_entity:str;target_entity:str;source_cardinality:str;target_cardinality:str;name:str;id:str="";source_entity_id:str="";target_entity_id:str=""
@dataclass
class ERDiagramStructure:title:str;entities:list[EREntity];relationships:list[ERRelationship]
@dataclass(frozen=True)
class Point:x:float;y:float
@dataclass(frozen=True)
class Bounds:center_x:float;center_y:float;width:float;height:float
@dataclass
class AttributeLayout:entity_name:str;attribute_name:str;bounds:Bounds;entity_anchor:Point;attribute_anchor:Point;attribute_id:str="";owner_entity_id:str=""
@dataclass
class RelationshipLayout:relationship:ERRelationship;bounds:Bounds;source_anchor:Point;target_anchor:Point;source_diamond_anchor:Point;target_diamond_anchor:Point;source_cardinality_position:Point;target_cardinality_position:Point
@dataclass(frozen=True)
class LayoutEdge:id:str;source:str;target:str;edge_type:str;points:tuple[Point,...];cardinality:str=""
@dataclass
class ERRelationGraph:nodes:dict[str,str]=field(default_factory=dict);edges:list[LayoutEdge]=field(default_factory=list);core_entity_id:str=""
@dataclass
class ERLayout:
    entities:dict[str,Bounds]=field(default_factory=dict);attributes:list[AttributeLayout]=field(default_factory=list);relationships:list[RelationshipLayout]=field(default_factory=list)
    relation_graph:ERRelationGraph=field(default_factory=ERRelationGraph);relation_edges:list[LayoutEdge]=field(default_factory=list);attribute_edges:list[LayoutEdge]=field(default_factory=list)
    occupied_relation_corridors:list[tuple[Point,Point]]=field(default_factory=list);diagnostics:dict=field(default_factory=dict)

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
def boxes_overlap(a,b,padding=0):
    return abs(a.center_x-b.center_x)<(a.width+b.width)/2+padding and abs(a.center_y-b.center_y)<(a.height+b.height)/2+padding
def point_in_bounds(p,b,padding=0):
    return b.center_x-b.width/2-padding<p.x<b.center_x+b.width/2+padding and b.center_y-b.height/2-padding<p.y<b.center_y+b.height/2+padding
def orientation(a,b,c):return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)
def segments_cross(a,b,c,d):
    return orientation(a,b,c)*orientation(a,b,d)<-1e-6 and orientation(c,d,a)*orientation(c,d,b)<-1e-6
def segment_hits_bounds(a,b,bounds,padding=4):
    if point_in_bounds(a,bounds,padding) or point_in_bounds(b,bounds,padding):return True
    x1=bounds.center_x-bounds.width/2-padding;x2=bounds.center_x+bounds.width/2+padding;y1=bounds.center_y-bounds.height/2-padding;y2=bounds.center_y+bounds.height/2+padding
    corners=(Point(x1,y1),Point(x2,y1),Point(x2,y2),Point(x1,y2))
    return any(segments_cross(a,b,corners[i],corners[(i+1)%4]) for i in range(4))
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
        for entity_index,entity in enumerate(entities,1):
            entity.id=f"E{entity_index}"
            for attribute_index,attribute in enumerate(entity.attributes,1):
                attribute.id=f"{entity.id}A{attribute_index}";attribute.owner_entity_id=entity.id
        entity_ids={entity.name:entity.id for entity in entities}
        for relation_index,relation in enumerate(rels,1):
            relation.id=f"R{relation_index}";relation.source_entity_id=entity_ids.get(relation.source_entity,"");relation.target_entity_id=entity_ids.get(relation.target_entity,"")
        fatal=any(x.severity=="错误" for x in issues)
        return (None if fatal else ERDiagramStructure(title,entities,rels)),issues
    def fill_tree(self,s):
        self.tree.delete(*self.tree.get_children());a=self.tree.insert("","end",text="实体与属性",open=True);b=self.tree.insert("","end",text="实体关系",open=True)
        for e in s.entities:
            eid=self.tree.insert(a,"end",text=e.name,open=True)
            for x in e.attributes:self.tree.insert(eid,"end",text=x.name+("（主键）" if x.is_primary_key else ""))
        for r in s.relationships:self.tree.insert(b,"end",text=f"{r.source_entity}-{r.target_entity}：{r.source_cardinality}*{r.target_cardinality}，{r.name}")
    def build_entity_relation_graph(self,s):
        graph=ERRelationGraph({e.id:"entity" for e in s.entities})
        for relation in s.relationships:
            graph.nodes[relation.id]="relationship"
            graph.edges.append(LayoutEdge(f"{relation.id}S",relation.source_entity_id,relation.id,"entity_relationship",(),relation.source_cardinality))
            graph.edges.append(LayoutEdge(f"{relation.id}T",relation.id,relation.target_entity_id,"entity_relationship",(),relation.target_cardinality))
        if any(kind=="attribute" for kind in graph.nodes.values()):raise ValueError("实体关系骨架中禁止包含属性节点")
        return graph
    def select_core_entity(self,s):
        order={e.id:i for i,e in enumerate(s.entities)};degree={e.id:0 for e in s.entities};adj={e.id:set() for e in s.entities}
        for r in s.relationships:
            degree[r.source_entity_id]+=1;degree[r.target_entity_id]+=1;adj[r.source_entity_id].add(r.target_entity_id);adj[r.target_entity_id].add(r.source_entity_id)
        def reach(start):
            seen={start};queue=[start]
            while queue:
                for other in adj[queue.pop(0)]:
                    if other not in seen:seen.add(other);queue.append(other)
            return len(seen)
        aliases=("用户","会员","学生","患者","客户","人员","账号","居民")
        preferred=[e for e in s.entities if any(alias in e.name for alias in aliases)] or s.entities
        return max(preferred,key=lambda e:(degree[e.id],reach(e.id),-order[e.id])).id
    def layout_entity_relation_skeleton(self,s,graph):
        by_id={e.id:e for e in s.entities};order={e.id:i for i,e in enumerate(s.entities)};adj={e.id:[] for e in s.entities};relation_by_pair={}
        for r in s.relationships:
            adj[r.source_entity_id].append(r.target_entity_id);adj[r.target_entity_id].append(r.source_entity_id);relation_by_pair[frozenset((r.source_entity_id,r.target_entity_id))]=r
        core=self.select_core_entity(s);graph.core_entity_id=core;parent={core:None};depth={core:0};queue=[core]
        while queue:
            current=queue.pop(0)
            for child in sorted(adj[current],key=lambda x:order[x]):
                if child not in depth:depth[child]=depth[current]+1;parent[child]=current;queue.append(child)
        for eid in by_id:
            if eid not in depth:depth[eid]=1;parent[eid]=core
        descendants={eid:0 for eid in by_id}
        for eid in sorted(by_id,key=lambda x:depth[x],reverse=True):
            if parent.get(eid):descendants[parent[eid]]+=1+descendants[eid]
        positions={core:Point(760,330)};first=[eid for eid in by_id if depth[eid]==1]
        semantic_slots={"permission":(-125,300,230),"profile":(32,330,245),"service":(105,270,255),"business":(150,360,245)}
        fallback=[(-155,350,235),(-65,320,235),(15,350,245),(75,300,260),(135,350,245),(-20,420,270)]
        def role(eid):
            r=relation_by_pair.get(frozenset((core,eid)));text=(r.name if r else "")
            if any(x in text for x in ("权限","授权","管理","分配")):return "permission"
            if any(x in text for x in ("上传","登记","维护","填写","保存")):return "profile"
            if any(x in text for x in ("对话","咨询","推荐","服务")):return "service"
            if any(x in text for x in ("查看","浏览","使用","学习")):return "business"
            return ""
        used=set();fallback_index=0
        for eid in sorted(first,key=lambda x:(-descendants[x],order[x])):
            kind=role(eid)
            if kind and kind not in used:angle,rx,ry=semantic_slots[kind];used.add(kind)
            else:angle,rx,ry=fallback[fallback_index%len(fallback)];fallback_index+=1
            rad=math.radians(angle);positions[eid]=Point(positions[core].x+rx*math.cos(rad),positions[core].y+ry*math.sin(rad))
        for level in range(2,max(depth.values(),default=1)+1):
            for eid in sorted((x for x in by_id if depth[x]==level),key=lambda x:order[x]):
                p=parent[eid];pp=positions[p];grand=positions.get(parent.get(p),positions[core]);base=math.atan2(pp.y-grand.y,pp.x-grand.x)
                siblings=sorted((x for x in by_id if parent.get(x)==p and depth[x]==level),key=lambda x:order[x]);index=siblings.index(eid);spread=(index-(len(siblings)-1)/2)*.42;positions[eid]=Point(pp.x+330*math.cos(base+spread),pp.y+240*math.sin(base+spread))
        layout=ERLayout(relation_graph=graph)
        for eid,e in by_id.items():
            p=positions[eid];layout.entities[e.name]=Bounds(p.x,p.y,node_width(e.name,NODE_FONT,8,150,40),node_height(e.name,NODE_FONT,8,58,24))
        for r in s.relationships:
            sb,tb=layout.entities[r.source_entity],layout.entities[r.target_entity];dx=tb.center_x-sb.center_x;dy=tb.center_y-sb.center_y;length=math.hypot(dx,dy) or 1;offset=14*((order[r.source_entity_id]+order[r.target_entity_id])%3-1)
            rb=Bounds((sb.center_x+tb.center_x)/2-dy/length*offset,(sb.center_y+tb.center_y)/2+dx/length*offset,node_width(r.name,MEDIUM_FONT,8,100,34),node_height(r.name,MEDIUM_FONT,8,60,20))
            sa=rectangle_boundary_point(sb.center_x,sb.center_y,sb.width,sb.height,rb.center_x,rb.center_y);ta=rectangle_boundary_point(tb.center_x,tb.center_y,tb.width,tb.height,rb.center_x,rb.center_y);sda=diamond_boundary_point(rb.center_x,rb.center_y,rb.width,rb.height,sb.center_x,sb.center_y);tda=diamond_boundary_point(rb.center_x,rb.center_y,rb.width,rb.height,tb.center_x,tb.center_y)
            item=RelationshipLayout(r,rb,sa,ta,sda,tda,offset_from_anchor(sa,sda),offset_from_anchor(ta,tda));layout.relationships.append(item)
            edges=(LayoutEdge(f"{r.id}S",r.source_entity_id,r.id,"entity_relationship",(sa,sda),r.source_cardinality),LayoutEdge(f"{r.id}T",r.id,r.target_entity_id,"entity_relationship",(tda,ta),r.target_cardinality))
            layout.relation_edges.extend(edges);layout.occupied_relation_corridors.extend(((sa,sda),(tda,ta)))
        return layout,adj
    def attach_attributes_to_entities(self,s,layout,adj):
        occupied=list(layout.entities.values())+[r.bounds for r in layout.relationships];attribute_segments=[];name_by_id={e.id:e.name for e in s.entities}
        for entity in s.entities:
            eb=layout.entities[entity.name];channels=[math.atan2(layout.entities[name_by_id[n]].center_y-eb.center_y,layout.entities[name_by_id[n]].center_x-eb.center_x) for n in adj[entity.id]]
            angles=[math.radians(x) for x in range(-180,180,15)]
            angles.sort(key=lambda a:(-min((abs(math.atan2(math.sin(a-c),math.cos(a-c))) for c in channels),default=math.pi),abs(a+math.pi/2),a))
            used_angles=[]
            attributes=sorted(entity.attributes,key=lambda a:(not a.is_primary_key,entity.attributes.index(a)))
            for attribute in attributes:
                aw=node_width(attribute.name,SMALL_FONT,8,100,30);ah=node_height(attribute.name,SMALL_FONT,8,46,18);chosen=None;chosen_line=None
                ranked=[]
                for radius in (135,175,215,255,295,335,375):
                    for angle in angles:
                        if any(abs(math.atan2(math.sin(angle-old),math.cos(angle-old)))<math.radians(12) for old in used_angles):continue
                        candidate=Bounds(eb.center_x+radius*math.cos(angle),eb.center_y+radius*.72*math.sin(angle),aw,ah)
                        if any(boxes_overlap(candidate,b,14) for b in occupied):continue
                        ea=rectangle_boundary_point(eb.center_x,eb.center_y,eb.width,eb.height,candidate.center_x,candidate.center_y);aa=ellipse_boundary_point(candidate.center_x,candidate.center_y,candidate.width,candidate.height,eb.center_x,eb.center_y)
                        blockers=[b for b in occupied if b is not eb and b is not candidate]
                        blocker_hits=sum(segment_hits_bounds(ea,aa,b,5) for b in blockers)
                        relation_cross=sum(segments_cross(ea,aa,a,b) for a,b in layout.occupied_relation_corridors);attribute_cross=sum(segments_cross(ea,aa,a,b) for a,b in attribute_segments)
                        ranked.append((blocker_hits*20000+relation_cross*10000+attribute_cross*5000+radius,candidate,(ea,aa),angle))
                if ranked:
                    _,chosen,chosen_line,chosen_angle=min(ranked,key=lambda item:item[0]);used_angles.append(chosen_angle)
                if chosen is None:
                    raise ValueError(f"实体“{entity.name}”的属性“{attribute.name}”没有可用挂载扇区")
                occupied.append(chosen);attribute_segments.append(chosen_line);item=AttributeLayout(entity.name,attribute.name,chosen,*chosen_line,attribute.id,entity.id);layout.attributes.append(item);layout.attribute_edges.append(LayoutEdge(f"EA-{attribute.id}",entity.id,attribute.id,"entity_attribute",chosen_line))
        return layout
    def validate_er_layout(self,s,layout):
        node_types=dict(layout.relation_graph.nodes)
        for entity in s.entities:
            node_types[entity.id]="entity"
            for attribute in entity.attributes:node_types[attribute.id]="attribute"
        if len(layout.attribute_edges)!=sum(len(e.attributes) for e in s.entities):raise ValueError("属性边数量与属性数量不一致")
        for edge in layout.attribute_edges:
            if {node_types.get(edge.source),node_types.get(edge.target)}!={"entity","attribute"}:raise ValueError(f"非法属性连接：{edge.source}->{edge.target}")
        owners={a.id:a.owner_entity_id for e in s.entities for a in e.attributes}
        for attribute_id,owner_id in owners.items():
            edges=[edge for edge in layout.attribute_edges if attribute_id in (edge.source,edge.target)]
            if len(edges)!=1 or owner_id not in (edges[0].source,edges[0].target):raise ValueError(f"属性{attribute_id}没有且仅有一条所属实体连接")
        relation_crossings=sum(segments_cross(*a.points,*b.points) for i,a in enumerate(layout.relation_edges) for b in layout.relation_edges[i+1:] if not {a.source,a.target}&{b.source,b.target})
        attribute_crossings=sum(segments_cross(*a.points,*b.points) for i,a in enumerate(layout.attribute_edges) for b in layout.attribute_edges[i+1:] if not {a.source,a.target}&{b.source,b.target})
        layout.diagnostics={"entityCount":len(s.entities),"attributeCount":len(owners),"primaryKeyCount":sum(a.is_primary_key for e in s.entities for a in e.attributes),"relationshipCount":len(s.relationships),"entityRelationEdgeCount":len(layout.relation_edges),"attributeEdgeCount":len(layout.attribute_edges),"visibleNodeCount":len(s.entities)+len(owners)+len(s.relationships),"relationCrossings":relation_crossings,"attributeCrossings":attribute_crossings}
        return layout
    def make_layout(self,s):
        graph=self.build_entity_relation_graph(s);layout,adj=self.layout_entity_relation_skeleton(s,graph);self.attach_attributes_to_entities(s,layout,adj);return self.validate_er_layout(s,layout)
    def draw(self,c,s,l):
        c.delete("all");all_bounds=list(l.entities.values())+[x.bounds for x in l.attributes]+[x.bounds for x in l.relationships];title_x=(min(b.center_x-b.width/2 for b in all_bounds)+max(b.center_x+b.width/2 for b in all_bounds))/2;title_y=min(b.center_y-b.height/2 for b in all_bounds)-85;c.create_text(title_x,title_y,text=s.title,font=(FONT,DIAGRAM_TITLE_FONT),font_weight="600",max_units=18)
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
