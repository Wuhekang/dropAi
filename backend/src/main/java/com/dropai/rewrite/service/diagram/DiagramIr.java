package com.dropai.rewrite.service.diagram;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

@JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="diagramType",visible=true)
@JsonSubTypes({
        @JsonSubTypes.Type(value=DiagramIr.FlowchartIr.class,name="FLOWCHART"),
        @JsonSubTypes.Type(value=DiagramIr.ErDiagramIr.class,name="ER_DIAGRAM"),
        @JsonSubTypes.Type(value=DiagramIr.FunctionModuleIr.class,name="FUNCTION_MODULE"),
        @JsonSubTypes.Type(value=DiagramIr.ArchitectureIr.class,name="ARCHITECTURE"),
        @JsonSubTypes.Type(value=DiagramIr.UseCaseIr.class,name="USE_CASE"),
        @JsonSubTypes.Type(value=DiagramIr.BlockDiagramIr.class,name="BLOCK_DIAGRAM"),
        @JsonSubTypes.Type(value=DiagramIr.SequenceDiagramIr.class,name="SEQUENCE_DIAGRAM")})
public sealed interface DiagramIr permits DiagramIr.FlowchartIr,DiagramIr.ErDiagramIr,DiagramIr.FunctionModuleIr,DiagramIr.ArchitectureIr,DiagramIr.UseCaseIr,DiagramIr.BlockDiagramIr,DiagramIr.SequenceDiagramIr {
    String version(); DiagramType diagramType(); String title();
    enum DiagramType {FLOWCHART,ER_DIAGRAM,FUNCTION_MODULE,ARCHITECTURE,USE_CASE,BLOCK_DIAGRAM,SEQUENCE_DIAGRAM}
    enum FlowNodeKind {START,PROCESS,DECISION,END}
    enum RelationSource {DECLARED,INFERRED,USER}
    enum UseCaseRelationKind {ASSOCIATION,INCLUDE,EXTEND,GENERALIZATION}
    enum MessageKind {CALL,ASYNC,RETURN}
    record Warning(String code,String message,Double confidence){}
    record FlowNode(String id,FlowNodeKind kind,String text){}
    record Edge(String id,String from,String to,String kind,String label,Integer order){}
    record FlowchartIr(String version,DiagramType diagramType,String title,List<FlowNode> nodes,List<Edge> edges,List<Warning> warnings) implements DiagramIr{}
    record ErAttribute(String id,String name,String sqlType,boolean primaryKey,boolean foreignKey,boolean unique,boolean nullable){}
    record ErEntity(String id,String name,List<ErAttribute> attributes,boolean associationEntity){}
    record ErRelation(String id,String sourceEntityId,String sourceField,String targetEntityId,String targetField,String sourceCardinality,String targetCardinality,String name,RelationSource source,Double confidence){}
    record ErDiagramIr(String version,DiagramType diagramType,String title,List<ErEntity> entities,List<ErRelation> relations,List<String> coreEntityIds,List<Warning> warnings) implements DiagramIr{}
    record ModuleNode(String id,String name,String parentId,List<String> functions){}
    record FunctionModuleIr(String version,DiagramType diagramType,String title,List<ModuleNode> modules,List<Warning> warnings) implements DiagramIr{}
    record ArchitectureLayer(String id,String name,List<String> components){}
    record ArchitectureIr(String version,DiagramType diagramType,String title,List<ArchitectureLayer> layers,List<Edge> dependencies,List<Warning> warnings) implements DiagramIr{}
    record Actor(String id,String name,String parentId){}
    record UseCase(String id,String systemId,String name){}
    record UseCaseRelation(String id,String from,String to,UseCaseRelationKind kind){}
    record UseCaseIr(String version,DiagramType diagramType,String title,String systemId,String systemName,List<Actor> actors,List<UseCase> useCases,List<UseCaseRelation> relations,List<Warning> warnings) implements DiagramIr{}
    record Block(String id,String kind,String text){}
    record BlockDiagramIr(String version,DiagramType diagramType,String title,List<Block> blocks,List<Edge> edges,List<Warning> warnings) implements DiagramIr{}
    record Participant(String id,String kind,String name){}
    record Message(String id,String from,String to,MessageKind kind,String text,Integer order){}
    record SequenceDiagramIr(String version,DiagramType diagramType,String title,List<Participant> participants,List<Message> messages,List<Warning> warnings) implements DiagramIr{}
}
