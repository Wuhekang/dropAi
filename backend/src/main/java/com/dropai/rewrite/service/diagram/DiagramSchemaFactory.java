package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class DiagramSchemaFactory {
    private final ObjectMapper mapper; public DiagramSchemaFactory(ObjectMapper mapper){this.mapper=mapper;}
    public JsonNode schema(DiagramType type){ObjectNode p=props(type);switch(type){
        case FLOWCHART->{p.set("nodes",array(obj("id","string","kind",enums("START","PROCESS","DECISION","END"),"text","string")));p.set("edges",edgeArray());}
        case ER_DIAGRAM->{p.set("entities",array(obj("id","string","name","string","associationEntity","boolean","attributes",array(obj("id","string","name","string","sqlType","string","primaryKey","boolean","foreignKey","boolean","unique","boolean","nullable","boolean")))));p.set("relations",array(obj("id","string","sourceEntityId","string","sourceField","string","targetEntityId","string","targetField","string","sourceCardinality",enums("1","N","M"),"targetCardinality",enums("1","N","M"),"name","string","source",enums("DECLARED","INFERRED","USER"),"confidence","number")));p.set("coreEntityIds",array(typeNode("string")));}
        case FUNCTION_MODULE->p.set("modules",array(obj("id","string","name","string","parentId",nullableString(),"functions",array(typeNode("string")))));
        case ARCHITECTURE->{p.set("layers",array(obj("id","string","name","string","components",array(typeNode("string")))));p.set("dependencies",edgeArray());}
        case USE_CASE->{p.set("systemId",typeNode("string"));p.set("systemName",typeNode("string"));p.set("actors",array(obj("id","string","name","string","parentId",nullableString())));p.set("useCases",array(obj("id","string","systemId","string","name","string")));p.set("relations",array(obj("id","string","from","string","to","string","kind",enums("ASSOCIATION","INCLUDE","EXTEND","GENERALIZATION"))));}
        case BLOCK_DIAGRAM->{p.set("blocks",array(obj("id","string","kind",enums("LEFT","CENTER","RIGHT"),"text","string")));p.set("edges",edgeArray());}
        case SEQUENCE_DIAGRAM->{p.set("participants",array(obj("id","string","kind",enums("ACTOR","BOUNDARY","CONTROL","SERVICE","DATABASE"),"name","string")));p.set("messages",array(obj("id","string","from","string","to","string","kind",enums("CALL","ASYNC","RETURN"),"text","string","order","integer")));}}
        ObjectNode root=base(type);root.set("properties",p);return root;}
    private ObjectNode base(DiagramType type){ObjectNode n=mapper.createObjectNode();n.put("type","object");n.put("additionalProperties",false);n.set("required",arrayStrings(required(type)));return n;}
    private String[] required(DiagramType t){return switch(t){case FLOWCHART->new String[]{"version","diagramType","title","nodes","edges","warnings"};case ER_DIAGRAM->new String[]{"version","diagramType","title","entities","relations","coreEntityIds","warnings"};case FUNCTION_MODULE->new String[]{"version","diagramType","title","modules","warnings"};case ARCHITECTURE->new String[]{"version","diagramType","title","layers","dependencies","warnings"};case USE_CASE->new String[]{"version","diagramType","title","systemId","systemName","actors","useCases","relations","warnings"};case BLOCK_DIAGRAM->new String[]{"version","diagramType","title","blocks","edges","warnings"};case SEQUENCE_DIAGRAM->new String[]{"version","diagramType","title","participants","messages","warnings"};};}
    private ObjectNode props(DiagramType type){ObjectNode p=mapper.createObjectNode();p.set("version",constant("1.0"));p.set("diagramType",constant(type.name()));p.set("title",typeNode("string"));p.set("warnings",array(obj("code","string","message","string","confidence",nullableNumber())));return p;}
    private ObjectNode edgeArray(){return array(obj("id","string","from","string","to","string","kind","string","label","string","order","integer"));}
    private JsonNode obj(Object... values){ObjectNode n=mapper.createObjectNode();n.put("type","object");n.put("additionalProperties",false);ObjectNode p=n.putObject("properties");ArrayNode req=n.putArray("required");for(int i=0;i<values.length;i+=2){String key=(String)values[i];Object value=values[i+1];p.set(key,value instanceof JsonNode j?j:typeNode((String)value));req.add(key);}return n;}
    private ObjectNode typeNode(String type){return mapper.createObjectNode().put("type",type);} private ObjectNode constant(String value){return mapper.createObjectNode().put("const",value);}
    private ObjectNode enums(String... values){ObjectNode n=mapper.createObjectNode().put("type","string");n.set("enum",arrayStrings(values));return n;}
    private ObjectNode nullableString(){ObjectNode n=mapper.createObjectNode();ArrayNode a=n.putArray("type");a.add("string");a.add("null");return n;}
    private ObjectNode nullableNumber(){ObjectNode n=mapper.createObjectNode();ArrayNode a=n.putArray("type");a.add("number");a.add("null");return n;}
    private ObjectNode array(JsonNode items){ObjectNode n=mapper.createObjectNode().put("type","array");n.set("items",items);return n;}
    private ArrayNode arrayStrings(String... values){ArrayNode a=mapper.createArrayNode();for(String v:values)a.add(v);return a;}
}
