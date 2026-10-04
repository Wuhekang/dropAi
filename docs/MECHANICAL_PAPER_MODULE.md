# 机械设计论文模块

## 开发基线与隔离

远端基线：`origin/codex/dokiai-academic-frontend-v2` 的 `b4acc34bdb55915fdb90ae20788a00377bd13fe7`。
开发分支：`codex/mechanical-thesis-title-only`。独立工作目录，不覆盖 F:\dropAi 的未提交改动。
`origin/main` 是另一条历史，不以它替换当前项目分支。

## 用户流程

`/mechanical-design` 默认打开论文设计，可切回原结构方案。用户只输入题目，直接生成，不确认大纲、不上传任务书、不选模型。
第一章一个真实联网实物图；第二章结构图和装配结构；第三章原生公式和受力图；第四章四张不同零件图；第五章三维总装、二维总装、网格、两组应力/位移云图；第六章总结。
合计 15 个插图位置，只有图 1-1 使用检索，其余 14 张调用既有图片服务。未找到实物图时保留其他成果并允许重试，不用生成图片冒充实物照片。
无封面、诚信声明、流程图、参考文献或资料来源段。来源 URL 保存在服务端任务记录，不写入论文或前端。云图采用灰白视口、单模型、坐标轴、约束和载荷箭头、右侧彩虹色标的定性分析版式，不编造有限元求解数据。方法小节保留一次简短的方法说明。

统一规划四个不同零件的材料、尺寸、孔位、载荷和解析计算。14 个二级小节为独立分支，共享 14 线程正文池；14 张生成配图共享 4 线程图片池。多个用户共享并发上限，不无限增加外部模型调用。每次最多两个工作流协调任务；同一个用户不重复启动进行中的论文。
正文目标 8050 字（统计汉字、字母、数字，不含摘要、标题、表格和公式），每节字数不合格自动重写最多三次。最终仅接受 6000—9000 字；不截断段落凑上限，不复制填充段落。

## 存储、权限与计费

不新增数据库、表或列。复用 `document_job`，`source_feature=MECHANICAL_PAPER`，结构化任务快照存入已有 LONGTEXT/CLOB 的 `paragraphs_json`。
图片和 DOCX 保存到持久目录 `mechanical.paper.output-dir`（默认 `data/mechanical-papers`），部署时此目录必须作为持久卷保留。
每个任务完成后短事务更新快照；网络调用不持有行锁。所有任务读取、重试、图片和下载验证所属用户。
浏览器只缓存账号对应任务 ID，正文状态以服务端为准。请求断线自动重连；服务重启后提示继续生成，复用完成的文字和图片，不重新调用已成功任务。
生成成功时在短事务内锁定任务行、复用 DOCX_GENERATE 定价并登记完成与扣费；重试不重复扣费。失败不扣费。没有修改现有定价数据。

## 模型与桌面配置

正文：原有 MatrixDesignService 配置，不更换模型。
联网：原查文献模块的 DoubaoWebSearchProvider / Responses API / web_search 工具，增加专用实物图页面查询，不执行文献检索。
图片：原 DoubaoImageProvider。禁止缺配置时把正文模型当作生图模型调用。
新增只读桌面环境加载器，仅读取 `%USERPROFILE%\Desktop\.env` 或 `桌面\.env` 中 DOUBAO_ 开头键；系统环境变量和启动参数优先，日志不输出密钥。

当前桌面 .env 没有图片服务模型和启用设置，以下参数需用户确认后配置，开发过程没有修改桌面 .env：

```dotenv
DOUBAO_IMAGE_ENABLED=true
DOUBAO_IMAGE_MODEL=<用户已开通的图片模型或接入点 ID>
DOUBAO_IMAGE_BASE_URL=<用户确认的图片服务基地址>
# 或直接设置 DOUBAO_IMAGE_ENDPOINT=<完整 /images/generations 地址>
DOUBAO_IMAGE_SIZE=<该图片模型支持的尺寸>
DOUBAO_WEB_SEARCH_ENABLED=true
```

没有配置图片模型时前端显示“Doki 图片服务尚未配置”，不能触发一个注定失败且可能产生费用的任务。前端及本模块错误提示不展示底层厂商和模型名。

## Word 格式

母版来自用户确认的《板类零件钻孔夹具结构设计_修订版.docx》，通过 tools/prepare-mechanical-paper-template.py 提取实际段落角色，保留其页面尺寸、页边距、样式和页脚，去掉旧正文与媒体。
源 SHA-256：`118358f1e06d1637e3ccd558d8fefaa4201320d20c162f468c64f4679443a647`。
版心源页面 7560310 × 10692130 EMU，左右边距 1080135 / 899795 EMU，单节。
段落角色：Title、摘要标题、正文、一级章节、二级章节、图题；所有标题保持黑色。
公式为 Word OMML；三线表为原生 Word 表格；目录是真 TOC 域；所有一级章节段前分页。
插图默认宽 14.5 cm，高度超过约 12.9 cm 时同比缩小，保持比例；图和图题相邻、图片段 keepNext。
Windows 默认使用隐藏 Word COM 刷新目录和页码（Word 必须可用）；失败不会登记整篇成功。非 Windows 默认保留真实目录域和打开时更新设置；没有验证非 Office 的目录刷新能力，部署验收需单独确认。可通过 `mechanical.paper.word-field-refresh=false` 关闭服务端 Word 刷新，之后目录必须在 Word 更新。

## API 与验证

`/api/mechanical-papers/readiness`、`POST /api/mechanical-papers`、任务详情、最近任务、重试、配图预览、Word 下载。
前端轮询展示真实子任务完成百分比、运行状态、等待时长和轻微加载动画，不让伪进度达到 100%。

```powershell
cd backend
mvn -Dtest=MechanicalPaperTest,MechanicalPaperWorkflowTest,MechanicalPaperBillingTest test
cd ../frontend
npm ci --ignore-scripts
npm run build
```

测试覆盖：15 图位置、第一章仅一张照片、四个零件图、四张定性云图、公式与表格检查、Word 原生公式/TOC/分页、权限、14 个正文分支同时进入模型、重启后恢复、重试不重新生图、缺图片配置不创建任务。
已通过 24 项模块及积分/搜索回归测试，包括扣费幂等与所属用户校验；常规测试跳过 1 项需要本地样稿和 Word 的可选排版测试。另行启用该排版测试后通过，Word 实际生成了含页码的目录。回填检查使用确认稿的正文与配图，不是新的外部模型生成证据。当前环境无 bundled LibreOffice，规范渲染器报 soffice.exe 缺失，使用隐藏 Word 导出 PDF 后转页面 PNG 检查。
上述测试使用模拟模型，不代表外部图片服务已实测。图片模型获批并配置后，仍需按“夹具设计”进行一次真实题目到 DOCX 的端到端验收，检查实物图相关性、各张图片结构/尺寸一致性、内容与公式、目录页码和逐页排版。
