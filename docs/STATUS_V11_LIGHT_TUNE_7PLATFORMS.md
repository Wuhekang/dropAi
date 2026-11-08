# STATUS — V11 Light Tune 七平台

- PR #24继续draft、不合并。调整树：F:\dropAi-humanize-integration。
- 参考已核验：v7f基线与tune-12pct实际DOCX逐段对应six-paragraph-changes.json；六段1761→1547字符。仅学习换入口、说清楚、略收篇幅，没有把参考段落或论文实体数字硬编码成返回稿。
- OrdinaryFullRebuild改为v11轻量入口调整，移除v10按词组层层展开路径。OrdinaryPlatformMethods保留七个平台表达差别，增加六种通用模式（定义条件、规模周转、数据矛盾、延误定位、减少重填、现场指引）。不强制拆句、不加连接词凑密度，不全局压短。
- 项目humanize-zh-academic和humanize-platform-paperpass两份SKILL已对齐；普通档仍调用已编码的Ordinary提示，不声称加载了旧版SKILL文件。100%—130%仅软参考，无字数硬门槛，无按长度退回或自动重试。序号/占位结构保护保持原样。大雅路由、深度与双降流程未改；通用SKILL新增规则明确只适用于普通档。
- 35项Java测试通过。重启使用Desktop\.env豆包配置及本地数据库覆盖。四个相关class与两份SKILL已同步至本地运行目录。
- 同一原始source.docx，经与前端一致的localhost /api/document/rewrite/upload，普通降AI各平台各提交一个完整job；七平台全部SUCCESS、37/37实际改写，共259段。没有局部段落补跑或手改下载稿。

## 字符与文件

统计改写的37段，含标点、不计空白，原稿均7002字符。全部106.67%—111.77%，未出现150%以上的大扩写；全文包含未改标题、表格、文献等，另记在各平台provenance中。下列文件均位于指定local-v11-light-tune目录：

|平台|改写后字符|原稿比例|页数|文件|jobId|
|---|---:|---:|---:|---|---|
|CNKI|7583|108.3%|15|CNKI.docx|1a0745384ddb4d068d796b37cfd37f35|
|WEIPU|7823|111.73%|16|WEIPU.docx|c3052c3222b645f88d8fdf556418b6dc|
|WANFANG|7469|106.67%|15|WANFANG.docx|8e052ff24b364edc985a0e12c03a8113|
|GEZIDA|7762|110.85%|15|GEZIDA.docx|b52c82d4bb334ff0a8dde261cd9e91e6|
|PAPERPASS|7820|111.68%|15|PAPERPASS.docx|4aecbc49dd8b4585929667f1082efacf|
|PAPERYY|7648|109.23%|15|PAPERYY.docx|fb24b6aeab194a9eae25b9e5c2cb5358|
|ZHUQUE|7826|111.77%|16|ZHUQUE.docx|d12f7eeeb1014934a1011f6c961a1bb1|

## 审计与限制

- 七份稿全部通过：段落/字符/分节属性、原图片、域、书签、批注锚点、非目标文本、19个上标引用run的位置/编号/样式均保留。除原有引用不新增交叉引用。
- PaperPass、维普、格子达、PaperYY、朱雀的数字序列完全一致；知网94段把重复2024写成“同年”，万方94段先讲2012再讲2024，数值及统计范围人工核对未变。没有按纯数字序列差异误判失败，也没手改返回文件。
- 七份Word只读导出，共107页预览，逐页检查。字数变化带来自然分页：表4-1/4-2在部分稿跨页，维普/朱雀研究不足尾句落到下一页；未擅调字号、行距、表格、分节或分页来补偿。预览前后下载稿SHA256一致。
- 轻量改写仍保留部分原有短语；PaperPass 5.3后半段保留较多原表达。换入口/篇幅达标不等于已测得低AI，不能把此次生成当作8.06%的复现。
- PaperPass新复测报告尚未产生。按本次prompt“仍靠owner交检”，待用户用本轮PAPERPASS.docx提交检测，再核对总体、摘要、4.4和5.3。没有外部交检、没有竞品消费、没有虚构检测分数。

交付目录：C:\Users\14525\Desktop\dropai-live-test\codex-reports\distill-samples\jiangzhonggou-7\local-v11-light-tune\
每平台附PLATFORM-status.json与PLATFORM-provenance.json；seven-platform-audit.json为汇总。PaperPass下载SHA256：0506976db61db79f610859f01709d45627507b9b9366d6d8c78d313424731820。
