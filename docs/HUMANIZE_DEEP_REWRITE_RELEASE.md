# 降 AI 整段重构更新

基线：`439e5854082fa47638c701405a44322709420381`。
分支：`codex/humanize-deep-rewrite-439e5854`。

## 本次范围

- 原生 Skill（通用、知网、维普、万方及原生格子达选项）、兼容提示词及大雅独立 Skill：允许基于原有事实整段重构，不限定为微量换词。
- 段落长度85%—130%仅作软参考。原生降 AI 不再按字面重合程度删除整句；超出范围只记入服务器 LengthControl 日志，`lengthStatus=ABOVE_SOFT_REFERENCE`。
- 大雅同步取消列举句的20汉字硬限、图表说明最多两句的提示词硬限；既有平台路由、成组处理和事实保护不变。
- 降 AI / 双降从目录结束后的可编辑正文开始，不再等待摘要标题或一级标题。支持手工目录、目录样式、自动目录内容控件和目录域；目录前已识别的摘要仍可处理。
- 常见方括号摘要标签和关键词得到保护。标题、目录项、公式、引用和后置参考文献等保护范围保留；原生路线不把表格纳入新增范围。
- 不修改纯降重的处理范围、阶段调用次序、收费或模型配置；不合并未完成的格子达专项开发。

## 验证记录

- 178项相关回归测试通过，失败0、错误0、跳过0（含启用本地稿件参数的2项测试）。
- 两份幼小衔接稿件的正文覆盖分别为99段和94段，均纳入此前漏掉的方括号摘要，关键词未纳入。
- 使用模拟模型验证完整文档输出、原样结果和失败回退，Word主文档XML与输入一致；原始文件未改动。
- `mvn -o -DskipTests package` 成功；两套项目 Skill 结构校验通过。
- 测试没有调用付费模型或第三方检测器。不能据此推算检测率；需在部署后以同一平台、版本和范围复测。字数变化可能引起自然分页变化，不承诺页数固定。

可选真实稿件回归参数：

```text
-Ddropai.test.learning.original=<原稿绝对路径>
-Ddropai.test.learning.optimized=<优化稿绝对路径>
```

## Windows服务器更新（CMD）

本次只有后端和 Skill 改动，不需要重新构建前端。先保留服务器配置；fetch成功后才切换。若提示本地修改冲突，请先核对，不要强制重置。

```bat
cd /d C:\Users\Administrator\dropAi

copy /Y backend\src\main\resources\application.yml C:\Users\Administrator\application.yml.server.bak

set GIT_LFS_SKIP_SMUDGE=1
git fetch origin codex/humanize-deep-rewrite-439e5854 && git switch --detach origin/codex/humanize-deep-rewrite-439e5854
set GIT_LFS_SKIP_SMUDGE=

copy /Y C:\Users\Administrator\application.yml.server.bak backend\src\main\resources\application.yml

git rev-parse HEAD

cd backend
mvn -DskipTests package
```

核对HEAD为交付提交并确认打包成功后，再使用原来的启动脚本重启后端。启动脚本、服务器配置与密钥不在此次提交中。
