package com.dropai.rewrite.service.diagram;

import com.dropai.rewrite.service.diagram.DiagramIr.DiagramType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DiagramPromptFactory {
    private static final String SYSTEM="""
            你是 DROP-AI DiagramIR 图形关系规划器。你的唯一任务是把用户需求转换成当前图类型的完整 DiagramIR JSON。这是完整替换结果，不是修改补丁。
            生成前必须先理解“题目/研究对象/业务过程”：标题和节点都必须围绕用户给出的主题，不得套用通用业务流程，不得把背景介绍、技术名词堆砌或示例内容当作主流程。
            只输出一个符合当前 JSON Schema 的 JSON 对象；不输出 Markdown、解释、注释或分析；不生成 DSL、坐标、字体、颜色、大小、布局或样式；不得引用不存在的ID或生成重复ID；修改现有IR时返回完整新IR并尽量保留未修改对象ID；不确定内容写入warnings，禁止伪装为事实。
            """;
    private final DiagramSchemaFactory schemas; private final ObjectMapper mapper;
    public DiagramPromptFactory(DiagramSchemaFactory schemas,ObjectMapper mapper){this.schemas=schemas;this.mapper=mapper;}
    public Prompt build(DiagramType type,String instruction,DiagramIr current){
        try{String currentJson=current==null?"null":mapper.writeValueAsString(current);JsonNode schema=schemas.schema(type);
            String user="当前图类型："+type+"\n关系规则："+rules(type)+"\n复杂度限制："+limits(type)+"\nJSON Schema："+mapper.writeValueAsString(schema)+"\n用户要求："+instruction+"\n当前精简DiagramIR："+currentJson;
            return new Prompt(SYSTEM,user,schema,SYSTEM.length()+user.length());
        }catch(Exception e){throw new DiagramGenerationException("DIAGRAM_IR_SCHEMA_INVALID","无法构建DiagramIR提示词");}}
    private String rules(DiagramType t){return switch(t){
        case FLOWCHART->"先识别题目、控制对象、起止条件和主流程顺序；标题必须来自用户主题，禁止使用“业务流程”“系统流程”等泛标题；只画一个核心过程，不拆成多个子系统；必须有且只有一个start，必须至少有一个end，所有从start可达的分支都必须能到达end；decision必须是二元判断，且只能有两个标签不同的出口；三档及以上状态必须合并为一个处理节点，例如“按光照等级计算PWM”，不得从同一个decision拉出三条或四条分支；持续循环必须增加“是否停止”二元判断，“否”回到采集节点，“是”进入end；非end节点不得成为死节点。";
        case ER_DIAGRAM->"识别至少2个实体、主外键、唯一和可空；每个实体必须有非空名称和至少1个属性；至少生成1条关系，所有实体都必须参与关系，禁止自连接；识别1:1、1:N、M:N；明确SQL约束标记DECLARED且不得修改；仅引用真实表字段；推断标记INFERRED及confidence。";
        case FUNCTION_MODULE->"真实DSL固定为系统→模块→功能三级结构。modules中的每一项都是系统下的直属模块，禁止创建根模块、parentId或空壳父模块；每个模块必须有非空name和至少1项非空functions；操作步骤不得当作模块。";
        case ARCHITECTURE->"识别至少2个层，每层必须有非空名称和至少1个非空组件；层名不得重复，同层组件不得重复；当前DSL按相邻层自动连线，不输出dependencies或布局。";
        case USE_CASE->"只生成一个系统边界；至少1个参与者和1个用例；ASSOCIATION必须从参与者指向用例；INCLUDE和EXTEND只能连接同一系统内的两个用例；每个用例都必须至少参与一条关系；当前DSL不支持GENERALIZATION和参与者parentId，禁止生成。";
        case BLOCK_DIAGRAM->"识别功能块、输入、输出和真实连接；必须同时包含left、center、right三个分区且每个节点都参与连接；禁止自连接及right到left的反向连接；区分数据、控制、能量和信号语义。";
        case SEQUENCE_DIAGRAM->"识别2到6个参与者和1到8条有明确顺序的核心消息；参与者类型仅限ACTOR、BOUNDARY、CONTROL、SERVICE、DATABASE；消息仅使用CALL或RETURN，RETURN由返回方向表达；禁止Mapper或映射层。";};}
    private String limits(DiagramType t){return switch(t){
        case FLOWCHART->"只保留一个主流程，6到10个节点最合适，硬性最多10个节点；最多2个decision；节点文字最多12个中文字符或18个英文字符；标题控制在6到18个中文字符；按“采集/判断/输出/显示/通信”等原文顺序抽象，不把每个光照档位、按键细节或异常分支展开成长链。";
        case ER_DIAGRAM->"最多8个核心实体，每个实体最多8个核心属性；只保留直接业务关系。";
        case FUNCTION_MODULE->"最多3级、16个模块，每个模块最多5项功能；合并同义功能。";
        case ARCHITECTURE->"最多6层，每层最多6个核心组件。";
        case USE_CASE->"最多5个参与者、12个核心用例；忽略界面操作细节。";
        case BLOCK_DIAGRAM->"最多12个核心功能块；只保留主要数据、控制或信号连接。";
        case SEQUENCE_DIAGRAM->"最多6个参与者、8条核心消息；合并连续的内部调用和重复返回。";};}
    public record Prompt(String system,String user,JsonNode schema,int characterCount){}
}
