package com.dropai.rewrite.service.ppt;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes only the metadata projection of a document front matter. It never
 * rewrites source blocks, chapters or candidate-page content.
 */
@Component
public final class PptMetadataNormalizer {
    private static final int FRONT_MATTER_BLOCK_LIMIT = 60;
    private static final String LABEL = "(?:课\\s*题\\s*名\\s*称|题\\s*目|学\\s*院|院\\s*校|学\\s*校|"
            + "专\\s*业|学\\s*号|学\\s*生\\s*姓\\s*名|姓\\s*名|汇\\s*报\\s*人|"
            + "指\\s*导\\s*教\\s*师|教\\s*师|答\\s*辩\\s*日\\s*期|完\\s*成\\s*日\\s*期|日\\s*期)";
    private static final Pattern FIELD = Pattern.compile(
            "^(?:\\d{8,}\\s*)?(" + LABEL + ")(?:(?:\\s*[:：﹕︰]\\s*)|(?:\\s+))(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PARSER_PAGE_PREFIX = Pattern.compile("^\\s*\\[第\\s*\\d+\\s*页]\\s*");
    private static final Pattern CHINESE_DATE = Pattern.compile(
            "^(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*月(?:\\s*(\\d{1,2})\\s*日)?$");
    private static final Pattern SEPARATED_DATE = Pattern.compile(
            "^(20\\d{2})\\s*[-/.]\\s*(\\d{1,2})(?:\\s*[-/.]\\s*(\\d{1,2}))?$");
    private static final Pattern BODY_BOUNDARY = Pattern.compile(
            "^(?:第[一二三四五六七八九十0-9]+章|[1-9]\\d*(?:[.．]\\d+)*\\s*(?:绪论|引言|研究|系统|需求|设计|实现|测试|总结)).*$");

    public Map<String, String> extract(List<String> sourceBlocks) {
        List<String> blocks = frontMatterBlocks(sourceBlocks);
        Map<String, LinkedHashSet<String>> found = new LinkedHashMap<>();
        for (int index = 0; index < blocks.size(); index++) {
            String block = normalizeFrontMatterBlock(blocks.get(index));
            Matcher matcher = FIELD.matcher(block);
            if (matcher.matches()) {
                String key = keyForLabel(matcher.group(1));
                String value = matcher.group(2).strip();
                if ("title".equals(key) && shouldAppendTitleContinuation(value, blocks, index)) {
                    value += normalizeFrontMatterBlock(blocks.get(index + 1));
                }
                add(found, key, normalizeValue(key, value));
            }
            if (!containsField(block) && isDate(block)) {
                add(found, "date", normalizeDate(block));
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        found.forEach((key, values) -> result.put(key, resolveUnique(key, values)));
        return Map.copyOf(result);
    }

    public Map<String, String> normalizeValues(Map<String, String> metadata) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (metadata == null) {
            return Map.of();
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null && !value.isBlank()) {
                normalized.put(key, normalizeValue(key, value));
            }
        });
        return Map.copyOf(normalized);
    }

    List<String> frontMatterBlocks(List<String> sourceBlocks) {
        if (sourceBlocks == null || sourceBlocks.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String raw : sourceBlocks) {
            if (result.size() >= FRONT_MATTER_BLOCK_LIMIT) {
                break;
            }
            String block = normalizeFrontMatterBlock(raw);
            if (block.isBlank()) {
                continue;
            }
            String compact = block.replaceAll("\\s+", "");
            if (isFrontMatterEnd(compact) || BODY_BOUNDARY.matcher(compact).matches()) {
                break;
            }
            result.add(raw);
        }
        return List.copyOf(result);
    }

    String normalizeValue(String key, String value) {
        String normalized = normalizeBlock(value)
                .replaceFirst("^[：:﹕︰]+", "")
                .replaceFirst("[：:﹕︰]+$", "")
                .strip();
        return switch (key) {
            case "presenter", "advisor" -> normalizePersonName(normalized);
            case "studentNumber" -> normalized.replaceAll("\\s+", "");
            case "date" -> normalizeDate(normalized);
            default -> normalized;
        };
    }

    private boolean shouldAppendTitleContinuation(String title, List<String> blocks, int index) {
        if (index + 1 >= blocks.size()) {
            return false;
        }
        String next = normalizeFrontMatterBlock(blocks.get(index + 1));
        if (next.isBlank() || next.length() > 30 || containsField(next) || isDate(next)
                || isFrontMatterEnd(next.replaceAll("\\s+", ""))) {
            return false;
        }
        return (title.matches(".*(?:基于|的|与|和|及)$") && next.matches("^(?:设计|实现|研究|分析).*$"))
                || "设计与实现".equals(next);
    }

    private String normalizePersonName(String value) {
        String compact = value.replaceAll("\\s+", "");
        if (compact.matches("[\\p{IsHan}·]{2,8}")) {
            return compact;
        }
        return value.replaceAll("\\s+", " ").strip();
    }

    private String normalizeDate(String value) {
        Matcher matcher = CHINESE_DATE.matcher(value);
        if (!matcher.matches()) {
            matcher = SEPARATED_DATE.matcher(value);
        }
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported PPT metadata date: " + value);
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        String dayValue = matcher.group(3);
        try {
            if (dayValue == null) {
                YearMonth.of(year, month);
                return year + "年" + month + "月";
            }
            int day = Integer.parseInt(dayValue);
            LocalDate.of(year, month, day);
            return year + "年" + month + "月" + day + "日";
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid PPT metadata date: " + value, exception);
        }
    }

    private boolean isDate(String value) {
        return CHINESE_DATE.matcher(value).matches() || SEPARATED_DATE.matcher(value).matches();
    }

    private boolean containsField(String value) {
        return FIELD.matcher(value).find();
    }

    private String keyForLabel(String label) {
        String normalized = label.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "题目", "课题名称" -> "title";
            case "学院", "院校", "学校" -> "institution";
            case "专业" -> "major";
            case "学号" -> "studentNumber";
            case "学生姓名", "姓名", "汇报人" -> "presenter";
            case "指导教师", "教师" -> "advisor";
            case "答辩日期", "完成日期", "日期" -> "date";
            default -> throw new IllegalArgumentException("Unknown PPT metadata label: " + label);
        };
    }

    private String resolveUnique(String key, Set<String> values) {
        if (values.size() == 1) {
            return values.iterator().next();
        }
        if ("title".equals(key)) {
            String longest = values.stream().max((left, right) -> Integer.compare(left.length(), right.length()))
                    .orElseThrow();
            boolean repeatedFormTruncation = values.stream().allMatch(value -> value.equals(longest)
                    || (longest.startsWith(value)
                    && value.matches(".*(?:的|与|和|及)$")
                    && longest.substring(value.length()).matches("^(?:设计与实现|设计|实现|研究|分析).*$")));
            if (repeatedFormTruncation) {
                return longest;
            }
        }
        throw new IllegalStateException("PPT Rendering V1 metadata is ambiguous: " + key);
    }

    private void add(Map<String, LinkedHashSet<String>> found, String key, String value) {
        if (!value.isBlank()) {
            found.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
        }
    }

    private boolean isFrontMatterEnd(String compact) {
        String lower = compact.toLowerCase(Locale.ROOT);
        return lower.equals("摘要") || lower.startsWith("摘要:") || lower.equals("abstract")
                || lower.startsWith("abstract:") || lower.equals("目录") || lower.startsWith("关键词");
    }

    private String normalizeBlock(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC);
        return PptDocumentParser.clean(normalized)
                .replace('﹕', ':')
                .replace('︰', ':')
                .replace('：', ':');
    }

    private String normalizeFrontMatterBlock(String value) {
        return PARSER_PAGE_PREFIX.matcher(normalizeBlock(value)).replaceFirst("");
    }
}
