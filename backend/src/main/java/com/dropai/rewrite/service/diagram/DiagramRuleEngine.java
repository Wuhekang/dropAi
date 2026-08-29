package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.service.diagram.DiagramIr.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;

@Component
public class DiagramRuleEngine {
    public DiagramIr normalize(DiagramIr ir){if(ir==null)throw invalid("模型未返回DiagramIR");if(ir instanceof FlowchartIr x)return new FlowchartRuleEngine().normalize(x);if(ir instanceof ErDiagramIr x)return new ErDiagramRuleEngine().normalize(x);if(ir instanceof FunctionModuleIr x)return new FunctionModuleRuleEngine().normalize(x);if(ir instanceof ArchitectureIr x)return new ArchitectureRuleEngine().normalize(x);if(ir instanceof UseCaseIr x)return new UseCaseRuleEngine().normalize(x);if(ir instanceof BlockDiagramIr x)return new BlockDiagramRuleEngine().normalize(x);if(ir instanceof SequenceDiagramIr x)return new SequenceDiagramRuleEngine().normalize(x);throw invalid("不支持的DiagramIR类型");}
    interface Rule<T extends DiagramIr>{T normalize(T value);}
    static final class FlowchartRuleEngine implements Rule<FlowchartIr>{
        public FlowchartIr normalize(FlowchartIr x){
            List<FlowNode> nodes=new ArrayList<>(unique(x.nodes(),FlowNode::id));
            if(nodes.isEmpty())throw invalid("流程图没有节点");
            Map<String,FlowNode> by=index(nodes,FlowNode::id);
            List<Edge> normalizedEdges=new ArrayList<>(edges(x.edges(),by.keySet()));
            long starts=nodes.stream().filter(n->n.kind()==FlowNodeKind.START).count();
            if(starts!=1)throw invalid("流程图必须有且只有一个开始节点");

            List<Warning> normalizedWarnings=new ArrayList<>(warnings(x.warnings()));
            if(nodes.stream().noneMatch(n->n.kind()==FlowNodeKind.END)){
                Set<String> outgoing=normalizedEdges.stream().map(Edge::from).collect(java.util.stream.Collectors.toSet());
                List<FlowNode> terminals=nodes.stream().filter(n->n.kind()!=FlowNodeKind.START&&!outgoing.contains(n.id())).toList();
                if(terminals.isEmpty())throw invalid("流程图必须包含明确的结束节点；循环流程请增加“是否停止”判断及结束出口");
                if(nodes.size()>=10)throw invalid("流程图缺少结束节点，且节点数已达上限，请合并次要步骤后重试");
                String endId=nextEndId(by.keySet());
                nodes.add(new FlowNode(endId,FlowNodeKind.END,"结束"));
                int order=normalizedEdges.stream().map(Edge::order).filter(Objects::nonNull).max(Integer::compareTo).orElse(0);
                for(FlowNode terminal:terminals)normalizedEdges.add(new Edge("e"+(++order),terminal.id(),endId,"normal","",order));
                normalizedWarnings.add(new Warning("FLOW_END_ADDED","生成结果缺少结束节点，系统已将所有末端分支汇入结束。",1d));
                by=index(nodes,FlowNode::id);
            }

            Set<String> outgoing=new HashSet<>(),incoming=new HashSet<>();
            Map<String,Set<String>> labels=new HashMap<>();
            Map<String,Integer> outCounts=new HashMap<>();
            Map<String,List<String>> forward=new HashMap<>(),reverse=new HashMap<>();
            for(FlowNode n:nodes){forward.put(n.id(),new ArrayList<>());reverse.put(n.id(),new ArrayList<>());}
            for(Edge e:normalizedEdges){
                outgoing.add(e.from());incoming.add(e.to());outCounts.merge(e.from(),1,Integer::sum);
                forward.get(e.from()).add(e.to());reverse.get(e.to()).add(e.from());
                if(by.get(e.from()).kind()==FlowNodeKind.DECISION){
                    if(blank(e.label()))throw invalid("判断节点的每个分支必须有标签");
                    if(!labels.computeIfAbsent(e.from(),k->new HashSet<>()).add(e.label()))throw invalid("判断节点分支标签不能重复");
                }
            }
            FlowNode start=nodes.stream().filter(n->n.kind()==FlowNodeKind.START).findFirst().orElseThrow();
            if(incoming.contains(start.id()))throw invalid("开始节点不能有入边");
            for(FlowNode n:nodes){
                if(n.kind()==FlowNodeKind.DECISION){
                    int count=outCounts.getOrDefault(n.id(),0);
                    if(labels.getOrDefault(n.id(),Set.of()).size()<2)throw invalid("判断节点至少需要两个不同分支");
                    if(count>2)throw invalid("判断节点最多只能有两个分支，三档及以上状态请合并为处理节点："+n.text());
                }
                if(n.kind()==FlowNodeKind.END&&outgoing.contains(n.id()))throw invalid("结束节点不能有出边："+n.text());
                if(n.kind()!=FlowNodeKind.END&&!outgoing.contains(n.id()))throw invalid("存在没有后续关系的节点："+n.text());
            }
            Set<String> reachable=walk(List.of(start.id()),forward);
            if(reachable.size()!=nodes.size())throw invalid("流程图存在从开始节点无法到达的孤立步骤");
            List<String> ends=nodes.stream().filter(n->n.kind()==FlowNodeKind.END).map(FlowNode::id).toList();
            Set<String> canReachEnd=walk(ends,reverse);
            for(FlowNode n:nodes)if(!canReachEnd.contains(n.id()))throw invalid("从节点“"+n.text()+"”无法到达任何结束节点");
            return new FlowchartIr("1.0",DiagramType.FLOWCHART,cleanTitle(x.title(),"流程图"),nodes,normalizedEdges,normalizedWarnings);
        }
        private static String nextEndId(Set<String> ids){String id="END";int i=2;while(ids.contains(id))id="END_"+(i++);return id;}
        private static Set<String> walk(Collection<String> roots,Map<String,List<String>> graph){Set<String> seen=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>(roots);while(!queue.isEmpty()){String id=queue.removeFirst();if(!seen.add(id))continue;queue.addAll(graph.getOrDefault(id,List.of()));}return seen;}
    }
    static final class ErDiagramRuleEngine implements Rule<ErDiagramIr>{
        public ErDiagramIr normalize(ErDiagramIr x){
            List<ErEntity> source=unique(x.entities(),ErEntity::id);
            if(source.size()<2)throw invalid("ER图至少需要两个实体");
            List<ErEntity> entities=new ArrayList<>();Set<String> entityNames=new HashSet<>();
            for(ErEntity entity:source){
                String name=cleanValue(entity.name(),"实体名称");
                if(!entityNames.add(name))throw invalid("ER实体名称不能重复："+name);
                List<ErAttribute> attributes=new ArrayList<>();Set<String> attributeNames=new HashSet<>();
                for(ErAttribute attribute:safe(entity.attributes())){
                    String attributeName=cleanValue(attribute.name(),"实体“"+name+"”的属性名称");
                    if(!attributeNames.add(attributeName))continue;
                    attributes.add(new ErAttribute(id(attribute.id(),"a",attributes.size()+1),attributeName,nz(attribute.sqlType()),attribute.primaryKey(),attribute.foreignKey(),attribute.unique(),attribute.nullable()));
                }
                if(attributes.isEmpty())throw invalid("实体“"+name+"”至少需要一个属性");
                entities.add(new ErEntity(entity.id(),name,attributes,entity.associationEntity()));
            }
            Map<String,ErEntity> by=index(entities,ErEntity::id);List<ErRelation> rels=new ArrayList<>();Set<String> seen=new HashSet<>();Map<String,Integer> degree=new HashMap<>();by.keySet().forEach(k->degree.put(k,0));
            for(ErRelation r:safe(x.relations())){
                if(!by.containsKey(r.sourceEntityId())||!by.containsKey(r.targetEntityId()))throw invalid("ER关系引用不存在实体");
                if(r.sourceEntityId().equals(r.targetEntityId()))throw invalid("ER图禁止实体自连接："+by.get(r.sourceEntityId()).name());
                String relationName=blank(r.name())?"关联":cleanValue(r.name(),"关系名称");
                String key=r.sourceEntityId()+">"+r.targetEntityId()+":"+relationName+":"+card(r.sourceCardinality())+":"+card(r.targetCardinality());
                if(seen.add(key)){
                    rels.add(new ErRelation(id(r.id(),"r",rels.size()+1),r.sourceEntityId(),nz(r.sourceField()),r.targetEntityId(),nz(r.targetField()),card(r.sourceCardinality()),card(r.targetCardinality()),relationName,r.source()==null?RelationSource.USER:r.source(),r.confidence()==null?1:r.confidence()));
                    degree.merge(r.sourceEntityId(),1,Integer::sum);degree.merge(r.targetEntityId(),1,Integer::sum);
                }
            }
            if(rels.isEmpty())throw invalid("ER图至少需要一条实体关系");
            for(ErEntity entity:entities)if(degree.getOrDefault(entity.id(),0)==0)throw invalid("实体“"+entity.name()+"”没有参与任何关系");
            List<String> core=safe(x.coreEntityIds()).stream().filter(by::containsKey).distinct().toList();
            return new ErDiagramIr("1.0",DiagramType.ER_DIAGRAM,cleanTitle(x.title(),"ER图"),entities,rels,core,warnings(x.warnings()));
        }
    }
    static final class FunctionModuleRuleEngine implements Rule<FunctionModuleIr>{
        public FunctionModuleIr normalize(FunctionModuleIr x){
            List<ModuleNode> source=unique(x.modules(),ModuleNode::id);if(source.isEmpty())throw invalid("功能模块图至少需要一个模块");
            Set<String> ids=new HashSet<>();source.forEach(m->ids.add(m.id()));Map<String,List<ModuleNode>> children=new LinkedHashMap<>();source.forEach(m->children.put(m.id(),new ArrayList<>()));List<ModuleNode> roots=new ArrayList<>();
            for(ModuleNode module:source){
                if(!blank(module.parentId())){
                    if(!ids.contains(module.parentId()))throw invalid("模块引用不存在的父模块");
                    if(module.id().equals(module.parentId()))throw invalid("模块不能将自身作为父模块");
                    children.get(module.parentId()).add(module);
                }else roots.add(module);
            }
            if(roots.isEmpty())throw invalid("功能模块层级形成循环，找不到直属模块");
            List<ModuleNode> topModules=roots.size()==1&&!children.get(roots.get(0).id()).isEmpty()?children.get(roots.get(0).id()):roots;
            List<ModuleNode> modules=new ArrayList<>();Set<String> names=new HashSet<>();boolean flattened=false;
            for(ModuleNode module:topModules){
                String name=cleanValue(module.name(),"模块名称");LinkedHashSet<String> collected=new LinkedHashSet<>();collectFunctions(module,children,new HashSet<>(),collected);List<String> functions=new ArrayList<>(collected);
                if(functions.isEmpty())throw invalid("模块“"+name+"”至少需要一项功能");
                if(!names.add(name))throw invalid("模块名称不能重复："+name);
                if(!blank(module.parentId())||!children.get(module.id()).isEmpty())flattened=true;
                modules.add(new ModuleNode(module.id(),name,null,functions));
            }
            if(modules.isEmpty())throw invalid("功能模块图没有可绘制的模块和功能");
            List<Warning> ws=new ArrayList<>(warnings(x.warnings()));
            if(flattened)ws.add(new Warning("FUNCTION_HIERARCHY_FLATTENED","当前绘图器固定为系统、模块、功能三级，已移除空壳根模块并展开为直属模块。",1d));
            return new FunctionModuleIr("1.0",DiagramType.FUNCTION_MODULE,cleanTitle(x.title(),"功能模块图"),modules,ws);
        }
        private void collectFunctions(ModuleNode module,Map<String,List<ModuleNode>> children,Set<String> visiting,LinkedHashSet<String> out){
            if(!visiting.add(module.id()))throw invalid("功能模块层级存在循环");
            out.addAll(cleanItems(module.functions()));
            for(ModuleNode child:children.getOrDefault(module.id(),List.of())){
                List<ModuleNode> descendants=children.getOrDefault(child.id(),List.of());List<String> direct=cleanItems(child.functions());
                if(direct.isEmpty()&&descendants.isEmpty())out.add(cleanValue(child.name(),"功能名称"));
                else collectFunctions(child,children,visiting,out);
            }
            visiting.remove(module.id());
        }
    }
    static final class ArchitectureRuleEngine implements Rule<ArchitectureIr>{
        public ArchitectureIr normalize(ArchitectureIr x){
            List<ArchitectureLayer> source=unique(x.layers(),ArchitectureLayer::id);List<ArchitectureLayer> layers=new ArrayList<>();Set<String> names=new HashSet<>();
            for(ArchitectureLayer layer:source){
                String name=cleanValue(layer.name(),"架构层名称");if(!names.add(name))throw invalid("架构层名称不能重复："+name);
                List<String> components=cleanItems(layer.components());if(components.isEmpty())throw invalid("架构层“"+name+"”至少需要一个组件");
                layers.add(new ArchitectureLayer(layer.id(),name,components));
            }
            if(layers.size()<2)throw invalid("架构图至少需要两个完整层");
            List<Warning> ws=new ArrayList<>(warnings(x.warnings()));if(!safe(x.dependencies()).isEmpty())ws.add(new Warning("ARCHITECTURE_DEPENDENCIES_OMITTED","当前架构图按相邻层自动连接，已忽略额外依赖线。",1d));
            return new ArchitectureIr("1.0",DiagramType.ARCHITECTURE,cleanTitle(x.title(),"系统架构图"),layers,List.of(),ws);
        }
    }
    static final class UseCaseRuleEngine implements Rule<UseCaseIr>{
        public UseCaseIr normalize(UseCaseIr x){
            List<Actor> actorSource=unique(x.actors(),Actor::id);List<UseCase> caseSource=unique(x.useCases(),UseCase::id);
            if(actorSource.isEmpty())throw invalid("用例图至少需要一个参与者");if(caseSource.isEmpty())throw invalid("用例图至少需要一个用例");
            Map<String,String> actorIds=new LinkedHashMap<>(),caseIds=new LinkedHashMap<>();List<Actor> actors=new ArrayList<>();List<UseCase> cases=new ArrayList<>();boolean actorHierarchy=false;
            for(Actor actor:actorSource){String newId="A"+(actors.size()+1);actorIds.put(actor.id(),newId);actors.add(new Actor(newId,cleanValue(actor.name(),"参与者名称"),null));actorHierarchy|=!blank(actor.parentId());}
            String systemId="S1";for(UseCase useCase:caseSource){String newId="U"+(cases.size()+1);caseIds.put(useCase.id(),newId);cases.add(new UseCase(newId,systemId,cleanValue(useCase.name(),"用例名称")));}
            List<UseCaseRelation> rels=new ArrayList<>();Set<String> seen=new HashSet<>(),connectedCases=new HashSet<>();boolean droppedGeneralization=false;
            for(UseCaseRelation relation:safe(x.relations())){
                UseCaseRelationKind kind=relation.kind()==null?UseCaseRelationKind.ASSOCIATION:relation.kind();String from,to;
                if(kind==UseCaseRelationKind.GENERALIZATION){droppedGeneralization=true;continue;}
                if(kind==UseCaseRelationKind.ASSOCIATION){
                    if(actorIds.containsKey(relation.from())&&caseIds.containsKey(relation.to())){from=actorIds.get(relation.from());to=caseIds.get(relation.to());}
                    else if(actorIds.containsKey(relation.to())&&caseIds.containsKey(relation.from())){from=actorIds.get(relation.to());to=caseIds.get(relation.from());}
                    else throw invalid("ASSOCIATION必须连接参与者和用例");
                    connectedCases.add(to);
                }else{
                    if(!caseIds.containsKey(relation.from())||!caseIds.containsKey(relation.to()))throw invalid(kind+"只能连接两个用例");
                    from=caseIds.get(relation.from());to=caseIds.get(relation.to());if(from.equals(to))throw invalid("用例关系禁止自连接");connectedCases.add(from);connectedCases.add(to);
                }
                String key=from+">"+to+":"+kind;if(seen.add(key))rels.add(new UseCaseRelation("R"+(rels.size()+1),from,to,kind));
            }
            for(UseCase useCase:cases)if(!connectedCases.contains(useCase.id()))throw invalid("用例“"+useCase.name()+"”没有参与任何关系");
            List<Warning> ws=new ArrayList<>(warnings(x.warnings()));
            if(actorHierarchy||droppedGeneralization)ws.add(new Warning("USE_CASE_UNSUPPORTED_HIERARCHY_REMOVED","当前绘图器不支持参与者继承，已移除不受支持的层级关系。",1d));
            return new UseCaseIr("1.0",DiagramType.USE_CASE,cleanTitle(x.title(),"用例图"),systemId,cleanTitle(x.systemName(),x.title()),actors,cases,rels,ws);
        }
    }
    static final class BlockDiagramRuleEngine implements Rule<BlockDiagramIr>{
        public BlockDiagramIr normalize(BlockDiagramIr x){
            List<Block> source=unique(x.blocks(),Block::id);if(source.isEmpty())throw invalid("系统框图至少需要三个节点");
            Map<String,String> ids=new LinkedHashMap<>();Map<String,Block> byOriginal=new LinkedHashMap<>();List<Block> blocks=new ArrayList<>();Set<String> zones=new HashSet<>();
            for(Block block:source){String zone=Objects.toString(block.kind(),"").toUpperCase(Locale.ROOT);if(!Set.of("LEFT","CENTER","RIGHT").contains(zone))throw invalid("系统框图分区只能是LEFT、CENTER或RIGHT");String newId="B"+(blocks.size()+1);ids.put(block.id(),newId);byOriginal.put(block.id(),block);blocks.add(new Block(newId,zone,cleanValue(block.text(),"功能块名称")));zones.add(zone);}
            for(String zone:List.of("LEFT","CENTER","RIGHT"))if(!zones.contains(zone))throw invalid("系统框图缺少"+zone.toLowerCase(Locale.ROOT)+"分区节点");
            List<Edge> normalizedEdges=new ArrayList<>();Set<String> seen=new HashSet<>();Map<String,Integer> degree=new HashMap<>();ids.values().forEach(v->degree.put(v,0));int order=0;
            for(Edge edge:safe(x.edges())){
                if(!ids.containsKey(edge.from())||!ids.containsKey(edge.to()))throw invalid("系统框图连接引用不存在节点");String from=ids.get(edge.from()),to=ids.get(edge.to());if(from.equals(to))throw invalid("系统框图禁止节点自连接");
                String fromZone=Objects.toString(byOriginal.get(edge.from()).kind(),"").toUpperCase(Locale.ROOT),toZone=Objects.toString(byOriginal.get(edge.to()).kind(),"").toUpperCase(Locale.ROOT);if("RIGHT".equals(fromZone)&&"LEFT".equals(toZone))throw invalid("系统框图禁止从right反向连接left");
                String key=from+">"+to;if(seen.add(key)){normalizedEdges.add(new Edge("E"+(++order),from,to,nz(edge.kind()),"",order));degree.merge(from,1,Integer::sum);degree.merge(to,1,Integer::sum);}
            }
            if(normalizedEdges.isEmpty())throw invalid("系统框图至少需要一条连接");for(Block block:blocks)if(degree.getOrDefault(block.id(),0)==0)throw invalid("功能块“"+block.text()+"”没有参与连接");
            return new BlockDiagramIr("1.0",DiagramType.BLOCK_DIAGRAM,cleanTitle(x.title(),"系统框图"),blocks,normalizedEdges,warnings(x.warnings()));
        }
    }
    static final class SequenceDiagramRuleEngine implements Rule<SequenceDiagramIr>{
        public SequenceDiagramIr normalize(SequenceDiagramIr x){
            List<Participant> source=unique(x.participants(),Participant::id);if(source.size()<2||source.size()>6)throw invalid("时序图参与者数量必须为2至6个");
            Set<String> allowed=Set.of("ACTOR","BOUNDARY","CONTROL","SERVICE","DATABASE");Map<String,String> ids=new LinkedHashMap<>();List<Participant> participants=new ArrayList<>();
            for(Participant participant:source){String kind=Objects.toString(participant.kind(),"").toUpperCase(Locale.ROOT);if(!allowed.contains(kind))throw invalid("时序图参与者类型非法："+kind);String name=cleanValue(participant.name(),"参与者名称");if(name.matches("(?i).*Mapper.*")||name.contains("映射层")||name.contains("数据访问映射器"))throw invalid("时序图禁止Mapper或映射层");String newId="P"+(participants.size()+1);ids.put(participant.id(),newId);participants.add(new Participant(newId,kind,name));}
            List<Message> messages=new ArrayList<>();boolean asyncMapped=false;int order=0;
            for(Message message:safe(x.messages())){
                if(!ids.containsKey(message.from())||!ids.containsKey(message.to()))throw invalid("时序消息引用不存在参与者");if(messages.size()>=8)break;String text=cleanValue(message.text(),"消息内容");if(text.matches("(?i).*Mapper.*")||text.contains("映射层")||text.contains("数据访问映射器"))throw invalid("时序消息禁止Mapper或映射层");MessageKind kind=message.kind()==null?MessageKind.CALL:message.kind();if(kind==MessageKind.ASYNC){kind=MessageKind.CALL;asyncMapped=true;}messages.add(new Message("M"+(++order),ids.get(message.from()),ids.get(message.to()),kind,text,order));
            }
            if(messages.isEmpty())throw invalid("时序图至少需要一条消息");List<Warning> ws=new ArrayList<>(warnings(x.warnings()));if(asyncMapped)ws.add(new Warning("SEQUENCE_ASYNC_RENDERED_AS_CALL","当前时序图DSL不区分异步箭头，已按调用消息绘制。",1d));
            return new SequenceDiagramIr("1.0",DiagramType.SEQUENCE_DIAGRAM,cleanTitle(x.title(),"时序图"),participants,messages,ws);
        }
    }
    private static List<Edge> edges(List<Edge> input,Set<String> ids){List<Edge> out=new ArrayList<>();Set<String> seen=new HashSet<>();int order=0;for(Edge e:safe(input)){if(!ids.contains(e.from())||!ids.contains(e.to()))throw invalid("关系引用不存在节点："+e.from()+" -> "+e.to());String key=e.from()+">"+e.to()+":"+nz(e.label());if(seen.add(key))out.add(new Edge(id(e.id(),"e",++order),e.from(),e.to(),nz(e.kind()),nz(e.label()),order));}return out;}
    private static <T> List<T> unique(List<T> values,Function<T,String> id){List<T> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(T v:safe(values)){String key=id.apply(v);if(blank(key)||!seen.add(key))throw invalid("存在缺失或重复ID");out.add(v);}return out;}
    private static <T> Map<String,T> index(List<T> values,Function<T,String> id){Map<String,T> m=new LinkedHashMap<>();values.forEach(v->m.put(id.apply(v),v));return m;}
    private static String id(String value,String prefix,int number){String cleaned=blank(value)?"":value.replaceAll("[^A-Za-z0-9_-]","");return cleaned.isBlank()?prefix+number:cleaned;}
    private static String cleanTitle(String v,String d){return cleanValue(blank(v)?d:v,"标题");}
    private static String cleanValue(String value,String label){String cleaned=Objects.toString(value,"").replaceAll("[\\r\\n\\t]+"," ").replace('|','／').replace('｜','／').trim();if(cleaned.isBlank())throw invalid(label+"不能为空");return cleaned;}
    private static List<String> cleanItems(List<String> values){LinkedHashSet<String> out=new LinkedHashSet<>();for(String value:safe(values)){String cleaned=Objects.toString(value,"").replaceAll("[\\r\\n\\t]+"," ").replaceAll("[,，、;；|｜]+","／").trim();if(!cleaned.isBlank())out.add(cleaned);}return new ArrayList<>(out);}
    private static String card(String v){return "1".equals(v)?"1":"M".equalsIgnoreCase(v)?"M":"N";}private static String nz(String v){return v==null?"":v;}private static boolean blank(String v){return v==null||v.isBlank();}private static <T> List<T> safe(List<T> v){return v==null?List.of():v;}private static List<Warning> warnings(List<Warning> v){return safe(v);}
    private static DiagramGenerationException invalid(String message){return new DiagramGenerationException("DIAGRAM_RELATION_INVALID",message+"，原图已恢复。");}
}
