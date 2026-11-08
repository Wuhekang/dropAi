package com.dropai.rewrite.service.impl;

/** Observable wording patterns from paid outputs, not recovered server prompts. */
final class OrdinaryPlatformMethods {
    private OrdinaryPlatformMethods() { }
    static String instruction(String type) {
        String code = type != null && type.contains("@") ? type.substring(type.indexOf('@') + 1) : "GENERAL";
        String method = switch (NativeHumanizeProfiles.normalize(code)) {
            case "CNKI" -> "知网实稿：把紧缩名词结构展开为完整主谓表达，较多使用并且、使得、还需要、从公开的数据来看等常用连接。“通道规模持续扩大”可以说“通道的规模在不断扩大”。原有方法、数据、限制、建议可沿原顺序自然讲述。";
            case "WEIPU" -> "维普普通实稿：密集展开普通词组，补足的、了、以及、使得等自然连接。“持续提高”可说“一直都在进步当中”，“明显季节集中性”可说“明显的季节性特点”，“仍需进一步协同”可说“还需要加强合作”，“跨境数据协同、冷链标准衔接、全过程监测”可依语境说“跨境数据共享、冷链标准对接、全程监控”。保留正常的研究—结果—限制—建议顺序也可以，不强制变成另一种段落骨架。";
            case "GEZIDA" -> "格子达专用实稿：把并列概念改成常见动作或状态，“信息共享深度不足”可说“信息交流比较浅”，“标准衔接不充分”可说“标准衔接不到位”，“进行效率评价”可说“看运行得怎么样”。各句连续改写，不仅改连接词；顺序通顺就保留，普通词不用换成复杂的新术语。";
            case "PAPERPASS" -> "PaperPass专用实稿：词组替换和句内结构改写更充分，段首可直接从原文主体或原有动作起句，名词链改成完整动词句。“连接境外产地与国内市场”可说“把境外产地和国内市场联系起来”，“车位扩容”可说“车位增多了”，“平台投入使用”可说“平台运行起来了”。原有措施之间可重排，因果与转折保持可读；仍连续处理数据句、限制句和建议句，不能只做首尾替换。不复制竞品样本的新增调查、错误数字或错误指标。";
            case "PAPERYY" -> "PaperYY普通实稿：多用常见动宾短语，将省略的成分自然补全。“当前主要问题表现为”可说“目前的主要问题就是”，“标准衔接不充分”可说“标准没有实现对接”，“共同原因在于”可说“共同的原因就是”。各句维持原有承接，不为追求简短删去的、了、以及等自然连接。";
            case "ZHUQUE" -> "朱雀普通实稿：把抽象性质写成普通状态，“季节集中性”可说“有明显的季节性”，“信息共享深度不足”可说“信息共享程度不高”，“全流程监测不足”可说“全过程监控做得不够”。用平常的原因、转折和建议句串起原文内容，不一律改成短句，也不改成自带、另一边看等刻意口语。";
            case "WANFANG" -> "万方专用实稿：抽象名词链展开为平常的说明，保留数据、观察和解释间的合理顺序。“现阶段、程度不高、存在问题、存在着欠缺、根据公开的数据可以得知”等可自然使用。“数字化管理”可说“数字手段”，“标准衔接不充分”可说“标准之间的衔接存在问题”。不强行数据前置，不用运行水准、覆盖力度等复杂词组重新包装。";
            default -> "按普通词组展开方式重述，合理顺序可保持，不强制压缩和禁词清洗。";
        };
        return "\n【当前平台的实测表达方式】" + method
                + "\n这些示例用于学习词组展开方式，须按本段语境使用，不是逐项硬替换，不新增例子中的对象、事件或证据。保持结构占位符与原有序号完整。";
    }
    /** Adds paragraph-specific guidance only for the two measured ordinary profiles. */
    static String instruction(String type, String source) {
        String common = instruction(type);
        if (!OrdinaryRewriteAmplitude.applies(type) || type == null || !type.contains("@") || source == null) return common;
        String platform = NativeHumanizeProfiles.normalize(type.substring(type.indexOf('@') + 1));
        if (!"PAPERPASS".equals(platform) && !"WEIPU".equals(platform)) return common;
        // Keep study-summary paragraphs on the measured v7 wording instruction.
        if (source.contains("本文") || source.contains("本研究")) return common;
        StringBuilder focus = new StringBuilder();
        if (source.matches("(?s).*(是指|指的是|概念|理论|协同|成熟度|适宜环境).*")) {
            focus.append("定义和理论说明也要逐句展开：先辨明原文对象、条件和含义，再把紧缩词组写成完整说明，补出原文省略的主语和自然连接。定义边界、适用条件、否定及程度不能改；不要为解释概念另加案例。 ");
        }
        if (source.matches("(?s).*(万吨|亿元|货运量|进出口|同比|增长|贸易规模).*")) {
            focus.append("规模和数据段不只是换段首：连同数值前后的动作、时间范围和解释一起改写，把紧缩的统计描述展开为通顺句子。保留原值、单位、年份、统计范围、同比基期和引用对应关系；提高多少不能写成达到多少。 ");
        }
        if (source.matches("(?s).*(应当|应该|需要|建议|建立|完善|制定|推进|明确|统一|共享|监测|温控|查验区).*")) {
            focus.append("问题和对策段与摘要采用同样充分的词组改写：将名词并列展开为原文已有主体要做的动作，说明原文已有的对象与条件，各句连续处理，不只改收尾。自然补足的、了、使得、以及，但不要每句机械插入，不新增步骤、责任主体或效果承诺；已有措施顺序通顺就保留。 ");
        }
        if ("PAPERPASS".equals(platform)) {
            if (source.matches("(?s).*(监测|风险|应急|时间戳|异常|温度记录|分散数据).*")) {
                focus.append("监测与问题段重点：将‘分散掌握局部数据、缺乏统一标准、难以判断延误、对应分析、计划偏离、维持服务水平’这一类紧缩表达，按本段实际内容展开为谁掌握什么、信息如何接起来、哪里无法判断、哪些情况影响安排的完整说明。所有已有条件、因果、否定和不确定性都保留，不只替换同一个词根或照抄原句后半段；不能添加原文没有的管理动作或事件。 ");
            }
            if (source.matches("(?s).*(标准|操作指引|记录口径|温湿度|装卸|复检|包装|成熟度).*")) {
                focus.append("标准与对策段重点：不要仍以‘围绕……形成……推动……明确……提供依据’串起整段；把原文已有各措施分别改成平常的动作说法，把紧缩的操作词组展开并用自然连接接续。每个并列措施都要处理，包含温控、装卸、记录、异常处置等后半段时不能遗漏；不新增主体、步骤、顺序或成效，不把建议写成已经实现。允许保留合理的措施顺序，不要求强拆句。 ");
            }
            if (source.matches("(?s).*(口岸|规模|通道|设施|数字化|平台|车位|业务能力).*")) {
                focus.append("现状与设施段重点：数据周围的描述句、设施动作句、限制句要与数字句一起重述。将‘设施扩容、平台应用、业务协同、能力提升’等概括表达，按已有主语展开为事情怎样开展、对象如何变化以及尚有哪些限制；不额外杜撰建设内容，不把通行效率解释成货损下降。 ");
            }
        }
        return focus.isEmpty() ? common : common + "\n【本段类型补充】" + focus
                + "这只是表达指引，不设换词比例或新验收条件，不强制拆句换骨架；没有原文依据的具体细节不得补写。";
    }

}
