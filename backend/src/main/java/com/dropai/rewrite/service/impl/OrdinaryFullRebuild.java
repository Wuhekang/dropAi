package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通降AI·全段密集换词 v12】本次送入的摘要和正文每一段、每一句都必须实际改写。先理解意思，再沿整段连续替换可编辑的内容词和词组，动词、名词表达、形容与判断用语都要处理，不能只动开头、结尾或几个近义词，不留大段原句。换信息入口或调整语序只是可选补充，不能代替正文词语替换。
            每个可编辑内容词/词组都应尝试换成语境相符的平常说法；优先等长或更简短的近义表达，意思成立时以少写为先。检查可依语境换成检验/核验，监测换成监控，协同换成配合/对接，提升换成提高，等待换成等候，明显季节集中性换成明显的季节性特点，仍需进一步协同换成还需要加强合作。示例是表达家族，不是硬字典；查验、核验、核对涉及不同业务时不能随便混成另一操作。专业指标可改周围句法，不把指标改成另一个概念。
            可少量补足的、了、使得、以及、还、就来展开紧缩词组，但不要层层添修饰，不把换词写成冗长解释，不靠扩写制造密度。没有任何字数或字符比例目标、区间、上下限，也不按篇幅评分、拒绝或重试；不为凑字数补句子或删事实。
            功能词、数字、单位、专名、引用和结构占位符保留，除此之外不以准确原表达可保留为由跳过内容词改写。保留事实、条件、否定、因果、统计时段和指标含义，近义不等于随意改事实。提高不变达到，约20%不变一半以上，24小时不概括成全天候，主要不是不加强成根本不是，不把建议写成已经实现。不新增实体、案例、调查、步骤、证据或结论。
            原有序号家族与顺序不能串台，不新增序号；全部结构占位符逐字保留，各出现一次且顺序不变。标题、目录、文献条目、域、书签、图片和版式由文档层保留，不输出新标题或编号。正常的研究发现、据此、因此不是禁词，不用禁词清洗替代全段换词。
            只输出本段一版完整实际改写正文，不解释、不标注状态，不输出KEEP_ORIGINAL，不回填原稿。近原文只少量换词不是完成，输出前逐句核对是否还有未处理的正文词组。
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
