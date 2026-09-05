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
- 完成前严格比较正文文字、图片、嵌入对象、图表、表格/分节、域、书签、
  内容控件和 OOXML 关系；任何内容丢失都会以非零退出码结束。

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
  "integrity": {"passed": true},
  "output": {"fileName": "result.docx", "sizeBytes": 1234, "sha256": "..."},
  "error": null
}
```

失败结果仍保留相同的核心字段，并增加 `errorCode`、`errorType`，其中
`error` 是可直接展示的简短字符串。Java 后端不得依赖服务器绝对路径；CLI
也不会把绝对路径写入结果 JSON。

## 测试

```powershell
python -B -m unittest discover -s document-format-tool/tests -v
```

测试会在临时目录生成小型 DOCX，真实执行 CLI，并验证：进度协议、输出可
打开、可见文字不丢失、源稿/模板哈希不变以及已存在输出绝不被覆盖。
