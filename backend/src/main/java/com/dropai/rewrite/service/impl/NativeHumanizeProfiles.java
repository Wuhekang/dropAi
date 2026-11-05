package com.dropai.rewrite.service.impl;

import java.util.Locale;

/**
 * Platform and intensity selection for native 降AI only.
 *
 * <p>降重 and 双降 keep {@link #legacyRewriteCode(String)}. 大雅 stays off this list and is not
 * routed here. Blank and unknown values stay {@code GENERAL}.
 */
public final class NativeHumanizeProfiles {

    public static final String GENERAL = "GENERAL";
    public static final String BASE_SKILL = "humanize-zh-academic";
    /** Document 双降 second pass. Keeps the pre-existing shared humanize prompt. */
    public static final String SHARED_HUMANIZE = "humanize-shared";

    public enum Kind {
        /** Shared native skill, no extra platform overlay. */
        GENERAL,
        /** 普通模式 / 深度改写 on the shared native skill. */
        DEPTH_PAIR,
        /** One 降AI专用 profile. Depth does not switch the skill. */
        DEDICATED
    }

    private NativeHumanizeProfiles() {
    }

    public static String normalize(String platform) {
        if (platform == null || platform.isBlank()) {
            return GENERAL;
        }
        String compact = platform.trim().replace("-", "").replace("_", "").replace(" ", "");
        String upper = compact.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CNKI", "ZHIWANG" -> "CNKI";
            case "WEIPU", "VIP", "CQVIP" -> "WEIPU";
            case "WANFANG", "WANGFANG" -> "WANFANG";
            case "GEZIDA" -> "GEZIDA";
            case "PAPERPASS" -> "PAPERPASS";
            case "PAPERYY" -> "PAPERYY";
            case "ZHUQUE" -> "ZHUQUE";
            case "TURNITIN" -> "TURNITIN";
            case "DAYA" -> GENERAL;
            default -> chineseAlias(platform.trim());
        };
    }

    /**
     * Platforms the rewrite / double pipelines already accepted. New 降AI codes collapse to GENERAL
     * so those prompts stay unchanged.
     */
    public static String legacyRewriteCode(String platform) {
        if (platform == null || platform.isBlank()) {
            return GENERAL;
        }
        return switch (platform.trim().toUpperCase(Locale.ROOT)) {
            case "CNKI", "WEIPU", "WANFANG", "GEZIDA" -> platform.trim().toUpperCase(Locale.ROOT);
            default -> GENERAL;
        };
    }

    public static String displayName(String platform) {
        return switch (platform == null ? "" : platform) {
            case "CNKI" -> "知网";
            case "WEIPU" -> "维普";
            case "WANFANG" -> "万方";
            case "GEZIDA" -> "格子达";
            case "PAPERPASS" -> "PaperPass";
            case "PAPERYY" -> "PaperYY";
            case "ZHUQUE" -> "朱雀";
            case "TURNITIN" -> "Turnitin";
            default -> "通用";
        };
    }

    public static Kind kind(String platform) {
        return switch (platform == null ? "" : platform) {
            case "CNKI", "WEIPU", "PAPERYY", "ZHUQUE" -> Kind.DEPTH_PAIR;
            case "GEZIDA", "PAPERPASS", "WANFANG", "TURNITIN" -> Kind.DEDICATED;
            default -> Kind.GENERAL;
        };
    }

    public static boolean usesNativeSkill(String platform) {
        return switch (platform == null ? "" : platform) {
            case "GENERAL", "CNKI", "WEIPU", "WANFANG", "GEZIDA",
                    "PAPERPASS", "PAPERYY", "ZHUQUE", "TURNITIN" -> true;
            default -> false;
        };
    }

    public static String skillName(String platform) {
        return switch (platform == null ? "" : platform) {
            case "GEZIDA" -> "humanize-platform-gezida";
            case "PAPERPASS" -> "humanize-platform-paperpass";
            case "WANFANG" -> "humanize-platform-wanfang";
            case "TURNITIN" -> "humanize-platform-turnitin";
            default -> BASE_SKILL;
        };
    }

    public static boolean isHumanizeType(String rewriteType) {
        String base = baseRewriteType(rewriteType);
        return "humanize".equals(base)
                || "智能降AI".equals(base)
                || "降低AI写作痕迹".equals(base)
                || "深度降低AI写作痕迹".equals(base);
    }

    public static boolean isDeep(String rewriteType) {
        return "深度降低AI写作痕迹".equals(baseRewriteType(rewriteType));
    }

    /**
     * Document mode stays {@code humanize}. Only the existing deep rewrite type selects 深度改写.
     * {@code humanize}, {@code FULL_AI_REDUCE} and {@code PRECISE_AI_REDUCE} stay 普通.
     */
    public static String humanizeBase(String rawMode) {
        if (rawMode != null && "深度降低AI写作痕迹".equals(rawMode.trim())) {
            return "深度降低AI写作痕迹";
        }
        return "humanize";
    }

    public static String workflowType(String baseRewriteType, String platform) {
        String base = baseRewriteType == null || baseRewriteType.isBlank() ? "humanize" : baseRewriteType;
        if (platform == null || platform.isBlank() || GENERAL.equals(platform)) {
            return base;
        }
        return base + "@" + platform;
    }

    public static String baseRewriteType(String rewriteType) {
        if (rewriteType == null) {
            return "";
        }
        int index = rewriteType.indexOf('@');
        return index >= 0 ? rewriteType.substring(0, index) : rewriteType;
    }

    public static String profileInstruction(String rewriteType, String platform) {
        Kind kind = kind(platform);
        if (kind == Kind.GENERAL) {
            return "";
        }
        if (kind == Kind.DEDICATED) {
            return dedicatedInstruction(platform);
        }
        return isDeep(rewriteType) ? deepInstruction(platform) : ordinaryInstruction(platform);
    }

    private static String chineseAlias(String trimmed) {
        return switch (trimmed) {
            case "知网" -> "CNKI";
            case "维普" -> "WEIPU";
            case "万方" -> "WANFANG";
            case "格子达" -> "GEZIDA";
            case "朱雀" -> "ZHUQUE";
            case "大雅" -> GENERAL;
            default -> GENERAL;
        };
    }

    private static String dedicatedInstruction(String platform) {
        return switch (platform) {
            case "GEZIDA" -> """
                    【原生降AI平台档】profile:gezida-ai-dedicated-v1
                    格子达使用降AI专用档。不要改用通用档、普通模式或深度改写。
                    允许在段内改信息入口、拆句、并句并重组句组，用口语打散书面腔。不要只做逐句换词。
                    篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。校准样本 148898 是 186 字到 175 字，可作幅度参考，不要为了贴样本加长。
                    高风险模板段必须重写，即使读起来已经通顺。不得留下“本文旨在、研究表明”。
                    保留事实、数字、单位、年份、因果强度、专名、引用和占位符。不得新增事实，不得丢掉独立判断。
                    """;
            case "PAPERPASS" -> """
                    【原生降AI平台档】profile:paperpass-ai-dedicated-v1
                    PaperPass 使用降AI专用档。不要改用通用档、普通模式或深度改写。幅度以 PaperPass 专用 Skill 为准。
                    校准样本是降重狗订单 148903：186 字到 195 字，仍在篇幅带内。要改句式骨架和信息入口，允许段内拆句、并句、重组句组，不得只做逐句换词。
                    篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。
                    模板段即使已经通顺也要重写。末句必须仍是数据安全与隐私保护服务于可持续的实践价值。不得写成“不足之处，有待于探究”，不得新增原文没有的缺口或结论。
                    第二轮订单 148945：门诊科室不得写成门诊科目。完整记录占比为91.5% 必须保住完整记录所占比例，不得写成“被记录的有91.5%”。
                    数字、单位、年份、因果强度、专名、引用和占位符原值不动。
                    """;
            case "WANFANG" -> """
                    【原生降AI平台档】profile:wanfang-ai-dedicated-v1
                    万方使用降AI专用档。不要改用通用档、普通模式或深度改写。
                    允许在段内改信息入口、拆句、并句并重组句组。用因为、并且、另外承接，不要只做逐句换词。
                    校准样本是降重狗订单 148902：186 字到 182 字。篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。
                    模板段即使已经通顺也要重写。末句必须保住可持续的实践价值。不得写成“防止该技术方案失效”。
                    第二轮订单 148944：浸泡20分钟必须保留。25摄氏度、60%、3个重复、12片、36小时、每6小时、0.02克、显著性水平0.05 等数字不得删改。
                    不得丢掉独立判断，不得新增原文没有的事实。
                    """;
            case "TURNITIN" -> """
                    【原生降AI平台档】profile:turnitin-ai-dedicated-v1
                    Turnitin 使用英文降AI专用档。不要改用通用档、普通模式或深度改写。
                    英文段内可以改信息入口、拆句、并句并重组句组。保持英文，不要译成中文。不要只做逐词替换。
                    词数贴近原文，优先略少，约90%—108%，优先不超过105%，不要扩写。样本是 100 个英文词到 101 个英文词。
                    In recent years、This paper aims to、Research shows that 这类模板起笔，即使已经通顺也要改入口。
                    禁止事实漂移：operational efficiency 不得写成 economic vitality of a region。
                    第二轮订单 148946：出口卡必须保留 speed、clarity of signs、whether the wait felt reasonable 三项。禁止 small-print 和 sense of anticipation。Holds that required items from closed storage were excluded 必须留下。
                    数字、专名、程度、因果和占位符原值不动。不得新增主体或地域。
                    """;
            default -> "";
        };
    }

    private static String ordinaryInstruction(String platform) {
        return switch (platform) {
            case "CNKI" -> """
                    【原生降AI平台档】profile:cnki-ordinary-v1
                    知网普通模式：沿用通用 Skill 的术语、事实锁和保护规则。深度档更强，不要套用深度档的强制配额，但本档必须打散入口和句组，不能只换近义词。
                    保持书面学术白话，不要写成格子达那种更口语的句子。
                    校准样本是降重狗订单 148900：186 字改成 185 字，可作篇幅和起笔参考，不是逐句锁死。
                    - 允许在段内改信息入口、拆句、并句并重组句组。不要跨段调序，不要新增事实，不要丢掉独立判断。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。
                    - 软化研究腔可以作为改入口的一种做法。近年来，随着……快速发展→近些年来，由于……迅猛发展；本文旨在通过构建→本文用……来；研究表明→从研究可知。
                    - 各行各业→各个行业，重要路径→有效途径。不要改成“伴随着、经过……并且”这种格子达口吻。
                    - 事实、术语、数字、单位、年份、因果强度、专名、引用和占位符不动。
                    - 模板段即使已经通顺也要重写，不能整段留下。没有模板骨架、且改动会碰到锁定事实的短句才可以保留。
                    """;
            case "WEIPU" -> """
                    【原生降AI平台档】profile:weipu-ordinary-v1
                    维普普通模式：沿用通用 Skill 的术语、事实锁和保护规则。深度档更强，不要套用深度档的强制配额。
                    只换近义词并加长，AIGC 不会下降。要打散信息入口和句组，同时把篇幅压在原文附近。
                    校准样本是降重狗订单 148908：186 字改到 179 字附近，可作节奏参考。不要照抄竞品把实践价值改成“持续使用的必要性”，也不要为了贴样本加长。
                    - 允许在段内改信息入口、拆句、并句并重组句组。不要跨段调序，不要新增事实，不要丢掉独立判断，不要只做近义词替换。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。
                    - 软化研究腔起笔只是例子，不是逐句对应。近年来，随着……快速发展→近几年来，由于……发展迅速；本文旨在通过构建一套系统化的分析框架→本文主要通过对……进行系统的分析，或从已有对象写起；研究表明，通过……→从实际研究中得知，在……的基础上。
                    - 不要连续使用“通过……实现……”“基于……构建……”。连接可以用“由于、另外”，不要堆“因此、此外、同时、进而、从而”。
                    - 末句必须保住“可持续的实践价值”。可以写成持续的应用价值，不得改成“持续使用的必要性”，也不得另写用途。
                    - 同类功能名不要反复换成同一组高级同义词。数字、单位、年份、因果强度、专名、引用和占位符原值不动。
                    - 模板段即使已经通顺也要重写入口和句组，不能整段留下。
                    """;
            case "PAPERYY" -> """
                    【原生降AI平台档】profile:paperyy-ordinary-v1
                    PaperYY 普通模式：沿用通用 Skill 的术语、事实锁和保护规则。深度档更强，不要套用深度档。
                    校准样本是降重狗订单 148904，只借起笔和末句说法，不要跟着它把 186 字写成 207 字。
                    - 允许在段内改信息入口、拆句、并句并重组句组。不要跨段调序，不要新增事实，不要丢掉独立判断，不要只做近义词替换。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写，不要再加句子垫长。
                    - 软化研究声明起笔。本文旨在通过构建→本文主要从建立一个……入手；研究表明→经过研究发现。
                    - 减少“本研究、本文通过、可以看出”连续起笔，但留下原文已有的对象、条件和数据，不要另起一套研究声明。
                    - 末句“可持续的实践价值”可以写成“持续的可行性”，不要改成新的结论或待探究的不足。
                    - 不要把每个功能点补成完整的论证段。模板段即使已经通顺也要重写骨架。
                    """;
            case "ZHUQUE" -> """
                    【原生降AI平台档】profile:zhuque-ordinary-v1
                    朱雀普通模式：沿用通用 Skill 的术语、事实锁和保护规则。深度档更强，不要套用深度档。
                    可以改入口和句组。发展这层事实要留下，不要删掉后再换成空话。
                    校准样本是降重狗订单 148907：186 字到 194 字，可作起笔参考，不要再加长。
                    - 允许在段内改信息入口、拆句、并句并重组句组。不要跨段调序，不要新增事实，不要丢掉独立判断，不要只做近义词替换。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写，不要补成新的论证段。
                    - 软化起笔：近年来，随着……快速发展→近些年来，由于……迅速发展；本文旨在→本文主要是为了；研究表明→从已有研究可知。
                    - 运行效率和运营成本必须留下。不得改成“综合运作水平”或“企业的开支”。
                    - 末句保持数据安全、隐私保护和可持续的应用价值。“值得注意的是、综上所述、具有重要意义”删掉后不要补近义空话。
                    - 保留句长起伏。模板段即使已经通顺也要重写，不能整段留下。
                    """;
            default -> "";
        };
    }

    private static String deepInstruction(String platform) {
        return switch (platform) {
            case "CNKI" -> """
                    【原生降AI平台档】profile:cnki-deep-v1
                    知网深度改写：在通用 Skill 之上提高一档，不是知网普通模式。
                    - 不得保留“背景-问题-措施-意义”的叙述顺序，从原文已有的具体事实、对象或条件起笔。
                    - 综述腔和段末意义句删除，不用近义词补回。
                    - 至少调整一处句组顺序或做一次拆合，仍不新增事实，不遗漏独立信息。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。深度不得靠加长完成。
                    """;
            case "WEIPU" -> """
                    【原生降AI平台档】profile:weipu-deep-v1
                    维普深度改写：在通用 Skill 之上提高一档，不是维普普通模式。
                    - 连续句子不得共用同一语法骨架，至少两句要改起笔方式。
                    - 连接词能删则删，不要改成另一套连接词。
                    - 必须拉开句长差距，禁止改完后句子长度重新对齐。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。深度不得靠加长完成。
                    """;
            case "PAPERYY" -> """
                    【原生降AI平台档】profile:paperyy-deep-v1
                    PaperYY 深度改写：在通用 Skill 之上提高一档，不是 PaperYY 普通模式。
                    - 禁止以研究声明句开头，从原文中的对象、条件或结果起笔。
                    - 拆掉“提出问题—分析—得出意义”的三段式骨架。
                    - 段末不单设总结句，也不把短句统一扩成论文腔。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。深度不得靠加长完成。
                    """;
            case "ZHUQUE" -> """
                    【原生降AI平台档】profile:zhuque-deep-v1
                    朱雀深度改写：在通用 Skill 之上提高一档，不是朱雀普通模式。
                    - 必须改变开头句式，并至少拆分或合并一处句子。
                    - 保留轻微不对称，禁止再做一遍“理顺”润色。
                    - 套话删除后不回补同义空话，不把节奏重新写匀。
                    - 篇幅贴近原文，优先略短，控制在90%—108%，优先不超过105%，不要扩写。深度不得靠加长完成。
                    """;
            default -> "";
        };
    }
}
