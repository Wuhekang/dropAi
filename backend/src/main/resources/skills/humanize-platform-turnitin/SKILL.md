---
name: humanize-platform-turnitin
description: Dedicated Turnitin English 降AI profile. Stronger than the shared native skill and not an ordinary/deep pair.
profile-id: turnitin-ai-dedicated-v1
---

# Turnitin降AI专用

本档只服务 Turnitin 英文降AI。它不是通用档，也没有普通模式和深度改写两档。不承诺检测百分比。

## 硬约束

1. 保留原意、论点、事实、条件、否定和因果关系。
2. 不得新增原文没有的数据、案例、引用、作者、年份、功能或结论。
3. 不得输出标题、列表、多个版本或解释。
4. `[[DROP_AI_PROTECTED_数字]]` 必须逐字保留，顺序不变。
5. 引用编号、专名、数字和单位保持原样。
6. 不得只做同义词替换。允许整段大幅重构表达，但不得新增证据或遗漏独立信息。不要求原句结构可还原。
7. 长度仅作软参考：普通段落可参考原文的85%—130%；为重组已有信息可超出。不为凑比例扩写。
8. 每个送入段落都要处理。处理后的有效正文可以与原文相同，不要为了制造差异改动准确内容。
9. 中文句子不要翻译成英文，也不要改成英文论文腔。本档的句式规则只用在英文句子上。

## 专用策略

Turnitin 对英文段的节奏过于均匀、过渡词可预测很敏感。比通用档更强的地方是：英文段必须改变信息出现顺序，或至少拆开、合并一处句子边界。

- 拉开句子长短。不要让相邻英文句子词数接近。
- 不要使用 Furthermore, Moreover, Additionally, In conclusion, It is important to note, plays a crucial role, a comprehensive analysis, sheds light on。
- 不要把简单动词改成一串名词化结构。
- 从原文已有的对象、条件或结果起笔，不要先写空泛的研究意义。
- 中文段落若夹有英文术语，术语原样保留，中文本身按事实约束改写，不整段译成英文。

## 输出

只输出改写后的正文。
