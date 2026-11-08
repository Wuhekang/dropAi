package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通降AI·短同义词加薄连接 v14】所有送改摘要和正文都必须逐句实际改写，不能KEEP_ORIGINAL、回填原稿、只换段首或少量近义词。理解原意后，沿整段连续替换可编辑内容词和词组，优先常见、短的近义表达，不把换入口或调序当成换词完成。专名、数字、单位、引用和结构保护片段保留，其周围的正文仍须换词。
            用短词组密换＋薄连接：持续提高→持续提升/不断提高，协同→配合，监测→监控，等待→等候，衔接→对接，以期→以便/希望，建设→建起。只在必要处加的、了、使得、以及、有点、一下，不把每个名词都解释一遍。按业务语境选词，不套成逐项硬字典；需要保留专业指标时改写周围动词和句法，不改变指标含义。
            普通语域仍要平常，不作论文润色或书面升档，也不整句口语大扩。不要新增一直都在……当中、和上一年同期相比增长了、搭建的相关工作、有用的参考、这些方向/这项工作等重水包装。同比增长可简说同比增加/比去年同期多，时间范围要保留；以期可说希望/以便，不扩成希望能给……提供有用的参考。让句子短而仍充分换词，不能缩短后又接近原文。
            改写段约110%—120%只作对标轻水量的软诊断方向，不是完成条件；少些可正常返回，不为凑比例补字，不按字数单独退回、拒绝或重试。大扩写不能作为密度方法，出现冗长措辞应换成短同义表达而非删事实。候选先不近原文，再少重水/少升档，最后才比较温和篇幅。
            正常的研究发现、据此、因此不是禁词，原有研究—数据—限制—建议顺序可保留。不新增核验、流转、疏解、层级、研究结果显示、管控系统等升档替换，原文已有词或专名除外。查验不能乱换成另一业务操作；通行能力不变通行效率、货值不变交易总额、温度异常不变温度超标、系统故障不变系统宕机。
            不新增实体、案例、调查、步骤、证据或效果。保留条件、否定、因果、程度及统计口径：提高不变达到，约20%不变一半以上；原有约20%可短说大概两成，不能因此加长整句。主要不是不变根本不是，减少不变不用再。应、可、需、鼓励等仍是建议或可能，不写成我们已做、我们也明确了或已经落实；可减少不变已经减少。
            原有序号家族和顺序不变，不另造序号。所有结构占位符逐字保留、各一次且顺序不变。标题、目录、文献条目、域、书签、批注锚点、图片、格式和上标引用由文档层保留，不输出新标题或编号。
            只输出本段一版完整改写正文，不解释、不标注状态，不返回未经实际改写的原稿。
            """;
    static String instruction(String type) {
        return INSTRUCTION;
    }
    /** Source-used terminology is exempt; this is soft candidate preference, not a hard gate. */
    static List<String> formalUpgrades(String source, String output) {
        List<String> words = new ArrayList<>();
        if (source == null || output == null) return words;
        for (String word : List.of("核验", "流转", "疏解", "层级", "研究结果显示", "管控系统")) {
            if (!source.contains(word) && output.contains(word)) words.add(word);
        }
        for (String[] pair : new String[][]{{"通行能力", "通行效率"}, {"货值", "交易总额"},
                {"温度异常", "温度超标"}, {"系统故障", "系统宕机"}}) {
            if (source.contains(pair[0]) && !source.contains(pair[1]) && output.contains(pair[1])) words.add(pair[1]);
        }
        if (source.contains("主要不是") && output.contains("根本不是") && !source.contains("根本不是")) words.add("主要不是→根本不是");
        if (source.contains("减少") && output.contains("不用再") && !source.contains("不用再")) words.add("减少→不用再");
        if (source.contains("鼓励") && output.contains("明确了") && !source.contains("明确了")) words.add("建议→已明确");
        return words;
    }
    static List<String> paddingPhrases(String source, String output) {
        List<String> hits = new ArrayList<>();
        if (source == null || output == null) return hits;
        for (String regex : List.of("一直都在[^。；，]{0,16}当中", "和上一年(?:同期)?相比(?:增长|上涨|提升)了",
                "搭建的相关工作", "有用的参考", "这些方向", "这项工作", "靠着[^。；]{0,30}以及[^。；]{0,20}相关工作")) {
            var matcher = Pattern.compile(regex).matcher(output);
            while (matcher.find()) if (!source.contains(matcher.group())) hits.add(matcher.group());
        }
        return hits;
    }
    static double softLengthCost(String source, String output) {
        int original = source == null ? 0 : source.replaceAll("\\s+", "").length();
        int rewritten = output == null ? 0 : output.replaceAll("\\s+", "").length();
        // Soft tie-break only: never ask for padding when shorter, never retry on length alone.
        return original == 0 ? 0 : Math.max(0, rewritten / (double) original - 1.20);
    }
    static boolean preferCandidate(String source, String candidate, String current) {
        if (current == null) return true;
        int comparison = Boolean.compare(OrdinaryRewriteAmplitude.needsLexicalRetry(source, candidate),
                OrdinaryRewriteAmplitude.needsLexicalRetry(source, current));
        if (comparison != 0) return comparison < 0;
        int candidateIssues = formalUpgrades(source, candidate).size() + paddingPhrases(source, candidate).size();
        int currentIssues = formalUpgrades(source, current).size() + paddingPhrases(source, current).size();
        comparison = Integer.compare(candidateIssues, currentIssues);
        if (comparison != 0) return comparison < 0;
        comparison = Double.compare(softLengthCost(source, candidate), softLengthCost(source, current));
        if (comparison != 0) return comparison < 0;
        return overlap(source, candidate) < overlap(source, current);
    }
    private static final Pattern ORDINAL = Pattern.compile(com.dropai.rewrite.service.TextStructureProtector.ORDINARY_ORDINAL_REGEX);
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?[%％]?");
    private OrdinaryFullRebuild() { }
    static List<String> issues(String source, String output) {
        List<String> issues = new ArrayList<>();
        if (output == null || output.isBlank()) return List.of("输出为空");
        if (!matches(ORDINAL, source).equals(matches(ORDINAL, output))) issues.add("序号体系或顺序发生变化");
        return List.copyOf(issues);
    }
    /** Repair marker family only, retaining the generated prose instead of pasting the source. */
    static String repairOrdinals(String source, String output) {
        if (output == null || output.isBlank()) return output;
        List<String> expected = matches(ORDINAL, source);
        List<String> actual = matches(ORDINAL, output);
        if (expected.equals(actual)) return output;
        if (expected.size() == actual.size()) {
            var matcher = ORDINAL.matcher(output);
            StringBuffer repaired = new StringBuffer();
            int index = 0;
            while (matcher.find()) matcher.appendReplacement(repaired, java.util.regex.Matcher.quoteReplacement(expected.get(index++)));
            matcher.appendTail(repaired);
            return repaired.toString();
        }
        String prose = ORDINAL.matcher(output).replaceAll("");
        if (expected.isEmpty()) return prose.replaceAll("(^|[。；;\\n])\\s*[，,、：:]", "$1").trim();
        // Missing list markers can be attached to existing clause starts, never inside a word.
        var clauses = Pattern.compile("(?:^|(?<=[。；;，,]))[^。；;，,]+").matcher(prose);
        List<Integer> starts = new ArrayList<>();
        while (clauses.find()) if (!clauses.group().isBlank()) starts.add(clauses.start());
        if (starts.size() < expected.size()) return output;
        StringBuilder repaired = new StringBuilder(prose);
        for (int i = expected.size() - 1; i >= 0; i--) {
            int slot = i * starts.size() / expected.size();
            String marker = expected.get(i);
            repaired.insert(starts.get(slot), marker + (marker.endsWith("是") ? "" : "，"));
        }
        return repaired.toString();
    }

    static List<String> diagnostics(String source, String output) {
        List<String> issues = new ArrayList<>();
        if (output == null || output.isBlank()) return List.of();
        if (!numbers(source).equals(numbers(output))) issues.add("数值表达有差异（仅供复核，不阻断普通档）");
        for (String entity : List.of("实地考察", "海上丝绸之路", "智能仓储")) {
            if (!source.contains(entity) && output.contains(entity)) issues.add("新增未提供的事实：" + entity);
        }
        List<String> registerIssues = formalUpgrades(source, output);
        if (!registerIssues.isEmpty()) issues.add("新添书面升档或指标漂移措辞：" + String.join("、", registerIssues));
        List<String> padding = paddingPhrases(source, output);
        if (!padding.isEmpty()) issues.add("重水措辞需复核：" + String.join("、", padding));
        if (OrdinaryRewriteAmplitude.needsLexicalRetry(source, output)) issues.add("近原文换词不足：已输出可用候选，但密度质量不通过，需复核");
        return List.copyOf(issues);
    }
    private static List<String> matches(Pattern p, String s) { return p.matcher(s).results().map(x -> x.group()).toList(); }
    private static Map<String, Long> numbers(String s) {
        Map<String, Long> m = new HashMap<>();
        for (String n : matches(NUMBER, s)) m.merge(n.replace('％','%'),1L,Long::sum);
        return m;
    }
    static double overlap(String a, String b) {
        a=a.replaceAll("[^\\p{L}]", ""); b=b.replaceAll("[^\\p{L}]", "");
        Set<String> grams=new HashSet<>(); for(int i=0;i+3<=a.length();i++) grams.add(a.substring(i,i+3));
        if(grams.isEmpty()) return a.equals(b)?1:0;
        return grams.stream().filter(b::contains).count()/(double)grams.size();
    }
}
