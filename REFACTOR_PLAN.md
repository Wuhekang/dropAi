# 通用机械类毕业设计成果包重构方案

## 当前问题

- 原流程由固定的 `length/width/height/wheelbase/wheelDiameter` 参数驱动，只适合单一小车侧视示意图。
- 前端与后端分别维护一份绘图逻辑，论文、计算和CAD没有共享数据源。
- DXF只有轮廓实体，缺少工程图图层、尺寸、文字、图框、标题栏、技术要求与零件拆分。
- 原工作流仅生成一张DXF和一份粗粒度Word，无法形成完整毕业设计交付包。

## 目标架构

统一以 `DesignProject` JSON 作为单一事实来源：

`资料解析 → 设计识别 → 参数分类 → 工程计算 → 工程图 → SolidWorks宏 → 论文 → 成果包`

模块位于 `backend/src/main/java/com/dropai/rewrite/modules/`：

- `documentParser`：解析和分类任务书、开题报告、模板、参考资料与CAD参考文件。
- `designAnalyzer`：识别通用机械设计目标与设备名称。
- `parameterEngine`：维护明确参数、推导参数、建议参数。
- `calculationEngine`：程序化计算并将结果写回统一项目模型。
- `drawingEngine`：独立生成DXF与SVG预览，输出总装图和主要零件图。
- `swMacroEngine`：生成零件基础实体VBA宏。
- `paperEngine`：生成论文、计算书与建模步骤DOCX。
- `exportEngine`：生成参数JSON、PDF预览与ZIP成果包。

## 已完成的第一阶段

- 新增通用项目JSON、成果包分析接口和成果包生成接口。
- 参数修改后会重新计算，并重新生成论文、计算书、CAD、宏和ZIP。
- DXF包含工程图图层、图框、标题栏、三视图、尺寸线、文字、技术要求和参数表。
- 输出总装图、壳体、底座、进出口与关键连接件图。
- 输出SolidWorks VBA宏与建模步骤DOCX。
- 前端升级为十步成果工作台。

## 后续深化

- 让AI分析结果严格按 `DesignProject` JSON Schema 输出明确参数和设计目标。
- 增加按设备族注册的计算规则与几何构造器，而不是在核心流程写死设备。
- 增加标准件库、材料库、公式来源和校核适用条件。
- 将工程图尺寸对象升级为完整DXF DIMENSION实体，并增加剖视图、明细栏和形位公差。
- 使用论文模板样式、真实参考文献与图片生成正式排版版本。
