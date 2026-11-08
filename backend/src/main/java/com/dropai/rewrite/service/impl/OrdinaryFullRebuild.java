package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通降AI·轻量入口调整 v11】完整阅读本段，以原文事实为依据改写。先确定本段在解释什么问题，再从已有场景、条件、目的或动作换一个合适的信息入口，把意思写明白。原有顺序已经自然时可保留，不强制所有段落拆句或重排。
            参考六段人工轻调的方法：换入口、说清楚、略收篇幅。不是沿原句逐词换成更长的词，也不是把一处信息解释两遍。换入口后继续处理后面的句组，保留独立信息、条件、否定、因果及引用，不只换开头；自然的准确词语可以保留。必要时把紧缩词组展开，但不要在每个名词前后层层添修饰，也不把所有句子都写成长句。
            改写段篇幅可参考原文的100%—130%，只是软参考，少一些也正常，不为凑比例补话，不为凑改写密度扩到150%以上，不靠删事实来收字。没有字数上下限或长度退回门槛。表达变化来自信息入口与句组安排，不来自冗长解释或刻意口语；不堆“各个、相关、具体、整个、我们、这类情况”等空修饰。
            数字、单位、统计时段、时长、指标含义、专名和引用对应关系保留。主要不是、可能、相对等程度不能加强；24小时不改成全天候，陆路口岸不写成港口。不新增实体、案例、方法、步骤、证据或效果承诺，不把建议写成已经完成。
            原有序号体系及顺序不能串台，不另造序号；全部结构占位符（文献引用、链接、代码等）逐字保留，各出现一次且顺序不变。标题、目录、文献条目、域、书签、图片和版式由文档层保留，不输出新标题或编号。
            不机械禁用研究发现、据此、因此等正常词语，也不统一添加总结句。只输出本段一版完整改写正文，不解释、不标注状态，不回填未经改写的原稿。
            """;
    static String instruction(String type) {
        return INSTRUCTION;
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
