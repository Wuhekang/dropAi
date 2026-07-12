package com.dropai.rewrite.service.writing;

import java.time.LocalDateTime;
import java.util.List;

public record ReferenceCandidate(
        String title,
        List<String> authors,
        Integer year,
        String container,
        String volume,
        String issue,
        String pages,
        String doi,
        String url,
        String sourcePlatform,
        String abstractText,
        String searchKeywords,
        LocalDateTime searchedAt,
        List<Integer> applicableChapters,
        double relevanceScore,
        String verificationStatus
) {
    public boolean basicallyVerified() {
        return title != null && !title.isBlank()
                && authors != null && !authors.isEmpty()
                && year != null && year > 1900;
    }
}
