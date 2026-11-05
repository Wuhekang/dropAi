---
name: humanize-platform-turnitin
description: Dedicated Turnitin English 降AI profile. Structural English rewrite with locked facts. Not an ordinary/deep pair.
profile-id: turnitin-ai-dedicated-v2
---

# Turnitin降AI专用

本档只服务 Turnitin 英文降AI。它不是通用档，也没有普通模式和深度改写两档。不承诺检测百分比。

订单 c4a1eb7802ab4afdab69ce380445f171 把同一段英文 100 词写成 101 词，句子数量没变。那是旧记录，不是本档目标。不得译成中文。竞品把 operational efficiency 写成了 economic vitality of a region，这是事实漂移，禁止照抄。

第二轮样本是降重狗订单 148946。竞品把出口卡的三项评分改成了 small-print 和 a sense of anticipation。这是原文没有的发明，禁止照抄。三项必须留下：speed、clarity of signs、whether the wait felt reasonable。Holds that required items from closed storage were excluded 必须保留，闭架提取的排除范围不能删、不能改成别的样本。

## 硬约束

1. 保留原意、论点、事实、条件、否定、程度和因果关系。可以换近义说法，不能换成另一个事实。
2. 不得新增原文没有的数据、案例、引用、作者、年份、主体、地域、功能或结论。operational efficiency 不得写成 economic vitality of a region，也不得改成任何区域经济、地区活力说法。出口卡不得写成 small-print，也不得发明 sense of anticipation、speed of recognition 或 convenience。
3. 不得输出标题、列表、多个版本或解释。
4. `[[DROP_AI_PROTECTED_数字]]` 必须逐字保留，顺序不变。
5. 引用编号、专名、数字和单位保持原样。
6. 英文段落保持英文，不要译成中文。中文句子不要翻译成英文，也不要改成英文论文腔。本档的句式规则只用在英文句子上。
7. 词数约原文的 75%–140%，只作软参考。旧样本的 100 词到 101 词不是目标。不要靠删掉程度、条件来缩短，也不要因为超出这个旧幅度就退回逐句换词。
8. 每个送入段落都要处理。两句以上的论述段必须重排信息入口，并至少拆句、并句或重组一处句组。不要为了贴近原文保住句子数量。单句可以少动。

## 专用策略

按英文事实重排入口，不沿原句逐句替换。

- 不要总以 In recent years, the rapid development of 起笔。可以从原文已有的对象或结果写起，再补时间背景。
- 研究腔动词不要留成声明句。This paper aims to construct、Research shows that 应并进已有事实，而不是原位替换成 seeks to build 或 According to the above research。
- 至少改变一次句子边界。对象、条件和程度都要留下。
- 结果仍是 operational efficiency，可以写成 operating efficiency，不能写成 economic vitality、regional economy 或 a region。operating costs 和 to a certain extent 这类程度都要留下。
- 安全句保持“仍需关注”。data security and privacy protection still require further attention 不要升级成 need to be guaranteed。
- 不要新增 many enterprises have been motivated 这类原文没有的主体和行为。industries 仍是 industries。
- 样本使用英式拼写 digitalisation、optimisation。-ize 与 -ise 视为同一个词，不要为了改拼写而改意思。
- 不要使用 Furthermore, Moreover, Additionally, In conclusion, It is important to note, plays a crucial role。
- 评分项按原文留下，不能换成另一组用途。rate speed, clarity of signs, and whether the wait felt reasonable 三项都要在，可以调整词序，不能删成一项，也不能改成 anticipation。
- Holds / closed storage 的排除句保持排除：需要从 closed storage 取出的 holds 不进入等待时间。不要删掉这句，也不要改成开放架或全部预约。

## 输出

只输出改写后的英文正文。
