package com.dropai.rewrite.service.impl;

import java.util.*;
import java.util.regex.Pattern;

/** Ordinary-only candidate acceptance. This is a lexical guard, not a semantic proof. */
final class OrdinaryFullRebuild {
    static final String INSTRUCTION = """
            【普通整段重构 v4】以原稿命题为边界，先理解再整段重写。几乎每个可替换的普通词都用自然、平实的同义表达重新组织，可加一两个字修饰，但不得让程度、范围、因果或结论发生变化。专名、术语没有等义替代时保留，不为换词制造病句。
            不能只换两三个词，也不能靠拆句或并句制造改写幅度；数据、措施、限制与建议之间的句架要重新组织。不要使用三项举措、稳步提升、针对这一现状、尚未充分传导为等宣传套话。年度同比可说比上一年增长；等待时间可说等候时间。周转效率和周转率只有定义等价时才替换。
            数字周边的短语可重写、信息可调整顺序，不要求逐字逐序相同；数值、单位、比较基期、约/超过/可等限定以及提高/达到关系必须等义，不编造实体、事件、调研或改变结论极性。
            序号体系硬锁：原有首先/其次/最后、一是/二是/三是、第一/第二/第三、①②③、第一章/第二章分别保持原有顺序与体系，不混用、不删号、不跳号。这里优先保留原序号文字。
            只输出正文。保留所有结构占位符。不要解释改写过程。
            """;
    private static final Pattern ORDINAL = Pattern.compile("第[一二三四五六七八九十]+章|首先|其次|最后|[一二三四五六七八九十]是|第[一二三四五六七八九十]+(?!章)|[①②③④⑤⑥⑦⑧⑨⑩]");
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?[%％]?");
    private static final Pattern BROCHURE = Pattern.compile("研究发现|据此|以期|综合运用|持续提高|尚未完全转化为|三项举措|稳步提升|针对这一现状|尚未充分传导为");
    private OrdinaryFullRebuild() { }
    static List<String> issues(String source, String output) {
        List<String> issues = new ArrayList<>();
        if (output == null || output.isBlank()) return List.of("输出为空");
        if (!matches(ORDINAL, source).equals(matches(ORDINAL, output))) issues.add("序号体系或顺序发生变化");
        if (!numbers(source).equals(numbers(output))) issues.add("数值发生变化；允许顺序和周边措辞改变，不允许数值改变");
        for (String entity : List.of("实地考察", "海上丝绸之路", "智能仓储")) {
            if (!source.contains(entity) && output.contains(entity)) issues.add("新增未提供的事实：" + entity);
        }
        if (source.contains("约20%") && (!output.contains("约20%") || !(output.contains("等候") || output.contains("等待")))) issues.add("约20%的等待时间含义未保留");
        if (source.contains("提高35%") && !output.matches("(?s).*提高(?:了)?35%.*")) issues.add("提高35%的数量关系未保留");
        if (BROCHURE.matcher(output).find()) issues.add("仍含旧模板或宣传式句架");
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
