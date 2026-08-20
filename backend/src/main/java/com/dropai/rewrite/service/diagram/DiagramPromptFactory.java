package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DiagramPromptFactory {
    private static final String SYSTEM="""
            你是 DROP-AI DiagramIR 图形关系规划器。你的唯一任务是把用户需求转换成当前图类型的完整 DiagramIR JSON。这是完整替换结果，不是修改补丁。
            只输出一个符合当前 JSON Schema 的 JSON 对象；不输出 Markdown、解释、注释或分析；不生成 DSL、坐标、字体、颜色、大小、布局或样式；不得引用不存在的ID或生成重复ID；修改现有IR时返回完整新IR并尽量保留未修改对象ID；不确定内容写入warnings，禁止伪装为事实。
            """;
    private final DiagramSchemaFactory schemas; private final ObjectMapper mapper;
    public DiagramPromptFactory(DiagramSchemaFactory schemas,ObjectMapper mapper){this.schemas=schemas;this.mapper=mapper;}
    public Prompt build(DiagramType type,String instruction,DiagramIr current){
        try{String currentJson=current==null?"null":mapper.writeValueAsString(current);JsonNode schema=schemas.schema(type);
            String user="当前图类型："+type+"\n关系规则："+rules(type)+"\nJSON Schema："+mapper.writeValueAsString(schema)+"\n用户要求："+instruction+"\n当前精简DiagramIR："+currentJson;
            return new Prompt(SYSTEM,user,schema,SYSTEM.length()+user.length());
        }catch(Exception e){throw new DiagramGenerationException("DIAGRAM_IR_SCHEMA_INVALID","无法构建DiagramIR提示词");}}
    private String rules(DiagramType t){return switch(t){
        case FLOWCHART->"必须有一个起点；普通节点从起点可达；decision至少两个标签不同的出口；循环用回边；非end节点不得成为死节点；路径最终到end或回到循环。";
        case ER_DIAGRAM->"识别实体、主外键、唯一和可空；识别1:1、1:N、M:N；明确SQL约束标记DECLARED且不得修改；仅引用真实表字段；推断标记INFERRED及confidence。";
        case FUNCTION_MODULE->"识别总功能及一二三级模块；使用单一父子层级；操作步骤不得当作模块。";
        case ARCHITECTURE->"识别层、组件、服务、数据库和外部系统；保留分层；区分依赖但不生成布局。";
        case USE_CASE->"识别参与者、用例、系统边界；区分ASSOCIATION、INCLUDE、EXTEND、GENERALIZATION。";
        case BLOCK_DIAGRAM->"识别功能块、输入、输出和真实连接；区分数据、控制、能量和信号语义。";
        case SEQUENCE_DIAGRAM->"识别参与者和生命线；消息有明确顺序；区分CALL、ASYNC、RETURN；当前DSL最多保留8条核心消息。";};}
    public record Prompt(String system,String user,JsonNode schema,int characterCount){}
}
