package com.dropai.rewrite.service.impl;

/** Ordinary lexical families learned from observed outputs; not a manuscript dictionary. */
final class OrdinaryPlatformMethods {
    private OrdinaryPlatformMethods() { }
    static String instruction(String type) {
        String code = type != null && type.contains("@") ? type.substring(type.indexOf('@') + 1) : "GENERAL";
        String method = switch (NativeHumanizeProfiles.normalize(code)) {
            case "CNKI" -> "知网：把抽象书面词组换成平常的主谓和动宾说法，所有正文句都处理，不只改研究腔段首。";
            case "WEIPU" -> "维普：全段大面积替换内容词组，必要处自然补的、了、以及、使得。研究、数据、限制、建议的顺序可保留，句内词语必须充分改写。";
            case "GEZIDA" -> "格子达：把概括词改成常用动作或状态，例如信息共享深度不足可说信息交流比较浅，标准衔接不充分可说标准对接不到位。每句都要换词，不靠结尾补意义。";
            case "PAPERPASS" -> "PaperPass：采用维普式全句连续换词，并进一步处理每个句组中的内容词、谓语和衔接表达；摘要、文献说明、定义、数据、问题、措施与结论都不例外。把只换段首或后半段原句照搬视为改写不足，不以长篇解释代替词组变化。";
            case "PAPERYY" -> "PaperYY：将内容词换为直白的动宾短语，把当前主要问题表现为换成目前主要问题就是等常见说法，中间各句也同样处理。";
            case "ZHUQUE" -> "朱雀：全句转换性质、状态和动作词，季节集中性可说有明显的季节性，全流程监测不足可说整个过程监控做得不够。不加口语噪声，不只替换连接词。";
            case "WANFANG" -> "万方：用平常词组替换概括术语及其周边句法，例如数字化管理可按语境说用数字手段管理，标准衔接不充分可说标准之间没有对接好。逐句处理，不换成更复杂的新术语。";
            default -> "采用维普式密集词组转换，每句都处理，必要连接自然补足，不靠长篇扩写。";
        };
        return "\n【平台密集换词方式】" + method
                + "\n表达家族：持续提高→不断地提升；等待时间→等候时长；全过程监测→全程监控；标准衔接→标准对接；进一步协同→加强配合。逐处按意思选择，不反复套同一个长短语。不是把词机械换成长版本，也不移植示例中的对象或事实。";
    }
    static String instruction(String type, String source) {
        String common = instruction(type);
        if (!OrdinaryRewriteAmplitude.applies(type) || source == null) return common;
        return common + "\n【全部送改正文】无摘要或正文豁免。绪论与文献说明中的背景、方法、结果用语，定义与现状中的性质和动作词，数据前后的叙述词组，问题与对策里的限制、措施、记录和结论表达，都要逐句替换。专名和数字保持不变，但不能因此把其周围整句照搬。正文较短也不以少动原词作为默认完成方式；只换入口不算完成。";
    }
}
