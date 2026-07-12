package com.dropai.rewrite.service.writing;

import java.util.List;

public record ReferenceSearchQuery(
        String projectId,
        String title,
        String major,
        List<String> keywords,
        List<String> chapterTitles,
        int yearStart,
        int yearEnd,
        int maxResults,
        int chineseTarget,
        int englishTarget
) {
    public String joinedKeywords() {
        StringBuilder builder = new StringBuilder();
        if (title != null) builder.append(title).append(' ');
        if (major != null) builder.append(major).append(' ');
        if (keywords != null) keywords.forEach(keyword -> builder.append(keyword).append(' '));
        String text = builder.toString();
        if ((text.contains("职业院校") || text.contains("职业教育")) && text.contains("就业") && text.contains("人工智能")) {
            return "vocational college students employability artificial intelligence vocational education employment skills";
        }
        if (text.contains("人工智能") || text.toLowerCase().contains("ai")) builder.append(" artificial intelligence");
        if (text.contains("职业院校") || text.contains("职业教育")) builder.append(" vocational education vocational college");
        if (text.contains("就业")) builder.append(" employability employment skills");
        return builder.toString().trim();
    }
}
