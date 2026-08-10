package com.dropai.rewrite.service.writing;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class OutlineNormalizeService {
    private static final Pattern CHAPTER_PREFIX = Pattern.compile("^(?:(?:第\\s*)?(?:[一二三四五六七八九十百零〇两\\d]+)\\s*章\\s*)+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_PREFIX = Pattern.compile("^(?:\\d+\\s*[.．、-]\\s*\\d+\\s*)+");

    public String chapterTitle(Object value) {
        String normalized = CHAPTER_PREFIX.matcher(clean(value)).replaceFirst("").trim();
        return normalized.isEmpty() ? "未命名章节" : normalized;
    }

    public String sectionTitle(Object value) {
        String normalized = SECTION_PREFIX.matcher(clean(value)).replaceFirst("").trim();
        return normalized.isEmpty() ? "未命名小节" : normalized;
    }

    private String clean(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
    }
}
