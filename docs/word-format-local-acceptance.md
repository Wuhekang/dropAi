# Word 模板格式化本地验收记录

验收日期：2026-08-29

开发基线：`9956b14f274177b7fcf6b1c201e4c4bc353a2663`

验收分支：`codex/word-format-home-integration-9956b14f`

## 验收环境与范围

- Windows 本机，Microsoft Word 16.0 可用。
- 前端从 `http://localhost:5173` 打开，后端为 `http://localhost:8080`。
- 从 Dokiai 首页真实点击进入“论文格式智能修改”。
- 通过页面文件选择器真实上传学校旧版 `.doc` 模板和真实论文 `.docx` 原稿。
- 页面未使用演示数据或伪造进度；任务由后端启动 Python 格式引擎并持续轮询。
- 同时回归首页“流程图生成”卡片，确认真实点击进入 `/drawing` 的 ThesisDiagram 编辑器，而不是 AI 工程模块。

## 页面端到端结果

- 成功任务编号：`c90909a4de7041ba9d97aef021c723aa`。
- 上传、模板提取、原稿分析、格式处理、完整性检查、结果生成共 6 个步骤全部完成。
- 格式调整：774 处；输出大小：4,601,208 字节。
- 页面下载文件、后端输出和结果报告 SHA-256 均为 `13eaf42f78fe60878f6a9e0cd9ed9177d9f60e225d7fec5babea65e56f64f82c`。
- 原稿 SHA-256：`9132a4f7896b674463d8b446889315e66df61c21f365ab59e0cdea1b5284bd47`。
- 模板 SHA-256：`1688e934105cc973e87d0c88f6f5e25b88442081b2e4f6089855f9af3ae6aaf3`。
- 完整性报告为 `passed=true`、`differences=[]`，上传前后的原稿与模板哈希保持不变。
- 任务目录中不存在 `.working` 临时产物，输出 DOCX 压缩包完整性检查通过（54 个条目）。
- 刷新带 `jobId` 的结果页面后，成功状态、处理统计和下载入口均可恢复。
- 该 Word 上传与处理流程结束后，浏览器控制台没有错误或警告。

首次真实上传暴露了 Word COM 偶发的“服务器运行失败”。转换器现在只对该明确的瞬时启动错误等待 0.5 秒并重试一次；不会枚举、关闭或结束用户已有的 Word 进程，也不会重试文档打开、保存等非瞬时错误。修正后使用同一组文件从页面重新上传并成功完成。

## 输出文档视觉检查

- 由于本机未安装 LibreOffice，使用 Microsoft Word 16.0 将成功输出导出为 76 页 PDF，再逐页栅格化检查。
- 76 页全部完成目视检查，未发现裁切、重叠、乱码、表格溢出、图片丢失或页码错位。
- 标题、正文、图表题注、表格和参考文献均保持可读，论文主体前 65 个模板前置段落按引擎告警原样保留。

## 自动化检查

- Python 格式引擎：8 项测试通过，包含 Word COM 瞬时启动错误重试与非瞬时错误不重试。
- Java Word 格式服务与流程图助手：13 项测试通过，0 失败、0 错误、1 项按环境跳过（Corretto JDK 17）。
- 流程图引擎：17 项单元测试及自检通过，PNG、VSDX 和中文字体检查正常。
- Windows 运行环境检查：Python 依赖与 Microsoft Word 16.0 COM 自检通过。
- 前端生产构建：通过，共转换 2,270 个模块；仅保留原有的大包体积提示。

## 运行配置

- Windows 启动脚本默认启用 Word 格式模块；仅启用时检查 `python-docx`、`openai`、`pywin32`，并在启动日志中显示最终配置。
- 更新脚本固定使用本次新基线，并安装 `document-format-tool/requirements-web.txt`。
- `.doc`、`.dotx` 模板仅在 Windows 且安装 Microsoft Word 时启用；Linux、Render 和 Docker 环境明确关闭旧版模板转换，应先将模板另存为 `.docx`。
- 豆包附加要求沿用 Dokiai 的 `DOUBAO_API_KEY`、`DOUBAO_MODEL` 和 `DOUBAO_ENDPOINT`；也可单独设置 `DOUBAO_BASE_URL`。
- 超大上传请求返回通用提示，不再误导其他模块为 Word 专属的 30/100 MB 限制。
