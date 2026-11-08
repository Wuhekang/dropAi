package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通降AI·维普式轻口语重述 v15】所有送改摘要和正文都须实际改写，不能KEEP_ORIGINAL、回填、少量换词或只换入口。先读清原义，再用常见词组连续改写各句，同时改变原文套式的开场、承接和收尾，不做原句骨架上的词典贴词。
            打断模板家族：不要留下裸起句研究发现、据此、以期、持续提高、尚未完全转化为，以及本文从……提出改进建议以期……的整套串法。可说经研究发现/从资料来看、不断加大……并且……从而使得……、因此本文提出了改进措施、以便提供一定的借鉴、整个链条等。原文未做调查，不得写经过调查发现；不新增任何研究经历。示例只指表达模式，不是每段都套同一套词。
            保持轻口语：内容词密换，必要处补的、了、使得、以及、并且、从而，让句组有自然的承接。同比数据可简说比去年同期增长了，不能把月度同比改成全年对比。定义可先说原有条件或要配合的事，措施可先说已有目的或场景，再接原措施与限制；后半段也要处理，不只换段首。
            轻水量约112%—120%只作软诊断与候选偏好，不能为凑比例补空话。偏短而仍近原文或模板未改，要重写表达；大幅加长或出现重水包装，要换成轻口语。仅字数不构成硬拒绝，最多约三次软候选后保留可用生成稿并在独立报告标质量，不回填原稿。不要新增一直都在……当中、和上一年同期相比增长了、搭建的相关工作、有用的参考这类整句扩写。
            不作书面升档：不要把查验换核验、周转换流转、分流换疏解、水平换层级、研究发现换研究结果显示，原文已有这些词或专名除外。不要造查点次序、放行进速这类生硬短词；常用业务名称可在周围换表达，不能为了换字改成另一操作或指标。
            保留原事实、数值、单位、统计口径、专名、条件、否定、因果、程度与建议语气。提高35%不是达到35%；约20%可说大概两成，不能说一半以上；24小时保持明确时长，不省成含糊的全天。原文“主要不是”可等义写成“主要并非”，绝不能加强为“根本不是”；“减少”不得夸成“完全不用/不用再”。应、可、需、鼓励仍是待办建议或可能，不能说已经落实、我们也明确了。通行能力不是通行效率，货值不是交易总额，温度异常不等于超标，系统故障不等于宕机。不能新增实体、调查、案例、步骤、证据或结论。
            序号家族和顺序不变，不另造序号；所有结构占位符逐字保留、各一次且顺序不变。标题、目录、文献条目、域、书签、批注锚点、图片、格式与上标引用由文档层保留，不改段落容器、不输出新标题或编号。
            只输出一版完整改写正文，不解释、不标注状态，不返回原稿或近原文少换词稿。
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
    static List<String> templateRemnants(String output) {
        List<String> hits = new ArrayList<>();
        if (output == null) return hits;
        for (String regex : List.of("(?:^|[。！？]\\s*)研究发现[，,：:]", "据此", "以期", "持续提高", "尚未完全转化为",
                "本文从[^。！？]{0,100}(?:提出|给出)[^。！？]{0,10}(?:改进|优化)建议",
                "^(?:围绕|紧扣|聚焦)[^。]{0,100}(?:形成|整理出|制定)[^。]{0,30}(?:指引|指南|规范)",
                "^.{1,12}(?:是|就是)[^。]{0,18}围绕")) {
            var matcher = Pattern.compile(regex).matcher(output);
            while (matcher.find()) hits.add(matcher.group());
        }
        return hits;
    }
    static List<String> unnaturalSwaps(String source, String output) {
        List<String> hits = new ArrayList<>();
        if (source == null || output == null) return hits;
        for (String word : List.of("查点次序", "放行进速")) if (!source.contains(word) && output.contains(word)) hits.add(word);
        return hits;
    }
    static double lengthRatio(String source, String output) {
        int original = source == null ? 0 : source.replaceAll("\\s+", "").length();
        int rewritten = output == null ? 0 : output.replaceAll("\\s+", "").length();
        return original == 0 ? 1 : rewritten / (double) original;
    }
    static double softLengthCost(String source, String output) {
        double ratio = lengthRatio(source, output);
        return ratio < 1.12 ? 1.12 - ratio : Math.max(0, ratio - 1.20);
    }
    static boolean preferCandidate(String source, String candidate, String current) {
        if (current == null) return true;
        int candidateWeak = (OrdinaryRewriteAmplitude.needsLexicalRetry(source, candidate) ? 1 : 0) + templateRemnants(candidate).size();
        int currentWeak = (OrdinaryRewriteAmplitude.needsLexicalRetry(source, current) ? 1 : 0) + templateRemnants(current).size();
        int comparison = Integer.compare(candidateWeak, currentWeak);
        if (comparison != 0) return comparison < 0;
        int candidateIssues = formalUpgrades(source, candidate).size() + paddingPhrases(source, candidate).size();
        int currentIssues = formalUpgrades(source, current).size() + paddingPhrases(source, current).size();
        comparison = Integer.compare(candidateIssues, currentIssues);
        if (comparison != 0) return comparison < 0;
        comparison = Double.compare(softLengthCost(source, candidate), softLengthCost(source, current));
        if (comparison != 0) return comparison < 0;
        comparison = Integer.compare(unnaturalSwaps(source, candidate).size(), unnaturalSwaps(source, current).size());
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
        List<String> templates = templateRemnants(output);
        if (!templates.isEmpty()) issues.add("模板家族残留：" + String.join("、", templates));
        if (!unnaturalSwaps(source, output).isEmpty()) issues.add("不自然短词：" + String.join("、", unnaturalSwaps(source, output)));
        if (lengthRatio(source, output) >= 1.40) issues.add("大幅扩写风险（软诊断，不拒绝输出）");
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
