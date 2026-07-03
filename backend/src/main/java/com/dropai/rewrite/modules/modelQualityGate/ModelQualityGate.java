package com.dropai.rewrite.modules.modelQualityGate;

import com.dropai.rewrite.modules.model.DesignProject;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ModelQualityGate {
    public Map<String, Object> evaluate(DesignProject project) {
        String deviceType = deviceType(project);
        List<Rule> required = requiredRules(deviceType);
        String text = projectText(project);
        List<String> missing = required.stream()
                .filter(rule -> !rule.pattern().matcher(text).find())
                .map(Rule::name)
                .toList();
        List<String> floating = floatingParts(project);
        List<String> symmetry = symmetryMissing(deviceType, text);
        int partCount = Math.max(project.getComponents().size(), project.getResolvedParts().size());
        int coreCount = required.size() - missing.size();
        boolean graduation = project.getDesignDepth() == null || !project.getDesignDepth().toLowerCase(Locale.ROOT).contains("engineering");
        boolean strictPartCount = graduation && !required.isEmpty() && !"generic".equals(deviceType);
        boolean partCountFailed = strictPartCount && partCount < 30;
        boolean coreCountFailed = graduation && !required.isEmpty() && coreCount < Math.min(8, required.size());

        List<String> issues = new ArrayList<>();
        if (!missing.isEmpty()) issues.add("core_structure_missing");
        if (!symmetry.isEmpty()) issues.add("symmetry_incomplete");
        if (!floating.isEmpty()) issues.add("assembly_connection_missing");
        if (partCountFailed) issues.add("graduation_part_count_insufficient");
        if (coreCountFailed) issues.add("core_structure_count_insufficient");
        int penalty = missing.size() * 12 + symmetry.size() * 8 + floating.size() * 8
                + (partCountFailed ? 15 : 0)
                + (coreCountFailed ? 12 : 0);
        int score = Math.max(0, Math.min(100, 100 - penalty));
        boolean success = issues.isEmpty() && score >= 75;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("code", success ? "MODEL_QUALITY_PASSED" : "MODEL_QUALITY_FAILED");
        result.put("message", success ? "model quality precheck passed" : "model quality precheck failed; frontend will repair or refuse display");
        result.put("deviceType", deviceType);
        result.put("missingParts", missing);
        result.put("floatingParts", floating);
        result.put("symmetryMissing", symmetry);
        result.put("qualityScore", score);
        result.put("partCount", partCount);
        result.put("coreStructureCount", coreCount);
        result.put("requiredCoreCount", required.size());
        result.put("issues", issues);
        return result;
    }

    private String deviceType(DesignProject project) {
        String text = projectText(project);
        if (has(text, "gravity settling", "settling", "\u91cd\u529b\u6c89\u964d", "\u6c89\u964d\u5ba4", "\u9664\u5c18")) return "settling_chamber";
        if (has(text, "crawler", "track", "\u722c\u58c1", "\u5c65\u5e26", "\u6cb9\u7f50", "\u673a\u5668\u4eba", "\u5438\u9644")) return "crawler_robot";
        if (has(text, "conveyor", "\u8f93\u9001\u673a", "\u8f93\u9001\u5e26", "\u6eda\u7b52")) return "conveyor";
        if (has(text, "manipulator", "\u673a\u68b0\u624b", "\u673a\u68b0\u81c2", "\u5939\u722a")) return "manipulator";
        return "generic";
    }

    private List<Rule> requiredRules(String deviceType) {
        if ("settling_chamber".equals(deviceType)) {
            return List.of(
                    rule("\u7bb1\u4f53\u4e3b\u4f53", "\u7bb1\u4f53|\u58f3\u4f53|\u6c89\u964d\u5ba4|chamber|shell"),
                    rule("\u8fdb\u6c14\u53e3", "\u8fdb\u6c14|\u8fdb\u98ce|\u5165\u53e3|inlet"),
                    rule("\u51fa\u6c14\u53e3", "\u51fa\u6c14|\u51fa\u98ce|\u51fa\u53e3|outlet"),
                    rule("\u6269\u6563\u6bb5", "\u6269\u6563|diffuser"),
                    rule("\u7070\u6597", "\u7070\u6597|hopper"),
                    rule("\u5378\u7070\u53e3", "\u5378\u7070|\u6392\u7070|ash outlet|discharge"),
                    rule("\u652f\u6491\u67b6", "\u652f\u6491|\u652f\u67b6|\u652f\u817f|support"),
                    rule("\u68c0\u4fee\u95e8", "\u68c0\u4fee|\u89c2\u5bdf\u5b54|access|inspection"),
                    rule("\u52a0\u5f3a\u7b4b", "\u52a0\u5f3a\u7b4b|\u52a0\u5f3a\u808b|rib")
            );
        }
        if ("crawler_robot".equals(deviceType)) {
            return List.of(
                    rule("\u673a\u67b6", "\u673a\u67b6|\u8f66\u67b6|frame"),
                    rule("\u5c65\u5e26\u673a\u6784", "\u5c65\u5e26|track"),
                    rule("\u9a71\u52a8\u8f6e", "\u9a71\u52a8\u8f6e|drive wheel"),
                    rule("\u4ece\u52a8\u8f6e", "\u4ece\u52a8\u8f6e|idler"),
                    rule("\u652f\u91cd\u8f6e", "\u652f\u91cd\u8f6e|\u6eda\u8f6e|roller"),
                    rule("\u6e05\u626b\u5237", "\u6e05\u626b|\u5237|brush"),
                    rule("\u68c0\u6d4b\u67b6", "\u68c0\u6d4b|\u4f20\u611f|sensor"),
                    rule("\u5916\u58f3", "\u5916\u58f3|\u9632\u62a4|cover")
            );
        }
        return List.of();
    }

    private List<String> floatingParts(DesignProject project) {
        return project.getComponents().stream()
                .filter(component -> blank(component.getMountTo()) && blank(component.getConstraintType()) && !component.isKeyPart())
                .map(DesignProject.Component::getName)
                .filter(name -> name != null && !name.isBlank())
                .limit(12)
                .toList();
    }

    private List<String> symmetryMissing(String deviceType, String text) {
        List<String> result = new ArrayList<>();
        if ("crawler_robot".equals(deviceType)) {
            if (!Pattern.compile("\u5de6.*\u5c65\u5e26|left.*track", Pattern.CASE_INSENSITIVE).matcher(text).find()) result.add("\u5de6\u5c65\u5e26");
            if (!Pattern.compile("\u53f3.*\u5c65\u5e26|right.*track", Pattern.CASE_INSENSITIVE).matcher(text).find()) result.add("\u53f3\u5c65\u5e26");
        }
        if ("settling_chamber".equals(deviceType)) {
            int supportLegs = countPattern(text, "\u652f\u6491\u817f|support leg|\u652f\u817f");
            if (supportLegs > 0 && supportLegs < 4) result.add("\u56db\u89d2\u652f\u6491\u817f");
            if (!has(text, "\u8fdb\u6c14", "\u8fdb\u98ce", "\u5165\u53e3", "inlet")) result.add("\u8fdb\u6c14\u7aef\u7ed3\u6784");
            if (!has(text, "\u51fa\u6c14", "\u51fa\u98ce", "\u51fa\u53e3", "outlet")) result.add("\u51fa\u6c14\u7aef\u7ed3\u6784");
        }
        return result;
    }

    private String projectText(DesignProject project) {
        StringBuilder text = new StringBuilder();
        append(text, project.getProjectTitle());
        append(text, project.getEquipmentName());
        append(text, project.getDesignType());
        append(text, project.getEquipmentType());
        append(text, project.getMainStructures());
        append(text, project.getStructureTree());
        append(text, project.getComponents());
        append(text, project.getResolvedParts());
        append(text, project.getAssemblyConstraints());
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder text, Object value) {
        if (value == null) return;
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) append(text, item);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, mapValue) -> {
                append(text, key);
                append(text, mapValue);
            });
            return;
        }
        text.append(' ').append(value);
        for (Method method : value.getClass().getMethods()) {
            if (!method.getName().startsWith("get") || method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) continue;
            try {
                Object nested = method.invoke(value);
                if (nested != value && nested != null && isSimple(nested)) text.append(' ').append(nested);
            } catch (ReflectiveOperationException ignored) {
                // Best-effort text extraction for quality diagnostics.
            }
        }
    }

    private boolean isSimple(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>;
    }

    private Rule rule(String name, String regex) {
        return new Rule(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    private boolean has(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private int countPattern(String text, String regex) {
        var matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record Rule(String name, Pattern pattern) {}
}
