# DropAI Mechanical Reality Audit

审计日期：2026-08-04
审计范围：当前仓库中的机械请求入口、设计对象、CAD 脚本生成、STEP 导出、装配、工程图及遗留机械生成入口。
审计方式：静态追踪实际可执行调用链；不以类名、DSL 字段名、测试报告或数据库状态代替 CAD 实现证据。

## 结论

**判定：当前机械模块尚未真正完成重构。**

新主链路能够通过 FreeCAD/OpenCascade 生成真实 BRep，并导出 STEP、STL、DXF、SVG 和 PDF 文件；但实际三维几何仍由硬编码的 Box、Cylinder、Sphere 及布尔 Cut/Fuse 拼接产生。CAD DSL 中声明的 Sketch、Extrude、Fillet、Chamfer 等特征没有被几何生成器解释执行，装配约束也只作为元数据记录，实际装配仅应用零件坐标。

此外，旧的 `DesignWorkflowService -> ParametricDxfService` 机械 DXF 生成入口仍被后端控制器和前端 API 暴露，因此旧机械生成逻辑并未删除干净。

## 1. Primitive / Mesh 检查

| 文件 | 实际证据 | 判定 |
|---|---|---|
| `backend/src/main/java/com/dropai/rewrite/mechanicalengine/cad/FreeCadJobGenerator.java` | 生成的 Python 使用 `Part.makeBox`、`Part.makeCylinder`、`Part.makeSphere`，并通过 `.cut()`、`.fuse()` 组合最终形状 | **核心生成仍是 Primitive 拼接** |
| `backend/src/main/java/com/dropai/rewrite/mechanicalengine/cad/FreeCadJobGenerator.java` | 使用 `Mesh.export` 导出浏览器预览 STL | Mesh 仅用于预览导出，不是 STEP 几何源 |
| `frontend/src/components/MechanicalBrepViewer.vue` | Three.js `Mesh` 与 `STLLoader` 显示 STL | 仅为浏览器显示，不属于 CAD 生成 |
| `backend/src/main/java/com/dropai/rewrite/service/ParametricDxfService.java` | 直接拼写 DXF `LINE`、`CIRCLE` 图元 | 遗留二维 Primitive 生成逻辑仍可访问 |

`MechanicalChiefEngineer` 虽然创建了名为 `sketch`、`extrude`、`fillet`、`chamfer`、`revolve`、`thread` 的 DSL 特征，但 `FreeCadJobGenerator` 不按这些特征生成几何，而是按固定零件名称进入硬编码分支。因此这些字段目前主要是描述性元数据。

## 2. 真正的 STEP 生成调用链

当前机械工作台主链路如下：

```text
User Request
  -> frontend/src/views/NewProject/index.vue
  -> frontend/src/api/rewrite.js: executeMechanicalProject()
  -> POST /mechanical/projects/execute
  -> MechanicalEngineController.execute()
  -> MechanicalEngineService.execute()
  -> MechanicalChiefEngineer.design()
  -> MechanicalProject / CadPart / AssemblySpec
  -> CadDslService.write()
  -> FreeCadJobGenerator.write()
  -> FreeCadExecutor.execute()
  -> FreeCAD Python: Part.makeBox/makeCylinder/makeSphere + cut/fuse
  -> Part.export(objects, "Assembly.STEP")
```

关键事实：

- Agent/设计层输出 `MechanicalProject`、零件 DSL 和 `AssemblySpec`。
- `CadDslService` 只把设计对象序列化成 `cad-model-spec.json`。
- `FreeCadJobGenerator` 没有通用解释 DSL 特征，而是根据固定零件名称生成 Python Primitive 代码。
- `FreeCadExecutor` 调用 FreeCAD 执行该 Python 脚本。
- 最终 STEP 由 FreeCAD 的 `Part.export` 写出。

仍然存在的旧并行入口：

```text
User Request
  -> POST /engineering-writing/workflows
  -> EngineeringWritingController
  -> DesignWorkflowService.submit()
  -> ParametricDxfService.generate()
  -> 手写 LINE/CIRCLE DXF
```

另有 `GET /engineering-writing/cad/dxf` 可直接调用同一旧 DXF 服务。该入口在 `frontend/src/api/rewrite.js` 中仍有对应 API。

## 3. STEP 来源判定

| 选项 | 结论 | 说明 |
|---|---|---|
| A. OpenCascade BRep | **是（底层内核和文件来源）** | FreeCAD `Part` 形状是 OpenCascade BRep，STEP 由 `Part.export` 导出 |
| B. FreeCAD Part Design | **否** | 未建立真实 Sketcher Sketch、PartDesign Body、Pad/Pocket、Fillet/Chamfer 特征树 |
| C. Primitive 拼接 | **是（实际建模方法）** | 实际形状由 Box/Cylinder/Sphere 与 Cut/Fuse 硬编码组合 |

因此不能只标记为 A 来宣称完成了参数化特征建模。准确表述是：**OpenCascade BRep 输出 + Primitive 布尔拼接建模**，不是 FreeCAD Part Design 工作流。

## 4. 零件特征检查

| 能力 | DSL/名称中存在 | CAD 中真实执行 | 判定 |
|---|---:|---:|---|
| Sketch | 是 | 否 | 失败 |
| Feature | 是 | 仅用 `PartDesign::Feature` 容纳已拼好的最终 Shape | 不构成特征树 |
| Extrude / Pad | 是 | 否；实际用 `makeBox`/`makeCylinder` | 失败 |
| Cut / Pocket | 是 | 有 `.cut()` 布尔运算，但由固定零件分支硬编码 | 部分存在，不是 DSL 驱动 |
| Fillet | 是 | 否 | 失败 |
| Chamfer | 是 | 否 | 失败 |
| Revolve | 是 | 否；实际用圆柱组合 | 失败 |
| Thread | 是 | 否 | 失败 |

`PartDesign::Feature` 在这里仅包装一个已经完成的 Shape。它不会自动把 Primitive 布尔结果变成可编辑的 Sketch/Pad/Pocket/Fillet/Chamfer 历史树。

按本次审计标准，零件生成判定为 **失败**，仍属于旧式 Primitive 几何生成。

## 5. Assembly 检查

设计对象中确实存在：

- `components`
- `constraints`
- `position`
- `orientation`
- fixed / coincident / slider / concentric 等约束名称

但实际 FreeCAD 脚本只执行：

```text
obj.Placement.Base = component.position
```

没有发现：

- Mate/Constraint 求解器调用
- Assembly Workbench 约束对象
- 约束驱动的 Placement 计算
- 对 `orientation` 的实际应用
- 装配层级或可求解连接关系

最终 `Part.export(objects, Assembly.STEP)` 导出的是按坐标摆放的零件集合。所谓“约束回执”只记录 JSON 中声明的约束数量，验证器也只比较元数据数量，没有验证 CAD 文档中是否存在并成功求解约束。

按“如果只是坐标移动则失败”的标准，Assembly 判定为 **失败**。

## 6. Drawing 检查

工程图不是只有数据库记录，当前链路确实生成文件：

| 格式 | 生成方式 | 真实性判定 |
|---|---|---|
| SVG | 对实际 BRep 使用 `Drawing.projectToSVG` 生成装配和零件投影 | 真实文件 |
| DXF | 使用 `importDXF.export(objects, Assembly.dxf)` | 真实文件 |
| PDF | `EngineeringArtifactService` 读取实际投影线并通过 PDFBox 绘制 | 真实文件 |

但工程图完整性仍不足：

- 装配 SVG 主要是前、上、右投影，没有证据表明已生成真实剖视图。
- 零件 SVG 主要是单一投影。
- 未发现由真实几何语义驱动的完整尺寸、公差、基准、材料、粗糙度和标题栏标注。
- DXF 是几何直接导出，没有证据表明包含完整制造标注。
- PDF 是投影线加固定文本，不等同于完整可制造工程图。

因此：**Drawing 文件真实性通过，工程交付完整性仅部分通过。**

## 7. 旧逻辑删除状态

旧机械生成逻辑没有完全删除：

1. `DesignWorkflowService` 仍是活动 Spring Service，并异步调用 `ParametricDxfService.generate()`。
2. `EngineeringWritingController` 仍暴露工作流生成和直接 DXF 下载接口。
3. `frontend/src/api/rewrite.js` 仍保留这些旧接口的调用函数。
4. 新主链路内部仍采用与旧系统相同性质的 Primitive/Boolean 生成方式，只是改为通过 FreeCAD/OpenCascade 输出真实 BRep 和 STEP。

## 8. 验证器可信度

`MechanicalArtifactValidator` 能验证 STEP/STL/SVG/DXF/PDF 文件签名、尺寸、投影数据及实体数量，能够防止“只有数据库记录却标记成功”的一部分问题。

但它当前不能证明：

- DSL 中的 Sketch/Extrude/Fillet/Chamfer 被真正执行。
- FreeCAD 文档存在真实 Part Design 特征树。
- Assembly constraints 被应用和求解。
- 工程图包含完整制造标注。

所以当前测试通过不能推导出“机械核心重构已完成”。它只能证明文件生成链路和基础 BRep 导出可工作。

## 最终审计表

| 审计项 | 结果 |
|---|---|
| 旧机械生成逻辑已删除 | **失败** |
| STEP 为真实 OpenCascade BRep | **通过** |
| STEP 来自 Part Design 特征树 | **失败** |
| 不再使用 Primitive 拼接 | **失败** |
| Sketch/Extrude/Cut/Fillet/Chamfer 真实执行 | **失败** |
| 装配 Mate/Constraint 真实求解 | **失败** |
| 仅靠坐标 Placement 装配已消除 | **失败** |
| DXF/SVG/PDF 为真实文件 | **通过** |
| 工程图达到完整制造交付要求 | **部分通过/仍不足** |

**总判定：MECHANICAL CORE REWRITE NOT COMPLETE。**

本报告仅记录审计结果；除新增本报告外，未修改机械代码、数据库结构或配置。
