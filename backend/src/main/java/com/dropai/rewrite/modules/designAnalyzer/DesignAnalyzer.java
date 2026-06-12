package com.dropai.rewrite.modules.designAnalyzer;

import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;

@Service
public class DesignAnalyzer {
    public DesignProject analyze(String title, List<DocumentParser.ParsedDocument> documents) {
        DesignProject project = new DesignProject();
        if (title != null && !title.isBlank()) project.setProjectTitle(title.trim());
        String sourceText = documents.stream().map(DocumentParser.ParsedDocument::text).reduce("", (a, b) -> a + "\n" + b);
        String evidence = project.getProjectTitle() + "\n" + sourceText;
        project.setEquipmentName(inferEquipmentName(project.getProjectTitle(), evidence));
        project.setDesignType(detectArchitecture(evidence));
        project.setMainFunctions(detectFunctions(sourceText));
        project.setWorkingPrinciple(buildPrinciple(project.getMainFunctions()));
        Map<String, String> aliases = Map.of(
                "总长", "总长|长度|设备长度", "总宽", "总宽|宽度|设备宽度", "总高", "总高|高度|设备高度",
                "设计载荷", "设计载荷|额定载荷|载荷", "安全系数", "安全系数");
        for (DocumentParser.ParsedDocument document : documents) {
            aliases.forEach((name, alias) -> extractNumber(document, name, alias, project));
        }
        project.getVerificationItems().add("任务书明确参数复核");
        project.getVerificationItems().add("计算结果与图纸尺寸一致性复核");
        return project;
    }
    private String inferEquipmentName(String title, String evidence) {
        String cleaned = title.replace("设计", "").replace("毕业", "").trim();
        if (!cleaned.isBlank() && !cleaned.equals("通用机械类")) return cleaned;
        Map<String, List<String>> names = architectureVocabulary();
        return names.entrySet().stream().filter(entry -> entry.getValue().stream().anyMatch(evidence::contains))
                .map(Map.Entry::getKey).findFirst().orElse("机械设备");
    }

    private String detectArchitecture(String evidence) {
        return architectureVocabulary().entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(evidence::contains))
                .map(Map.Entry::getKey).findFirst().orElse("通用机械结构");
    }

    private Map<String, List<String>> architectureVocabulary() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        catalog.put("沉降分离设备", List.of("重力沉降室", "沉降室", "沉降腔", "排灰斗", "除尘"));
        catalog.put("带式输送设备", List.of("输送机", "输送带", "驱动滚筒", "从动滚筒", "托辊"));
        catalog.put("关节机械手", List.of("机械手", "夹爪", "大臂", "小臂", "末端执行器"));
        return catalog;
    }
    private void extractNumber(DocumentParser.ParsedDocument document, String name, String alias, DesignProject project) {
        Pattern pattern = Pattern.compile("(?:" + alias + ")\\s*[:：=]?\\s*(\\d+(?:\\.\\d+)?)\\s*(mm|m|kg|t)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(document.text());
        if (!matcher.find() || project.getExplicitParameters().stream().anyMatch(item -> name.equals(item.getName()))) return;
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? defaultUnit(name) : matcher.group(2);
        if ("m".equalsIgnoreCase(unit) && name.startsWith("总")) { value *= 1000; unit = "mm"; }
        if ("t".equalsIgnoreCase(unit) && "设计载荷".equals(name)) { value *= 1000; unit = "kg"; }
        project.getExplicitParameters().add(new DesignProject.Parameter(name, value, unit, document.fileName() + "明确给出", null));
    }
    private String defaultUnit(String name) {
        if (name.startsWith("总")) return "mm";
        if ("设计载荷".equals(name)) return "kg";
        return "";
    }

    private List<String> detectFunctions(String text) {
        Map<String, List<String>> vocabulary = new LinkedHashMap<>();
        vocabulary.put("输送与导向", List.of("输送", "传送", "运输", "导向", "送料"));
        vocabulary.put("分离与筛选", List.of("分离", "筛分", "筛选", "沉降", "过滤"));
        vocabulary.put("搅拌与混合", List.of("搅拌", "混合", "均化"));
        vocabulary.put("提升与搬运", List.of("提升", "升降", "搬运", "抓取", "吊装"));
        vocabulary.put("驱动与传动", List.of("驱动", "电机", "减速", "传动", "转动"));
        vocabulary.put("定位与夹持", List.of("定位", "夹持", "夹紧", "固定"));
        vocabulary.put("进料与排料", List.of("进料", "给料", "出料", "排料", "进风", "出风"));
        vocabulary.put("检测与控制", List.of("检测", "监测", "控制", "传感"));
        List<String> functions = new ArrayList<>();
        vocabulary.forEach((function, words) -> {
            if (words.stream().anyMatch(text::contains)) functions.add(function);
        });
        if (functions.isEmpty()) functions.add("承载与工艺处理");
        return functions;
    }

    private String buildPrinciple(List<String> functions) {
        return "设备通过" + String.join("、", functions) + "等功能单元协同工作，主体结构提供工作空间，"
                + "支撑与安装结构传递载荷，接口结构连接上下游系统，检修结构保证维护可达性。";
    }
}
