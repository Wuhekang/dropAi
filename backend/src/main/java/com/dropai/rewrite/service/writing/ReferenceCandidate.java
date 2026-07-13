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
        String verificationStatus,
        String documentType,
        String language,
        String sourceType,
        String sourceTitle,
        String sourceSnippet
) {
    public ReferenceCandidate(String title, List<String> authors, Integer year, String container, String volume,
                              String issue, String pages, String doi, String url, String sourcePlatform,
                              String abstractText, String searchKeywords, LocalDateTime searchedAt,
                              List<Integer> applicableChapters, double relevanceScore, String verificationStatus) {
        this(title, authors, year, container, volume, issue, pages, doi, url, sourcePlatform, abstractText,
                searchKeywords, searchedAt, applicableChapters, relevanceScore, verificationStatus,
                "JOURNAL", "", "OTHER_PUBLIC", "", "");
    }

    public boolean basicallyVerified() {
        return title != null && !title.isBlank()
                && authors != null && !authors.isEmpty()
                && authors.stream().noneMatch(author -> author == null || author.isBlank() || author.contains("UNVERIFIED"))
                && year != null && year > 1900
                && container != null && !container.isBlank()
                && url != null && !url.isBlank();
    }
}
