# ThesisDiagram DSL

版本：1.5

统一生成器支持 `@FunctionModule、@Flowchart、@ERDiagram、@ArchitectureDiagram、@UseCaseDiagram、@BlockDiagram、@SequenceDiagram`。

## 通用注释模块

七种图形统一使用 `//= ... =//` 块注释。注释可以单独占一行，也可以跨越多行；注释中的头标记、节点、连接和章节名均不参与解析、校验与绘图。

```dsl
//= 这是一条单行注释 =//
@Flowchart

//=
这里可以记录多行设计说明。
注释中的 N99|process|示例节点 不会生成图形。
=//
```

注释不能嵌套。建议将注释单独成行，不要把注释符号插在标识符或连接表达式内部。程序清除注释时保留原始换行，因此问题列表中的行号仍与编辑器一致。

## @Flowchart 默认分支规范

流程图节点仅使用 `top、bottom、left、right` 四个固定端口。中央主流程从上向下排列，连接使用 `source.bottom → target.top`。

普通节点多个出口时不生成虚拟分流圆点：第一条连接作为向下主流程；附加连接默认从源流程框 `right` 水平连接右侧目标流程框 `left`。支路内部继续使用 `bottom → top`；支路回归主流程时，从支路末端 `left` 直接连接目标主流程框 `right`。分支和回归连接不得吸附到主流程线。

判断节点第一条分支从 `bottom` 输出，第二条分支从 `right` 输出。所有连接仅由水平和垂直线段组成，Canvas、JSON、PNG、VSDX 共用布局中的端口和 `points`。

显式 `branch`、`merge` 仍可用于复杂旧文件，但属于可选兼容语法。普通多出口和多入口不会自动创建可见或虚拟圆点。超过三条后续流程会产生 `FLOW_TOO_MANY_BRANCHES` 警告，建议拆分流程或显式使用 `branch`。

### 连接标签定位

流程连接的标签在布局阶段绑定到明确线段。程序把 `points` 拆分为连续线段，优先选择最长水平线段，其次选择次长水平线段；没有水平线段时才选择最长垂直线段。

水平线标签位于线段上方12像素并水平居中，位置必须保持在线段两端范围内。支路返回标签必须绑定到向左返回的水平线段，禁止放在前置垂直下降线右侧。判断节点 `right/left` 出口的短标签位于水平线上方；纯竖直 `bottom` 出口标签位于竖线右侧。

布局结果必须保存 `label_position、label_segment_index、label_anchor`。Canvas、PNG、JSON和VSDX直接使用这些数据，导出器不得重新计算标签位置。VSDX标签使用独立、透明、无边框且可编辑的文本图形。

其他完整规则见 `ThesisDiagram_DSL七种图形语言规范.md`。
