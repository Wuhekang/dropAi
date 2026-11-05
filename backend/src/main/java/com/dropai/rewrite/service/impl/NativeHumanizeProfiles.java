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
                    篇幅与原文接近，可以略短，不要扩写。用口语化近义改写，软化“本文旨在、研究表明”，保留事实和原有句子数量。
                    """;
            case "PAPERPASS" -> """
                    【原生降AI平台档】profile:paperpass-ai-dedicated-v1
                    PaperPass 使用降AI专用档。不要改用通用档、普通模式或深度改写。幅度以 PaperPass 专用 Skill 为准。
                    """;
            case "WANFANG" -> """
                    【原生降AI平台档】profile:wanfang-ai-dedicated-v1
                    万方使用降AI专用档。不要改用通用档、普通模式或深度改写。幅度以万方专用 Skill 为准。
                    """;
            case "TURNITIN" -> """
                    【原生降AI平台档】profile:turnitin-ai-dedicated-v1
                    Turnitin 使用英文降AI专用档。不要改用通用档、普通模式或深度改写。幅度以 Turnitin 专用 Skill 为准。
                    """;
            default -> "";
        };
    }

    private static String ordinaryInstruction(String platform) {
        return switch (platform) {
            case "CNKI" -> """
                    【原生降AI平台档】profile:cnki-ordinary-v1
                    知网普通模式：沿用通用 Skill，只做贴近原文的书面近义改写，不整段换序，也不要写成格子达那种更口语的句子。
                    校准样本是降重狗订单 148900：同一段 186 字改成 185 字，四句都保留。
                    - 篇幅与原文接近，可以略短一两字，不要扩写，不要增删句子。
                    - 软化研究腔但保留原句。近年来，随着……快速发展→近些年来，由于……迅猛发展；本文旨在通过构建→本文用……来；研究表明→从研究可知。
                    - 各行各业→各个行业，重要路径→有效途径。语气保持学术白话，不要改成“伴随着、经过……并且”这种格子达口吻。
                    - 事实、术语和出现顺序不动。已经自然、简短的句子可以保留。
                    """;
            case "WEIPU" -> """
                    【原生降AI平台档】profile:weipu-ordinary-v1
                    维普普通模式：沿用通用 Skill，只打散明显的句式规律，不要求每段更换信息入口。
                    - 不要连续使用“通过……实现……”“基于……构建……”。
                    - 保留句长差异，少用“因此、此外、同时、进而、从而”。
                    - 同类功能名不要反复换成同一组高级同义词。
                    - 已经自然的句子可以保留。
                    """;
            case "PAPERYY" -> """
                    【原生降AI平台档】profile:paperyy-ordinary-v1
                    PaperYY 普通模式：沿用通用 Skill，压低研究声明腔，做贴近原文的近义改写，不额外强制拆段，也不整段换序。
                    校准样本是降重狗订单 148904：同一段 186 字改成 207 字，四句都保留。
                    - 篇幅与原文接近，可以略长，不要大幅扩写，不要增删句子。
                    - 软化研究腔但保留原句。本文旨在→本文主要从……入手；研究表明→经过研究发现。
                    - 不要用“本研究……”“本文旨在……”“本文通过……”或“可以看出”起笔。本文主要从……入手是软化后的说法，可以保留。
                    - 近年来→近几年来，随着……快速发展→由于……发展迅速，各行各业→各个行业。不要改成“从研究可知”或“伴随着”。
                    - 事实、术语和出现顺序不动。末句只做轻度换说。已经自然的句子可以保留。
                    """;
            case "ZHUQUE" -> """
                    【原生降AI平台档】profile:zhuque-ordinary-v1
                    朱雀普通模式：沿用通用 Skill，去掉过度流畅的套话，不要求改变信息顺序。
                    - 删除“值得注意的是、综上所述、具有重要意义、随着……的发展”，不要换成近义空话。
                    - 保留句长起伏，不要把短句全部补成完整句。
                    - 不要再做一遍把句子理顺的润色。
                    - 已经自然的句子可以保留。
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
                    """;
            case "WEIPU" -> """
                    【原生降AI平台档】profile:weipu-deep-v1
                    维普深度改写：在通用 Skill 之上提高一档，不是维普普通模式。
                    - 连续句子不得共用同一语法骨架，至少两句要改起笔方式。
                    - 连接词能删则删，不要改成另一套连接词。
                    - 必须拉开句长差距，禁止改完后句子长度重新对齐。
                    """;
            case "PAPERYY" -> """
                    【原生降AI平台档】profile:paperyy-deep-v1
                    PaperYY 深度改写：在通用 Skill 之上提高一档，不是 PaperYY 普通模式。
                    - 禁止以研究声明句开头，从原文中的对象、条件或结果起笔。
                    - 拆掉“提出问题—分析—得出意义”的三段式骨架。
                    - 段末不单设总结句，也不把短句统一扩成论文腔。
                    """;
            case "ZHUQUE" -> """
                    【原生降AI平台档】profile:zhuque-deep-v1
                    朱雀深度改写：在通用 Skill 之上提高一档，不是朱雀普通模式。
                    - 必须改变开头句式，并至少拆分或合并一处句子。
                    - 保留轻微不对称，禁止再做一遍“理顺”润色。
                    - 套话删除后不回补同义空话，不把节奏重新写匀。
                    """;
            default -> "";
        };
    }
}
