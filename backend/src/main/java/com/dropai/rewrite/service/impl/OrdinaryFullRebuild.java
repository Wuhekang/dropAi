package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通降AI·v7f口语密集展开 v13】按下列实测表达方式完整重述输入正文：在整段范围内连续改写普通词组、谓语和连接成分，把紧缩的书面概念展开成平常的说法，允许补入自然的“的、了、使得、并且、以及、还”等成分。不要只替换开头和结尾，中间各句也要按同样密度处理。
            例如“持续提高”可说“一直都在进步当中”，“仍需进一步协同”可说“还需要加强合作”，“尚未完全转化为全链条稳定性”可说“还没有完全转化为整个链条的稳定性”，“全过程监测”可说“全程监控”。按语境变通，不是固定词典替换，不把示例中没有的事实加到正文里。
            论述顺序合理就保留原顺序，句子数量可以不变。不要为追求变化强制拆句、调序或重建背景—问题—根源骨架。意思成立时偏向较短、少绕的表达，不设字数或字符比例目标、区间或上下限，不按篇幅评分、拒绝或重试。让常用表达自然展开，不换成更凝练、更复杂的学术术语。
            “研究发现、经研究发现、但是、因此本文、据此、以期、希望能够”等不是违禁词，按正文语境保留或自然替换。不另做学术润色、禁词清洗或统一收尾，不为了删掉这些词改回紧缩论文表达。正文整体允许充分改写，不要求词语、数字表达及信息顺序逐字相等；依据已有内容重述，不额外编造实体、经历或证据。
            仅保护结构：原有序号体系及顺序不能串台，不自行新增序号；全部结构占位符（含文献引用、链接、代码等）逐字保留，各出现一次且顺序不变。标题、目录、文献条目、域、书签、图片和版式由文档层保留，不输出新的标题或编号。
            所有送改摘要和正文都必须逐句实际改写，不能KEEP_ORIGINAL、回填原稿或只换几个词。密集替换可编辑内容词，但优先平常说法和必要展开，不把每个词升成论文术语；换入口只能补充，不能替代正文换词。专名、数字、单位、引用和序号留存，业务术语可换周围表达来写清，不为换词把指标改成另一概念。
            正面表达家族：通过→靠着/靠；建设→建起/搭建；同比增长→和上一年同期相比增长了；等待时间→等待时长/等候时间；明显季节集中性→很明显的季节集中特点；进一步协同→还需要加强配合；以期→希望能给……；整个链条、一条链上的各方可依语境使用。约20%可说大概两成，不能说一半以上；提高35%不能说达到35%。不把月度同比改成与全年相比。
            不要新增核验、流转、疏解、层级、研究结果显示、管控系统这类升档替换，原文已用的词或专名除外。查验可说查验用的/查验这一步，不为了换掉查验而改成另一业务动作；周转、分流、水平可按原义用普通动作说清。不把通行能力改通行效率、货值改交易总额、温度异常改温度超标、系统故障改系统宕机。保留条件、否定、程度和必要限定：主要不是不变根本不是，减少不变不用再，可能不变必然。原文的应、可、需、鼓励等建议仍写成待办建议，不能写成我们已经做了、我们也明确了或已经落实；可减少不能写成已经减少。不新增实体、调查、步骤或效果。
            只输出本段一版完整正文，不给解释、不标注完成状态，不直接返回未经改写的原稿。
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
    static double candidatePenalty(String source, String output) {
        return formalUpgrades(source, output).size() + overlap(source, output);
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
