package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.service.diagram.DiagramIr.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SemanticIrLiteAdapter {
    private final ObjectMapper mapper;
    public SemanticIrLiteAdapter(ObjectMapper mapper){this.mapper=mapper;}

    public DiagramIr adapt(DiagramType expected,String json,String titleHint){
        try{
            JsonNode root=mapper.readTree(json);String actual=text(root,"type");
            if(!expected.name().equalsIgnoreCase(actual))throw bad("模型返回图类型与请求不一致");
            List<JsonNode> nodes=list(root.path("nodes")),relations=list(root.path("relations"));
            if(expected==DiagramType.ER_DIAGRAM)for(JsonNode node:nodes)if(node instanceof ObjectNode value&&blank(text(node,"text"))&&!blank(text(node,"table")))value.put("text",text(node,"table"));
            LinkedHashMap<String,JsonNode> byText=new LinkedHashMap<>();
            for(JsonNode node:nodes){String name=text(node,"text");if(name.isBlank()||byText.putIfAbsent(name,node)!=null)throw bad("节点文字为空或重复");}
            for(JsonNode relation:relations)if(!byText.containsKey(text(relation,"from"))||!byText.containsKey(text(relation,"to")))throw bad("关系引用了不存在的节点");
            String title=title(titleHint,expected);
            return switch(expected){
                case FLOWCHART->flow(nodes,relations,title);
                case ER_DIAGRAM->er(nodes,relations,title);
                case FUNCTION_MODULE->modules(nodes,relations,title);
                case ARCHITECTURE->architecture(nodes,relations,title);
                case USE_CASE->useCase(nodes,relations,title);
                case BLOCK_DIAGRAM->block(nodes,relations,title);
                case SEQUENCE_DIAGRAM->sequence(nodes,relations,title);
            };
        }catch(DiagramGenerationException e){throw e;}catch(Exception e){throw new DiagramGenerationException("INVALID_LOCAL_MODEL_JSON","本地绘图模型返回的SemanticIR无法解析。",false,e);}
    }

    public JsonNode toLite(DiagramIr ir){
        ObjectNode root=mapper.createObjectNode();root.put("type",ir.diagramType().name());ArrayNode nodes=root.putArray("nodes"),relations=root.putArray("relations");
        if(ir instanceof FlowchartIr x){Map<String,String> names=new HashMap<>();for(FlowNode n:x.nodes()){names.put(n.id(),n.text());node(nodes,n.kind().name().toLowerCase(),n.text());}for(Edge e:x.edges())relation(relations,names.get(e.from()),names.get(e.to()),"flow",e.label());}
        else if(ir instanceof ErDiagramIr x){Map<String,String> names=new HashMap<>();for(ErEntity e:x.entities()){names.put(e.id(),e.name());ObjectNode n=node(nodes,"entity",e.name());ArrayNode attrs=n.putArray("attributes");for(ErAttribute a:e.attributes()){ObjectNode v=attrs.addObject().put("name",a.name()).put("type",Objects.toString(a.sqlType(),""));if(a.primaryKey())v.put("pk",true);if(a.foreignKey())v.put("fk",true);if(a.unique())v.put("unique",true);if(!a.nullable())v.put("nullable",false);}}for(ErRelation r:x.relations()){ObjectNode v=relation(relations,names.get(r.sourceEntityId()),names.get(r.targetEntityId()),"relationship",r.name());v.put("fromCardinality",r.sourceCardinality()).put("toCardinality",r.targetCardinality()).put("fromField",Objects.toString(r.sourceField(),"")).put("toField",Objects.toString(r.targetField(),"")).put("source",r.source().name());}}
        else if(ir instanceof FunctionModuleIr x){node(nodes,"root",x.title());Map<String,String> names=new HashMap<>();for(ModuleNode m:x.modules()){names.put(m.id(),m.name());node(nodes,"module",m.name());for(String f:m.functions())node(nodes,"module",f);}for(ModuleNode m:x.modules()){String parent=m.parentId()==null?x.title():names.get(m.parentId());relation(relations,parent,m.name(),"contains","");for(String f:m.functions())relation(relations,m.name(),f,"contains","");}}
        else if(ir instanceof ArchitectureIr x){for(ArchitectureLayer layer:x.layers())for(String component:layer.components())node(nodes,"service",component).put("group",layer.name());}
        else if(ir instanceof UseCaseIr x){Map<String,String> names=new HashMap<>();for(Actor a:x.actors()){names.put(a.id(),a.name());node(nodes,"actor",a.name());}for(UseCase u:x.useCases()){names.put(u.id(),u.name());node(nodes,"use_case",u.name()).put("group",x.systemName());}for(UseCaseRelation r:x.relations())relation(relations,names.get(r.from()),names.get(r.to()),r.kind().name().toLowerCase(),"");}
        else if(ir instanceof BlockDiagramIr x){Map<String,String> names=new HashMap<>();for(Block b:x.blocks()){names.put(b.id(),b.text());node(nodes,"LEFT".equalsIgnoreCase(b.kind())?"input":"RIGHT".equalsIgnoreCase(b.kind())?"output":"block",b.text());}for(Edge e:x.edges())relation(relations,names.get(e.from()),names.get(e.to()),blank(e.kind())?"signal_flow":e.kind(),e.label());}
        else if(ir instanceof SequenceDiagramIr x){Map<String,String> names=new HashMap<>();for(Participant p:x.participants()){names.put(p.id(),p.name());node(nodes,"ACTOR".equalsIgnoreCase(p.kind())?"actor":"lifeline",p.name());}for(Message m:x.messages())relation(relations,names.get(m.from()),names.get(m.to()),switch(m.kind()){case ASYNC->"async";case RETURN->"return";default->"sync";},m.text());}
        return root;
    }

    private FlowchartIr flow(List<JsonNode> nodes,List<JsonNode> relations,String title){
        Map<String,String> ids=ids(nodes,"N");List<Edge> edges=new ArrayList<>();int i=0;
        Map<String,List<String>> labels=new HashMap<>();Set<String> outgoing=new HashSet<>();
        for(JsonNode r:relations){i++;String from=text(r,"from"),label=text(r,"label");edges.add(new Edge("E"+i,ids.get(from),ids.get(text(r,"to")),"normal",label,i));outgoing.add(from);labels.computeIfAbsent(from,k->new ArrayList<>()).add(label);}
        List<FlowNode> outNodes=new ArrayList<>();int starts=0;
        for(int index=0;index<nodes.size();index++){JsonNode n=nodes.get(index);String name=text(n,"text");FlowNodeKind kind;try{kind=enumValue(FlowNodeKind.class,text(n,"kind"));}catch(Exception ignored){kind=FlowNodeKind.PROCESS;}
            if(kind==FlowNodeKind.START){starts++;if(starts>1)kind=FlowNodeKind.PROCESS;}
            if(kind==FlowNodeKind.DECISION){List<String> branchLabels=labels.getOrDefault(name,List.of());if(branchLabels.size()!=2||branchLabels.stream().anyMatch(SemanticIrLiteAdapter::blank)||new HashSet<>(branchLabels).size()!=2)kind=FlowNodeKind.PROCESS;}
            if(!outgoing.contains(name)&&kind!=FlowNodeKind.END)kind=FlowNodeKind.END;
            outNodes.add(new FlowNode(ids.get(name),kind,name));}
        if(starts==0&&!outNodes.isEmpty()){FlowNode first=outNodes.get(0);outNodes.set(0,new FlowNode(first.id(),FlowNodeKind.START,first.text()));}
        return new FlowchartIr("1.0",DiagramType.FLOWCHART,title,outNodes,edges,List.of());
    }

    private ErDiagramIr er(List<JsonNode> nodes,List<JsonNode> relations,String title){
        List<JsonNode> entityNodes=nodes.stream().filter(n->"entity".equalsIgnoreCase(text(n,"kind"))).toList();
        if(entityNodes.size()<2)throw bad("ER图至少需要两个实体");
        Map<String,String> ids=ids(entityNodes,"E");List<ErEntity> entities=new ArrayList<>();
        for(JsonNode n:entityNodes){List<ErAttribute> attrs=new ArrayList<>();Set<String> attributeNames=new HashSet<>();int index=0;for(JsonNode a:list(n.path("attributes"))){String name=text(a,"name");if(blank(name)||!attributeNames.add(name))continue;index++;attrs.add(new ErAttribute(ids.get(text(n,"text"))+"A"+index,name,blank(text(a,"type"))?"VARCHAR(255)":text(a,"type"),bool(a,"pk"),bool(a,"fk"),bool(a,"unique"),!a.has("nullable")||bool(a,"nullable")));if(index>=8)break;}if(attrs.isEmpty())attrs.add(new ErAttribute(ids.get(text(n,"text"))+"A1",text(n,"text")+"ID","BIGINT",true,false,true,false));entities.add(new ErEntity(ids.get(text(n,"text")),text(n,"text"),attrs,false));}
        List<ErRelation> out=new ArrayList<>();int index=0;for(JsonNode r:relations){String from=text(r,"from"),to=text(r,"to");if(!ids.containsKey(from)||!ids.containsKey(to))continue;index++;String legacyCard=text(r,"cardinality");String name=!blank(text(r,"label"))?text(r,"label"):!blank(text(r,"text"))?text(r,"text"):"关联";out.add(new ErRelation("R"+index,ids.get(from),text(r,"fromField"),ids.get(to),text(r,"toField"),card(blank(text(r,"fromCardinality"))?legacyCard:text(r,"fromCardinality")),card(blank(text(r,"toCardinality"))?legacyCard:text(r,"toCardinality")),name,source(text(r,"source")),1d));}
        return new ErDiagramIr("1.0",DiagramType.ER_DIAGRAM,title,entities,out,entities.stream().limit(5).map(ErEntity::id).toList(),List.of());
    }

    private FunctionModuleIr modules(List<JsonNode> nodes,List<JsonNode> relations,String fallback){
        JsonNode root=nodes.stream().filter(n->"root".equalsIgnoreCase(text(n,"kind"))).findFirst().orElseThrow(()->bad("功能模块图缺少root"));String rootName=text(root,"text");
        boolean rootAsSource=relations.stream().anyMatch(r->rootName.equals(text(r,"from"))),rootAsTarget=relations.stream().anyMatch(r->rootName.equals(text(r,"to")));
        List<JsonNode> normalizedRelations=relations;if(rootAsTarget&&!rootAsSource){normalizedRelations=new ArrayList<>();for(JsonNode relation:relations)normalizedRelations.add(mapper.createObjectNode().put("from",text(relation,"to")).put("to",text(relation,"from")).put("kind","contains"));}
        Map<String,List<String>> children=children(normalizedRelations);Map<String,String> parent=parents(normalizedRelations);
        for(JsonNode n:nodes){String name=text(n,"text");if(!name.equals(rootName)&&blank(parent.get(name))){parent.put(name,rootName);children.computeIfAbsent(rootName,k->new ArrayList<>()).add(name);}}
        Map<String,String> moduleIds=new LinkedHashMap<>();int seq=0;
        for(JsonNode n:nodes){String name=text(n,"text");if(name.equals(rootName))continue;if(!children.getOrDefault(name,List.of()).isEmpty()||rootName.equals(parent.get(name)))moduleIds.put(name,"M"+(++seq));}
        List<ModuleNode> out=new ArrayList<>();for(Map.Entry<String,String> entry:moduleIds.entrySet()){String name=entry.getKey(),p=parent.get(name);List<String> functions=new ArrayList<>(children.getOrDefault(name,List.of()).stream().filter(child->children.getOrDefault(child,List.of()).isEmpty()).toList());if(functions.isEmpty())functions.add(name+"功能");out.add(new ModuleNode(entry.getValue(),name,moduleIds.get(p),functions));}
        return new FunctionModuleIr("1.0",DiagramType.FUNCTION_MODULE,blank(rootName)?fallback:rootName,out,List.of());
    }

    private ArchitectureIr architecture(List<JsonNode> nodes,List<JsonNode> relations,String title){
        LinkedHashMap<String,List<String>> groups=new LinkedHashMap<>();for(JsonNode n:nodes)groups.computeIfAbsent(blank(text(n,"group"))?layer(text(n,"kind")):text(n,"group"),x->new ArrayList<>()).add(text(n,"text"));
        List<ArchitectureLayer> layers=new ArrayList<>();int i=0;for(var e:groups.entrySet())layers.add(new ArchitectureLayer("L"+(++i),e.getKey(),e.getValue()));
        return new ArchitectureIr("1.0",DiagramType.ARCHITECTURE,title,layers,List.of(),List.of());
    }

    private UseCaseIr useCase(List<JsonNode> nodes,List<JsonNode> relations,String fallback){
        String system=nodes.stream().map(n->text(n,"group")).filter(x->!blank(x)).findFirst().orElse(fallback),sid="S1";Map<String,String> ids=ids(nodes,"U");List<Actor> actors=new ArrayList<>();List<UseCase> cases=new ArrayList<>();
        for(JsonNode n:nodes)if("actor".equalsIgnoreCase(text(n,"kind")))actors.add(new Actor(ids.get(text(n,"text")),text(n,"text"),null));else cases.add(new UseCase(ids.get(text(n,"text")),sid,text(n,"text")));
        List<UseCaseRelation> out=new ArrayList<>();int i=0;for(JsonNode r:relations)out.add(new UseCaseRelation("R"+(++i),ids.get(text(r,"from")),ids.get(text(r,"to")),enumValue(UseCaseRelationKind.class,text(r,"kind"))));
        return new UseCaseIr("1.0",DiagramType.USE_CASE,system,sid,system,actors,cases,out,List.of());
    }

    private BlockDiagramIr block(List<JsonNode> nodes,List<JsonNode> relations,String title){
        Map<String,String> ids=ids(nodes,"B");List<Block> blocks=new ArrayList<>();for(JsonNode n:nodes)blocks.add(new Block(ids.get(text(n,"text")),blockSide(text(n,"kind")),text(n,"text")));
        List<Edge> edges=new ArrayList<>();int i=0;for(JsonNode r:relations){i++;edges.add(new Edge("E"+i,ids.get(text(r,"from")),ids.get(text(r,"to")),text(r,"kind"),text(r,"label"),i));}
        return new BlockDiagramIr("1.0",DiagramType.BLOCK_DIAGRAM,title,blocks,edges,List.of());
    }

    private SequenceDiagramIr sequence(List<JsonNode> nodes,List<JsonNode> relations,String title){
        Map<String,String> ids=ids(nodes,"P");List<Participant> participants=new ArrayList<>();for(JsonNode n:nodes)participants.add(new Participant(ids.get(text(n,"text")),participantKind(n),text(n,"text")));
        List<Message> messages=new ArrayList<>();int i=0;for(JsonNode r:relations){i++;String label=!blank(text(r,"label"))?text(r,"label"):!blank(text(r,"text"))?text(r,"text"):defaultMessage(text(r,"from"),text(r,"to"),text(r,"kind"));messages.add(new Message("M"+i,ids.get(text(r,"from")),ids.get(text(r,"to")),messageKind(text(r,"kind")),label,i));}
        return new SequenceDiagramIr("1.0",DiagramType.SEQUENCE_DIAGRAM,title,participants,messages,List.of());
    }

    private static Map<String,String> ids(List<JsonNode> nodes,String prefix){LinkedHashMap<String,String> result=new LinkedHashMap<>();int i=0;for(JsonNode n:nodes)result.put(text(n,"text"),prefix+(++i));return result;}
    private static ObjectNode node(ArrayNode nodes,String kind,String text){return nodes.addObject().put("kind",kind).put("text",text);}
    private static ObjectNode relation(ArrayNode relations,String from,String to,String kind,String label){ObjectNode value=relations.addObject().put("from",Objects.toString(from,"")).put("to",Objects.toString(to,"")).put("kind",kind);if(!blank(label))value.put("label",label);return value;}
    private static Map<String,List<String>> children(List<JsonNode> relations){Map<String,List<String>> result=new LinkedHashMap<>();for(JsonNode r:relations)result.computeIfAbsent(text(r,"from"),x->new ArrayList<>()).add(text(r,"to"));return result;}
    private static Map<String,String> parents(List<JsonNode> relations){Map<String,String> result=new HashMap<>();for(JsonNode r:relations)result.put(text(r,"to"),text(r,"from"));return result;}
    private static List<JsonNode> list(JsonNode value){List<JsonNode> result=new ArrayList<>();if(value.isArray())value.forEach(result::add);return result;}
    private static String text(JsonNode n,String field){return n.path(field).asText("").trim();}private static boolean bool(JsonNode n,String field){return n.path(field).asBoolean(false);}private static boolean blank(String v){return v==null||v.isBlank();}
    private static String title(String hint,DiagramType type){return blank(hint)?switch(type){case FLOWCHART->"业务流程图";case ER_DIAGRAM->"系统ER图";case FUNCTION_MODULE->"系统功能模块图";case ARCHITECTURE->"系统架构图";case USE_CASE->"系统用例图";case BLOCK_DIAGRAM->"系统框图";case SEQUENCE_DIAGRAM->"系统时序图";}:hint.trim();}
    private static String card(String value){return "1".equals(value)?"1":"M".equalsIgnoreCase(value)?"M":"N";}private static RelationSource source(String value){try{return RelationSource.valueOf(value.toUpperCase());}catch(Exception e){return RelationSource.INFERRED;}}
    private static String layer(String kind){return switch(kind.toLowerCase()){case "client"->"客户端层";case "gateway"->"接入层";case "database","cache"->"数据层";case "external_system"->"外部系统";default->"服务层";};}
    private static String blockSide(String kind){return switch(kind.toLowerCase()){case "input","sensor","storage"->"LEFT";case "output","actuator"->"RIGHT";default->"CENTER";};}
    private static String participantKind(JsonNode node){String kind=text(node,"kind"),name=text(node,"text");if("actor".equalsIgnoreCase(kind))return "ACTOR";if(name.contains("数据库")||name.toLowerCase().contains("database"))return "DATABASE";if(name.contains("控制")||name.toLowerCase().contains("controller"))return "CONTROL";if(name.contains("页面")||name.contains("界面")||name.toLowerCase().contains("page"))return "BOUNDARY";return "SERVICE";}
    private static MessageKind messageKind(String value){return switch(value.toLowerCase()){case "async"->MessageKind.ASYNC;case "return"->MessageKind.RETURN;default->MessageKind.CALL;};}
    private static String defaultMessage(String from,String to,String kind){return "return".equalsIgnoreCase(kind)?"返回处理结果":from+"请求"+to;}
    private static <E extends Enum<E>> E enumValue(Class<E> type,String value){try{return Enum.valueOf(type,value.toUpperCase());}catch(Exception e){throw bad("非法语义类型："+value);}}
    private static DiagramGenerationException bad(String message){return new DiagramGenerationException("INVALID_LOCAL_MODEL_SEMANTICS",message);}
}
