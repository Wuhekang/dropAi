# DropAI 毕业设计生成系统诊断报告

生成时间：2026-06-18T23:12:26+08:00

## 诊断结论

当前系统链路已经不是 `StructureTree -> PrimitiveGenerator -> Render` 的空链路。`StructureTree`、`PartList`、`AssemblyTree`、`ConstraintList`、`DrawingPlan`、`BOM` 和 `DOCX` 都真实执行并参与下一层。

本轮修复重点是把链路从“能跑”提升到“可信”：识别失败不再默认生成通用机械设备；结构树做去重归一；标准件 mock 不再冒充在线检索；装配约束增加接口字段；CAD 只使用 `DrawingPlan` 生成清晰三视图；DOCX 低于完整章节不标记成功。

## 关键数量

| 项目 | 数量 |
| --- | ---: |
| 结构节点 | 12 |
| 解析零件 | 12 |
| 标准件 | 6 |
| 在线真实标准件 | 0 |
| Mock标准件 | 6 |
| 非标件 | 6 |
| 装配组件 | 12 |
| 装配约束 | 12 |
| CAD视图 | 3 |
| CAD尺寸 | 12 |
| BOM条目 | 12 |
| DOCX章节 | 11 |

## 质量门禁

- 项目识别 fallback：已禁止进入正式生成。
- 结构树重复：已由 `StructureTreeNormalizer` 合并。
- 标准件来源：mock 统一标记为 `retrievalStatus=mock`，不再显示 `online_found`。
- 装配约束：已包含 `axisId`、`mountingFace`、`holePattern`、`contactFace`、`symmetryPlane`、`offsetDistance`、`source`。
- CAD：输入来源为 `DrawingPlan`，当前只保留主视图、俯视图、侧视图、BOM、参数表、技术要求、标题栏。
- 尺寸来源：不再用 `component envelope` 冒充正式尺寸，缺失尺寸标记为“待校核”。
- DOCX：已恢复摘要、关键词、第1章到第6章、参考文献、致谢的完整门禁。
- 中文编码：本轮重写的核心模块和审计报告均使用 UTF-8。

## 仍需注意

1. 真实在线标准件平台尚未接入，当前只是接口层和 mock 数据流跑通。
2. 3D标准件是参数化近似模型，不等于真实STEP模型。
3. 图片、CAD参考图目前还没有深度语义解析。
4. 论文内容已经完整，但要达到优秀样稿密度还需要继续按参考稿增强图表和计算深度。
