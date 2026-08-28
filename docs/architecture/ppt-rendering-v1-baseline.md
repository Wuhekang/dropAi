# DokiAI Academic PPT Rendering Contract V1

## 1. 文档状态

- 状态：冻结（Frozen）
- 基线：Rendering Contract V1
- 适用范围：DokiAI Academic PPT 确定性渲染链路
- 核心原则：内容规划与视觉渲染严格分离；相同输入必须产生相同的标准化渲染计划。

本文件是 Rendering V1 的架构约束。后续实现、测试和评审均不得绕过本基线。需要改变本基线时，必须通过独立的架构决策和版本升级完成，不得在 Renderer 或布局实现中隐式改变。

## 2. V1 阶段目标

Rendering V1 的目标是建立一条职责清晰、结果可复现、失败可定位、质量可阻断的正式 PPTX 渲染链路：

1. 将已验证的页面树编译为唯一、完整且不可变的 `SlideRenderPlan`。
2. 以配置化主题和确定性布局规则控制页面视觉表现。
3. 将 Renderer 降级为只执行绘制指令的纯执行器。
4. 在生成前后分别检查可渲染性、绘制计划、PPTX 包结构和视觉结果。
5. 保证本地与服务器在相同输入和运行配置下得到一致的标准化 RenderPlan。
6. 保持文字、卡片、表格等内容为原生可编辑 PPT 元素。

## 3. V1 非目标

本阶段不负责：

- 修改 DocumentParser、ContentPlanner、ContentSanitizer、OutlinePlanner 或 OutlineValidator。
- 修改页面内容、数量、顺序、答辩结论或素材绑定关系。
- 新增第二套主题、随机模板、复杂动画或在线模板市场。
- 开发机械、环境、视觉传达等专业皮肤。
- 调整数据库结构或重新设计前端工作流。
- 让大模型参与颜色、布局或渲染阶段决策。
- 将整页渲染为图片来代替可编辑 PPT 元素。

超出范围的需求只能记录到后续 Backlog，不得进入 Rendering V1 当前分支。

## 4. 冻结的确定性渲染链路

```text
ValidatedPresentationTree
        ↓
PageRenderabilityValidator
        ↓
ThemeEngine
        ↓
LayoutPlanner
        ↓
RenderPlanCompiler
        ↓
SlideRenderPlan
        ↓
RenderPlanValidator
        ↓
PureRenderer
        ↓
PptxPackageInspector
        ↓
PreviewRenderer
        ↓
PptxQualityGate
```

任何正式生成入口、本地命令、后台任务和服务器接口都必须调用同一套核心链路。禁止绕过 `SlideRenderPlan` 将页面树直接交给 Renderer。

## 5. 模块职责边界

### 5.1 PageRenderabilityValidator

只检查页面树是否具备进入视觉规划阶段的必要数据，例如页面类型、展示内容、素材引用和表格展示模型。它不得总结内容、补写正文、重新拆页或重新绑定素材。

### 5.2 ThemeEngine

只负责解析和校验：

- 颜色、字体和字号约束
- 间距、安全区、圆角、边框和阴影
- 组件视觉样式
- 主题继承和 Token 覆盖
- 字体可用性和颜色对比度

ThemeEngine 决定“长什么样”，不得读取论文正文、修改页面或决定内容重要性。

### 5.3 LayoutPlanner

只根据已经存在的 `pageType`、`pagePurpose`、`contentType`、`imageRole`、`assetKind`、素材数量和内容容量，从固定 LayoutCatalog 中选择兼容布局，并计算元素的位置、尺寸和层级。

LayoutPlanner 必须使用确定性选择和固定回退顺序，不得随机选版，不得生成、删除、改写或重新排序页面。

### 5.4 RenderPlanCompiler

负责将页面、已解析主题、布局配方和素材描述编译为完整的 `SlideRenderPlan`。文字测量、字号适配、图片比例计算、边界框和 zIndex 必须在此阶段确定。

### 5.5 RenderPlanValidator

只检查 RenderPlan 的结构与可绘制性，包括未知布局、未知组件、素材缺失、文字溢出、元素越界、非法遮挡、图片比例失真和表格容量超限。验证失败必须返回明确问题，不得静默修复。

### 5.6 PureRenderer

Renderer 只读取已经验证并冻结的 `SlideRenderPlan`，调用 PPTX 绘制库按顺序绘制元素。

Renderer 不得：

- 增加、删除、合并、改写或重新排序页面。
- 选择或更换布局。
- 总结、截断、缩写或补充文字。
- 决定字号、坐标、尺寸、裁切或 zIndex。
- 搜索、替换或重新绑定素材。
- 调用大模型、数据库、DocumentParser 或 ContentPlanner。
- 使用未声明字体、默认主题或默认母版回退。

### 5.7 PptxPackageInspector

只检查生成后 OOXML 包的结构完整性、页面关系、媒体关系、母版、备注、隐藏内容和禁止字段，不修改 PPTX。

### 5.8 PreviewRenderer

使用固定环境将 PPTX 渲染为页面预览，用于视觉检查。不同预览分辨率不得改变幻灯片元素位置。

### 5.9 PptxQualityGate

QualityGate 只聚合检查结果并决定正式文件是否允许交付。它不得自动缩小字号、删除文字、裁切图片、截掉表格或修改页面。

正式状态必须遵循：

```text
GENERATING_PPTX
→ INSPECTING_PACKAGE
→ RENDERING_PREVIEW
→ RUNNING_QUALITY_GATE
→ COMPLETED 或 QUALITY_FAILED
```

文件写入成功不等于任务完成；只有质量门禁通过后才能标记为正式完成并允许下载。

## 6. SlideRenderPlan 契约

`SlideRenderPlan` 是内容规划系统与 PPTX 渲染系统之间的严格边界，也是 Renderer 的唯一正式输入。

根对象至少包含：

- `schemaVersion`
- `presentationId`
- `sourceTreeHash`
- 引擎、主题和 LayoutCatalog 的版本及哈希
- 幻灯片尺寸
- 有序的 `slides`

每页至少包含：

- `slideId`
- `sourcePageId`
- `index`
- `pageType`
- `layoutId`
- 有序的 `elements`

每个元素必须在 Renderer 执行前确定：

- `elementId` 和 `elementType`
- `x`、`y`、`width`、`height`
- `zIndex`
- `styleRef` 或 `resolvedStyle`
- `text` 或 `assetId`
- 图片元素的 `fitMode` 和 `cropAllowed`

RenderPlan 流程固定为：

```text
compile → validate → freeze → hash → render
```

通过验证后的 RenderPlan 不可变。Renderer 不得改变其内存对象或解释高层布局语义。

## 7. 确定性要求

当以下输入一致时，标准化后的 `SlideRenderPlan` 必须完全一致：

- ValidatedPresentationTree
- 素材文件及其哈希
- 主题 ID、版本和哈希
- LayoutCatalog 版本和哈希
- 字体配置和字体文件哈希
- 引擎版本

禁止将以下因素用于布局结果：

- 随机数或随机模板
- 当前时间
- 不稳定的对象遍历顺序
- 服务器系统字体的静默回退
- Renderer 内部的临时自适应判断

标准化过程必须固定字段顺序、页面顺序、元素顺序和数值精度，再计算 `normalized-render-plan.sha256`。

## 8. 七个独立提交的固定顺序

1. `docs(ppt): freeze rendering v1 architecture baseline`
   - 仅冻结本架构文档。
2. `feat(ppt): add rendering v1 schemas and enums`
   - 四份 Schema、枚举、错误码、校验测试和示例。
3. `test(ppt): add health management rendering fixture`
   - 健康管理论文固定页面树、素材、禁用词、预期页数和 RenderPlan 快照。
4. `feat(ppt): add academic purple baseline theme`
   - `academic-base`、唯一正式主题 `academic-purple`、主题解析和字体检查。
5. `feat(ppt): compile and validate slide render plans`
   - LayoutCatalog、布局选择、RenderPlan 编译、测量和验证。
6. `refactor(ppt): make renderer execute render plans only`
   - Renderer 纯执行化，删除所有内容和布局决策。
7. `feat(ppt): add pptx package and visual quality gates`
   - 包结构扫描、预览、禁止字段检查和正式下载门禁。

提交顺序不可合并或提前。Commit 2 完成以前不得修改现有 Renderer。

## 9. 当前阶段禁止修改和新增的范围

Rendering V1 阶段禁止顺带修改：

- DocumentParser、ContentPlanner、ContentSanitizer
- OutlinePlanner、OutlineValidator、页面树结构
- AssetMapper 的素材绑定逻辑
- 数据库结构和非必要后端接口
- 现有前端工作流

禁止顺带增加：

- 第二套主题或专业皮肤
- 随机布局和大模型视觉决策
- 复杂动画和模板市场
- Renderer 默认模板或字体回退
- 质量门禁自动修复

## 10. 健康管理论文端到端验收链路

固定测试 `HealthManagementPptRenderingE2ETest` 应执行：

```text
读取固定 ValidatedPresentationTree
→ 校验页面可渲染性
→ 解析 academic-purple
→ 选择布局
→ 编译 SlideRenderPlan
→ 校验并冻结 RenderPlan
→ 保存 render-plan.json
→ PureRenderer 输出 PPTX
→ 扫描 OOXML 包
→ 渲染页面 PNG
→ 执行质量门禁
→ 输出 quality-report.json
```

完整调试包应包含：

```text
output/health-management/
├── presentation.pptx
├── render-plan.json
├── quality-report.json
├── generation-manifest.json
├── package-inspection.json
├── preview/
│   ├── slide-001.png
│   └── ...
└── logs/
    └── rendering.log
```

固定夹具必须隔离内容规划阶段的变化，使渲染测试可以准确定位为页面树、主题、布局、RenderPlan、Renderer、PPTX 包或预览环境问题。

## 11. 本地与服务器一致性要求

本地 CLI、后台任务和服务器接口必须调用同一个正式入口和同一个引擎核心；旧 Renderer 必须最终从依赖图中移除。

本地和服务器必须使用相同的：

- 主题目录及其版本
- LayoutCatalog
- 字体文件和字体配置
- Renderer 和 PPTX 依赖版本
- 固定排序和数值精度规则

生成清单必须至少记录：

```text
sourceTreeHash
themeHash
layoutCatalogHash
fontProfileHash
renderPlanHash
rendererVersion
GitCommit
```

一致性验收优先比较 `normalized-render-plan.sha256`，不得以原始 PPTX 二进制哈希作为主要标准。

如果 RenderPlan 哈希不同，问题位于配置、ThemeEngine、LayoutPlanner 或编译阶段；如果 RenderPlan 一致但预览不同，问题位于 Renderer、字体环境、依赖版本或预览软件。

## 12. 最终质量验收标准

Rendering V1 完成时必须满足：

- 页面数量与页面树 100% 一致。
- 每页具有唯一、稳定的 `sourcePageId` 映射。
- Renderer 新增、删除、改写或重新布局页面数量为 0。
- PPTX 内部字段泄露数量为 0。
- 默认母版占位文字数量为 0。
- 必选素材缺失数量为 0。
- 图片比例失真数量为 0。
- 文本机械截断和主要内容省略数量为 0。
- 文本溢出数量为 0。
- 页面越界元素数量为 0。
- 非法元素遮挡数量为 0。
- 字体替换数量为 0，或被明确记录并阻断正式交付。
- 本地与服务器标准化 RenderPlan 哈希一致。
- 文本、卡片和表格保持为原生可编辑元素。
- 质量门禁失败时不得标记为正式完成或提供正式下载。

## 13. 架构防回归规则

评审必须拒绝以下实现：

1. ThemeEngine 读取论文内容并成为第二个 ContentPlanner。
2. LayoutPlanner 对无法适配的内容进行静默截断或缩小到硬下限以下。
3. Renderer 根据 `layoutId` 临时决定页面结构或自动补救上游问题。
4. QualityGate 修改生成结果而不是报告并阻断。
5. 本地、CLI 和服务器维护不同渲染入口或不同主题资源。

以上约束构成 DokiAI Academic PPT Rendering Contract V1 的正式架构基线。
