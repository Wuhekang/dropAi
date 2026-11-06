# DOCX 文字回填图片保留修复

基线：`b3512fe3a604aecc571298be22d0ceee1191cff1`（包含此前基于 `439e5854` 的原生降 AI 更新）。
分支：`codex/preserve-docx-images`。

## 原因与范围

原生文档服务的文字回填原先删除段落全部 run 后再写入文字。同一段落内的嵌入式图片、浮动图片、VML 图片等也存放在这些 run 中，会随之被删除。媒体文件即便还留在 DOCX 压缩包里，没有显示节点也不会显示图片。

已用模拟模型复现：旧实现在降重、降 AI、双降的正常改写中均丢失同段图片；原文回退分支本身不回填，未在此样例中发现丢图。用户截图稿件尚缺原稿和异常输出，不能将截图的实际原因视为已经逐文件核实。

本次仅修复回填：

- 含图段落只改直接文字节点，保留绘图节点、图片引用、大小、锚点设置和原有顺序。新文字在原文字位置分配，图文交界尽量落在标点或空白处，不以小数点、数字内分隔符作边界。
- 支持同一 run 或不同 run 中的图文混排、浮动图片、VML 和兼容性图片分支；不重建图片，不重新压缩图片。
- 如果同时含有不能安全处理的域、超链接、修订、手动换行或可见文本框等结构，整个原段落不变。此时不再调用模型、不重走质量门禁，任务仍正常输出，段落结果与实际文档同步保留原文。
- 原样输出、异常回退、质量门禁回退均保持原段落 XML 不变；图片纯段落、表格和页眉页脚不扩展处理范围。
- 降 AI / 降重 Skill、模型调用、事实保护、长度策略、收费、前端均未修改。大雅仅补充图片保护回归测试，没有修改其处理逻辑。

文字长度改变仍可能引起自然换行、分页和浮动图片的位置变化，不承诺页数或屏幕坐标固定。本修复不恢复此前已删除且没有原稿的图片，也不修复原稿本身失效的外链图片。

## 日志与效果边界

结构回退只写服务器日志：

```text
ImagePreservationFallback jobId=... paragraphIndex=... stage=docx_refill action=KEEP_ORIGINAL reason=UNSAFE_GRAPHIC_TEXT_STRUCTURE
```

`paragraphIndex` 与任务详情中的段落 index 一致，为正文顶层段落的零起始索引，不是页码或筛选后的段落序号。通过 jobId 与段落 index 可对应原文。结构回退不增加失败数，不显示客户报错；保留原文不等于该段已达到查重或 AI 检测目标。

## 验证

- 214项相关回归测试通过，失败0、错误0、跳过0；后端离线打包通过。
- 图片专项覆盖正常改写、原样结果、异常回退、质量门禁回退、结构保护回退及三种原生模式；检查文档成功输出、无额外模型调用、真实改写计数与任务详情一致。
- 保存并重新打开结果，比较图片节点、表格 XML、媒体字节、关系文件及页眉页脚；另验证 Unicode、字体属性和图文边界。
- 包含此前两份幼小衔接稿件的模拟原样/回退回归；没有调用付费模型或第三方检测器。
- 可选视觉样例参数：`-Ddropai.test.images.outputDirectory=target/image-preservation-qa`。样例仅用于内部验证，不是交付论文。
- 当前 Windows 环境无可用的配套 LibreOffice，使用独立 Word 实例只读导出样例并逐页检查 PNG。修改前后各1页，5处显示图片均保留；浮动图片随文字重排的位置变化不作为丢图。

## Windows 服务器更新

本次仅需重新打包后端。先备份服务器配置；如果切换提示本地修改冲突，不要强制重置。

```bat
cd /d C:\Users\Administrator\dropAi

copy /Y backend\src\main\resources\application.yml C:\Users\Administrator\application.yml.server.bak

set GIT_LFS_SKIP_SMUDGE=1
git fetch origin codex/preserve-docx-images && git switch --detach origin/codex/preserve-docx-images
set GIT_LFS_SKIP_SMUDGE=

copy /Y C:\Users\Administrator\application.yml.server.bak backend\src\main\resources\application.yml

git rev-parse HEAD

cd backend
mvn -DskipTests package
```

核对 HEAD 为交付提交并确认打包成功，再使用原启动脚本重启后端。部署不会自动重做历史任务，原来已经丢图的结果应从含图原稿重新处理。
