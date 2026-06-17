package com.dropai.rewrite.modules.designAnalyzer;

import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;
import com.dropai.rewrite.service.MatrixDesignService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DesignAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(DesignAnalyzer.class);
    private final MatrixDesignService matrixDesignService;
    private final ObjectMapper objectMapper;

    public DesignAnalyzer() {
        this(null, new ObjectMapper());
    }

    public DesignAnalyzer(MatrixDesignService matrixDesignService, ObjectMapper objectMapper) {
        this.matrixDesignService = matrixDesignService;
        this.objectMapper = objectMapper;
    }

    public DesignProject analyze(String title, List<DocumentParser.ParsedDocument> documents) {
        String sourceText = documents.stream()
                .filter(DocumentParser.ParsedDocument::textReadable)
                .map(DocumentParser.ParsedDocument::text)
                .reduce("", (a, b) -> a + "\n" + b);
        DesignProject project = analyzeByRules(title, sourceText, documents);
        if (!sourceText.isBlank() && matrixDesignService != null && matrixDesignService.apiKeyConfigured()) {
            try {
                mergeAiResult(project, matrixDesignService.generate(aiInstructions(), aiPrompt(title, sourceText)));
            } catch (Exception exception) {
                log.warn("AI设计目标识别失败，已使用本地规则识别结果 reason={}", exception.getMessage());
                project.getVerificationItems().add("AI识别增强失败，当前结果由本地规则从任务书中提取：" + compact(exception.getMessage()));
            }
        }
        enforceLocalSemanticGuards(project, title + "\n" + sourceText);
        return project;
    }

    private DesignProject analyzeByRules(String title, String sourceText, List<DocumentParser.ParsedDocument> documents) {
        DesignProject project = new DesignProject();
        project.setProjectId("dp-" + UUID.randomUUID());
        String textTitle = firstNonBlank(title, detectTitle(sourceText));
        project.setProjectTitle(textTitle);
        String evidence = textTitle + "\n" + sourceText;
        project.setEquipmentName(inferEquipmentName(textTitle, evidence));
        project.setDesignType(detectDesignType(evidence));
        project.setProjectCategory("机械类毕业设计");
        project.setMainFunctions(detectFunctions(evidence));
        project.setMainStructures(detectStructures(evidence, project.getEquipmentName(), project.getDesignType()));
        project.setWorkingPrinciple(buildPrinciple(project.getMainFunctions(), project.getMainStructures()));
        extractParameters(documents, project);
        project.getVerificationItems().add("任务书文字读取与识别结果复核");
        project.getVerificationItems().add("论文参数、计算参数和CAD尺寸一致性复核");
        return project;
    }

    private String detectTitle(String text) {
        List<Pattern> patterns = List.of(
                Pattern.compile("(?:毕业设计\\(论文\\)?题目|毕业设计题目|设计题目|课题名称|题目)\\s*[:：]?\\s*([^\\n\\r]{4,80})"),
                Pattern.compile("([^\\n\\r]{4,60}(?:设计|系统|装置|设备|机构|机械手|输送机|沉降室)[^\\n\\r]{0,20})")
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) return cleanTitle(matcher.group(1));
        }
        return "";
    }

    private String cleanTitle(String value) {
        return value == null ? "" : value.replaceAll("[：:，,。；;].*$", "").trim();
    }

    private String inferEquipmentName(String title, String evidence) {
        Map<String, List<String>> names = equipmentVocabulary();
        for (Map.Entry<String, List<String>> entry : names.entrySet()) {
            if (entry.getValue().stream().anyMatch(evidence::contains)) return entry.getKey();
        }
        Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]{2,24})(?:设计|系统|装置|设备)").matcher(title);
        if (matcher.find()) return matcher.group(1).replace("工业通风除尘用", "").trim();
        return title == null || title.isBlank() ? "" : title.replace("设计", "").trim();
    }

    private String detectDesignType(String evidence) {
        if (containsAny(evidence, "爬壁", "履带", "磁吸附", "油罐检测", "壁面检测", "清扫刷")) return "机械结构设计 / 机器人结构设计 / 机电一体化设计";
        if (containsAny(evidence, "PLC", "控制系统", "梯形图", "传感器", "自动控制")) return "PLC控制设计";
        if (containsAny(evidence, "机械手", "夹爪", "气缸", "伺服", "机械臂")) return "自动化设备设计";
        if (containsAny(evidence, "输送机", "输送带", "滚筒", "托辊")) return "输送设备设计";
        if (containsAny(evidence, "除尘", "沉降", "过滤", "废气", "环保")) return "环保设备结构设计";
        if (containsAny(evidence, "齿轮", "链传动", "带传动", "减速器", "轴系")) return "机械传动设计";
        if (containsAny(evidence, "机电", "电机", "驱动", "执行器")) return "机电一体化设计";
        return evidence.isBlank() ? "" : "机械结构设计";
    }

    private void extractParameters(List<DocumentParser.ParsedDocument> documents, DesignProject project) {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("总长", "总长|长度|设备长度|外形长度");
        aliases.put("总宽", "总宽|宽度|设备宽度|外形宽度");
        aliases.put("总高", "总高|高度|设备高度|外形高度");
        aliases.put("设计载荷", "设计载荷|额定载荷|载荷|承载");
        aliases.put("安全系数", "安全系数");
        aliases.put("处理风量", "处理风量|风量");
        aliases.put("输送速度", "输送速度|带速|运行速度");
        aliases.put("输送量", "输送量|生产率|运输量");
        aliases.put("功率", "功率|电机功率|驱动功率");
        aliases.put("爬行速度", "爬行速度|行走速度|运行速度");
        aliases.put("吸附力", "吸附力|磁吸力|附着力");
        aliases.put("清扫效率", "清扫效率");
        aliases.put("检测精度", "检测精度|检测误差");
        aliases.put("续航时间", "续航时间|工作时间");
        for (DocumentParser.ParsedDocument document : documents) {
            if (!document.textReadable()) continue;
            aliases.forEach((name, alias) -> extractNumber(document, name, alias, project));
        }
    }

    private void extractNumber(DocumentParser.ParsedDocument document, String name, String alias, DesignProject project) {
        Pattern pattern = Pattern.compile("(?:" + alias + ")\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)\\s*(mm|m|kg|t|m3/h|m³/h|m/s|kw|kW)?", Pattern.CASE_INSENSITIVE);
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
        if ("处理风量".equals(name)) return "m3/h";
        if ("输送速度".equals(name)) return "m/s";
        if ("功率".equals(name)) return "kW";
        if ("爬行速度".equals(name)) return "m/min";
        if ("吸附力".equals(name)) return "N";
        if ("清扫效率".equals(name)) return "%";
        if ("检测精度".equals(name)) return "mm";
        if ("续航时间".equals(name)) return "h";
        return "";
    }

    private List<String> detectFunctions(String text) {
        Map<String, List<String>> vocabulary = new LinkedHashMap<>();
        vocabulary.put("含尘气流沉降", List.of("沉降", "除尘", "含尘", "颗粒物"));
        vocabulary.put("颗粒物收集", List.of("排灰", "灰斗", "收集"));
        vocabulary.put("连续输送物料", List.of("输送", "运输", "带式", "托辊"));
        vocabulary.put("动力传递", List.of("驱动", "传动", "电机", "减速"));
        vocabulary.put("工件抓取与释放", List.of("机械手", "夹爪", "抓取", "搬运"));
        vocabulary.put("油罐壁面爬行", List.of("爬壁", "履带", "壁面", "油罐"));
        vocabulary.put("磁吸附稳定附着", List.of("磁吸附", "永磁", "吸附力", "附着"));
        vocabulary.put("表面清扫", List.of("清扫", "圆盘刷", "刷盘"));
        vocabulary.put("壁面缺陷检测", List.of("检测", "传感器", "缺陷"));
        vocabulary.put("模块化维护", List.of("模块化", "快拆", "维护"));
        vocabulary.put("设备检修维护", List.of("检修", "维护", "观察窗", "检修门"));
        vocabulary.put("定位安装与支撑", List.of("支撑", "机架", "底座", "安装"));
        List<String> functions = new ArrayList<>();
        vocabulary.forEach((function, words) -> {
            if (words.stream().anyMatch(text::contains)) functions.add(function);
        });
        if (functions.isEmpty() && !text.isBlank()) functions.add("完成指定机械工艺过程");
        return functions;
    }

    private List<String> detectStructures(String evidence, String equipment, String designType) {
        if (containsAny(evidence + equipment + designType, "爬壁", "履带", "磁吸附", "油罐检测", "清扫刷", "壁面检测")) {
            return List.of("履带行走机构", "驱动轮", "从动轮", "支重轮", "履带", "永磁吸附机构", "磁吸附模块",
                    "圆盘清扫刷", "清扫驱动电机", "检测传感器安装架", "滑轨调节机构", "快拆结构", "机架",
                    "防护外壳", "驱动电机", "减速器", "电池/控制模块安装舱");
        }
        if (containsAny(evidence + equipment + designType, "沉降室", "除尘", "灰斗")) {
            return List.of("壳体", "进风口", "沉降腔", "排灰斗", "检修门", "出风口", "支撑架");
        }
        if (containsAny(evidence + equipment + designType, "输送机", "输送带", "滚筒")) {
            return List.of("驱动滚筒", "从动滚筒", "输送带", "机架", "电机减速机", "支腿");
        }
        if (containsAny(evidence + equipment + designType, "机械手", "夹爪", "机械臂")) {
            return List.of("底座", "立柱", "大臂", "小臂", "关节驱动组件", "夹爪");
        }
        return List.of("主体结构", "支撑结构", "连接结构", "安装结构", "功能结构", "检修结构", "接口结构");
    }

    private String buildPrinciple(List<String> functions, List<String> structures) {
        if (functions.isEmpty() && structures.isEmpty()) return "";
        return "设备通过" + String.join("、", functions) + "等功能实现设计目标；"
                + String.join("、", structures) + "共同构成主体、接口、支撑和维护空间。";
    }

    private Map<String, List<String>> equipmentVocabulary() {
        Map<String, List<String>> catalog = new LinkedHashMap<>();
        catalog.put("油罐检测爬壁机器人", List.of("油罐检测爬壁机器人", "爬壁机器人", "油罐检测", "磁吸附", "履带行走", "清扫刷"));
        catalog.put("重力沉降室", List.of("重力沉降室", "沉降室", "沉降腔", "排灰斗", "除尘"));
        catalog.put("带式输送机", List.of("带式输送机", "输送机", "输送带", "驱动滚筒", "从动滚筒"));
        catalog.put("机械手", List.of("机械手", "夹爪", "大臂", "小臂", "末端执行器"));
        return catalog;
    }

    private void mergeAiResult(DesignProject project, String response) throws Exception {
        JsonNode root = objectMapper.readTree(stripFence(response));
        setIfPresent(root, "title", project::setProjectTitle);
        setIfPresent(root, "projectTitle", project::setProjectTitle);
        setIfPresent(root, "equipmentName", project::setEquipmentName);
        setIfPresent(root, "designType", project::setDesignType);
        setIfPresent(root, "projectCategory", project::setProjectCategory);
        if (root.has("mainFunctions")) project.setMainFunctions(readStringArray(root.get("mainFunctions")));
        if (root.has("mainStructures")) project.setMainStructures(readStringArray(root.get("mainStructures")));
        if (root.has("parameters") && root.get("parameters").isArray()) {
            for (JsonNode node : root.get("parameters")) {
                String name = node.path("name").asText("");
                if (name.isBlank() || project.allParameters().stream().anyMatch(p -> name.equals(p.getName()))) continue;
                Object value = node.path("value").isNumber() ? node.path("value").numberValue() : node.path("value").asText("");
                project.getExplicitParameters().add(new DesignProject.Parameter(
                        name, value, node.path("unit").asText(""), node.path("source").asText("AI识别"), null));
            }
        }
    }

    private void enforceLocalSemanticGuards(DesignProject project, String evidence) {
        if (containsAny(evidence, "爬壁", "履带", "磁吸附", "油罐检测", "清扫刷", "壁面检测")) {
            project.setEquipmentName("油罐检测爬壁机器人");
            project.setDesignType("机械结构设计 / 机器人结构设计 / 机电一体化设计");
            project.getMainFunctions().removeIf(item -> containsAny(item, "含尘", "沉降", "颗粒物", "排灰"));
            project.getMainStructures().removeIf(item -> containsAny(item, "沉降", "进风", "出风", "排灰斗", "灰斗", "沉降腔"));
            for (String item : List.of("油罐壁面爬行", "磁吸附稳定附着", "表面清扫", "检测模块安装", "壁面缺陷检测", "模块化维护")) {
                if (!project.getMainFunctions().contains(item)) project.getMainFunctions().add(item);
            }
            for (String item : List.of("履带行走机构", "驱动轮", "从动轮", "支重轮", "履带", "永磁吸附机构", "磁吸附模块",
                    "圆盘清扫刷", "清扫驱动电机", "检测传感器安装架", "滑轨调节机构", "快拆结构", "机架", "防护外壳",
                    "驱动电机", "减速器", "电池/控制模块安装舱")) {
                if (!project.getMainStructures().contains(item)) project.getMainStructures().add(item);
            }
        }
    }

    private void setIfPresent(JsonNode root, String field, java.util.function.Consumer<String> setter) {
        String value = root.path(field).asText("");
        if (!value.isBlank()) setter.accept(value);
    }

    private List<String> readStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (!node.isArray()) return result;
        node.forEach(item -> {
            String value = item.asText("");
            if (!value.isBlank()) result.add(value);
        });
        return result;
    }

    private String aiInstructions() {
        return "你是机械类本科毕业设计任务书识别助手。只输出JSON，不要解释。";
    }

    private String aiPrompt(String title, String text) {
        return """
                请从任务书中识别毕业设计目标，输出JSON：
                {
                  "title":"",
                  "equipmentName":"",
                  "designType":"",
                  "projectCategory":"机械类毕业设计",
                  "mainFunctions":[],
                  "mainStructures":[],
                  "parameters":[{"name":"","value":"","unit":"","source":"任务书"}]
                }
                设计类型从机械结构设计、机械传动设计、环保设备设计、输送设备设计、自动化设备设计、PLC控制设计、机电一体化设计中选择或组合。
                用户填写题目：%s
                任务书内容：
                %s
                """.formatted(title == null ? "" : title, text.substring(0, Math.min(text.length(), 6000)));
    }

    private String stripFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) text = text.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        return text;
    }

    private boolean containsAny(String value, String... words) {
        if (value == null) return false;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return "";
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) return "无详细错误";
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 220 ? result.substring(0, 220) + "..." : result;
    }
}
