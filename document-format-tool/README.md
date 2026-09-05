# Dokiai Word 格式处理引擎

这里是从已完成真实论文验证的桌面工具中抽出的无界面核心。Java 后端以
短生命周期子进程调用 `format_cli.py`，无需启动 PySide 界面，也不会调用
或嵌入桌面 EXE。

## 支持范围

- 论文原稿：仅 `.docx`。
- 格式模板：`.docx`；Windows 且安装 Microsoft Word 时额外支持 `.doc`、
  `.dotx`。
- 自动提取模板的纸张、页边距、正文、1-4 级标题、图名、表名、参考文献、
  表格边框/字体/行距、重复表头和页码规则。
- Web 分析先读取模板中的正文、规范表、文本框、红字和批注文字，再由豆包
  判断文档用途并并行提取各项规则。明确的书面要求优先于说明文字自身的样式。
- 纯撰写规范只用于提取规则；“封面见附件”等描述不会当成封面复制。
  只有确认存在独立封面/声明页并确定范围后才复制，不复制模板的摘要样例。
- 确认界面显示文字依据以及未确认项目；AI 超时的项目保留程序提取值供核对。
  原文无依据的默认值不会标成 AI 已识别。
- 正文数据表使用不可覆盖的固定规范：黑色三线表（1.5/0.75 磅）、
  表格与单元格内容居中、宋体小四、零缩进、全表不加粗；封面布局表和
  含公式的排版表继续保留原样。
- 原稿始终只读，输出必须使用一个尚不存在的新路径。
- Web/CLI 默认采用“格式优先”交付：处理完成且输出 DOCX 可读取、原稿与模板
  未被改动时即可下载。正文文字哈希、图表/分节、域、书签和关系部件等细致
  比较仅作为风险提醒，不再把这些差异直接变成任务失败。
- `formatReport` 按实际执行记录列出已处理项，以及未确认/未匹配的待人工核对项；
  它是处理记录，不代表系统已人工验收每个格式问题。坏文件、处理异常、输出
  覆盖冲突等仍会拒绝交付，原稿不会被覆盖。

Linux/macOS 不提供 Microsoft Word COM。上传 `.doc` 或 `.dotx` 模板时 CLI
会明确返回 `LEGACY_TEMPLATE_UNSUPPORTED`，不会尝试低保真转换。生产容器
应要求用户先把这两种模板另存为 `.docx`。

## 安装

```powershell
python -m pip install -r document-format-tool/requirements-web.txt
```

Web 运行不需要 `PySide6` 或 `PyInstaller`。`openai` 用于模板文字分析与豆包规则解析；
`pywin32` 只会在 Windows 安装。

启动前可检查 Python 依赖；Windows 需要直接处理 `.doc/.dotx` 时再加
`--legacy`，它会实际验证 Microsoft Word COM：

```powershell
python document-format-tool/runtime_check.py --legacy
```

## CLI

```powershell
python -X utf8 document-format-tool/format_cli.py `
  --source "storage/job/source.docx" `
  --template "storage/job/template.docx" `
  --output "storage/job/result.docx" `
  --result-json "storage/job/result.json" `
  --instructions-file "storage/job/instructions.txt"
```

省略 `--instructions-file` 时完全采用模板识别规则。提供指令文件时默认使用
确定性的本地解析器；再加 `--use-doubao` 则改用豆包解析。豆包只接收格式
指令、学校模板文字及规则 JSON，不读取待修改论文正文，并从进程环境读取：

- `ARK_API_KEY` 或 `DOUBAO_API_KEY`
- `DOUBAO_MODEL`
- 可选 `DOUBAO_BASE_URL`；未设置时也会兼容项目已有的 `DOUBAO_ENDPOINT`，并自动去掉 `/chat/completions`

模板 AI 使用“段落并行提取 → 总体整合”，不再先串行等待整份文档用途判断。
相邻短段按最多 4 段、合计最多 160 字的小批派发，每段仍保留独立证据编号，
封面候选与正文、批注分别分组；长段分片。超过 64 个批次时按相邻文字量分组，每份模板最多
64 个分段请求及 1 个整合请求，同时在途最多 32 个。单次读取前 48000 字符，
超出部分明确提示人工核对。总体整合只能选择已经校验过的字段值与证据；
某段或整合超时时保留其余有效结果，不将空 JSON 计为成功识别的格式。
一次任务共用一个线程安全的 HTTP 客户端与连接池，所有分段及整合结束后才关闭。
无字段冲突时由本地索引直接完成总体整合（`local_complete`），不重复请求 AI 确认
相同结果；只有冲突字段才进入一次 AI 裁决。裁决返回紧凑的事实编号列表，由本地
索引恢复字段，不会改动已有确定结果。

- `DOUBAO_FORMAT_AI_CONCURRENCY`：单个模板的并发上限，默认 32，范围 1–32。
  这是每个模板内部的并发，与服务端同时处理的任务数不同；多任务时应按 API 配额调低。
- `DOUBAO_FORMAT_AI_TIMEOUT_SECONDS`：每个请求的超时，默认 45 秒，范围 8–60。
  不自动重试超时请求；32 路并发不代表不会限流或超时。
- `DOUBAO_FORMAT_AI_FAST_MODE`：默认 true，请求关闭深度思考、使用 JSON 对象输出；
  不支持这些参数的兼容模型可设为 false。输出上限为 4096 tokens。
- 分段进度实时写入 `analyzing_template` 事件，分析结果的
  `templateAnalysis.parallelAnalysis` 保存请求数、成功返回数、整合状态、耗时与脱敏异常类型。

若部分 AI 用途分析未完成，已经由程序验证边界及真实文字证据的封面/声明范围
仍可供客户确认；无可靠候选范围的规范说明不会复制。模板识别结束后英文与数字
默认使用 Times New Roman，再应用客户明确修改的字体值。

字号名称与磅值按同一字段合并证据和优先级（例如“小二”与 18 磅），避免全文
通用字号覆盖某一级标题的专门要求。替换封面时需保护原稿最早正文起点，后续
章节中出现“引言”等标题不能成为删除前文的依据。

Web 首次调用加 `--analyze-only`，结果包含 `editableRules`、`analyzedRules`、
`templateAnalysis` 和 `templateSha256`。后端保存完整规则与用途判断，确认时将
服务器保存的这三个分析字段和客户修改的 `editableRules` 写入 `--rules-file`。
处理阶段先校验模板哈希，再载入分析快照并应用客户修改，不重新提取覆盖已确认值。
前端不能替换服务器保存的分析快照或封面复制范围。

## 进程契约

stdout 只写逐行 UTF-8 JSON，并在每行后立即 flush：

```json
{"type":"progress","progress":56,"stage":"processing","message":"正在把模板规则安全应用到论文副本"}
```

稳定字段为：

- `type`: 固定为 `progress`
- `progress`: `0..100` 整数
- `stage`: `validating`、`reading_template_text`、`extracting_template`、
  `ai_analyzing`、`awaiting_confirmation`、`restoring_rules`、`applying_rules`、
  `analyzing_source`、`processing`、`integrity_check`、`completed` 或 `failed`
- `message`: 中文进度说明

成功退出码为 `0`；运行期失败为非零。参数解析成功后，无论成功或失败都会
原子写入 `--result-json`。

成功结果包含：

```json
{
  "success": true,
  "changedCount": 12,
  "warnings": [],
  "templateNotes": [],
  "instructionNotes": [],
  "analysis": {},
  "ruleSummary": {},
  "integrity": {"passed": true, "mode": "format_first", "basicChecksPassed": true, "deliveryAllowed": true, "differences": {}},
  "formatReport": {"applied": [{"item": "普通正文", "count": 12}], "notApplied": [], "warnings": [], "changedCount": 12},
  "output": {"fileName": "result.docx", "sizeBytes": 1234, "sha256": "..."},
  "error": null
}
```

`integrity.passed` 表示细致比较是否一致，不等同于是否可交付。新模式下后端必须
同时确认 `mode=format_first`、`basicChecksPassed=true` 和 `deliveryAllowed=true`；
旧版严格模式仍要求 `passed=true`。核心库 `validate_preservation` 的默认严格行为
不变，只有显式 `strict=False` 才将细致差异降级为提示。

失败结果仍保留相同的核心字段，并增加 `errorCode`、`errorType`，其中
`error` 是可直接展示的简短字符串。Java 后端不得依赖服务器绝对路径；CLI
也不会把绝对路径写入结果 JSON。

## 测试

```powershell
python -B -m unittest discover -s document-format-tool/tests -v
```

测试会在临时目录生成小型 DOCX，真实执行 CLI，并验证：进度协议、输出可
打开、可见文字不丢失、源稿/模板哈希不变以及已存在输出绝不被覆盖。
