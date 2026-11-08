package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通降AI·实测词组展开 v7】按下列实测表达方式完整重述输入正文：在整段范围内连续改写普通词组、谓语和连接成分，把紧缩的书面概念展开成平常的说法，允许补入自然的“的、了、使得、并且、以及、还”等成分。不要只替换开头和结尾，中间各句也要按同样密度处理。
            例如“持续提高”可说“一直都在进步当中”，“仍需进一步协同”可说“还需要加强合作”，“尚未完全转化为全链条稳定性”可说“还没有完全转化为整个链条的稳定性”，“全过程监测”可说“全程监控”。按语境变通，不是固定词典替换，不把示例中没有的事实加到正文里。
            论述顺序合理就保留原顺序，句子数量可以不变。不要为追求变化强制拆句、调序或重建背景—问题—根源骨架。没有压缩任务，不要压短句子，也不设原稿字数比例或扩写上限。让常用表达自然展开，不换成更凝练、更复杂的学术术语。
            “研究发现、经研究发现、但是、因此本文、据此、以期、希望能够”等不是违禁词，按正文语境保留或自然替换。不另做学术润色、禁词清洗或统一收尾，不为了删掉这些词改回紧缩论文表达。正文整体允许充分改写，不要求词语、数字表达及信息顺序逐字相等；依据已有内容重述，不额外编造实体、经历或证据。
            仅保护结构：原有序号体系及顺序不能串台，不自行新增序号；全部结构占位符（含文献引用、链接、代码等）逐字保留，各出现一次且顺序不变。标题、目录、文献条目、域、书签、图片和版式由文档层保留，不输出新的标题或编号。
            只输出本段一版完整正文，不给解释、不标注完成状态，不直接返回未经改写的原稿。
            """;
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
        if (source.length() >= 80 && overlap(source, output) > 0.65) issues.add("整段换词密度不足");
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
