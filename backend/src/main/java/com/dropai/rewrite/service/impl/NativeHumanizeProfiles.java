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
            return dedicatedInstruction(platform) + ("PAPERPASS".equals(platform) ? competitorOrdinaryInstruction() : "");
        }
        return isDeep(rewriteType) ? deepInstruction(platform) : ordinaryInstruction(platform)
                + ("WEIPU".equals(platform) ? competitorOrdinaryInstruction() : "");
    }

    private static String competitorOrdinaryInstruction() {
        return """
                【竞品样本A普通表达约束 v3】
                摘要、研究方法和枚举段落必须重建句组关系，不能只把研究发现换成经过研究得知。
                禁止将旧套话替换为另一套宣传骨架：得到直接提升、运行态势、核心环节、优化思路，以及“针对这些短板，研究从……等方向提出优化思路，供……参考”。
                用有具体主语的平实学术句说明已有措施、结果和限制，少用多层名词串联。可交替用经过研究得知、经研究发现、研究显示、从结果看；按原意连接但是、因此本文就……提出改善措施、从而给……提供一定的借鉴，不能整段照搬固定模板。
                数据句与限制句要换一种组织方式，而非保留原有并列骨架。持续提高可按原义说明能力怎样变化；尚未完全转化为可按原义说明仍缺少什么衔接。不得凭空补原因。
                等待时长或等待时间在语义不变时用等候时间；避免用得到直接提升替代持续提高。同比增长只有在原文明确年度同比时才可用比上一年增长，月度同比不能改成上一年。
                公开资料仍是公开资料，不能改成行业调研信息；没有实际调研不能声称调查、访谈或实测。
                避免保留这些模板骨架：研究发现、据此、以期、综合运用、持续提高、尚未完全转化为、本文从……等方面提出改进建议。
                可参照样本A使用平实表达，如经过研究得知、因此本文提出改善措施、从而给……借鉴；不得每段套用同一个开头或结尾。
                同比增长可在同比基期明确为上一年时表达为比上一年增长；等待时间可表达为等候时间。周转效率与周转率并非当然等价，指标定义不明时保留原术语。
                方法段用研究对象与已有资料组织方法之间的关系，枚举段按原有类别重新拆合说明，不增加方法、类别或步骤。
                事实锁优先：年份、吨位、百分比、专名、引用、图表结构和结论不得增删改；保留可、约、超过等限定及比较基期，不把可能效果写成已经实现。
                不得模仿竞品失真：不能新增出口加工基地系统，不能将上市周期互补改成上市时间相近。
                """;
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
                    【原生降AI平台档】profile:gezida-ai-dedicated-v2
                    格子达使用降AI专用档。不要改用通用档，也不要另写深度档。
                    正常论述段必须重排信息入口，并至少拆句、并句或重组一处句组。可以口语化，但禁止只做口语近义替换，也禁止为了贴近原文而保住句子数量。
                    软化“本文旨在、研究表明”，但不能只换这两个起笔。订单 148898 的 186 字到 175 字只是旧记录，不是字数上限。年份、吨位、百分比、专名、引用和原文结论不能换。
                    """;
            case "PAPERPASS" -> """
                    【原生降AI平台档】profile:paperpass-ai-dedicated-v2
                    PaperPass 使用降AI专用档。不要改用通用档，也不要另写深度档。
                    正常论述段必须重排信息入口，并至少拆句、并句或重组句组。禁止停在逐句换词。旧订单 148903 的 186 字到 195 字不是目标幅度。
                    原文若在讲数据安全与隐私保护服务于可持续的实践价值，必须保住这层意思。不得写成“不足之处，有待于探究”，不得新增原文没有的缺口或结论。
                    第二轮订单 148945：门诊科室不得写成门诊科目。完整记录占比为91.5% 必须保住完整记录所占比例，不得写成“被记录的有91.5%”。
                    篇幅约 70%–150%。超出 130% 不要退回近义版，也不要删掉独立事实。
                    """;
            case "WANFANG" -> """
                    【原生降AI平台档】profile:wanfang-ai-dedicated-v2
                    万方使用降AI专用档。不要改用通用档，也不要另写深度档。
                    正常论述段必须重排信息入口，并至少拆句、并句或重组一处句组。因为、并且、另外可以承接，但禁止只做因果近义替换，也禁止保住原句数量。
                    订单 148902 的 186 字到 182 字只是旧记录。末句必须保住可持续的实践价值。不得写成“防止该技术方案失效”。
                    第二轮订单 148944：浸泡20分钟必须保留。25摄氏度、60%、3个重复、12片、36小时、每6小时、0.02克、显著性水平0.05 等数字不得删改。
                    篇幅约 70%–150%。超出 130% 不要退回近义版。
                    """;
            case "TURNITIN" -> """
                    【原生降AI平台档】profile:turnitin-ai-dedicated-v2
                    Turnitin 使用英文降AI专用档。不要改用通用档，也不要另写深度档。
                    英文论述段必须重排信息入口，并至少拆句、并句或重组一处句组。保持英文，不要译成中文。禁止只做逐句换词，也禁止为了贴近原文而保住句子数量。
                    禁止事实漂移：operational efficiency 不得写成 economic vitality of a region。
                    第二轮订单 148946：出口卡必须保留 speed、clarity of signs、whether the wait felt reasonable 三项。禁止 small-print 和 sense of anticipation。Holds that required items from closed storage were excluded 必须留下。
                    词数约原文的 75%–140%。超出旧样本的 100 词到 101 词不要退回近义版。
                    """;
            default -> "";
        };
    }

    private static String ordinaryInstruction(String platform) {
        return switch (platform) {
            case "CNKI" -> """
                    【原生降AI平台档】profile:cnki-ordinary-v2
                    知网普通模式：正常论述段必须重排信息入口，并至少拆句、并句或重组一处句组。禁止逐句换词，禁止保住原句数量和“背景—目的—结论”骨架。不要套用深度档，也不要改成格子达口吻。
                    事实锁优先于幅度：年份、吨位、百分比、专名、引用编号和图表结构不得增删改，锁定项的相对顺序不变。不得新增原文没有的结论。
                    订单 148900 只保留事实约束：运行效率、运营成本、数据安全和隐私保护都要留下；原文已有的可持续实践价值或应用价值必须留下。不要把该订单 186 字改成 185 字、四句不动当作目标，也不要固定写成“本文用”或“从研究可知”。
                    篇幅约原文 70%–150%，只作软参考。结构改写超出 130% 时不要退回近义版，也不要为了压长度删掉独立事实。
                    单句和只剩锁定事实的片段可以少动。两句以上的论述段不能原样交回，也不要把“具备持续的应用价值”写进原文没有这层意思的段落。
                    """;
            case "WEIPU" -> """
                    【原生降AI平台档】profile:weipu-ordinary-v2
                    维普普通模式：正常论述段必须重排信息入口，并至少拆句、并句或重组一处句组。禁止连续“通过……实现……”“基于……构建……”，禁止逐句换词，禁止保住原句数量。不要套用深度档。
                    事实锁优先：年份、吨位、百分比、专名、引用和图表结构不得增删改，锁定项的相对顺序不变。
                    订单 148908 只保留事实约束：末句必须仍是可持续的实践价值或应用价值，不得写成“持续使用的必要性”。运行效率和运营成本都要留下。不要照搬该订单四句不动、186 字到 179 字的换词写法。
                    不要把“具备持续的应用价值”写进原文没有这层意思的段落。篇幅约 70%–150%。超出 130% 不要退回近义版。
                    同类功能名不要反复换成同一组高级同义词。单句可以少动，两句以上的论述段不能原样交回。
                    """;
            case "PAPERYY" -> """
                    【原生降AI平台档】profile:paperyy-ordinary-v2
                    PaperYY 普通模式：正常论述段必须重排信息入口，并至少拆句、并句或重组一处句组。禁止逐句换词，禁止保住原句数量。不要套用深度档。
                    订单 148904 只保留事实约束：末句可以写成持续的可行性，不得写成新的不足或待探究。运行效率和运营成本都要留下。不要把四句不动、186 字到 207 字当作目标。
                    “本文主要从”“经过研究发现”只是旧样本的软化例子，不能代替结构改写。
                    年份、吨位、百分比、专名、引用和图表结构不得增删改。篇幅约 70%–150%。超出 130% 不要退回近义版。
                    """;
            case "ZHUQUE" -> """
                    【原生降AI平台档】profile:zhuque-ordinary-v2
                    朱雀普通模式：正常论述段必须重排信息入口，并至少拆句、并句或重组一处句组。禁止只软化套话起笔，禁止保住原句数量和信息顺序。不要套用深度档。
                    订单 148907 只保留事实约束：运行效率和运营成本必须留下，不得改成“综合运作水平”或“企业的开支”。发展这层事实若原文已有，不能删掉后换成空话。不要照搬四句不动、186 字到 194 字。
                    原文已有的数据安全、隐私保护和可持续应用价值必须留下。“值得注意的是、综上所述、具有重要意义”删掉后不要补近义空话，也不要每段都用“具备持续的应用价值”收尾。
                    年份、吨位、百分比、专名、引用和图表结构不得增删改。篇幅约 70%–150%。超出 130% 不要退回近义版。
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
