package com.dropai.rewrite.service.impl;

/** Empirical prompt variants from the seven same-source samples on 2026-11-08.
 * One sample per target is evidence for wording choices, not a detector score guarantee.
 */
final class OrdinaryPlatformMethods {
    private OrdinaryPlatformMethods() { }
    static String instruction(String type) {
        String code = type != null && type.contains("@") ? type.substring(type.indexOf('@') + 1) : "GENERAL";
        String method = switch (NativeHumanizeProfiles.normalize(code)) {
            case "CNKI" -> "知网：把压缩的名词短语展开为主语和动作，例如供给量、通行条件；方法和条件交代完整。以具体对象或资料起句，重排措施与结果之间的主从关系，替换持续、据此、以期等连接骨架。不添加调查经历。";
            case "WEIPU" -> "维普：用常见动词展开紧缩学术短语，如共享、对接、全程监控；同比改为比上一年增长。把多项条件串成自然的叙述，长句中穿插短句；问题和建议分开交代，减少抽象名词堆叠。不把效率偷换成定义不同的周转率。";
            case "GEZIDA" -> "格子达：把并列名词改成各自的动作或状态，如存在一些不协调的地方、全程监控、应急处理；把原因、约束和措施按读者理解顺序重排。重构并列句和长定语，避免只换连接词。保留原有列举标记但重写各项内容。";
            case "PAPERPASS" -> "PaperPass：较大幅度改变信息入口及句组组合，先交代对象的状态或限制，再解释已有措施和结果；用动作描述替换抽象概念，拆开多层定语，合并重复铺垫。可在段内重排事实，不能照样本虚构人员管理、80分钟等数据；重构密度来自措辞和句架。";
            case "PAPERYY" -> "PaperYY：将书面压缩结构改成常用的动宾短语，数据比较写成比上一年增长；用创建、共享、对接、监控、处置等合适的日常学术词。措施、效果和协调不足分别成句，避免成串的从而使得与希望能够模板。";
            case "ZHUQUE" -> "朱雀：围绕已有主体重新组织依靠什么、发生什么变化、仍有哪些差异；用短动作句替换名词化表达，连接词少而具体。可以交替使用长短句，保留等待时间等指标对象，不把节约时间写成无对象的百分比。";
            case "WANFANG" -> "万方：将数据和观察放在前面，解释随后展开；把抽象名词链改为可读的原因或状态句。比较采用比上一年增长、等待时间缩短等完整关系，末尾建议直接对应已述问题，不再沿研究结果—但是—因此本文—希望的整段骨架。";
            default -> "通用扩展：围绕具体对象、条件和动作重排句组，用普通学术中文整段重写，删去重复的泛化铺垫。";
        };
        return "\n【平台实测改写手法】" + method
                + "\n上述手法是单篇样本的可借鉴表达，不要复刻样本中的病句、新实体、错误数字或虚假调查。每段根据自身内容重组，不强制同一种开头。";
    }
}
