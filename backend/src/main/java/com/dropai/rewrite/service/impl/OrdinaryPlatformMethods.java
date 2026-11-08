package com.dropai.rewrite.service.impl;

/** Light entry-point patterns learned from six reviewed local edits, not manuscript templates. */
final class OrdinaryPlatformMethods {
    private OrdinaryPlatformMethods() { }
    static String instruction(String type) {
        String code = type != null && type.contains("@") ? type.substring(type.indexOf('@') + 1) : "GENERAL";
        String method = switch (NativeHumanizeProfiles.normalize(code)) {
            case "CNKI" -> "知网：先交代具体对象或问题，再接已有分析，保留必要的学术术语，去掉绕远的背景铺垫。";
            case "WEIPU" -> "维普：用平常词组说清已有动作与条件，必要连接自然保留，不靠大量加的、了、以及拉长句子。";
            case "GEZIDA" -> "格子达：把抽象概括落到原有对象和动作，避免每段都以作用或意义收尾。";
            case "PAPERPASS" -> "PaperPass：优先换已有信息的入口，定义可从适用条件起笔，问题段可从要判断的问题起笔，对策可从目的或操作场景起笔，再接原有措施与限制。只做必要展开，不以扩写制造改写密度。";
            case "PAPERYY" -> "PaperYY：直接说明谁做什么、哪些条件起作用，删去同义复述，保留自然的长短句差异。";
            case "ZHUQUE" -> "朱雀：把性质概括写成清楚的状态与动作，不刻意口语化，不逐句添加解释。";
            case "WANFANG" -> "万方：先说原文已有的具体事情，再说数据或限制，保持平实语气，不添宏观结论。";
            default -> "在原有事实内换入口、说清楚、略收篇幅。";
        };
        return "\n【平台轻量表达】" + method;
    }

    static String instruction(String type, String source) {
        String common = instruction(type);
        if (!OrdinaryRewriteAmplitude.applies(type) || source == null) return common;
        return common + "\n【六种人工轻调模式】以下只示范已有信息怎样重新安排，字母代表本段已有内容，不是待补写的事实，不套用示例中的领域对象。\n"
                + "1 定义：原有‘各环节A不必相同；X协同是各方协调A、B、C’，可先讲‘A不必在各环节都一样’，再说明各方要把A、B、C商量好。不要把每个概念再解释一遍。\n"
                + "2 现状规模：原有‘通道连接A和B，货物在此周转，数据说明规模变化’，可先说货物如何经过通道，再交代连接范围和原有数据。数字及统计范围不变，不新增趋势判断。\n"
                + "3 数据分析：原有‘数据改善但旺季仍排队’，可从‘为什么改善后仍会排队’起笔，接原数据，再说明原文已有的条件和限制。不要把平均改善等同于每个时段都改善。\n"
                + "4 监测问题：原有‘环节多、数据分散、难找延误’，可先说要判断延误出在哪一环，再交代数据为什么接不起来，最后保留已有风险与应急限制。\n"
                + "5 数据复用：原有‘统一字段、一次录入以减少重复填报’，可先说减少重复填报这个目的，再说哪些字段与规则要统一，并保留原有权限和安全边界。\n"
                + "6 标准对策：原有‘围绕A、B形成指引，并明确异常程序和记录’，可先说现场需要一套能照着做的指引，再把已有操作要求、异常处理和记录安排说清楚；不新增步骤或结果。\n"
                + "只选与本段内容相符的思路，不逐项输出六种模式。正常摘要和无关段落不硬套这些入口，不复制样例句，不移植论文专名、数字或原句。";
    }
}
