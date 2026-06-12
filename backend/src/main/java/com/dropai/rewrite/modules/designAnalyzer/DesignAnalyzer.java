package com.dropai.rewrite.modules.designAnalyzer;

import com.dropai.rewrite.modules.documentParser.DocumentParser;
import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DesignAnalyzer {
    public DesignProject analyze(String title, List<DocumentParser.ParsedDocument> documents) {
        DesignProject project = new DesignProject();
        if (title != null && !title.isBlank()) project.setProjectTitle(title.trim());
        project.setEquipmentName(inferEquipmentName(project.getProjectTitle()));
        project.setDesignType("通用机械类毕业设计");
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
    private String inferEquipmentName(String title) {
        return title.replace("设计", "").replace("毕业", "").trim().isBlank() ? "机械设备" : title.replace("设计", "").trim();
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
}
