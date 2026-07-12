package com.dropai.rewrite;

import com.dropai.rewrite.service.writing.ReferenceCandidate;
import com.dropai.rewrite.service.writing.ReferenceSearchQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingGenerationFeatureTests {
    @Test
    void vocationalAiTopicUsesEnglishAcademicSearchTerms() {
        ReferenceSearchQuery query = new ReferenceSearchQuery(
                "p1",
                "人工智能时代职业院校学生就业能力提升路径研究",
                "职业教育",
                List.of("人工智能", "就业能力"),
                List.of("绪论"),
                2020,
                2026,
                20,
                8,
                12
        );
        String keywords = query.joinedKeywords();
        assertTrue(keywords.contains("vocational college"));
        assertTrue(keywords.contains("employability"));
        assertTrue(keywords.contains("artificial intelligence"));
    }

    @Test
    void referenceCandidateRequiresTitleAuthorsAndYearForVerificationPool() {
        ReferenceCandidate valid = new ReferenceCandidate("Title", List.of("Author"), 2024,
                "Journal", "", "", "", "", "https://example.test", "openalex", "",
                "keyword", LocalDateTime.now(), List.of(1), 1.0, "VERIFIED");
        ReferenceCandidate invalid = new ReferenceCandidate("Title", List.of(), 2024,
                "Journal", "", "", "", "", "https://example.test", "openalex", "",
                "keyword", LocalDateTime.now(), List.of(1), 1.0, "UNVERIFIED");
        assertTrue(valid.basicallyVerified());
        assertFalse(invalid.basicallyVerified());
    }
}
